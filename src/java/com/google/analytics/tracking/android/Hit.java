package com.google.analytics.tracking.android;

class Hit {
    private final long mHitId;
    private String mHitString;
    private final long mHitTime;
    private String mHitUrlScheme = "https:";

    /* Access modifiers changed, original: 0000 */
    public String getHitParams() {
        return this.mHitString;
    }

    /* Access modifiers changed, original: 0000 */
    public void setHitString(String hitString) {
        this.mHitString = hitString;
    }

    /* Access modifiers changed, original: 0000 */
    public long getHitId() {
        return this.mHitId;
    }

    /* Access modifiers changed, original: 0000 */
    public long getHitTime() {
        return this.mHitTime;
    }

    Hit(String hitString, long hitId, long hitTime) {
        this.mHitString = hitString;
        this.mHitId = hitId;
        this.mHitTime = hitTime;
    }

    /* Access modifiers changed, original: 0000 */
    public String getHitUrlScheme() {
        return this.mHitUrlScheme;
    }

    /* Access modifiers changed, original: 0000 */
    /* JADX WARNING: Missing block: B:1:0x0002, code skipped:
            return;
     */
    public void setHitUrl(java.lang.String r3) {
        /*
        r2 = this;
        if (r3 != 0) goto L_0x0003;
    L_0x0002:
        return;
    L_0x0003:
        r0 = r3.trim();
        r0 = android.text.TextUtils.isEmpty(r0);
        if (r0 != 0) goto L_0x0002;
    L_0x000d:
        r0 = r3.toLowerCase();
        r1 = "http:";
        r0 = r0.startsWith(r1);
        if (r0 != 0) goto L_0x001b;
    L_0x001a:
        return;
    L_0x001b:
        r0 = "http:";
        r2.mHitUrlScheme = r0;
        goto L_0x001a;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.google.analytics.tracking.android.Hit.setHitUrl(java.lang.String):void");
    }
}
