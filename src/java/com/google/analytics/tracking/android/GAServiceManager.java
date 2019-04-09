package com.google.analytics.tracking.android;

import android.content.Context;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Message;
import com.google.analytics.tracking.android.GAUsage.Field;
import com.google.android.gms.common.util.VisibleForTesting;

public class GAServiceManager extends ServiceManager {
    private static final Object MSG_OBJECT = new Object();
    private static GAServiceManager instance;
    private boolean connected = true;
    private Context ctx;
    private int dispatchPeriodInSeconds = 1800;
    private Handler handler;
    private boolean listenForNetwork = true;
    private AnalyticsStoreStateListener listener = new AnalyticsStoreStateListener() {
        public void reportStoreIsEmpty(boolean isEmpty) {
            GAServiceManager.this.updatePowerSaveMode(isEmpty, GAServiceManager.this.connected);
        }
    };
    private GANetworkReceiver networkReceiver;
    private boolean pendingDispatch = true;
    private boolean pendingForceLocalDispatch;
    private String pendingHostOverride;
    private AnalyticsStore store;
    private boolean storeIsEmpty = false;
    private volatile AnalyticsThread thread;

    public static GAServiceManager getInstance() {
        if (instance == null) {
            instance = new GAServiceManager();
        }
        return instance;
    }

    private GAServiceManager() {
    }

    @VisibleForTesting
    static void clearInstance() {
        instance = null;
    }

    @VisibleForTesting
    GAServiceManager(Context ctx, AnalyticsThread thread, AnalyticsStore store, boolean listenForNetwork) {
        this.store = store;
        this.thread = thread;
        this.listenForNetwork = listenForNetwork;
        initialize(ctx, thread);
    }

    private void initializeNetworkReceiver() {
        this.networkReceiver = new GANetworkReceiver(this);
        this.networkReceiver.register(this.ctx);
    }

    private void initializeHandler() {
        this.handler = new Handler(this.ctx.getMainLooper(), new Callback() {
            public boolean handleMessage(Message msg) {
                if (1 == msg.what && GAServiceManager.MSG_OBJECT.equals(msg.obj)) {
                    GAUsage.getInstance().setDisableUsage(true);
                    GAServiceManager.this.dispatchLocalHits();
                    GAUsage.getInstance().setDisableUsage(false);
                    if (GAServiceManager.this.dispatchPeriodInSeconds > 0 && !GAServiceManager.this.storeIsEmpty) {
                        GAServiceManager.this.handler.sendMessageDelayed(GAServiceManager.this.handler.obtainMessage(1, GAServiceManager.MSG_OBJECT), (long) (GAServiceManager.this.dispatchPeriodInSeconds * 1000));
                    }
                }
                return true;
            }
        });
        if (this.dispatchPeriodInSeconds > 0) {
            this.handler.sendMessageDelayed(this.handler.obtainMessage(1, MSG_OBJECT), (long) (this.dispatchPeriodInSeconds * 1000));
        }
    }

    /* Access modifiers changed, original: declared_synchronized */
    /* JADX WARNING: Missing block: B:7:0x0010, code skipped:
            return;
     */
    public synchronized void initialize(android.content.Context r2, com.google.analytics.tracking.android.AnalyticsThread r3) {
        /*
        r1 = this;
        monitor-enter(r1);
        r0 = r1.ctx;	 Catch:{ all -> 0x0024 }
        if (r0 != 0) goto L_0x0011;
    L_0x0005:
        r0 = r2.getApplicationContext();	 Catch:{ all -> 0x0024 }
        r1.ctx = r0;	 Catch:{ all -> 0x0024 }
        r0 = r1.thread;	 Catch:{ all -> 0x0024 }
        if (r0 == 0) goto L_0x0013;
    L_0x000f:
        monitor-exit(r1);
        return;
    L_0x0011:
        monitor-exit(r1);
        return;
    L_0x0013:
        r1.thread = r3;	 Catch:{ all -> 0x0024 }
        r0 = r1.pendingDispatch;	 Catch:{ all -> 0x0024 }
        if (r0 != 0) goto L_0x0027;
    L_0x0019:
        r0 = r1.pendingForceLocalDispatch;	 Catch:{ all -> 0x0024 }
        if (r0 == 0) goto L_0x000f;
    L_0x001d:
        r1.setForceLocalDispatch();	 Catch:{ all -> 0x0024 }
        r0 = 0;
        r1.pendingForceLocalDispatch = r0;	 Catch:{ all -> 0x0024 }
        goto L_0x000f;
    L_0x0024:
        r0 = move-exception;
        monitor-exit(r1);
        throw r0;
    L_0x0027:
        r1.dispatchLocalHits();	 Catch:{ all -> 0x0024 }
        r0 = 0;
        r1.pendingDispatch = r0;	 Catch:{ all -> 0x0024 }
        goto L_0x0019;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.google.analytics.tracking.android.GAServiceManager.initialize(android.content.Context, com.google.analytics.tracking.android.AnalyticsThread):void");
    }

    /* Access modifiers changed, original: 0000 */
    @VisibleForTesting
    public AnalyticsStoreStateListener getListener() {
        return this.listener;
    }

    /* Access modifiers changed, original: declared_synchronized */
    public synchronized AnalyticsStore getStore() {
        if (this.store == null) {
            if (this.ctx != null) {
                this.store = new PersistentAnalyticsStore(this.listener, this.ctx);
                if (this.pendingHostOverride != null) {
                    this.store.getDispatcher().overrideHostUrl(this.pendingHostOverride);
                    this.pendingHostOverride = null;
                }
            } else {
                throw new IllegalStateException("Cant get a store unless we have a context");
            }
        }
        if (this.handler == null) {
            initializeHandler();
        }
        if (this.networkReceiver == null && this.listenForNetwork) {
            initializeNetworkReceiver();
        }
        return this.store;
    }

    /* Access modifiers changed, original: declared_synchronized */
    @VisibleForTesting
    public synchronized void overrideHostUrl(String hostOverride) {
        if (this.store != null) {
            this.store.getDispatcher().overrideHostUrl(hostOverride);
        } else {
            this.pendingHostOverride = hostOverride;
        }
    }

    @Deprecated
    public synchronized void dispatchLocalHits() {
        if (this.thread != null) {
            GAUsage.getInstance().setUsage(Field.DISPATCH);
            this.thread.dispatch();
            return;
        }
        Log.v("Dispatch call queued. Dispatch will run once initialization is complete.");
        this.pendingDispatch = true;
    }

    @Deprecated
    public void setForceLocalDispatch() {
        if (this.thread != null) {
            GAUsage.getInstance().setUsage(Field.SET_FORCE_LOCAL_DISPATCH);
            this.thread.setForceLocalDispatch();
            return;
        }
        Log.v("setForceLocalDispatch() queued. It will be called once initialization is complete.");
        this.pendingForceLocalDispatch = true;
    }

    /* Access modifiers changed, original: declared_synchronized */
    @VisibleForTesting
    public synchronized void updatePowerSaveMode(boolean storeIsEmpty, boolean connected) {
        if (this.storeIsEmpty == storeIsEmpty) {
            if (this.connected == connected) {
                return;
            }
        }
        if ((storeIsEmpty || !connected) && this.dispatchPeriodInSeconds > 0) {
            this.handler.removeMessages(1, MSG_OBJECT);
        }
        if (!storeIsEmpty && connected) {
            if (this.dispatchPeriodInSeconds > 0) {
                this.handler.sendMessageDelayed(this.handler.obtainMessage(1, MSG_OBJECT), (long) (this.dispatchPeriodInSeconds * 1000));
            }
        }
        StringBuilder append = new StringBuilder().append("PowerSaveMode ");
        String str = (!storeIsEmpty && connected) ? "terminated." : "initiated.";
        Log.v(append.append(str).toString());
        this.storeIsEmpty = storeIsEmpty;
        this.connected = connected;
    }

    /* Access modifiers changed, original: declared_synchronized */
    public synchronized void updateConnectivityStatus(boolean connected) {
        updatePowerSaveMode(this.storeIsEmpty, connected);
    }

    /* Access modifiers changed, original: declared_synchronized */
    public synchronized void onRadioPowered() {
        if (!this.storeIsEmpty) {
            if (this.connected && this.dispatchPeriodInSeconds > 0) {
                this.handler.removeMessages(1, MSG_OBJECT);
                this.handler.sendMessage(this.handler.obtainMessage(1, MSG_OBJECT));
            }
        }
    }
}
