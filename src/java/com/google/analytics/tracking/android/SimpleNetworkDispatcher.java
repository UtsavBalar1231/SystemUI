package com.google.analytics.tracking.android;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Build.VERSION;
import android.text.TextUtils;
import com.google.android.gms.common.util.VisibleForTesting;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;

class SimpleNetworkDispatcher implements Dispatcher {
    private final Context ctx;
    private GoogleAnalytics gaInstance;
    private final HttpClient httpClient;
    private URL mOverrideHostUrl;
    private final String userAgent;

    @VisibleForTesting
    SimpleNetworkDispatcher(HttpClient httpClient, GoogleAnalytics gaInstance, Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.userAgent = createUserAgentString("GoogleAnalytics", "3.0", VERSION.RELEASE, Utils.getLanguage(Locale.getDefault()), Build.MODEL, Build.ID);
        this.httpClient = httpClient;
        this.gaInstance = gaInstance;
    }

    SimpleNetworkDispatcher(HttpClient httpClient, Context ctx) {
        this(httpClient, GoogleAnalytics.getInstance(ctx), ctx);
    }

    public boolean okToDispatch() {
        NetworkInfo network = ((ConnectivityManager) this.ctx.getSystemService("connectivity")).getActiveNetworkInfo();
        if (network != null && network.isConnected()) {
            return true;
        }
        Log.v("...no network connectivity");
        return false;
    }

    public int dispatchHits(List<Hit> hits) {
        int hitsDispatched = 0;
        int maxHits = Math.min(hits.size(), 40);
        boolean firstSend = true;
        for (int i = 0; i < maxHits; i++) {
            Hit hit = (Hit) hits.get(i);
            URL url = getUrl(hit);
            if (url != null) {
                HttpHost httpHost = new HttpHost(url.getHost(), url.getPort(), url.getProtocol());
                String path = url.getPath();
                String params = !TextUtils.isEmpty(hit.getHitParams()) ? HitBuilder.postProcessHit(hit, System.currentTimeMillis()) : "";
                HttpEntityEnclosingRequest request = buildRequest(params, path);
                if (request != null) {
                    request.addHeader("Host", httpHost.toHostString());
                    if (Log.isVerbose()) {
                        logDebugInformation(request);
                    }
                    if (params.length() > 8192) {
                        Log.w("Hit too long (> 8192 bytes)--not sent");
                    } else if (this.gaInstance.isDryRunEnabled()) {
                        Log.i("Dry run enabled. Hit not actually sent.");
                    } else {
                        if (firstSend) {
                            GANetworkReceiver.sendRadioPoweredBroadcast(this.ctx);
                            firstSend = false;
                        }
                        try {
                            HttpResponse response = this.httpClient.execute(httpHost, request);
                            int statusCode = response.getStatusLine().getStatusCode();
                            HttpEntity entity = response.getEntity();
                            if (entity != null) {
                                entity.consumeContent();
                            }
                            if (statusCode != 200) {
                                Log.w("Bad response: " + response.getStatusLine().getStatusCode());
                            }
                        } catch (ClientProtocolException e) {
                            Log.w("ClientProtocolException sending hit; discarding hit...");
                        } catch (IOException e2) {
                            Log.w("Exception sending hit: " + e2.getClass().getSimpleName());
                            Log.w(e2.getMessage());
                            return hitsDispatched;
                        }
                    }
                }
            } else if (Log.isVerbose()) {
                Log.w("No destination: discarding hit: " + hit.getHitParams());
            } else {
                Log.w("No destination: discarding hit.");
            }
            hitsDispatched++;
        }
        return hitsDispatched;
    }

    private HttpEntityEnclosingRequest buildRequest(String params, String path) {
        if (TextUtils.isEmpty(params)) {
            Log.w("Empty hit, discarding.");
            return null;
        }
        HttpEntityEnclosingRequest request;
        String full = path + "?" + params;
        if (full.length() >= 2036) {
            request = new BasicHttpEntityEnclosingRequest("POST", path);
            try {
                request.setEntity(new StringEntity(params));
            } catch (UnsupportedEncodingException e) {
                Log.w("Encoding error, discarding hit");
                return null;
            }
        }
        request = new BasicHttpEntityEnclosingRequest("GET", full);
        request.addHeader("User-Agent", this.userAgent);
        return request;
    }

    private void logDebugInformation(HttpEntityEnclosingRequest request) {
        StringBuffer httpHeaders = new StringBuffer();
        for (Header header : request.getAllHeaders()) {
            httpHeaders.append(header.toString()).append("\n");
        }
        httpHeaders.append(request.getRequestLine().toString()).append("\n");
        if (request.getEntity() != null) {
            try {
                InputStream is = request.getEntity().getContent();
                if (is != null) {
                    int avail = is.available();
                    if (avail > 0) {
                        byte[] b = new byte[avail];
                        is.read(b);
                        httpHeaders.append("POST:\n");
                        httpHeaders.append(new String(b)).append("\n");
                    }
                }
            } catch (IOException e) {
                Log.v("Error Writing hit to log...");
            }
        }
        Log.v(httpHeaders.toString());
    }

    /* Access modifiers changed, original: 0000 */
    public String createUserAgentString(String product, String version, String release, String language, String model, String id) {
        return String.format("%s/%s (Linux; U; Android %s; %s; %s Build/%s)", new Object[]{product, version, release, language, model, id});
    }

    /* Access modifiers changed, original: 0000 */
    @VisibleForTesting
    public URL getUrl(Hit hit) {
        if (this.mOverrideHostUrl != null) {
            return this.mOverrideHostUrl;
        }
        try {
            String str;
            if ("http:".equals(hit.getHitUrlScheme())) {
                str = "http://www.google-analytics.com/collect";
            } else {
                str = "https://ssl.google-analytics.com/collect";
            }
            return new URL(str);
        } catch (MalformedURLException e) {
            Log.e("Error trying to parse the hardcoded host url. This really shouldn't happen.");
            return null;
        }
    }

    @VisibleForTesting
    public void overrideHostUrl(String hostUrl) {
        try {
            this.mOverrideHostUrl = new URL(hostUrl);
        } catch (MalformedURLException e) {
            this.mOverrideHostUrl = null;
        }
    }
}
