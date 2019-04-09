package com.google.analytics.tracking.android;

interface RateLimiter {
    boolean tokenAvailable();
}
