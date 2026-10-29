package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * First-time setup, three short screens with the remote: your city (for weather and prayer times), how prayer
 * times are worked out (a calculation method, or your mosque's MasjidBoardLive board), and the football teams to
 * follow. Opened automatically the first time Home starts, and again from the gear menu.
 */
public class SetupActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private LinearLayout col;
    private float dp;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        dp = getResources().getDisplayMetrics().density;
        root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {0xFF14231C, 0xFF0B0C0F, 0xFF101218}));
        setContentView(root);
        showCity();
    }

    private void page(String step, String title, String sub) {
        root.removeAllViews();
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(90), px(48), px(90), px(32));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        TextView s = text(13, R.string.font_secondary, 0xFFE0BD72);
        s.setLetterSpacing(0.3f);
        s.setText(step);
        col.addView(s);
        TextView t = text(34, R.string.font_heading, 0xFFF4F1EA);
        t.setText(title);
        t.setPadding(0, px(8), 0, px(6));
        col.addView(t);
        TextView u = text(16, R.string.font_regular, 0xB3F4F1EA);
        u.setText(sub);
        u.setPadding(0, 0, 0, px(20));
        col.addView(u);
    }

    // ---- 1. city ---------------------------------------------------------------------------------

    private void showCity() {
        page("SETUP · 1 OF 3", "Where is this TV?", "Type your city, then choose it from the list. It sets the weather and the prayer times.");
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("City, e.g. Johannesburg");
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        input.setTextColor(0xFFF4F1EA);
        input.setHintTextColor(0x66F4F1EA);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setBackground(glass(false));
        input.setPadding(px(20), px(12), px(20), px(12));
        row.addView(input, new LinearLayout.LayoutParams(px(460), -2));
        TextView search = button("Search");
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.leftMargin = px(14);
        row.addView(search, sl);
        col.addView(row);
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.topMargin = px(16);
        col.addView(results, rl);
        Runnable go = () -> find(input.getText().toString().trim(), results);
        search.setOnClickListener(v -> go.run());
        input.setOnEditorActionListener((v, id, e) -> {
            go.run();
            return true;
        });
        input.requestFocus();
    }

    private void find(String name, LinearLayout results) {
        if (name.isEmpty()) return;
        results.removeAllViews();
        TextView wait = text(16, R.string.font_regular, 0xB3F4F1EA);
        wait.setText("Searching…");
        results.addView(wait);
        new Thread(() -> {
            try {
                JSONObject o = new JSONObject(PrayerSource.fetch("https://geocoding-api.open-meteo.com/v1/search?count=6&language=en&name="
                        + URLEncoder.encode(name, "UTF-8")));
                JSONArray list = o.optJSONArray("results");
                main.post(() -> {
                    results.removeAllViews();
                    if (list == null || list.length() == 0) {
                        wait.setText("No place found with that name. Try another spelling.");
                        results.addView(wait);
                        return;
                    }
                    View first = null;
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject p = list.optJSONObject(i);
                        if (p == null) continue;
                        String label = p.optString("name") + (p.optString("admin1").isEmpty() ? "" : ", " + p.optString("admin1"))
                                + ", " + p.optString("country");
                        TextView b = button(label);
                        b.setOnClickListener(v -> {
                            ShareSetup.prefs(this).edit()
                                    .putLong("lat", Double.doubleToLongBits(p.optDouble("latitude")))
                                    .putLong("lon", Double.doubleToLongBits(p.optDouble("longitude")))
                                    .putString("place", p.optString("name")).apply();
                            showPrayer();
                        });
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                        lp.bottomMargin = px(8);
                        results.addView(b, lp);
                        if (first == null) first = b;
                    }
                    if (first != null) first.requestFocus();
                });
            } catch (Exception e) {
                main.post(() -> wait.setText("No internet connection. Check the Wi-Fi and try again."));
            }
        }).start();
    }

    // ---- 2. prayer times -------------------------------------------------------------------------------

    private void showPrayer() {
        page("SETUP · 2 OF 3", "Prayer times for " + ShareSetup.place(this),
                "Choose the calculation your community follows, or use your mosque's own timetable if it is on MasjidBoardLive.");
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, px(30));
        scroll.addView(list);
        col.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        View first = null;
        for (String[] m : ShareSetup.METHODS) {
            TextView b = button(m[1]);
            b.setOnClickListener(v -> {
                ShareSetup.prefs(this).edit().putInt("method", Integer.parseInt(m[0])).putString("board", "").apply();
                showTeams();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.bottomMargin = px(8);
            list.addView(b, lp);
            if (first == null) first = b;
        }
        TextView mosque = button("My mosque is on MasjidBoardLive…");
        mosque.setOnClickListener(v -> showBoard());
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-2, -2);
        ml.topMargin = px(10);
        list.addView(mosque, ml);
        if (first != null) first.requestFocus();
    }

    private void showBoard() {
        page("SETUP · 2 OF 3", "Your mosque's board",
                "Type the name at the end of its MasjidBoardLive address, e.g. masjidboardlive.com/boards/?maputo-babus-salam → maputo-babus-salam");
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        input.setTextColor(0xFFF4F1EA);
        input.setBackground(glass(false));
        input.setPadding(px(20), px(12), px(20), px(12));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        col.addView(input, new LinearLayout.LayoutParams(px(560), -2));
        TextView status = text(15, R.string.font_regular, 0xB3F4F1EA);
        status.setPadding(0, px(12), 0, px(12));
        col.addView(status);
        TextView use = button("Use this board");
        col.addView(use, new LinearLayout.LayoutParams(-2, -2));
        use.setOnClickListener(v -> {
            String id = input.getText().toString().trim().replaceAll(".*\\?", "").toLowerCase(Locale.ROOT);
            status.setText("Checking…");
            new Thread(() -> {
                try {
                    JSONObject o = new JSONObject(PrayerSource.fetch("https://masjidboardlive.com/boards/api/board.php?id=" + id));
                    JSONObject d = o.getJSONObject("data");
                    String name = d.optJSONObject("meta") != null ? d.optJSONObject("meta").optString("masjid") : id;
                    main.post(() -> {
                        ShareSetup.prefs(this).edit().putString("board", id).apply();
                        status.setText("Found: " + name);
                        main.postDelayed(this::showTeams, 900);
                    });
                } catch (Exception e) {
                    main.post(() -> status.setText("That board was not found. Check the name and try again."));
                }
            }).start();
        });
        input.requestFocus();
    }

    // ---- 3. teams ----------------------------------------------------------------------------------

    private void showTeams() {
        page("SETUP · 3 OF 3", "Your football teams",
                "Pick any you follow: on match days Home shows the live score and the line-ups. Skip it if you don't follow football.");
        Set<String> chosen = new HashSet<>(ShareSetup.prefs(this).getStringSet("teams", new HashSet<>()));
        ScrollView scroll = new ScrollView(this);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(grid);
        col.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout row = null;
        View first = null;
        for (int i = 0; i < ShareSetup.TEAMS.length; i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-2, -2);
                rl.bottomMargin = px(8);
                grid.addView(row, rl);
            }
            String name = ShareSetup.TEAMS[i][0];
            TextView chip = button((chosen.contains(name) ? "✓  " : "") + name);
            chip.setOnClickListener(v -> {
                if (chosen.contains(name)) chosen.remove(name);
                else chosen.add(name);
                chip.setText((chosen.contains(name) ? "✓  " : "") + name);
            });
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(px(250), -2);
            cl.rightMargin = px(8);
            row.addView(chip, cl);
            if (first == null) first = chip;
        }
        TextView done = button("Done  ›");
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-2, -2);
        dl.topMargin = px(12);
        col.addView(done, dl);
        done.setOnClickListener(v -> {
            ShareSetup.prefs(this).edit().putStringSet("teams", chosen).putBoolean("done", true).apply();
            finishSetup();
        });
        if (first != null) first.requestFocus();
    }

    private void finishSetup() {
        ShareSetup.tellSalahTimes(this);
        getSharedPreferences("salah_chip", Context.MODE_PRIVATE).edit().clear().apply();
        getSharedPreferences("weather_chip", Context.MODE_PRIVATE).edit().clear().apply();
        WallpaperLibrary.start(this);
        page("ALMOST READY", "Getting your wallpapers",
                "Freely licensed photos from Wikimedia Commons. The first few arrive now; the rest keep coming in the background.");
        TextView count = text(20, R.string.font_nav, 0xFFF4F1EA);
        col.addView(count);
        final long until = System.currentTimeMillis() + 90_000L;
        main.post(new Runnable() {
            @Override
            public void run() {
                int n = WallpaperLibrary.saved();
                count.setText(n + " of 10");
                if (n >= 10 || System.currentTimeMillis() > until || (!WallpaperLibrary.running() && n > 0)) {
                    startActivity(new Intent(SetupActivity.this, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    finish();
                    return;
                }
                main.postDelayed(this, 1000);
            }
        });
    }

    // ---- helpers -----------------------------------------------------------------------------------

    private GradientDrawable glass(boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(18));
        g.setColor(focused ? 0x40FFFFFF : 0x17FFFFFF);
        g.setStroke(Math.max(1, px(1)), focused ? 0xE6E0BD72 : 0x33FFFFFF);
        return g;
    }

    private TextView button(String label) {
        TextView b = text(18, R.string.font_nav, 0xFFF4F1EA);
        b.setText(label);
        b.setFocusable(true);
        b.setClickable(true);
        b.setSingleLine(true);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(px(20), px(11), px(20), px(11));
        b.setBackground(glass(false));
        b.setOnFocusChangeListener((v, has) -> {
            v.setBackground(glass(has));
            v.animate().scaleX(has ? 1.04f : 1f).scaleY(has ? 1.04f : 1f).setDuration(150).start();
        });
        return b;
    }

    private TextView text(float sp, int font, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        Typeface tf = UiFont.get(this, font);
        if (tf != null) t.setTypeface(tf);
        return t;
    }

    private int px(float v) {
        return Math.round(v * dp);
    }
}
