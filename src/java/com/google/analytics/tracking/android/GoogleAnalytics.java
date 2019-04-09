package com.google.analytics.tracking.android;

import android.content.Context;
import android.text.TextUtils;
import com.google.analytics.tracking.android.GAUsage.Field;
import com.google.android.gms.common.util.VisibleForTesting;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class GoogleAnalytics extends TrackerHandler {
    private static GoogleAnalytics sInstance;
    private volatile Boolean mAppOptOut;
    private Context mContext;
    private Tracker mDefaultTracker;
    private boolean mDryRun;
    private Logger mLogger;
    private AnalyticsThread mThread;
    private final Map<String, Tracker> mTrackers;

    @VisibleForTesting
    protected GoogleAnalytics(Context context) {
        this(context, GAThread.getInstance(context));
    }

    private GoogleAnalytics(Context context, AnalyticsThread thread) {
        this.mAppOptOut = Boolean.valueOf(false);
        this.mTrackers = new HashMap();
        if (context != null) {
            this.mContext = context.getApplicationContext();
            this.mThread = thread;
            AppFieldsDefaultProvider.initializeProvider(this.mContext);
            ScreenResolutionDefaultProvider.initializeProvider(this.mContext);
            ClientIdDefaultProvider.initializeProvider(this.mContext);
            this.mLogger = new DefaultLoggerImpl();
            return;
        }
        throw new IllegalArgumentException("context cannot be null");
    }

    public static GoogleAnalytics getInstance(Context context) {
        Class cls = GoogleAnalytics.class;
        synchronized (GoogleAnalytics.class) {
            if (sInstance == null) {
                sInstance = new GoogleAnalytics(context);
            }
            GoogleAnalytics googleAnalytics = sInstance;
            return googleAnalytics;
        }
    }

    static GoogleAnalytics getInstance() {
        Class cls = GoogleAnalytics.class;
        synchronized (GoogleAnalytics.class) {
            GoogleAnalytics googleAnalytics = sInstance;
            return googleAnalytics;
        }
    }

    @VisibleForTesting
    static GoogleAnalytics getNewInstance(Context context, AnalyticsThread thread) {
        Class cls = GoogleAnalytics.class;
        synchronized (GoogleAnalytics.class) {
            if (sInstance != null) {
                sInstance.close();
            }
            sInstance = new GoogleAnalytics(context, thread);
            GoogleAnalytics googleAnalytics = sInstance;
            return googleAnalytics;
        }
    }

    @VisibleForTesting
    static void clearInstance() {
        Class cls = GoogleAnalytics.class;
        synchronized (GoogleAnalytics.class) {
            sInstance = null;
            clearDefaultProviders();
        }
    }

    @VisibleForTesting
    static void clearDefaultProviders() {
        AppFieldsDefaultProvider.dropInstance();
        ScreenResolutionDefaultProvider.dropInstance();
        ClientIdDefaultProvider.dropInstance();
    }

    public boolean isDryRunEnabled() {
        GAUsage.getInstance().setUsage(Field.GET_DRY_RUN);
        return this.mDryRun;
    }

    public Tracker getTracker(String name, String trackingId) {
        Tracker tracker;
        synchronized (this) {
            if (TextUtils.isEmpty(name)) {
                throw new IllegalArgumentException("Tracker name cannot be empty");
            }
            tracker = (Tracker) this.mTrackers.get(name);
            if (tracker == null) {
                tracker = new Tracker(name, trackingId, this);
                this.mTrackers.put(name, tracker);
                if (this.mDefaultTracker == null) {
                    this.mDefaultTracker = tracker;
                }
            }
            if (!TextUtils.isEmpty(trackingId)) {
                tracker.set("&tid", trackingId);
            }
            GAUsage.getInstance().setUsage(Field.GET_TRACKER);
        }
        return tracker;
    }

    public Tracker getTracker(String trackingId) {
        return getTracker(trackingId, trackingId);
    }

    /* Access modifiers changed, original: 0000 */
    public void sendHit(Map<String, String> hit) {
        synchronized (this) {
            if (hit != null) {
                Utils.putIfAbsent(hit, "&ul", Utils.getLanguage(Locale.getDefault()));
                Utils.putIfAbsent(hit, "&sr", ScreenResolutionDefaultProvider.getProvider().getValue("&sr"));
                hit.put("&_u", GAUsage.getInstance().getAndClearSequence());
                GAUsage.getInstance().getAndClearUsage();
                this.mThread.sendHit(hit);
            } else {
                throw new IllegalArgumentException("hit cannot be null");
            }
        }
    }

    /* Access modifiers changed, original: 0000 */
    @VisibleForTesting
    public void close() {
    }

    public boolean getAppOptOut() {
        GAUsage.getInstance().setUsage(Field.GET_APP_OPT_OUT);
        return this.mAppOptOut.booleanValue();
    }

    public Logger getLogger() {
        return this.mLogger;
    }
}
