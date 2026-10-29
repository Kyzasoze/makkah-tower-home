package com.gathin.tvhome;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * The big clock is selectable: OK opens a list of cities to tick, and the chosen ones show in a line under the clock
 * ("MAKKAH 21:43 · LONDON 19:43"), updated every 20 seconds. The choice is kept on the TV.
 */
final class WorldClock {
    static final String[][] CITIES = {
            {"Makkah", "Asia/Riyadh"}, {"Madinah", "Asia/Riyadh"}, {"Dubai", "Asia/Dubai"}, {"Cairo", "Africa/Cairo"},
            {"Istanbul", "Europe/Istanbul"}, {"Johannesburg", "Africa/Johannesburg"}, {"Nairobi", "Africa/Nairobi"},
            {"Lagos", "Africa/Lagos"}, {"Casablanca", "Africa/Casablanca"}, {"Dar es Salaam", "Africa/Dar_es_Salaam"},
            {"Lisbon", "Europe/Lisbon"}, {"London", "Europe/London"}, {"Paris", "Europe/Paris"}, {"Berlin", "Europe/Berlin"},
            {"Moscow", "Europe/Moscow"}, {"Karachi", "Asia/Karachi"}, {"Mumbai", "Asia/Kolkata"}, {"Dhaka", "Asia/Dhaka"},
            {"Kuala Lumpur", "Asia/Kuala_Lumpur"}, {"Jakarta", "Asia/Jakarta"}, {"Beijing", "Asia/Shanghai"},
            {"Tokyo", "Asia/Tokyo"}, {"Sydney", "Australia/Sydney"}, {"New York", "America/New_York"},
            {"Toronto", "America/Toronto"}, {"Chicago", "America/Chicago"}, {"Los Angeles", "America/Los_Angeles"},
            {"São Paulo", "America/Sao_Paulo"}, {"Mexico City", "America/Mexico_City"}, {"Doha", "Asia/Qatar"},
    };

    private final Context ctx;
    private final TextView clock;
    private final TextView line;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final GradientDrawable focusBg = new GradientDrawable();
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            render();
            main.postDelayed(this, 20_000L);
        }
    };

    WorldClock(Context ctx, TextView clock, TextView line) {
        this.ctx = ctx;
        this.clock = clock;
        this.line = line;
        float dp = ctx.getResources().getDisplayMetrics().density;
        focusBg.setCornerRadius(18 * dp);
        focusBg.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        focusBg.setColors(new int[] {0x00FFFFFF, 0x00FFFFFF});
        clock.setBackground(focusBg);
        int pad = Math.round(10 * dp);
        clock.setPadding(pad, Math.round(2 * dp), pad, Math.round(2 * dp));
        clock.setFocusable(true);
        clock.setClickable(true);
        clock.setOnClickListener(v -> pick());
        clock.setOnFocusChangeListener((v, has) -> {
            focusBg.setColors(has ? new int[] {0x52FFFFFF, 0x24FFFFFF} : new int[] {0x00FFFFFF, 0x00FFFFFF});
            focusBg.setStroke(has ? Math.round(1.5f * dp) : 0, 0xE6FFFFFF);
            v.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f)
                    .setInterpolator(new OvershootInterpolator(2f)).setDuration(260).start();
        });
        clock.setPivotX(0);
        main.post(tick);
    }

    private SharedPreferences prefs() {
        return ctx.getSharedPreferences("world_clock", Context.MODE_PRIVATE);
    }

    private List<String> chosen() {
        String s = prefs().getString("cities", "Makkah");
        List<String> out = new ArrayList<>();
        for (String c : s.split("\\|")) if (!c.isEmpty()) out.add(c);
        return out;
    }

    private String zone(String city) {
        for (String[] c : CITIES) if (c[0].equals(city)) return c[1];
        return null;
    }

    void render() {
        List<String> cities = chosen();
        if (cities.isEmpty()) {
            line.setVisibility(View.GONE);
            return;
        }
        StringBuilder sb = new StringBuilder();
        Date now = new Date();
        for (String c : cities) {
            String z = zone(c);
            if (z == null) continue;
            SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.ROOT);
            f.setTimeZone(TimeZone.getTimeZone(z));
            if (sb.length() > 0) sb.append("     ");
            sb.append(c.toUpperCase(Locale.ROOT)).append("  ").append(f.format(now));
        }
        line.setText(sb);
        line.setVisibility(View.VISIBLE);
    }

    private void pick() {
        String[] names = new String[CITIES.length];
        boolean[] checked = new boolean[CITIES.length];
        Set<String> cur = new LinkedHashSet<>(chosen());
        for (int i = 0; i < CITIES.length; i++) {
            names[i] = CITIES[i][0];
            checked[i] = cur.contains(CITIES[i][0]);
        }
        AlertDialog d = new AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("World clock — choose cities")
                .setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Done", (dialog, w) -> {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < names.length; i++) if (checked[i]) sb.append(sb.length() > 0 ? "|" : "").append(names[i]);
                    prefs().edit().putString("cities", sb.toString()).apply();
                    render();
                })
                .setNegativeButton("Cancel", null)
                .create();
        d.show();
    }

    static TextView makeLine(Context ctx) {
        TextView t = new TextView(ctx);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTextColor(0xCCF4F1EA);
        t.setLetterSpacing(0.1f);
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        Typeface tf = UiFont.get(ctx, R.string.font_nav);
        if (tf != null) t.setTypeface(tf);
        t.setFocusable(false);
        return t;
    }
}
