package com.google.analytics.tracking.android;

import android.content.Context;
import android.text.TextUtils;
import com.google.android.gms.analytics.internal.Command;
import com.google.android.gms.common.util.VisibleForTesting;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

class GAThread extends Thread implements AnalyticsThread {
    private static GAThread sInstance;
    private volatile String mClientId;
    private volatile boolean mClosed = false;
    private volatile List<Command> mCommands;
    private final Context mContext;
    private volatile boolean mDisabled = false;
    private volatile String mInstallCampaign;
    private volatile ServiceProxy mServiceProxy;
    private final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue();

    static GAThread getInstance(Context ctx) {
        if (sInstance == null) {
            sInstance = new GAThread(ctx);
        }
        return sInstance;
    }

    private GAThread(Context ctx) {
        super("GAThread");
        if (ctx == null) {
            this.mContext = ctx;
        } else {
            this.mContext = ctx.getApplicationContext();
        }
        start();
    }

    @VisibleForTesting
    GAThread(Context ctx, ServiceProxy proxy) {
        super("GAThread");
        if (ctx == null) {
            this.mContext = ctx;
        } else {
            this.mContext = ctx.getApplicationContext();
        }
        this.mServiceProxy = proxy;
        start();
    }

    /* Access modifiers changed, original: protected */
    @VisibleForTesting
    public void init() {
        this.mServiceProxy.createService();
        this.mCommands = new ArrayList();
        this.mCommands.add(new Command("appendVersion", "&_v".substring(1), "ma3.0.2"));
        this.mCommands.add(new Command("appendQueueTime", "&qt".substring(1), null));
        this.mCommands.add(new Command("appendCacheBuster", "&z".substring(1), null));
    }

    public void sendHit(Map<String, String> hit) {
        final Map<String, String> hitCopy = new HashMap(hit);
        String hitTime = (String) hit.get("&ht");
        if (hitTime != null) {
            try {
                long ht = Long.valueOf(hitTime).longValue();
            } catch (NumberFormatException e) {
                hitTime = null;
            }
        }
        if (hitTime == null) {
            hitCopy.put("&ht", Long.toString(System.currentTimeMillis()));
        }
        queueToThread(new Runnable() {
            public void run() {
                if (TextUtils.isEmpty((CharSequence) hitCopy.get("&cid"))) {
                    hitCopy.put("&cid", GAThread.this.mClientId);
                }
                if (!GoogleAnalytics.getInstance(GAThread.this.mContext).getAppOptOut() && !GAThread.this.isSampledOut(hitCopy)) {
                    if (!TextUtils.isEmpty(GAThread.this.mInstallCampaign)) {
                        GAUsage.getInstance().setDisableUsage(true);
                        hitCopy.putAll(new MapBuilder().setCampaignParamsFromUrl(GAThread.this.mInstallCampaign).build());
                        GAUsage.getInstance().setDisableUsage(false);
                        GAThread.this.mInstallCampaign = null;
                    }
                    GAThread.this.fillAppParameters(hitCopy);
                    GAThread.this.mServiceProxy.putHit(HitBuilder.generateHitParams(hitCopy), Long.valueOf((String) hitCopy.get("&ht")).longValue(), GAThread.this.getUrlScheme(hitCopy), GAThread.this.mCommands);
                }
            }
        });
    }

    private String getUrlScheme(Map<String, String> hit) {
        if (!hit.containsKey("useSecure")) {
            return "https:";
        }
        return !Utils.safeParseBoolean((String) hit.get("useSecure"), true) ? "http:" : "https:";
    }

    private boolean isSampledOut(Map<String, String> hit) {
        if (hit.get("&sf") == null) {
            return false;
        }
        double sampleRate = Utils.safeParseDouble((String) hit.get("&sf"), 100.0d);
        if (sampleRate >= 100.0d || ((double) (hashClientIdForSampling((String) hit.get("&cid")) % 10000)) < 100.0d * sampleRate) {
            return false;
        }
        String hitType = hit.get("&t") != null ? (String) hit.get("&t") : "unknown";
        Log.v(String.format("%s hit sampled out", new Object[]{hitType}));
        return true;
    }

    @VisibleForTesting
    static int hashClientIdForSampling(String clientId) {
        int hashVal = 1;
        if (!TextUtils.isEmpty(clientId)) {
            hashVal = 0;
            for (int charPos = clientId.length() - 1; charPos >= 0; charPos--) {
                char curChar = clientId.charAt(charPos);
                hashVal = (((hashVal << 6) & 268435455) + curChar) + (curChar << 14);
                int lefMost7 = hashVal & 266338304;
                if (lefMost7 != 0) {
                    hashVal ^= lefMost7 >> 21;
                }
            }
        }
        return hashVal;
    }

    private void fillAppParameters(Map<String, String> hit) {
        DefaultProvider appFieldsProvider = AppFieldsDefaultProvider.getProvider();
        Utils.putIfAbsent(hit, "&an", appFieldsProvider.getValue("&an"));
        Utils.putIfAbsent(hit, "&av", appFieldsProvider.getValue("&av"));
        Utils.putIfAbsent(hit, "&aid", appFieldsProvider.getValue("&aid"));
        Utils.putIfAbsent(hit, "&aiid", appFieldsProvider.getValue("&aiid"));
        hit.put("&v", "1");
    }

    public void dispatch() {
        queueToThread(new Runnable() {
            public void run() {
                GAThread.this.mServiceProxy.dispatch();
            }
        });
    }

    public void setForceLocalDispatch() {
        queueToThread(new Runnable() {
            public void run() {
                GAThread.this.mServiceProxy.setForceLocalDispatch();
            }
        });
    }

    /* Access modifiers changed, original: 0000 */
    @VisibleForTesting
    public void queueToThread(Runnable r) {
        this.queue.add(r);
    }

    @VisibleForTesting
    static String getAndClearCampaign(Context context) {
        try {
            FileInputStream input = context.openFileInput("gaInstallData");
            byte[] inputBytes = new byte[8192];
            int readLen = input.read(inputBytes, 0, 8192);
            if (input.available() <= 0) {
                input.close();
                context.deleteFile("gaInstallData");
                if (readLen > 0) {
                    String campaignString = new String(inputBytes, 0, readLen);
                    Log.i("Campaign found: " + campaignString);
                    return campaignString;
                }
                Log.w("Campaign file is empty.");
                return null;
            }
            Log.e("Too much campaign data, ignoring it.");
            input.close();
            context.deleteFile("gaInstallData");
            return null;
        } catch (FileNotFoundException e) {
            Log.i("No campaign data found.");
            return null;
        } catch (IOException e2) {
            Log.e("Error reading campaign data.");
            context.deleteFile("gaInstallData");
            return null;
        }
    }

    private String printStackTrace(Throwable t) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream stream = new PrintStream(baos);
        t.printStackTrace(stream);
        stream.flush();
        return new String(baos.toByteArray());
    }

    public void run() {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Log.w("sleep interrupted in GAThread initialize");
        }
        try {
            if (this.mServiceProxy == null) {
                this.mServiceProxy = new GAServiceProxy(this.mContext, this);
            }
            init();
            this.mClientId = ClientIdDefaultProvider.getProvider().getValue("&cid");
            this.mInstallCampaign = getAndClearCampaign(this.mContext);
        } catch (Throwable t) {
            Log.e("Error initializing the GAThread: " + printStackTrace(t));
            Log.e("Google Analytics will not start up.");
            this.mDisabled = true;
        }
        while (!this.mClosed) {
            try {
                Runnable r = (Runnable) this.queue.take();
                if (!this.mDisabled) {
                    r.run();
                }
            } catch (InterruptedException e2) {
                Log.i(e2.toString());
            } catch (Throwable t2) {
                Log.e("Error on GAThread: " + printStackTrace(t2));
                Log.e("Google Analytics is shutting down.");
                this.mDisabled = true;
            }
        }
    }

    public LinkedBlockingQueue<Runnable> getQueue() {
        return this.queue;
    }

    public Thread getThread() {
        return this;
    }

    /* Access modifiers changed, original: 0000 */
    @VisibleForTesting
    public void close() {
        this.mClosed = true;
        interrupt();
    }

    /* Access modifiers changed, original: 0000 */
    @VisibleForTesting
    public boolean isDisabled() {
        return this.mDisabled;
    }
}
