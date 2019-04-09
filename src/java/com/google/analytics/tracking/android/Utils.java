package com.google.analytics.tracking.android;

import android.text.TextUtils;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

class Utils {
    private static final char[] HEXBYTES = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    Utils() {
    }

    public static Map<String, String> parseURLParameters(String parameterString) {
        Map<String, String> parameters = new HashMap();
        for (String s : parameterString.split("&")) {
            String[] ss = s.split("=");
            if (ss.length > 1) {
                parameters.put(ss[0], ss[1]);
            } else if (ss.length == 1 && ss[0].length() != 0) {
                parameters.put(ss[0], null);
            }
        }
        return parameters;
    }

    public static double safeParseDouble(String s, double defaultValue) {
        if (s == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static boolean safeParseBoolean(String s, boolean defaultValue) {
        if (s != null) {
            if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes") || s.equalsIgnoreCase("1")) {
                return true;
            }
            if (s.equalsIgnoreCase("false") || s.equalsIgnoreCase("no") || s.equalsIgnoreCase("0")) {
                return false;
            }
            return defaultValue;
        }
        return defaultValue;
    }

    public static String filterCampaign(String campaign) {
        if (TextUtils.isEmpty(campaign)) {
            return null;
        }
        String urlParameters = campaign;
        if (campaign.contains("?")) {
            String[] urlParts = campaign.split("[\\?]");
            if (urlParts.length > 1) {
                urlParameters = urlParts[1];
            }
        }
        if (urlParameters.contains("%3D")) {
            try {
                urlParameters = URLDecoder.decode(urlParameters, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                return null;
            }
        } else if (!urlParameters.contains("=")) {
            return null;
        }
        Map<String, String> paramsMap = parseURLParameters(urlParameters);
        String[] validParameters = new String[]{"dclid", "utm_source", "gclid", "utm_campaign", "utm_medium", "utm_term", "utm_content", "utm_id", "gmob_t"};
        StringBuilder params = new StringBuilder();
        for (int i = 0; i < validParameters.length; i++) {
            if (!TextUtils.isEmpty((CharSequence) paramsMap.get(validParameters[i]))) {
                if (params.length() > 0) {
                    params.append("&");
                }
                params.append(validParameters[i]).append("=").append((String) paramsMap.get(validParameters[i]));
            }
        }
        return params.toString();
    }

    static String getLanguage(Locale locale) {
        if (locale == null || TextUtils.isEmpty(locale.getLanguage())) {
            return null;
        }
        StringBuilder lang = new StringBuilder();
        lang.append(locale.getLanguage().toLowerCase());
        if (!TextUtils.isEmpty(locale.getCountry())) {
            lang.append("-").append(locale.getCountry().toLowerCase());
        }
        return lang.toString();
    }

    public static void putIfAbsent(Map<String, String> hit, String key, String value) {
        if (!hit.containsKey(key)) {
            hit.put(key, value);
        }
    }
}
