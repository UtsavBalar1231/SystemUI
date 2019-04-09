package com.google.analytics.tracking.android;

import android.text.TextUtils;
import com.google.analytics.tracking.android.GAUsage.Field;
import java.util.HashMap;
import java.util.Map;

public class MapBuilder {
    private Map<String, String> map = new HashMap();

    public MapBuilder set(String paramName, String paramValue) {
        GAUsage.getInstance().setUsage(Field.MAP_BUILDER_SET);
        if (paramName == null) {
            Log.w(" MapBuilder.set() called with a null paramName.");
        } else {
            this.map.put(paramName, paramValue);
        }
        return this;
    }

    public Map<String, String> build() {
        return new HashMap(this.map);
    }

    public static MapBuilder createEvent(String category, String action, String label, Long value) {
        String str = null;
        GAUsage.getInstance().setUsage(Field.CONSTRUCT_EVENT);
        MapBuilder builder = new MapBuilder();
        builder.set("&t", "event");
        builder.set("&ec", category);
        builder.set("&ea", action);
        builder.set("&el", label);
        String str2 = "&ev";
        if (value != null) {
            str = Long.toString(value.longValue());
        }
        builder.set(str2, str);
        return builder;
    }

    public MapBuilder setCampaignParamsFromUrl(String utmParams) {
        GAUsage.getInstance().setUsage(Field.MAP_BUILDER_SET_CAMPAIGN_PARAMS);
        String filteredCampaign = Utils.filterCampaign(utmParams);
        if (TextUtils.isEmpty(filteredCampaign)) {
            return this;
        }
        Map<String, String> paramsMap = Utils.parseURLParameters(filteredCampaign);
        set("&cc", (String) paramsMap.get("utm_content"));
        set("&cm", (String) paramsMap.get("utm_medium"));
        set("&cn", (String) paramsMap.get("utm_campaign"));
        set("&cs", (String) paramsMap.get("utm_source"));
        set("&ck", (String) paramsMap.get("utm_term"));
        set("&ci", (String) paramsMap.get("utm_id"));
        set("&gclid", (String) paramsMap.get("gclid"));
        set("&dclid", (String) paramsMap.get("dclid"));
        set("&gmob_t", (String) paramsMap.get("gmob_t"));
        return this;
    }
}
