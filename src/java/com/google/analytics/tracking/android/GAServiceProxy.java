package com.google.analytics.tracking.android;

import android.content.Context;
import android.content.Intent;
import com.google.analytics.tracking.android.AnalyticsGmsCoreClient.OnConnectedListener;
import com.google.analytics.tracking.android.AnalyticsGmsCoreClient.OnConnectionFailedListener;
import com.google.android.gms.analytics.internal.Command;
import com.google.android.gms.common.util.VisibleForTesting;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

class GAServiceProxy implements ServiceProxy, OnConnectedListener, OnConnectionFailedListener {
    private volatile AnalyticsClient client;
    private Clock clock;
    private volatile int connectTries;
    private final Context ctx;
    private volatile Timer disconnectCheckTimer;
    private volatile Timer failedConnectTimer;
    private boolean forceLocalDispatch;
    private final GoogleAnalytics gaInstance;
    private long idleTimeout;
    private volatile long lastRequestTime;
    private boolean pendingClearHits;
    private boolean pendingDispatch;
    private boolean pendingServiceDisconnect;
    private final Queue<HitParams> queue;
    private volatile Timer reConnectTimer;
    private volatile ConnectState state;
    private AnalyticsStore store;
    private AnalyticsStore testStore;
    private final AnalyticsThread thread;

    private enum ConnectState {
        CONNECTING,
        CONNECTED_SERVICE,
        CONNECTED_LOCAL,
        BLOCKED,
        PENDING_CONNECTION,
        PENDING_DISCONNECT,
        DISCONNECTED
    }

    private class DisconnectCheckTask extends TimerTask {
        private DisconnectCheckTask() {
        }

        /* synthetic */ DisconnectCheckTask(GAServiceProxy x0, AnonymousClass1 x1) {
            this();
        }

        public void run() {
            Object obj = null;
            if (GAServiceProxy.this.state == ConnectState.CONNECTED_SERVICE && GAServiceProxy.this.queue.isEmpty()) {
                if (GAServiceProxy.this.lastRequestTime + GAServiceProxy.this.idleTimeout >= GAServiceProxy.this.clock.currentTimeMillis()) {
                    obj = 1;
                }
                if (obj == null) {
                    Log.v("Disconnecting due to inactivity");
                    GAServiceProxy.this.disconnectFromService();
                    return;
                }
            }
            GAServiceProxy.this.disconnectCheckTimer.schedule(new DisconnectCheckTask(), GAServiceProxy.this.idleTimeout);
        }
    }

    private class FailedConnectTask extends TimerTask {
        private FailedConnectTask() {
        }

        /* synthetic */ FailedConnectTask(GAServiceProxy x0, AnonymousClass1 x1) {
            this();
        }

        public void run() {
            if (GAServiceProxy.this.state == ConnectState.CONNECTING) {
                GAServiceProxy.this.useStore();
            }
        }
    }

    private static class HitParams {
        private final List<Command> commands;
        private final long hitTimeInMilliseconds;
        private final String path;
        private final Map<String, String> wireFormatParams;

        public HitParams(Map<String, String> wireFormatParams, long hitTimeInMilliseconds, String path, List<Command> commands) {
            this.wireFormatParams = wireFormatParams;
            this.hitTimeInMilliseconds = hitTimeInMilliseconds;
            this.path = path;
            this.commands = commands;
        }

        public Map<String, String> getWireFormatParams() {
            return this.wireFormatParams;
        }

        public long getHitTimeInMilliseconds() {
            return this.hitTimeInMilliseconds;
        }

        public String getPath() {
            return this.path;
        }

        public List<Command> getCommands() {
            return this.commands;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("PATH: ");
            sb.append(this.path);
            if (this.wireFormatParams != null) {
                sb.append("  PARAMS: ");
                for (Entry<String, String> entry : this.wireFormatParams.entrySet()) {
                    sb.append((String) entry.getKey());
                    sb.append("=");
                    sb.append((String) entry.getValue());
                    sb.append(",  ");
                }
            }
            return sb.toString();
        }
    }

    private class ReconnectTask extends TimerTask {
        private ReconnectTask() {
        }

        /* synthetic */ ReconnectTask(GAServiceProxy x0, AnonymousClass1 x1) {
            this();
        }

        public void run() {
            GAServiceProxy.this.connectToService();
        }
    }

    @VisibleForTesting
    GAServiceProxy(Context ctx, AnalyticsThread thread, AnalyticsStore store, GoogleAnalytics gaInstance) {
        this.queue = new ConcurrentLinkedQueue();
        this.idleTimeout = 300000;
        this.testStore = store;
        this.ctx = ctx;
        this.thread = thread;
        this.gaInstance = gaInstance;
        this.clock = new Clock() {
            public long currentTimeMillis() {
                return System.currentTimeMillis();
            }
        };
        this.connectTries = 0;
        this.state = ConnectState.DISCONNECTED;
    }

    GAServiceProxy(Context ctx, AnalyticsThread thread) {
        this(ctx, thread, null, GoogleAnalytics.getInstance(ctx));
    }

    public void putHit(Map<String, String> wireFormatParams, long hitTimeInMilliseconds, String path, List<Command> commands) {
        Log.v("putHit called");
        this.queue.add(new HitParams(wireFormatParams, hitTimeInMilliseconds, path, commands));
        sendQueue();
    }

    public void dispatch() {
        switch (this.state) {
            case CONNECTED_LOCAL:
                dispatchToStore();
                return;
            case CONNECTED_SERVICE:
                return;
            default:
                this.pendingDispatch = true;
                return;
        }
    }

    public void clearHits() {
        Log.v("clearHits called");
        this.queue.clear();
        switch (this.state) {
            case CONNECTED_LOCAL:
                this.store.clearHits(0);
                this.pendingClearHits = false;
                return;
            case CONNECTED_SERVICE:
                this.client.clearHits();
                this.pendingClearHits = false;
                return;
            default:
                this.pendingClearHits = true;
                return;
        }
    }

    /* JADX WARNING: Missing block: B:7:0x001c, code skipped:
            return;
     */
    public synchronized void setForceLocalDispatch() {
        /*
        r2 = this;
        monitor-enter(r2);
        r0 = r2.forceLocalDispatch;	 Catch:{ all -> 0x0023 }
        if (r0 != 0) goto L_0x001d;
    L_0x0005:
        r0 = "setForceLocalDispatch called.";
        com.google.analytics.tracking.android.Log.v(r0);	 Catch:{ all -> 0x0023 }
        r0 = 1;
        r2.forceLocalDispatch = r0;	 Catch:{ all -> 0x0023 }
        r0 = com.google.analytics.tracking.android.GAServiceProxy.AnonymousClass3.$SwitchMap$com$google$analytics$tracking$android$GAServiceProxy$ConnectState;	 Catch:{ all -> 0x0023 }
        r1 = r2.state;	 Catch:{ all -> 0x0023 }
        r1 = r1.ordinal();	 Catch:{ all -> 0x0023 }
        r0 = r0[r1];	 Catch:{ all -> 0x0023 }
        switch(r0) {
            case 1: goto L_0x001b;
            case 2: goto L_0x001f;
            case 3: goto L_0x0026;
            case 4: goto L_0x001b;
            case 5: goto L_0x001b;
            case 6: goto L_0x001b;
            default: goto L_0x001b;
        };
    L_0x001b:
        monitor-exit(r2);
        return;
    L_0x001d:
        monitor-exit(r2);
        return;
    L_0x001f:
        r2.disconnectFromService();	 Catch:{ all -> 0x0023 }
        goto L_0x001b;
    L_0x0023:
        r0 = move-exception;
        monitor-exit(r2);
        throw r0;
    L_0x0026:
        r0 = 1;
        r2.pendingServiceDisconnect = r0;	 Catch:{ all -> 0x0023 }
        goto L_0x001b;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.google.analytics.tracking.android.GAServiceProxy.setForceLocalDispatch():void");
    }

    private Timer cancelTimer(Timer timer) {
        if (timer != null) {
            timer.cancel();
        }
        return null;
    }

    private void clearAllTimers() {
        this.reConnectTimer = cancelTimer(this.reConnectTimer);
        this.failedConnectTimer = cancelTimer(this.failedConnectTimer);
        this.disconnectCheckTimer = cancelTimer(this.disconnectCheckTimer);
    }

    public void createService() {
        if (this.client == null) {
            this.client = new AnalyticsGmsCoreClient(this.ctx, this, this);
            connectToService();
        }
    }

    /* JADX WARNING: Missing block: B:9:0x0023, code skipped:
            return;
     */
    /* JADX WARNING: Missing block: B:30:0x00a1, code skipped:
            r7.lastRequestTime = r7.clock.currentTimeMillis();
     */
    private synchronized void sendQueue() {
        /*
        r7 = this;
        monitor-enter(r7);
        r0 = java.lang.Thread.currentThread();	 Catch:{ all -> 0x0038 }
        r1 = r7.thread;	 Catch:{ all -> 0x0038 }
        r1 = r1.getThread();	 Catch:{ all -> 0x0038 }
        r0 = r0.equals(r1);	 Catch:{ all -> 0x0038 }
        if (r0 == 0) goto L_0x0024;
    L_0x0011:
        r0 = r7.pendingClearHits;	 Catch:{ all -> 0x0038 }
        if (r0 != 0) goto L_0x0034;
    L_0x0015:
        r0 = com.google.analytics.tracking.android.GAServiceProxy.AnonymousClass3.$SwitchMap$com$google$analytics$tracking$android$GAServiceProxy$ConnectState;	 Catch:{ all -> 0x0038 }
        r1 = r7.state;	 Catch:{ all -> 0x0038 }
        r1 = r1.ordinal();	 Catch:{ all -> 0x0038 }
        r0 = r0[r1];	 Catch:{ all -> 0x0038 }
        switch(r0) {
            case 1: goto L_0x006f;
            case 2: goto L_0x0099;
            case 3: goto L_0x0022;
            case 4: goto L_0x0022;
            case 5: goto L_0x0022;
            case 6: goto L_0x00d9;
            default: goto L_0x0022;
        };
    L_0x0022:
        monitor-exit(r7);
        return;
    L_0x0024:
        r0 = r7.thread;	 Catch:{ all -> 0x0038 }
        r0 = r0.getQueue();	 Catch:{ all -> 0x0038 }
        r1 = new com.google.analytics.tracking.android.GAServiceProxy$2;	 Catch:{ all -> 0x0038 }
        r1.<init>();	 Catch:{ all -> 0x0038 }
        r0.add(r1);	 Catch:{ all -> 0x0038 }
        monitor-exit(r7);
        return;
    L_0x0034:
        r7.clearHits();	 Catch:{ all -> 0x0038 }
        goto L_0x0015;
    L_0x0038:
        r0 = move-exception;
        monitor-exit(r7);
        throw r0;
    L_0x003b:
        r0 = r7.queue;	 Catch:{ all -> 0x0038 }
        r6 = r0.poll();	 Catch:{ all -> 0x0038 }
        r6 = (com.google.analytics.tracking.android.GAServiceProxy.HitParams) r6;	 Catch:{ all -> 0x0038 }
        r0 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0038 }
        r0.<init>();	 Catch:{ all -> 0x0038 }
        r1 = "Sending hit to store  ";
        r0 = r0.append(r1);	 Catch:{ all -> 0x0038 }
        r0 = r0.append(r6);	 Catch:{ all -> 0x0038 }
        r0 = r0.toString();	 Catch:{ all -> 0x0038 }
        com.google.analytics.tracking.android.Log.v(r0);	 Catch:{ all -> 0x0038 }
        r0 = r7.store;	 Catch:{ all -> 0x0038 }
        r1 = r6.getWireFormatParams();	 Catch:{ all -> 0x0038 }
        r2 = r6.getHitTimeInMilliseconds();	 Catch:{ all -> 0x0038 }
        r4 = r6.getPath();	 Catch:{ all -> 0x0038 }
        r5 = r6.getCommands();	 Catch:{ all -> 0x0038 }
        r0.putHit(r1, r2, r4, r5);	 Catch:{ all -> 0x0038 }
    L_0x006f:
        r0 = r7.queue;	 Catch:{ all -> 0x0038 }
        r0 = r0.isEmpty();	 Catch:{ all -> 0x0038 }
        if (r0 == 0) goto L_0x003b;
    L_0x0077:
        r0 = r7.pendingDispatch;	 Catch:{ all -> 0x0038 }
        if (r0 == 0) goto L_0x0022;
    L_0x007b:
        r7.dispatchToStore();	 Catch:{ all -> 0x0038 }
        goto L_0x0022;
    L_0x007f:
        r0 = r7.client;	 Catch:{ all -> 0x0038 }
        r1 = r6.getWireFormatParams();	 Catch:{ all -> 0x0038 }
        r2 = r6.getHitTimeInMilliseconds();	 Catch:{ all -> 0x0038 }
        r4 = r6.getPath();	 Catch:{ all -> 0x0038 }
        r5 = r6.getCommands();	 Catch:{ all -> 0x0038 }
        r0.sendHit(r1, r2, r4, r5);	 Catch:{ all -> 0x0038 }
    L_0x0094:
        r0 = r7.queue;	 Catch:{ all -> 0x0038 }
        r0.poll();	 Catch:{ all -> 0x0038 }
    L_0x0099:
        r0 = r7.queue;	 Catch:{ all -> 0x0038 }
        r0 = r0.isEmpty();	 Catch:{ all -> 0x0038 }
        if (r0 == 0) goto L_0x00ab;
    L_0x00a1:
        r0 = r7.clock;	 Catch:{ all -> 0x0038 }
        r0 = r0.currentTimeMillis();	 Catch:{ all -> 0x0038 }
        r7.lastRequestTime = r0;	 Catch:{ all -> 0x0038 }
        goto L_0x0022;
    L_0x00ab:
        r0 = r7.queue;	 Catch:{ all -> 0x0038 }
        r6 = r0.peek();	 Catch:{ all -> 0x0038 }
        r6 = (com.google.analytics.tracking.android.GAServiceProxy.HitParams) r6;	 Catch:{ all -> 0x0038 }
        r0 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0038 }
        r0.<init>();	 Catch:{ all -> 0x0038 }
        r1 = "Sending hit to service   ";
        r0 = r0.append(r1);	 Catch:{ all -> 0x0038 }
        r0 = r0.append(r6);	 Catch:{ all -> 0x0038 }
        r0 = r0.toString();	 Catch:{ all -> 0x0038 }
        com.google.analytics.tracking.android.Log.v(r0);	 Catch:{ all -> 0x0038 }
        r0 = r7.gaInstance;	 Catch:{ all -> 0x0038 }
        r0 = r0.isDryRunEnabled();	 Catch:{ all -> 0x0038 }
        if (r0 == 0) goto L_0x007f;
    L_0x00d2:
        r0 = "Dry run enabled. Hit not actually sent to service.";
        com.google.analytics.tracking.android.Log.v(r0);	 Catch:{ all -> 0x0038 }
        goto L_0x0094;
    L_0x00d9:
        r0 = "Need to reconnect";
        com.google.analytics.tracking.android.Log.v(r0);	 Catch:{ all -> 0x0038 }
        r0 = r7.queue;	 Catch:{ all -> 0x0038 }
        r0 = r0.isEmpty();	 Catch:{ all -> 0x0038 }
        if (r0 != 0) goto L_0x0022;
    L_0x00e7:
        r7.connectToService();	 Catch:{ all -> 0x0038 }
        goto L_0x0022;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.google.analytics.tracking.android.GAServiceProxy.sendQueue():void");
    }

    private void dispatchToStore() {
        this.store.dispatch();
        this.pendingDispatch = false;
    }

    private synchronized void useStore() {
        if (this.state != ConnectState.CONNECTED_LOCAL) {
            clearAllTimers();
            Log.v("falling back to local store");
            if (this.testStore == null) {
                GAServiceManager instance = GAServiceManager.getInstance();
                instance.initialize(this.ctx, this.thread);
                this.store = instance.getStore();
            } else {
                this.store = this.testStore;
            }
            this.state = ConnectState.CONNECTED_LOCAL;
            sendQueue();
        }
    }

    private synchronized void connectToService() {
        if (!this.forceLocalDispatch) {
            if (!(this.client == null || this.state == ConnectState.CONNECTED_LOCAL)) {
                try {
                    this.connectTries++;
                    cancelTimer(this.failedConnectTimer);
                    this.state = ConnectState.CONNECTING;
                    this.failedConnectTimer = new Timer("Failed Connect");
                    this.failedConnectTimer.schedule(new FailedConnectTask(this, null), 3000);
                    Log.v("connecting to Analytics service");
                    this.client.connect();
                } catch (SecurityException e) {
                    Log.w("security exception on connectToService");
                    useStore();
                }
            }
        }
        Log.w("client not initialized.");
        useStore();
        return;
    }

    private synchronized void disconnectFromService() {
        if (this.client != null) {
            if (this.state == ConnectState.CONNECTED_SERVICE) {
                this.state = ConnectState.PENDING_DISCONNECT;
                this.client.disconnect();
            }
        }
    }

    public synchronized void onConnected() {
        this.failedConnectTimer = cancelTimer(this.failedConnectTimer);
        this.connectTries = 0;
        Log.v("Connected to service");
        this.state = ConnectState.CONNECTED_SERVICE;
        if (this.pendingServiceDisconnect) {
            disconnectFromService();
            this.pendingServiceDisconnect = false;
            return;
        }
        sendQueue();
        this.disconnectCheckTimer = cancelTimer(this.disconnectCheckTimer);
        this.disconnectCheckTimer = new Timer("disconnect check");
        this.disconnectCheckTimer.schedule(new DisconnectCheckTask(this, null), this.idleTimeout);
    }

    public synchronized void onDisconnected() {
        if (this.state != ConnectState.PENDING_DISCONNECT) {
            Log.v("Unexpected disconnect.");
            this.state = ConnectState.PENDING_CONNECTION;
            if (this.connectTries >= 2) {
                useStore();
            } else {
                fireReconnectAttempt();
            }
        } else {
            Log.v("Disconnected from service");
            clearAllTimers();
            this.state = ConnectState.DISCONNECTED;
        }
    }

    public synchronized void onConnectionFailed(int errorCode, Intent resolution) {
        this.state = ConnectState.PENDING_CONNECTION;
        if (this.connectTries >= 2) {
            Log.w("Service unavailable (code=" + errorCode + "), using local store.");
            useStore();
        } else {
            Log.w("Service unavailable (code=" + errorCode + "), will retry.");
            fireReconnectAttempt();
        }
    }

    private void fireReconnectAttempt() {
        this.reConnectTimer = cancelTimer(this.reConnectTimer);
        this.reConnectTimer = new Timer("Service Reconnect");
        this.reConnectTimer.schedule(new ReconnectTask(this, null), 5000);
    }
}
