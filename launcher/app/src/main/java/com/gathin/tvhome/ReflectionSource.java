package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Verses and hadith for the idle screen. A Qur'an verse (Arabic with an English translation, from the public
 * alquran.cloud API) alternates with a hadith (English, from the free fawazahmed0 hadith API on jsDelivr: the
 * Forty Hadith of an-Nawawi, Hadith Qudsi, Sahih al-Bukhari and Sahih Muslim). Long passages are skipped so each
 * fits the screen. Everything shown is kept (up to 150) so the screen still has something to show offline.
 */
final class ReflectionSource {
    static final class Item {
        final boolean quran;
        final String arabic, text, source;
        /** The small heading over the passage ("Morning adhkar"); null: from the Qur'an / Hadith. */
        final String label;

        Item(boolean quran, String arabic, String text, String source) {
            this(quran, arabic, text, source, null);
        }

        Item(boolean quran, String arabic, String text, String source, String label) {
            this.quran = quran;
            this.arabic = arabic;
            this.text = text;
            this.source = source;
            this.label = label;
        }
    }

    interface Callback {
        void onItem(Item item);
    }

    private static final int AYAHS = 6236;
    private static final int MAX_AYAH_CHARS = 340, MAX_HADITH_CHARS = 400, KEEP = 150;
    private static final String[][] HADITH = {   // edition, how many, weight
            {"eng-nawawi", "42", "3"}, {"eng-qudsi", "40", "2"}, {"eng-bukhari", "7563", "3"}, {"eng-muslim", "7470", "2"}};
    /** Shown only when nothing has ever been fetched: Qur'an 94:5-6, Pickthall's translation (public domain). */
    private static final Item FALLBACK = new Item(true, "فَإِنَّ مَعَ الْعُسْرِ يُسْرًا ۝ إِنَّ مَعَ الْعُسْرِ يُسْرًا",
            "But lo! with hardship goeth ease, Lo! with hardship goeth ease.", "Qur'an · Ash-Sharh 94:5–6 · Pickthall");

    private final Context ctx;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private boolean nextQuran = true;
    private boolean adhkarTurn;
    private volatile boolean closed;

    ReflectionSource(Context context) {
        ctx = context.getApplicationContext();
    }

    void close() {
        closed = true;
        io.shutdownNow();
    }

    /** Something to show right away: a kept item, or the fallback verse. */
    Item cached() {
        try {
            JSONArray pool = pool();
            if (pool.length() > 0) return fromJson(pool.getJSONObject(rnd.nextInt(pool.length())));
        } catch (Exception ignored) {
        }
        return FALLBACK;
    }

    /** The next passage, alternating verse and hadith; a kept one when the network fails. */
    void next(Callback cb) {
        // Mornings and evenings: every other passage is one of the day's adhkar.
        String window = Adhkar.window();
        if (window != null && (adhkarTurn = !adhkarTurn)) {
            SharedPreferences p = prefs();
            int n = p.getInt("adhkar_" + window, 0);
            p.edit().putInt("adhkar_" + window, n + 1).apply();
            Item item = Adhkar.get(window, n);
            main.post(() -> {
                if (!closed) cb.onItem(item);
            });
            return;
        }
        final boolean quran = nextQuran;
        nextQuran = !nextQuran;
        try {
            io.execute(() -> {
                Item item = null;
                try {
                    item = quran ? fetchAyah() : fetchHadith();
                    if (item == null) item = quran ? fetchHadith() : fetchAyah();
                } catch (Exception ignored) {
                }
                if (item != null) keep(item);
                else item = cached();
                final Item out = item;
                if (!closed) main.post(() -> {
                    if (!closed) cb.onItem(out);
                });
            });
        } catch (Exception ignored) {
            // shut down
        }
    }

    private Item fetchAyah() {
        for (int tries = 0; tries < 5 && !closed; tries++) {
            try {
                int n = 1 + rnd.nextInt(AYAHS);
                JSONObject o = new JSONObject(get("https://api.alquran.cloud/v1/ayah/" + n
                        + "/editions/quran-simple,en.sahih"));
                JSONArray data = o.getJSONArray("data");
                JSONObject ar = data.getJSONObject(0), en = data.getJSONObject(1);
                String text = clean(en.getString("text"));
                String arabic = clean(ar.getString("text"));
                if (text.isEmpty() || text.length() > MAX_AYAH_CHARS) continue;
                JSONObject surah = en.getJSONObject("surah");
                String src = "Qur'an · " + surah.optString("englishName") + " " + surah.optInt("number") + ":"
                        + en.optInt("numberInSurah");
                return new Item(true, arabic, text, src);
            } catch (Exception ignored) {
                if (closed) return null;
            }
        }
        return null;
    }

    private Item fetchHadith() {
        int total = 0;
        for (String[] h : HADITH) total += Integer.parseInt(h[2]);
        for (int tries = 0; tries < 6 && !closed; tries++) {
            try {
                int pick = rnd.nextInt(total);
                String[] ed = HADITH[0];
                for (String[] h : HADITH) {
                    pick -= Integer.parseInt(h[2]);
                    if (pick < 0) {
                        ed = h;
                        break;
                    }
                }
                int n = 1 + rnd.nextInt(Integer.parseInt(ed[1]));
                String base = "https://cdn.jsdelivr.net/gh/fawazahmed0/hadith-api@1/editions/" + ed[0] + "/" + n;
                String body;
                try {
                    body = get(base + ".min.json");
                } catch (Exception e) {
                    body = get(base + ".json");
                }
                JSONObject o = new JSONObject(body);
                JSONArray list = o.optJSONArray("hadiths");
                JSONObject h = list != null && list.length() > 0 ? list.getJSONObject(0) : o.optJSONObject("hadith");
                if (h == null) continue;
                String text = clean(h.optString("text"));
                if (text.length() < 30 || text.length() > MAX_HADITH_CHARS) continue;
                JSONObject meta = o.optJSONObject("metadata");
                String book = meta != null ? meta.optString("name") : "";
                if (book.isEmpty()) book = bookName(ed[0]);
                int num = h.optInt("hadithnumber", n);
                return new Item(false, "", text, "Hadith · " + book + " " + num);
            } catch (Exception ignored) {
                if (closed) return null;
            }
        }
        return null;
    }

    private static String bookName(String edition) {
        switch (edition) {
            case "eng-nawawi":
                return "Forty Hadith of an-Nawawi";
            case "eng-qudsi":
                return "Forty Hadith Qudsi";
            case "eng-bukhari":
                return "Sahih al-Bukhari";
            default:
                return "Sahih Muslim";
        }
    }

    private static String clean(String s) {
        if (s == null) return "";
        return s.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", "CleanYourTV-Reflections/1.0");
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new Exception("HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) > 0) {
                    out.write(buf, 0, r);
                    if (out.size() > 2_000_000) throw new Exception("too large");
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }

    // ---- kept passages ----------------------------------------------------------------------

    private SharedPreferences prefs() {
        return ctx.getSharedPreferences("reflections", Context.MODE_PRIVATE);
    }

    private JSONArray pool() throws Exception {
        return new JSONArray(prefs().getString("pool", "[]"));
    }

    private synchronized void keep(Item item) {
        try {
            JSONArray pool = pool();
            JSONArray out = new JSONArray();
            int start = Math.max(0, pool.length() - (KEEP - 1));
            for (int i = start; i < pool.length(); i++) {
                JSONObject o = pool.getJSONObject(i);
                if (!item.source.equals(o.optString("s"))) out.put(o);
            }
            out.put(new JSONObject().put("q", item.quran).put("a", item.arabic).put("t", item.text).put("s", item.source));
            prefs().edit().putString("pool", out.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private static Item fromJson(JSONObject o) {
        return new Item(o.optBoolean("q"), o.optString("a"), o.optString("t"), o.optString("s"));
    }
}
