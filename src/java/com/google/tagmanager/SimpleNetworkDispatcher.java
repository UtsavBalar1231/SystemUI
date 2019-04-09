package com.google.tagmanager;

import android.content.Context;
import android.os.Build;
import android.os.Build.VERSION;
import com.google.android.gms.common.util.VisibleForTesting;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import org.apache.http.client.HttpClient;

class SimpleNetworkDispatcher implements Dispatcher {
    private final Context ctx;
    private DispatchListener dispatchListener;
    private final HttpClient httpClient;
    private final String userAgent = createUserAgentString("GoogleTagManager", "3.02", VERSION.RELEASE, getUserAgentLanguage(Locale.getDefault()), Build.MODEL, Build.ID);

    public interface DispatchListener {
    }

    @VisibleForTesting
    SimpleNetworkDispatcher(HttpClient httpClient, Context ctx, DispatchListener dispatchListener) {
        this.ctx = ctx.getApplicationContext();
        this.httpClient = httpClient;
        this.dispatchListener = dispatchListener;
    }

    /* Access modifiers changed, original: 0000 */
    public String createUserAgentString(String product, String version, String release, String language, String model, String id) {
        return String.format("%s/%s (Linux; U; Android %s; %s; %s Build/%s)", new Object[]{product, version, release, language, model, id});
    }

    static String getUserAgentLanguage(Locale locale) {
        if (locale == null || locale.getLanguage() == null || locale.getLanguage().length() == 0) {
            return null;
        }
        StringBuilder lang = new StringBuilder();
        lang.append(locale.getLanguage().toLowerCase());
        if (!(locale.getCountry() == null || locale.getCountry().length() == 0)) {
            lang.append("-").append(locale.getCountry().toLowerCase());
        }
        return lang.toString();
    }

    /* Access modifiers changed, original: 0000 */
    @VisibleForTesting
    public URL getUrl(Hit hit) {
        try {
            return new URL(hit.getHitUrl());
        } catch (MalformedURLException e) {
            Log.e("Error trying to parse the GTM url.");
            return null;
        }
    }
}
