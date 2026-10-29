package com.gathin.tvhome;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Today's prayer times as MasjidBoardLive-style JSON ({"ok":true,"data":{fajrAthan, dhuhrAthan...}}), from the
 * mosque's board when one is set up, else calculated by Aladhan for the city and method chosen in the setup.
 */
final class PrayerSource {
    private PrayerSource() {}

    static String today(Context c) throws Exception {
        String board = ShareSetup.board(c);
        if (!board.isEmpty()) {
            return fetch("https://masjidboardlive.com/boards/api/board.php?id=" + board + "&v=" + (System.currentTimeMillis() / 60_000L));
        }
        String url = String.format(Locale.ROOT, "https://api.aladhan.com/v1/timings?latitude=%.4f&longitude=%.4f&method=%d",
                ShareSetup.lat(c), ShareSetup.lon(c), ShareSetup.method(c));
        JSONObject t = new JSONObject(fetch(url)).getJSONObject("data").getJSONObject("timings");
        JSONObject d = new JSONObject();
        d.put("fajrAthan", hm(t.optString("Fajr")));
        d.put("sehriEnds", hm(t.optString("Imsak")));
        d.put("sunrise", hm(t.optString("Sunrise")));
        d.put("dhuhrAthan", hm(t.optString("Dhuhr")));
        d.put("asrAthan", hm(t.optString("Asr")));
        d.put("sunset", hm(t.optString("Sunset")));
        d.put("maghribAthan", hm(t.optString("Maghrib")));
        d.put("eshaAthan", hm(t.optString("Isha")));
        d.put("meta", new JSONObject().put("masjid", ShareSetup.place(c)).put("city", ShareSetup.place(c)));
        return new JSONObject().put("ok", true).put("data", d).toString();
    }

    /** "05:12 (CAT)" -> "05:12". */
    private static String hm(String v) {
        if (v == null) return "";
        v = v.trim();
        int sp = v.indexOf(' ');
        return sp > 0 ? v.substring(0, sp) : v;
    }

    static String fetch(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11; Smart TV) CleanYourTV");
        c.setRequestProperty("Accept", "application/json");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }
}
