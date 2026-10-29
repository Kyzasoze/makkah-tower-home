package com.gathin.tvhome;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Next salah beside the weather: a crystal-glass chip with a crescent, the prayer, its time and a countdown, from the
 * Babus Salam MasjidBoardLive board (the same feed as the Salah Times app). Counts down to the adhan, then to the
 * jamaah. OK opens Salah Times.
 */
public class SalahChip extends LinearLayout {
    private static final String TAG = "SalahChip";
    static final String BOARD = "maputo-babus-salam";
    static final String PRAYER_APP = "com.majnioui.tv.prayer";
    private static final String[] NAMES = {"Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"};

    private final Handler main = new Handler(Looper.getMainLooper());
    private final float dp;
    private final TextView icon, name, time, left;
    private final GradientDrawable bg = new GradientDrawable();
    private ObjectAnimator glow;
    private JSONObject data;
    private String loadedFor = "";
    private long loadedAt;
    private boolean loading;
    private InfoChip info;

    /** The line under this chip: Ramadan, Jumu'ah or a mosque notice. */
    void setInfo(InfoChip chip) {
        info = chip;
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!today().equals(loadedFor) || System.currentTimeMillis() - loadedAt > 60 * 60_000L) load();
            bind();
            main.postDelayed(this, 30_000L);
        }
    };

    public SalahChip(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setFocusable(true);
        setClickable(true);
        setPadding(px(14), px(6), px(18), px(6));
        bg.setCornerRadius(px(24));
        bg.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        glass(false);
        setBackground(bg);

        icon = text(22, R.string.font_regular, 0xFF6FF0A8);
        icon.setText("☪");
        addView(icon);
        name = text(22, R.string.font_nav, 0xFFF4F1EA);
        LayoutParams nl = new LayoutParams(-2, -2);
        nl.leftMargin = px(8);
        addView(name, nl);
        time = text(22, R.string.font_nav, 0xFFF4F1EA);
        LayoutParams tl = new LayoutParams(-2, -2);
        tl.leftMargin = px(8);
        addView(time, tl);
        left = text(12, R.string.font_secondary, 0xCCF4F1EA);
        left.setLetterSpacing(0.08f);
        left.setAllCaps(true);
        LayoutParams ll = new LayoutParams(-2, -2);
        ll.leftMargin = px(10);
        addView(left, ll);
        name.setText("Salah");
        time.setText("--:--");

        setOnFocusChangeListener((v, has) -> {
            glass(has);
            animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f)
                    .setInterpolator(new OvershootInterpolator(2f)).setDuration(260).start();
            icon.animate().rotation(has ? -20f : 0f).scaleX(has ? 1.25f : 1f).scaleY(has ? 1.25f : 1f)
                    .setInterpolator(new OvershootInterpolator(3f)).setDuration(420).start();
        });
        setOnClickListener(v -> {
            Intent i = getContext().getPackageManager().getLeanbackLaunchIntentForPackage(PRAYER_APP);
            if (i == null) i = getContext().getPackageManager().getLaunchIntentForPackage(PRAYER_APP);
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(i);
            }
        });

        String cached = prefs().getString("json", null);
        if (cached != null) {
            try {
                data = new JSONObject(cached).getJSONObject("data");
                loadedFor = prefs().getString("day", "");
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        main.post(tick);
        glow = ObjectAnimator.ofFloat(icon, "alpha", 1f, 0.55f, 1f);
        glow.setDuration(3600);
        glow.setRepeatCount(ValueAnimator.INFINITE);
        glow.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        main.removeCallbacksAndMessages(null);
        if (glow != null) glow.cancel();
        super.onDetachedFromWindow();
    }

    private void glass(boolean focused) {
        bg.setColors(focused ? new int[] {0x73FFFFFF, 0x38FFFFFF} : new int[] {0x2EFFFFFF, 0x0FFFFFFF});
        bg.setStroke(Math.max(1, px(focused ? 1.5f : 1f)), focused ? 0xE6FFFFFF : 0x40FFFFFF);
    }

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences("salah_chip", Context.MODE_PRIVATE);
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
    }

    private void load() {
        if (loading) return;
        loading = true;
        final String day = today();
        new Thread(() -> {
            String body = null;
            try {
                body = PrayerSource.today(getContext());
                new JSONObject(body).getJSONObject("data");
            } catch (Exception e) {
                Log.w(TAG, "fetch: " + e);
                body = null;
            }
            final String b = body;
            main.post(() -> {
                loading = false;
                if (b == null) return;
                try {
                    data = new JSONObject(b).getJSONObject("data");
                    loadedFor = day;
                    loadedAt = System.currentTimeMillis();
                    prefs().edit().putString("json", b).putString("day", day).apply();
                    bind();
                } catch (Exception e) {
                    Log.w(TAG, "parse: " + e);
                }
            });
        }).start();
    }

    private static String clean(String v) {
        if (v == null) return "";
        v = v.replace("&nbsp;", "").trim();
        if (!v.matches("\\d{1,2}:\\d{2}")) return "";
        return v.length() == 4 ? "0" + v : v;
    }

    private static String first(JSONObject d, String a, String b) {
        String v = clean(d.optString(a));
        return v.isEmpty() ? clean(d.optString(b)) : v;
    }

    private static int min(String hm) {
        return hm.isEmpty() ? -1 : Integer.parseInt(hm.substring(0, 2)) * 60 + Integer.parseInt(hm.substring(3, 5));
    }

    private void bind() {
        if (data == null) return;
        Calendar c = Calendar.getInstance();
        boolean friday = c.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY;
        int now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        String[] adhan = {first(data, "fajrAthan", "fajrStarts"), clean(data.optString("dhuhrAthan")),
                clean(data.optString("asrAthan")), first(data, "maghribAthan", "sunset"), first(data, "eshaAthan", "eshaStarts")};
        String[] jamaah = {clean(data.optString("fajrJamaah")), clean(data.optString("dhuhrJamaah")),
                clean(data.optString("asrJamaah")), clean(data.optString("maghribJamaah")), clean(data.optString("eshaJamaah"))};
        if (c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY && !clean(data.optString("zuhrSundays")).isEmpty()) {
            jamaah[1] = clean(data.optString("zuhrSundays"));
        }
        updateInfo(now, friday, adhan, jamaah);
        for (int i = 0; i < NAMES.length; i++) {
            String n = (i == 1 && friday) ? "Jumu'ah" : NAMES[i];
            int a = min(adhan[i]), j = min(jamaah[i]);
            if (a > now) {
                show(n, adhan[i], a - now, "adhan");
                return;
            }
            if (j > now) {
                show(n, jamaah[i], j - now, "jamaah");
                return;
            }
        }
        // After Isha: tomorrow's Fajr (today's time is the closest guess until the new board loads).
        show("Fajr", adhan[0], -1, "tomorrow");
    }

    /** Ramadan first (suhoor ends / iftar), then Jumu'ah on Friday mornings, then a notice from the board. */
    private void updateInfo(int now, boolean friday, String[] adhan, String[] jamaah) {
        if (info == null) return;
        try {
            if (IslamicDates.ramadan(getContext())) {
                // The board's own sehri time when it has one, else the Fajr adhan.
                String sehri = clean(data.optString("sehriEnds"));
                if (sehri.isEmpty()) sehri = adhan[0];
                int fajr = min(sehri), maghrib = min(adhan[3]);
                adhan = adhan.clone();
                adhan[0] = sehri;
                if (fajr > now) {
                    info.show("\u263E", "Suhoor ends " + adhan[0] + "  ·  " + span(fajr - now), 0xFF9DB4FF);
                    return;
                }
                if (maghrib > now) {
                    info.show("\u263E", "Iftar " + adhan[3] + "  ·  " + span(maghrib - now), 0xFFFFC46B);
                    return;
                }
                info.show("\u263E", "Ramadan Mubarak  ·  suhoor ends " + adhan[0] + " tomorrow", 0xFF9DB4FF);
                return;
            }
            String jJamaah = clean(data.optString("jumuahTime3"));
            if (jJamaah.isEmpty()) jJamaah = jamaah[1];
            int dhuhrJ = min(jJamaah);
            if (friday && dhuhrJ > now) {
                String khateeb = data.optString("jumuah_khateeb").trim();
                info.show("\u2726", "Jumu'ah today  ·  jamaah " + jJamaah + "  ·  " + span(dhuhrJ - now)
                        + (khateeb.isEmpty() ? "" : "  ·  " + khateeb), 0xFF6FF0A8);
                return;
            }
            String notice = notice();
            info.show("\u2709", notice, 0xFFE0BD72);
        } catch (Exception e) {
            info.show(null, null, 0);
        }
    }

    private static String span(int minutes) {
        return "in " + (minutes >= 60 ? minutes / 60 + "h " : "") + minutes % 60 + "m";
    }

    /** A notice from the board, if it carries one (any short text field named like a notice or announcement). */
    private String notice() {
        String raw = prefs().getString("json", null);
        if (raw == null) return null;
        try {
            JSONObject root = new JSONObject(raw);
            JSONObject d = root.optJSONObject("data");
            // MasjidBoardLive keeps the board's announcement in data.meta.announcement_info.
            JSONObject meta = d != null ? d.optJSONObject("meta") : null;
            if (meta != null) {
                String info = meta.optString("announcement_info").replaceAll("<[^>]+>", " ").replace("&nbsp;", " ")
                        .replaceAll("\\s+", " ").trim();
                if (info.length() >= 4) return info.length() > 220 ? info.substring(0, 217) + "…" : info;
            }
            String found = noticeIn(d);
            return found != null ? found : noticeIn(root);
        } catch (Exception e) {
            return null;
        }
    }

    private static String noticeIn(JSONObject o) {
        if (o == null) return null;
        java.util.Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            if (!k.toLowerCase(Locale.ROOT).matches(".*(notice|announce|message|news|ticker|scroll|bulletin|alert).*")) continue;
            Object v = o.opt(k);
            String text = null;
            if (v instanceof String) text = (String) v;
            else if (v instanceof org.json.JSONArray && ((org.json.JSONArray) v).length() > 0) {
                Object first = ((org.json.JSONArray) v).opt(0);
                text = first instanceof JSONObject ? ((JSONObject) first).optString("text", ((JSONObject) first).optString("title"))
                        : String.valueOf(first);
            }
            if (text == null) continue;
            text = text.replaceAll("<[^>]+>", " ").replace("&nbsp;", " ").replaceAll("\\s+", " ").trim();
            if (text.length() >= 8 && text.length() <= 220) return text;
        }
        return null;
    }

    private void show(String n, String t, int minutes, String what) {
        name.setText(n);
        time.setText(t);
        if (minutes < 0) left.setText(what);
        else left.setText((what.equals("jamaah") ? "jamaah in " : "in ") + (minutes >= 60 ? minutes / 60 + "h " : "") + minutes % 60 + "m");
        icon.setTextColor(minutes >= 0 && minutes <= 15 ? 0xFFFFD54F : 0xFF6FF0A8);
        setContentDescription("Next salah " + n + " " + t);
    }

    private TextView text(float sp, int font, int color) {
        TextView t = new TextView(getContext());
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        Typeface tf = UiFont.get(getContext(), font);
        if (tf != null) t.setTypeface(tf);
        t.setSingleLine(true);
        return t;
    }

    private int px(float v) {
        return Math.round(v * dp);
    }

    private static String fetch(String url) throws Exception {
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
