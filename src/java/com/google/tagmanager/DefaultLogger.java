package com.google.tagmanager;

import android.util.Log;
import com.google.tagmanager.Logger.LogLevel;

class DefaultLogger implements Logger {
    private LogLevel mLogLevel = LogLevel.WARNING;

    DefaultLogger() {
    }

    public void e(String message) {
        if (this.mLogLevel.ordinal() <= LogLevel.ERROR.ordinal()) {
            Log.e("GoogleTagManager", message);
        }
    }

    public void w(String message) {
        if (this.mLogLevel.ordinal() <= LogLevel.WARNING.ordinal()) {
            Log.w("GoogleTagManager", message);
        }
    }
}
