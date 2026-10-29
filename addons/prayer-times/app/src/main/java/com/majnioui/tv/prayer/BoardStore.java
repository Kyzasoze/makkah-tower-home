package com.majnioui.tv.prayer;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** The mosque board's JSON: fetching it and the copy kept on the TV (shared by the page and the adhan alarms). */
final class BoardStore {
    private static final String TAG = "PrayerBoard";
    static final String PREFS = "board";
    static final String KEY_ADHAN_ON = "adhan_on";

    private BoardStore() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
    }

    static boolean adhanOn(Context c) {
        return prefs(c).getBoolean(KEY_ADHAN_ON, true);
    }

    /** Wake the TV from standby for the Fajr adhan (and send it back to standby afterwards if nobody stirs). */
    static boolean fajrWake(Context c) {
        return prefs(c).getBoolean("fajr_wake", true);
    }

    // ---- share edition: where the times come from (set by Home's setup through ConfigReceiver) ----

    static String board(Context c) {
        return prefs(c).getString("cfg_board", c.getString(R.string.board_id));
    }

    static String place(Context c) {
        return prefs(c).getString("cfg_place", c.getString(R.string.place));
    }

    static String url(Context c) {
        return "https://masjidboardlive.com/boards/api/board.php?id=" + board(c)
                + "&v=" + (System.currentTimeMillis() / 60_000L);
    }

    /** Today's times as board JSON: the mosque's board, or Aladhan's calculation for the city and method. */
    static String fetchToday(Context c) throws Exception {
        if (!board(c).isEmpty()) return fetch(url(c));
        SharedPreferences p = prefs(c);
        String u = String.format(Locale.ROOT, "https://api.aladhan.com/v1/timings?latitude=%.4f&longitude=%.4f&method=%d",
                Double.longBitsToDouble(p.getLong("cfg_lat", Double.doubleToLongBits(21.4225))),
                Double.longBitsToDouble(p.getLong("cfg_lon", Double.doubleToLongBits(39.8262))), p.getInt("cfg_method", 4));
        JSONObject t = new JSONObject(fetch(u)).getJSONObject("data").getJSONObject("timings");
        JSONObject d = new JSONObject();
        d.put("fajrAthan", hm(t.optString("Fajr")));
        d.put("sehriEnds", hm(t.optString("Imsak")));
        d.put("sunrise", hm(t.optString("Sunrise")));
        d.put("dhuhrAthan", hm(t.optString("Dhuhr")));
        d.put("asrAthan", hm(t.optString("Asr")));
        d.put("sunset", hm(t.optString("Sunset")));
        d.put("maghribAthan", hm(t.optString("Maghrib")));
        d.put("eshaAthan", hm(t.optString("Isha")));
        d.put("meta", new JSONObject().put("masjid", place(c)).put("city", place(c)));
        return new JSONObject().put("ok", true).put("data", d).toString();
    }

    private static String hm(String v) {
        v = v == null ? "" : v.trim();
        int sp = v.indexOf(' ');
        return sp > 0 ? v.substring(0, sp) : v;
    }

    /** Fetches the board and saves it; returns the raw body, or null when it could not be read. */
    static String refresh(Context c) {
        try {
            String body = fetchToday(c);
            JSONObject json = new JSONObject(body);
            if (!json.optBoolean("ok", true) || json.optJSONObject("data") == null) return null;
            String now = new SimpleDateFormat("HH:mm", Locale.ROOT).format(new Date());
            prefs(c).edit().putString("json", body).putString("at", today() + " " + now).apply();
            return body;
        } catch (Exception e) {
            Log.w(TAG, "refresh: " + e);
            return null;
        }
    }

    /** The saved board's "data" object, or null. */
    static JSONObject cached(Context c) {
        String s = prefs(c).getString("json", null);
        if (s == null) return null;
        try {
            return new JSONObject(s).getJSONObject("data");
        } catch (Exception e) {
            return null;
        }
    }

    /** True when the saved board was read today (its times are today's). */
    static boolean cachedIsToday(Context c) {
        return prefs(c).getString("at", "").startsWith(today());
    }

    static String clean(String v) {
        if (v == null) return "";
        v = v.replace("&nbsp;", "").trim();
        if (!v.matches("\\d{1,2}:\\d{2}")) return "";
        return v.length() == 4 ? "0" + v : v;
    }

    static String first(JSONObject d, String a, String b) {
        String v = clean(d.optString(a));
        return v.isEmpty() ? clean(d.optString(b)) : v;
    }

    static String fetch(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 11; Smart TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36");
        c.setRequestProperty("Accept", "application/json");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }
}
