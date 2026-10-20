package io.freemytv;

import android.content.Context;
import android.content.SharedPreferences;

/** UI-process preferences. No OS version or device model assumptions. */
final class Performance {
    static volatile boolean lightweight = true, recommendations = false, weather = true, prices = false;
    static volatile int revision;
    private Performance() {}
    static void init(Context c) {
        SharedPreferences p = c.getSharedPreferences("performance", Context.MODE_PRIVATE);
        lightweight = p.getBoolean("lightweight", true);
        recommendations = p.getBoolean("recommendations", false);
        weather = p.getBoolean("weather", true);
        prices = p.getBoolean("prices", false);
    }
    static void set(Context c, String key, boolean value) {
        c.getSharedPreferences("performance", Context.MODE_PRIVATE).edit().putBoolean(key, value).apply();
        init(c);
        revision++;
        Recs.get(c).clearImages();
    }
}
