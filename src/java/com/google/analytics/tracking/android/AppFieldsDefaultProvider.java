package com.google.analytics.tracking.android;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import com.google.android.gms.common.util.VisibleForTesting;

class AppFieldsDefaultProvider implements DefaultProvider {
    private static AppFieldsDefaultProvider sInstance;
    private static Object sInstanceLock = new Object();
    protected String mAppId;
    protected String mAppInstallerId;
    protected String mAppName;
    protected String mAppVersion;

    public static void initializeProvider(Context c) {
        synchronized (sInstanceLock) {
            if (sInstance == null) {
                sInstance = new AppFieldsDefaultProvider(c);
            }
        }
    }

    @VisibleForTesting
    static void dropInstance() {
        synchronized (sInstanceLock) {
            sInstance = null;
        }
    }

    public static AppFieldsDefaultProvider getProvider() {
        return sInstance;
    }

    private AppFieldsDefaultProvider(Context c) {
        PackageManager pm = c.getPackageManager();
        this.mAppId = c.getPackageName();
        this.mAppInstallerId = pm.getInstallerPackageName(this.mAppId);
        String appName = this.mAppId;
        String appVersion = null;
        try {
            PackageInfo packageInfo = pm.getPackageInfo(c.getPackageName(), 0);
            if (packageInfo != null) {
                appName = pm.getApplicationLabel(packageInfo.applicationInfo).toString();
                appVersion = packageInfo.versionName;
            }
        } catch (NameNotFoundException e) {
            Log.e("Error retrieving package info: appName set to " + appName);
        }
        this.mAppName = appName;
        this.mAppVersion = appVersion;
    }

    @VisibleForTesting
    protected AppFieldsDefaultProvider() {
    }

    public String getValue(String field) {
        if (field == null) {
            return null;
        }
        if (field.equals("&an")) {
            return this.mAppName;
        }
        if (field.equals("&av")) {
            return this.mAppVersion;
        }
        if (field.equals("&aid")) {
            return this.mAppId;
        }
        if (field.equals("&aiid")) {
            return this.mAppInstallerId;
        }
        return null;
    }
}
