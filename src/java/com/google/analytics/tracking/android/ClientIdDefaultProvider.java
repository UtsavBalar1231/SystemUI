package com.google.analytics.tracking.android;

import android.content.Context;
import com.google.android.gms.common.util.VisibleForTesting;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

class ClientIdDefaultProvider implements DefaultProvider {
    private static ClientIdDefaultProvider sInstance;
    private static final Object sInstanceLock = new Object();
    private String mClientId;
    private boolean mClientIdLoaded = false;
    private final Object mClientIdLock = new Object();
    private final Context mContext;

    public static void initializeProvider(Context c) {
        synchronized (sInstanceLock) {
            if (sInstance == null) {
                sInstance = new ClientIdDefaultProvider(c);
            }
        }
    }

    @VisibleForTesting
    static void dropInstance() {
        synchronized (sInstanceLock) {
            sInstance = null;
        }
    }

    public static ClientIdDefaultProvider getProvider() {
        ClientIdDefaultProvider clientIdDefaultProvider;
        synchronized (sInstanceLock) {
            clientIdDefaultProvider = sInstance;
        }
        return clientIdDefaultProvider;
    }

    protected ClientIdDefaultProvider(Context c) {
        this.mContext = c;
        asyncInitializeClientId();
    }

    public String getValue(String field) {
        if ("&cid".equals(field)) {
            return blockingGetClientId();
        }
        return null;
    }

    private String blockingGetClientId() {
        if (!this.mClientIdLoaded) {
            synchronized (this.mClientIdLock) {
                if (!this.mClientIdLoaded) {
                    Log.v("Waiting for clientId to load");
                    while (true) {
                        try {
                            this.mClientIdLock.wait();
                        } catch (InterruptedException e) {
                            Log.e("Exception while waiting for clientId: " + e);
                        }
                        if (this.mClientIdLoaded) {
                            break;
                        }
                    }
                }
            }
        }
        Log.v("Loaded clientId");
        return this.mClientId;
    }

    private boolean storeClientId(String clientId) {
        try {
            Log.v("Storing clientId.");
            FileOutputStream fos = this.mContext.openFileOutput("gaClientId", 0);
            fos.write(clientId.getBytes());
            fos.close();
            return true;
        } catch (FileNotFoundException e) {
            Log.e("Error creating clientId file.");
            return false;
        } catch (IOException e2) {
            Log.e("Error writing to clientId file.");
            return false;
        }
    }

    /* Access modifiers changed, original: protected */
    public String generateClientId() {
        String result = UUID.randomUUID().toString().toLowerCase();
        if (storeClientId(result)) {
            return result;
        }
        return "0";
    }

    private void asyncInitializeClientId() {
        new Thread("client_id_fetcher") {
            public void run() {
                synchronized (ClientIdDefaultProvider.this.mClientIdLock) {
                    ClientIdDefaultProvider.this.mClientId = ClientIdDefaultProvider.this.initializeClientId();
                    ClientIdDefaultProvider.this.mClientIdLoaded = true;
                    ClientIdDefaultProvider.this.mClientIdLock.notifyAll();
                }
            }
        }.start();
    }

    /* Access modifiers changed, original: 0000 */
    /* JADX WARNING: Removed duplicated region for block: B:17:0x0060  */
    /* JADX WARNING: Removed duplicated region for block: B:22:? A:{SYNTHETIC, RETURN, ORIG_RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:22:? A:{SYNTHETIC, RETURN, ORIG_RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:17:0x0060  */
    /* JADX WARNING: Removed duplicated region for block: B:17:0x0060  */
    /* JADX WARNING: Removed duplicated region for block: B:22:? A:{SYNTHETIC, RETURN, ORIG_RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:22:? A:{SYNTHETIC, RETURN, ORIG_RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:17:0x0060  */
    @com.google.android.gms.common.util.VisibleForTesting
    public java.lang.String initializeClientId() {
        /*
        r9 = this;
        r5 = 0;
        r7 = r9.mContext;	 Catch:{ FileNotFoundException -> 0x003c, IOException -> 0x0050 }
        r8 = "gaClientId";
        r3 = r7.openFileInput(r8);	 Catch:{ FileNotFoundException -> 0x003c, IOException -> 0x0050 }
        r7 = 128; // 0x80 float:1.794E-43 double:6.32E-322;
        r0 = new byte[r7];	 Catch:{ FileNotFoundException -> 0x003c, IOException -> 0x0050 }
        r7 = 0;
        r8 = 128; // 0x80 float:1.794E-43 double:6.32E-322;
        r4 = r3.read(r0, r7, r8);	 Catch:{ FileNotFoundException -> 0x003c, IOException -> 0x0050 }
        r7 = r3.available();	 Catch:{ FileNotFoundException -> 0x003c, IOException -> 0x0050 }
        if (r7 > 0) goto L_0x002a;
    L_0x001b:
        if (r4 <= 0) goto L_0x003e;
    L_0x001d:
        r6 = new java.lang.String;	 Catch:{ FileNotFoundException -> 0x003c, IOException -> 0x0050 }
        r7 = 0;
        r6.<init>(r0, r7, r4);	 Catch:{ FileNotFoundException -> 0x003c, IOException -> 0x0050 }
        r3.close();	 Catch:{ FileNotFoundException -> 0x0068, IOException -> 0x0065 }
        r5 = r6;
    L_0x0027:
        if (r5 == 0) goto L_0x0060;
    L_0x0029:
        return r5;
    L_0x002a:
        r7 = "clientId file seems corrupted, deleting it.";
        com.google.analytics.tracking.android.Log.e(r7);	 Catch:{ FileNotFoundException -> 0x003c, IOException -> 0x0050 }
        r3.close();	 Catch:{ FileNotFoundException -> 0x003c, IOException -> 0x0050 }
        r7 = r9.mContext;	 Catch:{ FileNotFoundException -> 0x003c, IOException -> 0x0050 }
        r8 = "gaClientId";
        r7.deleteFile(r8);	 Catch:{ FileNotFoundException -> 0x003c, IOException -> 0x0050 }
        goto L_0x0027;
    L_0x003c:
        r1 = move-exception;
    L_0x003d:
        goto L_0x0027;
    L_0x003e:
        r7 = "clientId file seems empty, deleting it.";
        com.google.analytics.tracking.android.Log.e(r7);	 Catch:{ FileNotFoundException -> 0x003c, IOException -> 0x0050 }
        r3.close();	 Catch:{ FileNotFoundException -> 0x003c, IOException -> 0x0050 }
        r7 = r9.mContext;	 Catch:{ FileNotFoundException -> 0x003c, IOException -> 0x0050 }
        r8 = "gaClientId";
        r7.deleteFile(r8);	 Catch:{ FileNotFoundException -> 0x003c, IOException -> 0x0050 }
        goto L_0x0027;
    L_0x0050:
        r2 = move-exception;
    L_0x0051:
        r7 = "Error reading clientId file, deleting it.";
        com.google.analytics.tracking.android.Log.e(r7);
        r7 = r9.mContext;
        r8 = "gaClientId";
        r7.deleteFile(r8);
        goto L_0x0027;
    L_0x0060:
        r5 = r9.generateClientId();
        goto L_0x0029;
    L_0x0065:
        r2 = move-exception;
        r5 = r6;
        goto L_0x0051;
    L_0x0068:
        r1 = move-exception;
        r5 = r6;
        goto L_0x003d;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.google.analytics.tracking.android.ClientIdDefaultProvider.initializeClientId():java.lang.String");
    }
}
