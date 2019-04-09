package com.google.analytics.tracking.android;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build.VERSION;
import android.text.TextUtils;
import com.google.android.gms.analytics.internal.Command;
import com.google.android.gms.common.util.VisibleForTesting;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.http.impl.client.DefaultHttpClient;

class PersistentAnalyticsStore implements AnalyticsStore {
    private static final String CREATE_HITS_TABLE = String.format("CREATE TABLE IF NOT EXISTS %s ( '%s' INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, '%s' INTEGER NOT NULL, '%s' TEXT NOT NULL, '%s' TEXT NOT NULL, '%s' INTEGER);", new Object[]{HITS_TABLE, HIT_ID, HIT_TIME, HIT_URL, HIT_STRING, HIT_APP_ID});
    @VisibleForTesting
    static final String HITS_TABLE = "hits2";
    @VisibleForTesting
    static final String HIT_APP_ID = "hit_app_id";
    @VisibleForTesting
    static final String HIT_ID = "hit_id";
    @VisibleForTesting
    static final String HIT_STRING = "hit_string";
    @VisibleForTesting
    static final String HIT_TIME = "hit_time";
    @VisibleForTesting
    static final String HIT_URL = "hit_url";
    private Clock mClock;
    private final Context mContext;
    private final String mDatabaseName;
    private final AnalyticsDatabaseHelper mDbHelper;
    private volatile Dispatcher mDispatcher;
    private long mLastDeleteStaleHitsTime;
    private final AnalyticsStoreStateListener mListener;

    @VisibleForTesting
    class AnalyticsDatabaseHelper extends SQLiteOpenHelper {
        private boolean mBadDatabase;
        private long mLastDatabaseCheckTime = 0;

        AnalyticsDatabaseHelper(Context context, String databaseName) {
            super(context, databaseName, null, 1);
        }

        private boolean tablePresent(String table, SQLiteDatabase db) {
            Cursor cursor = null;
            try {
                SQLiteDatabase sQLiteDatabase = db;
                cursor = sQLiteDatabase.query("SQLITE_MASTER", new String[]{"name"}, "name=?", new String[]{table}, null, null, null);
                boolean moveToFirst = cursor.moveToFirst();
                if (cursor != null) {
                    cursor.close();
                }
                return moveToFirst;
            } catch (SQLiteException e) {
                Log.w("Error querying for table " + table);
                if (cursor != null) {
                    cursor.close();
                }
                return false;
            } catch (Throwable th) {
                if (cursor != null) {
                    cursor.close();
                }
                throw th;
            }
        }

        public SQLiteDatabase getWritableDatabase() {
            if (this.mBadDatabase) {
                boolean z;
                if (this.mLastDatabaseCheckTime + 3600000 <= PersistentAnalyticsStore.this.mClock.currentTimeMillis()) {
                    z = true;
                } else {
                    z = false;
                }
                if (!z) {
                    throw new SQLiteException("Database creation failed");
                }
            }
            SQLiteDatabase db = null;
            this.mBadDatabase = true;
            this.mLastDatabaseCheckTime = PersistentAnalyticsStore.this.mClock.currentTimeMillis();
            try {
                db = super.getWritableDatabase();
            } catch (SQLiteException e) {
                PersistentAnalyticsStore.this.mContext.getDatabasePath(PersistentAnalyticsStore.this.mDatabaseName).delete();
            }
            if (db == null) {
                db = super.getWritableDatabase();
            }
            this.mBadDatabase = false;
            return db;
        }

        public void onOpen(SQLiteDatabase db) {
            if (VERSION.SDK_INT < 15) {
                Cursor cursor = db.rawQuery("PRAGMA journal_mode=memory", null);
                try {
                    cursor.moveToFirst();
                } finally {
                    cursor.close();
                }
            }
            if (tablePresent(PersistentAnalyticsStore.HITS_TABLE, db)) {
                validateColumnsPresent(db);
            } else {
                db.execSQL(PersistentAnalyticsStore.CREATE_HITS_TABLE);
            }
        }

        private void validateColumnsPresent(SQLiteDatabase db) {
            boolean needsAppId = false;
            Cursor c = db.rawQuery("SELECT * FROM hits2 WHERE 0", null);
            Set<String> columns = new HashSet();
            try {
                String[] columnNames = c.getColumnNames();
                for (Object add : columnNames) {
                    columns.add(add);
                }
                if (columns.remove(PersistentAnalyticsStore.HIT_ID) && columns.remove(PersistentAnalyticsStore.HIT_URL) && columns.remove(PersistentAnalyticsStore.HIT_STRING) && columns.remove(PersistentAnalyticsStore.HIT_TIME)) {
                    if (!columns.remove(PersistentAnalyticsStore.HIT_APP_ID)) {
                        needsAppId = true;
                    }
                    if (!columns.isEmpty()) {
                        throw new SQLiteException("Database has extra columns");
                    } else if (needsAppId) {
                        db.execSQL("ALTER TABLE hits2 ADD COLUMN hit_app_id");
                        return;
                    } else {
                        return;
                    }
                }
                throw new SQLiteException("Database column missing");
            } finally {
                c.close();
            }
        }

        public void onCreate(SQLiteDatabase db) {
            FutureApis.setOwnerOnlyReadWrite(db.getPath());
        }

        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        }
    }

    PersistentAnalyticsStore(AnalyticsStoreStateListener listener, Context ctx) {
        this(listener, ctx, "google_analytics_v2.db");
    }

    @VisibleForTesting
    PersistentAnalyticsStore(AnalyticsStoreStateListener listener, Context ctx, String databaseName) {
        this.mContext = ctx.getApplicationContext();
        this.mDatabaseName = databaseName;
        this.mListener = listener;
        this.mClock = new Clock() {
            public long currentTimeMillis() {
                return System.currentTimeMillis();
            }
        };
        this.mDbHelper = new AnalyticsDatabaseHelper(this.mContext, this.mDatabaseName);
        this.mDispatcher = new SimpleNetworkDispatcher(new DefaultHttpClient(), this.mContext);
        this.mLastDeleteStaleHitsTime = 0;
    }

    @VisibleForTesting
    public void setClock(Clock clock) {
        this.mClock = clock;
    }

    @VisibleForTesting
    public AnalyticsDatabaseHelper getDbHelper() {
        return this.mDbHelper;
    }

    /* Access modifiers changed, original: 0000 */
    @VisibleForTesting
    public void setDispatcher(Dispatcher dispatcher) {
        this.mDispatcher = dispatcher;
    }

    public void clearHits(long appId) {
        boolean z = false;
        SQLiteDatabase db = getWritableDatabase("Error opening database for clearHits");
        if (db != null) {
            if (appId == 0) {
                db.delete(HITS_TABLE, null, null);
            } else {
                db.delete(HITS_TABLE, "hit_app_id = ?", new String[]{Long.valueOf(appId).toString()});
            }
            AnalyticsStoreStateListener analyticsStoreStateListener = this.mListener;
            if (getNumStoredHits() == 0) {
                z = true;
            }
            analyticsStoreStateListener.reportStoreIsEmpty(z);
        }
    }

    public void putHit(Map<String, String> wireFormatParams, long hitTimeInMilliseconds, String path, Collection<Command> commands) {
        deleteStaleHits();
        removeOldHitIfFull();
        fillVersionParameter(wireFormatParams, commands);
        writeHitToDatabase(wireFormatParams, hitTimeInMilliseconds, path);
    }

    private void fillVersionParameter(Map<String, String> wireFormatParams, Collection<Command> commands) {
        String clientVersionParam = "&_v".substring(1);
        if (commands != null) {
            for (Command command : commands) {
                if ("appendVersion".equals(command.getId())) {
                    wireFormatParams.put(clientVersionParam, command.getValue());
                    return;
                }
            }
        }
    }

    private void removeOldHitIfFull() {
        int hitsOverLimit = (getNumStoredHits() - 2000) + 1;
        if (hitsOverLimit > 0) {
            List<String> hitsToDelete = peekHitIds(hitsOverLimit);
            Log.v("Store full, deleting " + hitsToDelete.size() + " hits to make room.");
            deleteHits((String[]) hitsToDelete.toArray(new String[0]));
        }
    }

    private void writeHitToDatabase(Map<String, String> hit, long hitTimeInMilliseconds, String path) {
        SQLiteDatabase db = getWritableDatabase("Error opening database for putHit");
        if (db != null) {
            ContentValues content = new ContentValues();
            content.put(HIT_STRING, generateHitString(hit));
            content.put(HIT_TIME, Long.valueOf(hitTimeInMilliseconds));
            long appSystemId = 0;
            if (hit.containsKey("AppUID")) {
                try {
                    appSystemId = Long.parseLong((String) hit.get("AppUID"));
                } catch (NumberFormatException e) {
                }
            }
            content.put(HIT_APP_ID, Long.valueOf(appSystemId));
            if (path == null) {
                path = "http://www.google-analytics.com/collect";
            }
            if (path.length() != 0) {
                content.put(HIT_URL, path);
                try {
                    db.insert(HITS_TABLE, null, content);
                    this.mListener.reportStoreIsEmpty(false);
                } catch (SQLiteException e2) {
                    Log.w("Error storing hit");
                }
                return;
            }
            Log.w("Empty path: not sending hit");
        }
    }

    static String generateHitString(Map<String, String> urlParams) {
        List<String> keyAndValues = new ArrayList(urlParams.size());
        for (Entry<String, String> entry : urlParams.entrySet()) {
            keyAndValues.add(HitBuilder.encode((String) entry.getKey()) + "=" + HitBuilder.encode((String) entry.getValue()));
        }
        return TextUtils.join("&", keyAndValues);
    }

    /* Access modifiers changed, original: 0000 */
    public List<String> peekHitIds(int maxHits) {
        List<String> hitIds = new ArrayList();
        if (maxHits > 0) {
            SQLiteDatabase db = getWritableDatabase("Error opening database for peekHitIds.");
            if (db == null) {
                return hitIds;
            }
            Cursor cursor = null;
            try {
                cursor = db.query(HITS_TABLE, new String[]{HIT_ID}, null, null, null, null, String.format("%s ASC", new Object[]{HIT_ID}), Integer.toString(maxHits));
                if (cursor.moveToFirst()) {
                    while (true) {
                        hitIds.add(String.valueOf(cursor.getLong(0)));
                        if (!cursor.moveToNext()) {
                            break;
                        }
                    }
                }
                if (cursor != null) {
                    cursor.close();
                }
            } catch (SQLiteException e) {
                Log.w("Error in peekHits fetching hitIds: " + e.getMessage());
                if (cursor != null) {
                    cursor.close();
                }
            } catch (Throwable th) {
                if (cursor != null) {
                    cursor.close();
                }
            }
            return hitIds;
        }
        Log.w("Invalid maxHits specified. Skipping");
        return hitIds;
    }

    /* JADX WARNING: Removed duplicated region for block: B:34:0x00dd  */
    /* JADX WARNING: Removed duplicated region for block: B:34:0x00dd  */
    public java.util.List<com.google.analytics.tracking.android.Hit> peekHits(int r28) {
        /*
        r27 = this;
        r23 = new java.util.ArrayList;
        r23.<init>();
        r3 = "Error opening database for peekHits";
        r0 = r27;
        r2 = r0.getWritableDatabase(r3);
        if (r2 == 0) goto L_0x008f;
    L_0x0010:
        r19 = 0;
        r3 = 2;
        r4 = new java.lang.String[r3];	 Catch:{ SQLiteException -> 0x00b4 }
        r3 = "hit_id";
        r5 = 0;
        r4[r5] = r3;	 Catch:{ SQLiteException -> 0x00b4 }
        r3 = "hit_time";
        r5 = 1;
        r4[r5] = r3;	 Catch:{ SQLiteException -> 0x00b4 }
        r3 = 1;
        r3 = new java.lang.Object[r3];	 Catch:{ SQLiteException -> 0x00b4 }
        r5 = "hit_id";
        r6 = 0;
        r3[r6] = r5;	 Catch:{ SQLiteException -> 0x00b4 }
        r5 = "%s ASC";
        r9 = java.lang.String.format(r5, r3);	 Catch:{ SQLiteException -> 0x00b4 }
        r10 = java.lang.Integer.toString(r28);	 Catch:{ SQLiteException -> 0x00b4 }
        r3 = "hits2";
        r5 = 0;
        r6 = 0;
        r7 = 0;
        r8 = 0;
        r19 = r2.query(r3, r4, r5, r6, r7, r8, r9, r10);	 Catch:{ SQLiteException -> 0x00b4 }
        r24 = new java.util.ArrayList;	 Catch:{ SQLiteException -> 0x00b4 }
        r24.<init>();	 Catch:{ SQLiteException -> 0x00b4 }
        r3 = r19.moveToFirst();	 Catch:{ SQLiteException -> 0x01ab, all -> 0x01a1 }
        if (r3 != 0) goto L_0x0090;
    L_0x004b:
        if (r19 != 0) goto L_0x00b0;
    L_0x004d:
        r18 = 0;
        r3 = 3;
        r7 = new java.lang.String[r3];	 Catch:{ SQLiteException -> 0x0143 }
        r3 = "hit_id";
        r5 = 0;
        r7[r5] = r3;	 Catch:{ SQLiteException -> 0x0143 }
        r3 = "hit_string";
        r5 = 1;
        r7[r5] = r3;	 Catch:{ SQLiteException -> 0x0143 }
        r3 = "hit_url";
        r5 = 2;
        r7[r5] = r3;	 Catch:{ SQLiteException -> 0x0143 }
        r3 = 1;
        r3 = new java.lang.Object[r3];	 Catch:{ SQLiteException -> 0x0143 }
        r5 = "hit_id";
        r6 = 0;
        r3[r6] = r5;	 Catch:{ SQLiteException -> 0x0143 }
        r5 = "%s ASC";
        r12 = java.lang.String.format(r5, r3);	 Catch:{ SQLiteException -> 0x0143 }
        r13 = java.lang.Integer.toString(r28);	 Catch:{ SQLiteException -> 0x0143 }
        r6 = "hits2";
        r8 = 0;
        r9 = 0;
        r10 = 0;
        r11 = 0;
        r5 = r2;
        r19 = r5.query(r6, r7, r8, r9, r10, r11, r12, r13);	 Catch:{ SQLiteException -> 0x0143 }
        r3 = r19.moveToFirst();	 Catch:{ SQLiteException -> 0x0143 }
        if (r3 != 0) goto L_0x0111;
    L_0x008a:
        r15 = r24;
        if (r19 != 0) goto L_0x0175;
    L_0x008e:
        return r24;
    L_0x008f:
        return r23;
    L_0x0090:
        r4 = new com.google.analytics.tracking.android.Hit;	 Catch:{ SQLiteException -> 0x01ab, all -> 0x01a1 }
        r3 = 0;
        r0 = r19;
        r6 = r0.getLong(r3);	 Catch:{ SQLiteException -> 0x01ab, all -> 0x01a1 }
        r3 = 1;
        r0 = r19;
        r8 = r0.getLong(r3);	 Catch:{ SQLiteException -> 0x01ab, all -> 0x01a1 }
        r5 = 0;
        r4.<init>(r5, r6, r8);	 Catch:{ SQLiteException -> 0x01ab, all -> 0x01a1 }
        r0 = r24;
        r0.add(r4);	 Catch:{ SQLiteException -> 0x01b0, all -> 0x01a6 }
        r3 = r19.moveToNext();	 Catch:{ SQLiteException -> 0x01b0, all -> 0x01a6 }
        if (r3 == 0) goto L_0x004b;
    L_0x00af:
        goto L_0x0090;
    L_0x00b0:
        r19.close();
        goto L_0x004d;
    L_0x00b4:
        r21 = move-exception;
    L_0x00b5:
        r3 = new java.lang.StringBuilder;	 Catch:{ all -> 0x00d9 }
        r3.<init>();	 Catch:{ all -> 0x00d9 }
        r5 = "Error in peekHits fetching hitIds: ";
        r3 = r3.append(r5);	 Catch:{ all -> 0x00d9 }
        r5 = r21.getMessage();	 Catch:{ all -> 0x00d9 }
        r3 = r3.append(r5);	 Catch:{ all -> 0x00d9 }
        r3 = r3.toString();	 Catch:{ all -> 0x00d9 }
        com.google.analytics.tracking.android.Log.w(r3);	 Catch:{ all -> 0x00d9 }
        r15 = r23;
        if (r19 != 0) goto L_0x00d5;
    L_0x00d4:
        return r15;
    L_0x00d5:
        r19.close();
        goto L_0x00d4;
    L_0x00d9:
        r16 = move-exception;
    L_0x00da:
        if (r19 != 0) goto L_0x00dd;
    L_0x00dc:
        throw r16;
    L_0x00dd:
        r19.close();
        goto L_0x00dc;
    L_0x00e1:
        r0 = r24;
        r1 = r18;
        r3 = r0.get(r1);	 Catch:{ SQLiteException -> 0x0143 }
        r3 = (com.google.analytics.tracking.android.Hit) r3;	 Catch:{ SQLiteException -> 0x0143 }
        r5 = 1;
        r0 = r19;
        r5 = r0.getString(r5);	 Catch:{ SQLiteException -> 0x0143 }
        r3.setHitString(r5);	 Catch:{ SQLiteException -> 0x0143 }
        r0 = r24;
        r1 = r18;
        r3 = r0.get(r1);	 Catch:{ SQLiteException -> 0x0143 }
        r3 = (com.google.analytics.tracking.android.Hit) r3;	 Catch:{ SQLiteException -> 0x0143 }
        r5 = 2;
        r0 = r19;
        r5 = r0.getString(r5);	 Catch:{ SQLiteException -> 0x0143 }
        r3.setHitUrl(r5);	 Catch:{ SQLiteException -> 0x0143 }
    L_0x0109:
        r18 = r18 + 1;
        r3 = r19.moveToNext();	 Catch:{ SQLiteException -> 0x0143 }
        if (r3 == 0) goto L_0x008a;
    L_0x0111:
        r0 = r19;
        r0 = (android.database.sqlite.SQLiteCursor) r0;	 Catch:{ SQLiteException -> 0x0143 }
        r3 = r0;
        r20 = r3.getWindow();	 Catch:{ SQLiteException -> 0x0143 }
        r3 = r20.getNumRows();	 Catch:{ SQLiteException -> 0x0143 }
        if (r3 > 0) goto L_0x00e1;
    L_0x0120:
        r3 = 1;
        r5 = new java.lang.Object[r3];	 Catch:{ SQLiteException -> 0x0143 }
        r0 = r24;
        r1 = r18;
        r3 = r0.get(r1);	 Catch:{ SQLiteException -> 0x0143 }
        r3 = (com.google.analytics.tracking.android.Hit) r3;	 Catch:{ SQLiteException -> 0x0143 }
        r6 = r3.getHitId();	 Catch:{ SQLiteException -> 0x0143 }
        r3 = java.lang.Long.valueOf(r6);	 Catch:{ SQLiteException -> 0x0143 }
        r6 = 0;
        r5[r6] = r3;	 Catch:{ SQLiteException -> 0x0143 }
        r3 = "HitString for hitId %d too large.  Hit will be deleted.";
        r3 = java.lang.String.format(r3, r5);	 Catch:{ SQLiteException -> 0x0143 }
        com.google.analytics.tracking.android.Log.w(r3);	 Catch:{ SQLiteException -> 0x0143 }
        goto L_0x0109;
    L_0x0143:
        r21 = move-exception;
        r3 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0190 }
        r3.<init>();	 Catch:{ all -> 0x0190 }
        r5 = "Error in peekHits fetching hitString: ";
        r3 = r3.append(r5);	 Catch:{ all -> 0x0190 }
        r5 = r21.getMessage();	 Catch:{ all -> 0x0190 }
        r3 = r3.append(r5);	 Catch:{ all -> 0x0190 }
        r3 = r3.toString();	 Catch:{ all -> 0x0190 }
        com.google.analytics.tracking.android.Log.w(r3);	 Catch:{ all -> 0x0190 }
        r26 = new java.util.ArrayList;	 Catch:{ all -> 0x0190 }
        r26.<init>();	 Catch:{ all -> 0x0190 }
        r22 = 0;
        r25 = r24.iterator();	 Catch:{ all -> 0x0190 }
    L_0x016a:
        r3 = r25.hasNext();	 Catch:{ all -> 0x0190 }
        if (r3 != 0) goto L_0x017a;
    L_0x0170:
        r17 = r26;
        if (r19 != 0) goto L_0x0199;
    L_0x0174:
        return r26;
    L_0x0175:
        r19.close();
        goto L_0x008e;
    L_0x017a:
        r4 = r25.next();	 Catch:{ all -> 0x0190 }
        r4 = (com.google.analytics.tracking.android.Hit) r4;	 Catch:{ all -> 0x0190 }
        r3 = r4.getHitParams();	 Catch:{ all -> 0x0190 }
        r3 = android.text.TextUtils.isEmpty(r3);	 Catch:{ all -> 0x0190 }
        if (r3 != 0) goto L_0x0194;
    L_0x018a:
        r0 = r26;
        r0.add(r4);	 Catch:{ all -> 0x0190 }
        goto L_0x016a;
    L_0x0190:
        r14 = move-exception;
        if (r19 != 0) goto L_0x019d;
    L_0x0193:
        throw r14;
    L_0x0194:
        if (r22 != 0) goto L_0x0170;
    L_0x0196:
        r22 = 1;
        goto L_0x018a;
    L_0x0199:
        r19.close();
        goto L_0x0174;
    L_0x019d:
        r19.close();
        goto L_0x0193;
    L_0x01a1:
        r16 = move-exception;
        r23 = r24;
        goto L_0x00da;
    L_0x01a6:
        r16 = move-exception;
        r23 = r24;
        goto L_0x00da;
    L_0x01ab:
        r21 = move-exception;
        r23 = r24;
        goto L_0x00b5;
    L_0x01b0:
        r21 = move-exception;
        r23 = r24;
        goto L_0x00b5;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.google.analytics.tracking.android.PersistentAnalyticsStore.peekHits(int):java.util.List");
    }

    /* Access modifiers changed, original: 0000 */
    @VisibleForTesting
    public void setLastDeleteStaleHitsTime(long timeInMilliseconds) {
        this.mLastDeleteStaleHitsTime = timeInMilliseconds;
    }

    /* Access modifiers changed, original: 0000 */
    public int deleteStaleHits() {
        int i;
        boolean z = false;
        long now = this.mClock.currentTimeMillis();
        if (now > this.mLastDeleteStaleHitsTime + 86400000) {
            i = 1;
        } else {
            boolean i2 = false;
        }
        if (i2 == 0) {
            return 0;
        }
        this.mLastDeleteStaleHitsTime = now;
        SQLiteDatabase db = getWritableDatabase("Error opening database for deleteStaleHits.");
        if (db == null) {
            return 0;
        }
        long lastGoodTime = this.mClock.currentTimeMillis() - 2592000000L;
        int rslt = db.delete(HITS_TABLE, "HIT_TIME < ?", new String[]{Long.toString(lastGoodTime)});
        AnalyticsStoreStateListener analyticsStoreStateListener = this.mListener;
        if (getNumStoredHits() == 0) {
            z = true;
        }
        analyticsStoreStateListener.reportStoreIsEmpty(z);
        return rslt;
    }

    /* Access modifiers changed, original: 0000 */
    @Deprecated
    public void deleteHits(Collection<Hit> hits) {
        if (hits == null || hits.isEmpty()) {
            Log.w("Empty/Null collection passed to deleteHits.");
            return;
        }
        String[] hitIds = new String[hits.size()];
        int i = 0;
        for (Hit h : hits) {
            int i2 = i + 1;
            hitIds[i] = String.valueOf(h.getHitId());
            i = i2;
        }
        deleteHits(hitIds);
    }

    /* Access modifiers changed, original: 0000 */
    public void deleteHits(String[] hitIds) {
        boolean z = false;
        if (hitIds == null || hitIds.length == 0) {
            Log.w("Empty hitIds passed to deleteHits.");
            return;
        }
        SQLiteDatabase db = getWritableDatabase("Error opening database for deleteHits.");
        if (db != null) {
            Object[] objArr = new Object[1];
            objArr[0] = TextUtils.join(",", Collections.nCopies(hitIds.length, "?"));
            try {
                db.delete(HITS_TABLE, String.format("HIT_ID in (%s)", objArr), hitIds);
                AnalyticsStoreStateListener analyticsStoreStateListener = this.mListener;
                if (getNumStoredHits() == 0) {
                    z = true;
                }
                analyticsStoreStateListener.reportStoreIsEmpty(z);
            } catch (SQLiteException e) {
                Log.w("Error deleting hits " + hitIds);
            }
        }
    }

    /* Access modifiers changed, original: 0000 */
    public int getNumStoredHits() {
        int numStoredHits = 0;
        SQLiteDatabase db = getWritableDatabase("Error opening database for getNumStoredHits.");
        if (db == null) {
            return 0;
        }
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT COUNT(*) from hits2", null);
            if (cursor.moveToFirst()) {
                numStoredHits = (int) cursor.getLong(0);
            }
            if (cursor != null) {
                cursor.close();
            }
        } catch (SQLiteException e) {
            Log.w("Error getting numStoredHits");
            if (cursor != null) {
                cursor.close();
            }
        } catch (Throwable th) {
            if (cursor != null) {
                cursor.close();
            }
        }
        return numStoredHits;
    }

    public void dispatch() {
        Log.v("Dispatch running...");
        if (this.mDispatcher.okToDispatch()) {
            List<Hit> hits = peekHits(40);
            if (hits.isEmpty()) {
                Log.v("...nothing to dispatch");
                this.mListener.reportStoreIsEmpty(true);
                return;
            }
            int hitsDispatched = this.mDispatcher.dispatchHits(hits);
            Log.v("sent " + hitsDispatched + " of " + hits.size() + " hits");
            deleteHits(hits.subList(0, Math.min(hitsDispatched, hits.size())));
            if (hitsDispatched == hits.size() && getNumStoredHits() > 0) {
                GAServiceManager.getInstance().dispatchLocalHits();
            }
        }
    }

    public Dispatcher getDispatcher() {
        return this.mDispatcher;
    }

    /* Access modifiers changed, original: 0000 */
    @VisibleForTesting
    public AnalyticsDatabaseHelper getHelper() {
        return this.mDbHelper;
    }

    private SQLiteDatabase getWritableDatabase(String errorMessage) {
        try {
            return this.mDbHelper.getWritableDatabase();
        } catch (SQLiteException e) {
            Log.w(errorMessage);
            return null;
        }
    }
}
