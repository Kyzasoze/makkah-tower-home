package com.gathin.tvhome;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Weather beside the clock: a small crystal-glass chip (icon, temperature, conditions) that floats gently. When the
 * remote focuses it, it lifts and a glass panel slides down with the next days; moving away folds the panel up.
 * Data: Open-Meteo (no account or key), for the place below, every 30 minutes, kept for offline use.
 */
public class WeatherChip extends LinearLayout {
    private static final String TAG = "WeatherChip";
    static final double LAT = -25.9692, LON = 32.5732;
    static final String PLACE = "Maputo";
    private static final long REFRESH_MS = 30 * 60_000L;
    private static final int DAYS = 5;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final float dp;
    private final TextView temp, desc;
    private final WeatherIconView icon;
    private final android.graphics.Paint fx = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Path clip = new android.graphics.Path();
    private final android.graphics.RectF box = new android.graphics.RectF();
    private final long born = System.currentTimeMillis();
    private final GradientDrawable bg = new GradientDrawable();
    private ObjectAnimator floatAnim;
    private PopupWindow popup;
    private JSONObject data;
    private boolean loading;
    private InfoChip alert;

    /** A red line under the chip for severe weather today or tomorrow. */
    void setAlert(InfoChip chip) {
        alert = chip;
        try {
            if (data != null) bind();
        } catch (Exception ignored) {
        }
    }

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            load();
            main.postDelayed(this, REFRESH_MS);
        }
    };

    public WeatherChip(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setFocusable(true);
        setClickable(true);
        setPadding(px(14), px(6), px(18), px(6));
        bg.setCornerRadius(px(24));
        glass(false);
        setBackground(bg);
        setClipToPadding(false);

        icon = new WeatherIconView(c);
        addView(icon, new LayoutParams(px(36), px(34)));
        setWillNotDraw(false);
        temp = text(22, R.string.font_nav, 0xFFF4F1EA);
        LayoutParams tlp = new LayoutParams(-2, -2);
        tlp.leftMargin = px(8);
        addView(temp, tlp);
        desc = text(14, R.string.font_secondary, 0xCCF4F1EA);
        desc.setLetterSpacing(0.12f);
        desc.setAllCaps(true);
        LayoutParams dlp = new LayoutParams(-2, -2);
        dlp.leftMargin = px(10);
        addView(desc, dlp);
        temp.setText("--°");

        setOnFocusChangeListener((v, has) -> {
            glass(has);
            animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).translationY(has ? -px(2) : 0)
                    .setInterpolator(new OvershootInterpolator(2f)).setDuration(260).start();
            if (has) {
                icon.setScaleX(0.7f);
                icon.setScaleY(0.7f);
                icon.animate().scaleX(1.12f).scaleY(1.12f).setInterpolator(new OvershootInterpolator(3f)).setDuration(420).start();
            } else {
                icon.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
            }
            if (has) expand();
            else collapse();
        });
        setOnClickListener(v -> {
            if (popup == null) expand();
            else collapse();
        });

        String cached = prefs().getString("json", null);
        if (cached != null) {
            try {
                data = new JSONObject(cached);
                bind();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        main.post(refresh);
        floatAnim = ObjectAnimator.ofFloat(icon, "translationY", 0f, -px(3), 0f, px(1), 0f);
        floatAnim.setDuration(4200);
        floatAnim.setRepeatCount(ValueAnimator.INFINITE);
        floatAnim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        main.removeCallbacksAndMessages(null);
        if (floatAnim != null) floatAnim.cancel();
        dismissNow();
        super.onDetachedFromWindow();
    }

    // ---- the live weather inside the glass ---------------------------------------------------------

    @Override
    protected void dispatchDraw(android.graphics.Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w > 0 && h > 0) {
            int save = c.save();
            box.set(0, 0, w, h);
            clip.reset();
            clip.addRoundRect(box, px(24), px(24), android.graphics.Path.Direction.CW);
            c.clipPath(clip);
            ambient(c, w, h, (System.currentTimeMillis() - born) / 1000f);
            c.restoreToCount(save);
        }
        super.dispatchDraw(c);
        if (isShown()) postInvalidateDelayed(isFocused() ? 50 : 90);
    }

    /** Faint weather across the whole chip, a passing light sweep, and a neon line that matches the sky. */
    private void ambient(android.graphics.Canvas c, float w, float h, float t) {
        int kind = icon.kind();
        boolean day = icon.isDay();
        float ix = icon.getLeft() + icon.getWidth() / 2f, iy = h / 2f;
        fx.setShader(null);
        fx.setStyle(android.graphics.Paint.Style.FILL);
        if (kind == WeatherIconView.CLEAR && day) {
            float pulse = (float) (0.5 + 0.5 * Math.sin(t * 1.6));
            fx.setShader(new android.graphics.RadialGradient(ix, iy, h * (1.7f + 0.3f * pulse),
                    (int) (0x50 + 0x20 * pulse) << 24 | 0xFFB347, 0x00FF9A2E, android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, fx);
        } else if (kind == WeatherIconView.CLEAR) {
            for (int i = 0; i < 14; i++) {
                float sx = ((i * 0.618f + 0.11f) % 1f) * w, sy = ((i * 0.377f + 0.2f) % 1f) * h;
                float tw = (float) Math.abs(Math.sin(t * (0.9 + i * 0.21) + i));
                fx.setColor((int) (110 * tw) << 24 | 0xFFFFFF);
                c.drawCircle(sx, sy, (0.6f + 0.6f * tw) * dp, fx);
            }
        } else if (kind == WeatherIconView.RAIN || kind == WeatherIconView.DRIZZLE || kind == WeatherIconView.STORM) {
            fx.setStyle(android.graphics.Paint.Style.STROKE);
            fx.setStrokeWidth(Math.max(1f, dp));
            int n = kind == WeatherIconView.DRIZZLE ? 10 : 18;
            float slant = icon.isWindy() ? 0.5f : 0.2f;
            for (int i = 0; i < n; i++) {
                float life = (t * (kind == WeatherIconView.DRIZZLE ? 1.1f : 1.8f) + i * 0.173f) % 1f;
                float x = ((i * 0.618f) % 1f) * w * 1.1f, y = -h * 0.2f + life * h * 1.4f, len = h * 0.28f;
                fx.setColor(0x38A8D8FF);
                c.drawLine(x + slant * len, y - len, x, y, fx);
            }
            if (kind == WeatherIconView.STORM) {
                float f = WeatherIconView.flash((System.currentTimeMillis() - born) / 1000f + 0.05f);
                if (f > 0) {
                    fx.setStyle(android.graphics.Paint.Style.FILL);
                    fx.setColor((int) (70 * f) << 24 | 0xE8EEFF);
                    c.drawRect(0, 0, w, h, fx);
                }
            }
        } else if (kind == WeatherIconView.SNOW) {
            for (int i = 0; i < 14; i++) {
                float life = (t * 0.35f + i * 0.13f) % 1f;
                float x = ((i * 0.618f) % 1f) * w + (float) Math.sin(t + i) * 6 * dp, y = -2 * dp + life * (h + 4 * dp);
                fx.setColor(0x66FFFFFF);
                c.drawCircle(x, y, 1.3f * dp, fx);
            }
        } else if (kind == WeatherIconView.FOG || kind == WeatherIconView.CLOUDY) {
            float off = (float) Math.sin(t * 0.5) * w * 0.15f;
            fx.setShader(new android.graphics.LinearGradient(off, 0, off + w, 0, new int[] {0x00DCE4EC, 0x26DCE4EC, 0x00DCE4EC},
                    null, android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(0, h * 0.25f, w, h * 0.75f, fx);
            fx.setShader(null);
        }
        if (icon.isWindy()) {
            fx.setStyle(android.graphics.Paint.Style.STROKE);
            fx.setStrokeWidth(Math.max(1f, dp));
            for (int k = 0; k < 4; k++) {
                float life = (t * 0.55f + k * 0.25f) % 1f, y = h * (0.2f + 0.2f * k), x0 = -w * 0.3f + life * w * 1.6f;
                fx.setShader(new android.graphics.LinearGradient(x0 - w * 0.3f, y, x0, y, 0x00F0F8FF, 0x40F0F8FF,
                        android.graphics.Shader.TileMode.CLAMP));
                c.drawLine(x0 - w * 0.3f, y, x0, y + (float) Math.sin(t * 3 + k) * 2 * dp, fx);
            }
            fx.setShader(null);
        }
        // A light sweep every 7 s.
        float ph = (t % 7f) / 1.4f;
        if (ph < 1f) {
            float x = -w * 0.3f + ph * w * 1.6f, bw = w * 0.22f;
            fx.setStyle(android.graphics.Paint.Style.FILL);
            fx.setShader(new android.graphics.LinearGradient(x - bw, 0, x + bw, h * 0.6f,
                    new int[] {0x00FFFFFF, 0x1FFFFFFF, 0x00FFFFFF}, null, android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, fx);
            fx.setShader(null);
        }
        // Neon line along the bottom with a bright spark running along it.
        int neon = kind == WeatherIconView.CLEAR ? (day ? 0xFFFFB347 : 0xFF9DB4FF)
                : kind == WeatherIconView.RAIN || kind == WeatherIconView.DRIZZLE ? 0xFF58C8FF
                : kind == WeatherIconView.STORM ? 0xFFB59CFF : kind == WeatherIconView.SNOW ? 0xFFEAF6FF : 0xFFC9D3DD;
        float ly = h - 1.6f * dp;
        fx.setStyle(android.graphics.Paint.Style.STROKE);
        fx.setStrokeWidth(Math.max(1f, 1.1f * dp));
        fx.setShader(new android.graphics.LinearGradient(w * 0.12f, ly, w * 0.88f, ly,
                new int[] {neon & 0x00FFFFFF, (neon & 0x00FFFFFF) | 0x8C000000, neon & 0x00FFFFFF}, null,
                android.graphics.Shader.TileMode.CLAMP));
        c.drawLine(w * 0.12f, ly, w * 0.88f, ly, fx);
        fx.setShader(null);
        float sp = w * 0.14f + ((t * 0.28f) % 1f) * w * 0.72f;
        fx.setStyle(android.graphics.Paint.Style.FILL);
        fx.setShader(new android.graphics.RadialGradient(sp, ly, 7 * dp, neon, neon & 0x00FFFFFF,
                android.graphics.Shader.TileMode.CLAMP));
        c.drawCircle(sp, ly, 7 * dp, fx);
        fx.setShader(null);
    }

    /** Clear crystal glass; brighter with a light rim while focused. */
    private void glass(boolean focused) {
        bg.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        bg.setColors(focused ? new int[] {0x73FFFFFF, 0x38FFFFFF} : new int[] {0x2EFFFFFF, 0x0FFFFFFF});
        bg.setStroke(Math.max(1, px(focused ? 1.5f : 1f)), focused ? 0xE6FFFFFF : 0x40FFFFFF);
    }

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences("weather_chip", Context.MODE_PRIVATE);
    }

    private void load() {
        if (loading) return;
        loading = true;
        final String url = String.format(Locale.ROOT, "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum,wind_gusts_10m_max"
                + "&timezone=auto&forecast_days=%d", ShareSetup.lat(getContext()), ShareSetup.lon(getContext()), DAYS + 1);
        new Thread(() -> {
            String body = null;
            try {
                body = fetch(url);
                new JSONObject(body).getJSONObject("current");
            } catch (Exception e) {
                Log.w(TAG, "fetch: " + e);
                body = null;
            }
            final String b = body;
            main.post(() -> {
                loading = false;
                if (b == null) return;
                try {
                    data = new JSONObject(b);
                    prefs().edit().putString("json", b).apply();
                    bind();
                } catch (Exception e) {
                    Log.w(TAG, "parse: " + e);
                }
            });
        }).start();
    }

    private void bind() throws Exception {
        JSONObject cur = data.getJSONObject("current");
        int code = cur.optInt("weather_code");
        boolean day = cur.optInt("is_day", 1) == 1;
        icon.set(WeatherIconView.of(code), day, cur.optDouble("wind_speed_10m", 0) >= 20);
        temp.setText(Math.round(cur.optDouble("temperature_2m")) + "°");
        desc.setText(words(code));
        setContentDescription(ShareSetup.place(getContext()) + " " + temp.getText() + " " + words(code));
        if (alert != null) alert.show("\u26A0", severe(), 0xFFFF6B6B);
    }

    /** Thunderstorms, heavy rain (25 mm or more) or strong gusts (60 km/h or more) today or tomorrow, else null. */
    private String severe() {
        try {
            JSONObject d = data.getJSONObject("daily");
            JSONArray codes = d.getJSONArray("weather_code");
            JSONArray rain = d.optJSONArray("precipitation_sum"), gust = d.optJSONArray("wind_gusts_10m_max");
            for (int i = 0; i < 2 && i < codes.length(); i++) {
                String when = i == 0 ? "today" : "tomorrow";
                int code = codes.getInt(i);
                double mm = rain != null ? rain.optDouble(i, 0) : 0, km = gust != null ? gust.optDouble(i, 0) : 0;
                if (code >= 95) return "Thunderstorm warning " + when + (mm >= 10 ? " · " + Math.round(mm) + " mm of rain" : "");
                if (mm >= 25) return "Heavy rain " + when + " · about " + Math.round(mm) + " mm";
                if (km >= 60) return "Strong winds " + when + " · gusts up to " + Math.round(km) + " km/h";
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // ---- the forecast panel ------------------------------------------------------------------------

    private void expand() {
        if (popup != null || data == null || getWindowToken() == null) return;
        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(VERTICAL);
        panel.setPadding(px(22), px(16), px(22), px(18));
        GradientDrawable pbg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xEB1C1C1F, 0xF2121214});
        pbg.setCornerRadius(px(26));
        pbg.setStroke(px(1), 0x59FFFFFF);
        panel.setBackground(pbg);

        try {
            JSONObject cur = data.getJSONObject("current");
            TextView title = text(30, R.string.font_feature, 0xFFF4F1EA);
            title.setText(ShareSetup.place(getContext()) + "  " + Math.round(cur.optDouble("temperature_2m")) + "°");
            panel.addView(title);
            TextView head = text(15, R.string.font_secondary, 0xD9F4F1EA);
            head.setPadding(0, px(6), 0, 0);
            head.setLetterSpacing(0.1f);
            head.setText(words(cur.optInt("weather_code")).toUpperCase(Locale.ROOT) + "   ·   FEELS " + Math.round(cur.optDouble("apparent_temperature"))
                    + "°   ·   HUMIDITY " + cur.optInt("relative_humidity_2m") + "%   ·   WIND "
                    + Math.round(cur.optDouble("wind_speed_10m")) + " KM/H");
            panel.addView(head);

            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(HORIZONTAL);
            LayoutParams rlp = new LayoutParams(-2, -2);
            rlp.topMargin = px(12);
            panel.addView(row, rlp);

            JSONObject d = data.getJSONObject("daily");
            JSONArray time = d.getJSONArray("time"), codes = d.getJSONArray("weather_code"),
                    hi = d.getJSONArray("temperature_2m_max"), lo = d.getJSONArray("temperature_2m_min"),
                    rain = d.optJSONArray("precipitation_probability_max");
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
            SimpleDateFormat out = new SimpleDateFormat("EEE", Locale.ENGLISH);
            int n = Math.min(time.length(), DAYS + 1);
            for (int i = 0; i < n; i++) {
                LinearLayout card = new LinearLayout(getContext());
                card.setOrientation(VERTICAL);
                card.setGravity(Gravity.CENTER_HORIZONTAL);
                card.setPadding(px(14), px(10), px(14), px(10));
                GradientDrawable cbg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                        i == 0 ? new int[] {0x4DFFFFFF, 0x1AFFFFFF} : new int[] {0x26FFFFFF, 0x0DFFFFFF});
                cbg.setCornerRadius(px(18));
                cbg.setStroke(px(1), i == 0 ? 0x80FFFFFF : 0x33FFFFFF);
                card.setBackground(cbg);
                Date dt = in.parse(time.getString(i));
                TextView name = text(16, R.string.font_nav, 0xE6F4F1EA);
                name.setAllCaps(true);
                name.setLetterSpacing(0.12f);
                name.setText(i == 0 ? "Today" : out.format(dt));
                card.addView(name);
                WeatherIconView ic = new WeatherIconView(getContext());
                ic.set(WeatherIconView.of(codes.getInt(i)), true, false);
                LayoutParams il = new LayoutParams(px(42), px(40));
                il.topMargin = px(4);
                il.bottomMargin = px(2);
                card.addView(ic, il);
                TextView t = text(18, R.string.font_label, 0xFFF4F1EA);
                t.setText(Math.round(hi.getDouble(i)) + "°  " + Math.round(lo.getDouble(i)) + "°");
                card.addView(t);
                TextView r = text(12, R.string.font_secondary, 0x99BFE6FF);
                int pr = rain != null ? rain.optInt(i, -1) : -1;
                r.setText(pr >= 0 ? "☂ " + pr + "%" : " ");
                r.setPadding(0, px(3), 0, 0);
                card.addView(r);
                LayoutParams clp = new LayoutParams(px(96), -2);
                if (i > 0) clp.leftMargin = px(10);
                row.addView(card, clp);
                card.setAlpha(0f);
                card.setTranslationY(-px(14));
                card.animate().alpha(1f).translationY(0).setStartDelay(60L * i + 80)
                        .setInterpolator(new DecelerateInterpolator()).setDuration(320).start();
            }
        } catch (Exception e) {
            Log.w(TAG, "panel: " + e);
            return;
        }

        popup = new PopupWindow(panel, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false);
        popup.setFocusable(false);
        popup.setTouchable(false);
        popup.setOutsideTouchable(false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(px(16));
        panel.setAlpha(0f);
        panel.setScaleY(0.85f);
        panel.setPivotY(0);
        panel.setTranslationY(-px(10));
        try {
            popup.showAsDropDown(this, 0, px(10));
        } catch (Exception e) {
            popup = null;
            return;
        }
        panel.animate().alpha(1f).scaleY(1f).translationY(0).setInterpolator(new DecelerateInterpolator(1.6f))
                .setDuration(280).start();
    }

    private void collapse() {
        final PopupWindow p = popup;
        if (p == null) return;
        popup = null;
        View v = p.getContentView();
        v.animate().alpha(0f).scaleY(0.88f).translationY(-px(8)).setDuration(180)
                .withEndAction(() -> {
                    try {
                        p.dismiss();
                    } catch (Exception ignored) {
                    }
                }).start();
    }

    private void dismissNow() {
        if (popup != null) {
            try {
                popup.dismiss();
            } catch (Exception ignored) {
            }
            popup = null;
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------

    static String emoji(int code, boolean day) {
        if (code == 0) return day ? "☀️" : "🌙";
        if (code <= 2) return day ? "⛅" : "☁️";
        if (code == 3) return "☁️";
        if (code == 45 || code == 48) return "🌫️";
        if (code >= 51 && code <= 57) return "🌦️";
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return "🌧️";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "❄️";
        if (code >= 95) return "⛈️";
        return "🌡️";
    }

    static String words(int code) {
        if (code == 0) return "Clear";
        if (code == 1) return "Mostly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code >= 95) return "Thunderstorm";
        return "";
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
