package com.gathin.tvhome;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * One of three world-clock slots under the Makkah clock: a crystal-glass chip with the city's icon (bobbing gently,
 * each slot out of step with the others), its name and local time. Focus lifts it and spins the icon; OK picks
 * another city for this slot.
 */
public class CityChip extends LinearLayout {
    static final String[][] CITIES = {
            {"Makkah", "Asia/Riyadh", "🕋"}, {"Madinah", "Asia/Riyadh", "🕌"}, {"Dubai", "Asia/Dubai", "🇦🇪"},
            {"Doha", "Asia/Qatar", "🇶🇦"}, {"Cairo", "Africa/Cairo", "🇪🇬"}, {"Istanbul", "Europe/Istanbul", "🇹🇷"},
            {"Johannesburg", "Africa/Johannesburg", "🇿🇦"}, {"Nairobi", "Africa/Nairobi", "🇰🇪"},
            {"Dar es Salaam", "Africa/Dar_es_Salaam", "🇹🇿"}, {"Lagos", "Africa/Lagos", "🇳🇬"},
            {"Casablanca", "Africa/Casablanca", "🇲🇦"}, {"Lisbon", "Europe/Lisbon", "🇵🇹"}, {"London", "Europe/London", "🇬🇧"},
            {"Paris", "Europe/Paris", "🇫🇷"}, {"Berlin", "Europe/Berlin", "🇩🇪"}, {"Moscow", "Europe/Moscow", "🇷🇺"},
            {"Karachi", "Asia/Karachi", "🇵🇰"}, {"Mumbai", "Asia/Kolkata", "🇮🇳"}, {"Dhaka", "Asia/Dhaka", "🇧🇩"},
            {"Kuala Lumpur", "Asia/Kuala_Lumpur", "🇲🇾"}, {"Jakarta", "Asia/Jakarta", "🇮🇩"}, {"Beijing", "Asia/Shanghai", "🇨🇳"},
            {"Tokyo", "Asia/Tokyo", "🇯🇵"}, {"Sydney", "Australia/Sydney", "🇦🇺"}, {"New York", "America/New_York", "🇺🇸"},
            {"Toronto", "America/Toronto", "🇨🇦"}, {"Los Angeles", "America/Los_Angeles", "🇺🇸"},
            {"São Paulo", "America/Sao_Paulo", "🇧🇷"}, {"Mexico City", "America/Mexico_City", "🇲🇽"},
    };
    private static final String[] DEFAULTS = {"Makkah", "Madinah", "London"};

    private final int slot;
    private final float dp;
    private final TextView icon, name, time;
    private final GradientDrawable bg = new GradientDrawable();
    private final Handler main = new Handler(Looper.getMainLooper());
    private ObjectAnimator bob;
    private String[] city;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            bind();
            main.postDelayed(this, 15_000L);
        }
    };

    public CityChip(Context c, int slot) {
        super(c);
        this.slot = slot;
        dp = c.getResources().getDisplayMetrics().density;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setFocusable(true);
        setClickable(true);
        setPadding(px(9), px(4), px(12), px(4));
        bg.setCornerRadius(px(18));
        bg.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        glass(false);
        setBackground(bg);
        setClipToPadding(false);

        icon = text(15, R.string.font_regular, 0xFFFFFFFF);
        addView(icon);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(VERTICAL);
        LayoutParams cl = new LayoutParams(-2, -2);
        cl.leftMargin = px(6);
        addView(col, cl);
        name = text(9, R.string.font_secondary, 0xB3F4F1EA);
        name.setLetterSpacing(0.12f);
        name.setAllCaps(true);
        col.addView(name);
        time = text(15, R.string.font_nav, 0xFFF4F1EA);
        col.addView(time);

        setOnFocusChangeListener((v, has) -> {
            glass(has);
            animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).translationY(has ? -px(2) : 0)
                    .setInterpolator(new OvershootInterpolator(2f)).setDuration(260).start();
            if (has) icon.animate().rotationBy(360f).setDuration(650).start();
        });
        setOnClickListener(v -> pick());
        city = find(prefs().getString("slot" + slot, DEFAULTS[slot % DEFAULTS.length]));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        main.post(tick);
        bob = ObjectAnimator.ofFloat(icon, "translationY", 0f, -px(2.5f), 0f, px(1f), 0f);
        bob.setDuration(3600);
        bob.setStartDelay(slot * 1200L);
        bob.setRepeatCount(ValueAnimator.INFINITE);
        bob.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        main.removeCallbacksAndMessages(null);
        if (bob != null) bob.cancel();
        super.onDetachedFromWindow();
    }

    private void glass(boolean focused) {
        bg.setColors(focused ? new int[] {0x73FFFFFF, 0x38FFFFFF} : new int[] {0x2EFFFFFF, 0x0FFFFFFF});
        bg.setStroke(Math.max(1, px(focused ? 1.5f : 1f)), focused ? 0xE6FFFFFF : 0x40FFFFFF);
    }

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences("city_chips", Context.MODE_PRIVATE);
    }

    private static String[] find(String n) {
        for (String[] c : CITIES) if (c[0].equals(n)) return c;
        return CITIES[0];
    }

    private void bind() {
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.ROOT);
        f.setTimeZone(TimeZone.getTimeZone(city[1]));
        icon.setText(city[2]);
        name.setText(city[0]);
        time.setText(f.format(new Date()));
        setContentDescription(city[0] + " " + time.getText());
    }

    private void pick() {
        String[] names = new String[CITIES.length];
        int sel = 0;
        for (int i = 0; i < CITIES.length; i++) {
            names[i] = CITIES[i][2] + "   " + CITIES[i][0];
            if (CITIES[i][0].equals(city[0])) sel = i;
        }
        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("City for this clock")
                .setSingleChoiceItems(names, sel, (d, which) -> {
                    city = CITIES[which];
                    prefs().edit().putString("slot" + slot, city[0]).apply();
                    bind();
                    icon.animate().rotationBy(360f).setDuration(650).start();
                    d.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
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
}
