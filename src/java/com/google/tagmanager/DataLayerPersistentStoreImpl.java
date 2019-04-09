package com.google.tagmanager;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build.VERSION;
import com.google.android.gms.common.util.VisibleForTesting;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;

class DataLayerPersistentStoreImpl {
    private static final String CREATE_MAPS_TABLE = String.format("CREATE TABLE IF NOT EXISTS %s ( '%s' INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, '%s' STRING NOT NULL, '%s' BLOB NOT NULL, '%s' INTEGER NOT NULL);", new Object[]{"datalayer", "ID", "key", "value", "expires"});
    private Clock mClock;
    private final Context mContext;
    private DatabaseHelper mDbHelper;
    private final Executor mExecutor;
    private int mMaxNumStoredItems;

    @VisibleForTesting
    class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context context, String databaseName) {
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
            SQLiteDatabase db = null;
            try {
                db = super.getWritableDatabase();
            } catch (SQLiteException e) {
                DataLayerPersistentStoreImpl.this.mContext.getDatabasePath("google_tagmanager.db").delete();
            }
            if (db != null) {
                return db;
            }
            return super.getWritableDatabase();
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
            if (tablePresent("datalayer", db)) {
                validateColumnsPresent(db);
            } else {
                db.execSQL(DataLayerPersistentStoreImpl.CREATE_MAPS_TABLE);
            }
        }

        private void validateColumnsPresent(SQLiteDatabase db) {
            Cursor c = db.rawQuery("SELECT * FROM datalayer WHERE 0", null);
            Set<String> columns = new HashSet();
            try {
                String[] columnNames = c.getColumnNames();
                for (Object add : columnNames) {
                    columns.add(add);
                }
                if (!columns.remove("key") || !columns.remove("value") || !columns.remove("ID") || !columns.remove("expires")) {
                    throw new SQLiteException("Database column missing");
                } else if (!columns.isEmpty()) {
                    throw new SQLiteException("Database has extra columns");
                }
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

    @VisibleForTesting
    DataLayerPersistentStoreImpl(Context context, Clock clock, String databaseName, int maxNumStoredItems, Executor executor) {
        this.mContext = context;
        this.mClock = clock;
        this.mMaxNumStoredItems = maxNumStoredItems;
        this.mExecutor = executor;
        this.mDbHelper = new DatabaseHelper(this.mContext, databaseName);
    }
}
