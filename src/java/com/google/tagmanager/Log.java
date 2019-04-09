package com.google.tagmanager;

import com.google.android.gms.common.util.VisibleForTesting;

final class Log {
    @VisibleForTesting
    static Logger sLogger = new DefaultLogger();

    Log() {
    }

    public static void e(String message) {
        sLogger.e(message);
    }

    public static void w(String message) {
        sLogger.w(message);
    }
}
