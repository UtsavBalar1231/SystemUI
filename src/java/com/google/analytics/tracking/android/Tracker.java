package com.google.analytics.tracking.android;

import android.text.TextUtils;
import com.google.analytics.tracking.android.GAUsage.Field;
import com.google.android.gms.common.util.VisibleForTesting;
import java.util.HashMap;
import java.util.Map;

public class Tracker {
    private final AppFieldsDefaultProvider mAppFieldsDefaultProvider;
    private final ClientIdDefaultProvider mClientIdDefaultProvider;
    private final TrackerHandler mHandler;
    private final String mName;
    private final Map<String, String> mParams;
    private RateLimiter mRateLimiter;
    private final ScreenResolutionDefaultProvider mScreenResolutionDefaultProvider;

    Tracker(String name, String trackingId, TrackerHandler handler) {
        this(name, trackingId, handler, ClientIdDefaultProvider.getProvider(), ScreenResolutionDefaultProvider.getProvider(), AppFieldsDefaultProvider.getProvider(), new SendHitRateLimiter());
    }

    @VisibleForTesting
    Tracker(String name, String trackingId, TrackerHandler handler, ClientIdDefaultProvider clientIdDefaultProvider, ScreenResolutionDefaultProvider screenResolutionDefaultProvider, AppFieldsDefaultProvider appFieldsDefaultProvider, RateLimiter rateLimiter) {
        this.mParams = new HashMap();
        if (TextUtils.isEmpty(name)) {
            throw new IllegalArgumentException("Tracker name cannot be empty.");
        }
        this.mName = name;
        this.mHandler = handler;
        this.mParams.put("&tid", trackingId);
        this.mParams.put("useSecure", "1");
        this.mClientIdDefaultProvider = clientIdDefaultProvider;
        this.mScreenResolutionDefaultProvider = screenResolutionDefaultProvider;
        this.mAppFieldsDefaultProvider = appFieldsDefaultProvider;
        this.mRateLimiter = rateLimiter;
    }

    /* Access modifiers changed, original: 0000 */
    @VisibleForTesting
    public RateLimiter getRateLimiter() {
        return this.mRateLimiter;
    }

    public void send(Map<String, String> params) {
        GAUsage.getInstance().setUsage(Field.SEND);
        Map<String, String> paramsToSend = new HashMap();
        paramsToSend.putAll(this.mParams);
        if (params != null) {
            paramsToSend.putAll(params);
        }
        if (TextUtils.isEmpty((CharSequence) paramsToSend.get("&tid"))) {
            Log.w(String.format("Missing tracking id (%s) parameter.", new Object[]{"&tid"}));
        }
        String hitType = (String) paramsToSend.get("&t");
        if (TextUtils.isEmpty(hitType)) {
            Log.w(String.format("Missing hit type (%s) parameter.", new Object[]{"&t"}));
            hitType = "";
        }
        if (hitType.equals("transaction") || hitType.equals("item") || this.mRateLimiter.tokenAvailable()) {
            this.mHandler.sendHit(paramsToSend);
        } else {
            Log.w("Too many hits sent too quickly, rate limiting invoked.");
        }
    }

    public void set(String key, String value) {
        GAUsage.getInstance().setUsage(Field.SET);
        if (value != null) {
            this.mParams.put(key, value);
        } else {
            this.mParams.remove(key);
        }
    }
}
