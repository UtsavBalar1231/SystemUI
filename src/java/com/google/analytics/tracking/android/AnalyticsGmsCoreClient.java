package com.google.analytics.tracking.android;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import com.google.android.gms.analytics.internal.Command;
import com.google.android.gms.analytics.internal.IAnalyticsService;
import com.google.android.gms.analytics.internal.IAnalyticsService.Stub;
import java.util.List;
import java.util.Map;

class AnalyticsGmsCoreClient implements AnalyticsClient {
    private ServiceConnection mConnection;
    private Context mContext;
    private OnConnectedListener mOnConnectedListener;
    private OnConnectionFailedListener mOnConnectionFailedListener;
    private IAnalyticsService mService;

    final class AnalyticsServiceConnection implements ServiceConnection {
        AnalyticsServiceConnection() {
        }

        public void onServiceConnected(ComponentName component, IBinder binder) {
            Log.v("service connected, binder: " + binder);
            try {
                if ("com.google.android.gms.analytics.internal.IAnalyticsService".equals(binder.getInterfaceDescriptor())) {
                    Log.v("bound to service");
                    AnalyticsGmsCoreClient.this.mService = Stub.asInterface(binder);
                    AnalyticsGmsCoreClient.this.onServiceBound();
                    return;
                }
            } catch (RemoteException e) {
            }
            AnalyticsGmsCoreClient.this.mContext.unbindService(this);
            AnalyticsGmsCoreClient.this.mConnection = null;
            AnalyticsGmsCoreClient.this.mOnConnectionFailedListener.onConnectionFailed(2, null);
        }

        public void onServiceDisconnected(ComponentName component) {
            Log.v("service disconnected: " + component);
            AnalyticsGmsCoreClient.this.mConnection = null;
            AnalyticsGmsCoreClient.this.mOnConnectedListener.onDisconnected();
        }
    }

    public interface OnConnectedListener {
        void onConnected();

        void onDisconnected();
    }

    public interface OnConnectionFailedListener {
        void onConnectionFailed(int i, Intent intent);
    }

    public AnalyticsGmsCoreClient(Context context, OnConnectedListener onConnectedListener, OnConnectionFailedListener onConnectionFailedListener) {
        this.mContext = context;
        if (onConnectedListener != null) {
            this.mOnConnectedListener = onConnectedListener;
            if (onConnectionFailedListener != null) {
                this.mOnConnectionFailedListener = onConnectionFailedListener;
                return;
            }
            throw new IllegalArgumentException("onConnectionFailedListener cannot be null");
        }
        throw new IllegalArgumentException("onConnectedListener cannot be null");
    }

    public void connect() {
        Intent intent = new Intent("com.google.android.gms.analytics.service.START");
        intent.setComponent(new ComponentName("com.google.android.gms", "com.google.android.gms.analytics.service.AnalyticsService"));
        intent.putExtra("app_package_name", this.mContext.getPackageName());
        if (this.mConnection == null) {
            this.mConnection = new AnalyticsServiceConnection();
            boolean result = this.mContext.bindService(intent, this.mConnection, 129);
            Log.v("connect: bindService returned " + result + " for " + intent);
            if (!result) {
                this.mConnection = null;
                this.mOnConnectionFailedListener.onConnectionFailed(1, null);
            }
            return;
        }
        Log.e("Calling connect() while still connected, missing disconnect().");
    }

    public void disconnect() {
        this.mService = null;
        if (this.mConnection != null) {
            try {
                this.mContext.unbindService(this.mConnection);
            } catch (IllegalStateException e) {
            } catch (IllegalArgumentException e2) {
            }
            this.mConnection = null;
            this.mOnConnectedListener.onDisconnected();
        }
    }

    public void sendHit(Map<String, String> wireParams, long hitTimeInMilliseconds, String path, List<Command> commands) {
        try {
            getService().sendHit(wireParams, hitTimeInMilliseconds, path, commands);
        } catch (RemoteException e) {
            Log.e("sendHit failed: " + e);
        }
    }

    public void clearHits() {
        try {
            getService().clearHits();
        } catch (RemoteException e) {
            Log.e("clear hits failed: " + e);
        }
    }

    private IAnalyticsService getService() {
        checkConnected();
        return this.mService;
    }

    /* Access modifiers changed, original: protected */
    public void checkConnected() {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected. Call connect() and wait for onConnected() to be called.");
        }
    }

    public boolean isConnected() {
        return this.mService != null;
    }

    private void onServiceBound() {
        onConnectionSuccess();
    }

    private void onConnectionSuccess() {
        this.mOnConnectedListener.onConnected();
    }
}
