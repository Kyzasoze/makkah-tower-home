package com.majnioui.tv.prayer;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.icu.util.IslamicCalendar;
import android.icu.util.ULocale;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.provider.Settings;
import android.view.View;

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
 * Today's salah times from the local mosque's MasjidBoardLive board (the same feed its screen in the masjid
 * shows): adhan and jamaah for each prayer, sunrise, Jumu'ah on Fridays, and upcoming timetable changes.
 * The next adhan or jamaah is highlighted with a countdown. The last answer is kept, so times stay on screen offline.
 */
public class MainActivity extends Activity {
    private static final String TAG = "PrayerTimes";
    private static final String[] NAMES = {"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};
    private static final String[] ARABIC = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
    private static final int[] ICONS = {R.drawable.ic_fajr, R.drawable.ic_sunrise, R.drawable.ic_dhuhr,
            R.drawable.ic_asr, R.drawable.ic_maghrib, R.drawable.ic_isha};
    private static final String[] HIJRI_MONTHS = {"Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani",
            "Jumada al-Ula", "Jumada al-Akhirah", "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qi'dah",
            "Dhu al-Hijjah"};
    private static final long MINUTE_MS = 60_000L;
    private static final long REFRESH_MS = 60 * MINUTE_MS;

    private final Handler main = new Handler(Looper.getMainLooper());
    private Typeface font;
    private int accent;
    private TextView placeView, dates, next, extra, status, adhanBtn, testBtn;
    private LinearLayout rows;

    /** Adhan (or start) time per NAMES entry, "HH:mm"; jamaah may be empty. */
    private String[] adhan, jamaah;
    private String jumuah = "", changes = "", updated = "";
    private String loadedFor;
    private long loadedAt;
    private boolean loading, offline;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!today().equals(loadedFor) || System.currentTimeMillis() - loadedAt > REFRESH_MS) load();
            render();
            main.postDelayed(this, MINUTE_MS);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        font = getResources().getFont(R.font.outfit);
        accent = getColor(R.color.accent);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {(accent & 0x00FFFFFF) | 0x55000000, 0xFF050607, 0xFF050607}));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(72), dp(40), dp(72), dp(32));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        placeView = text(18, 400, 0xB3FFFFFF);
        placeView.setLetterSpacing(0.2f);
        placeView.setText(BoardStore.place(this).toUpperCase(Locale.ROOT));
        col.addView(placeView);

        next = text(46, 300, 0xFFF4F1EA);
        next.setTypeface(getResources().getFont(R.font.unbounded_black));
        next.setLetterSpacing(-0.01f);
        next.setPadding(0, dp(10), 0, dp(4));
        col.addView(next);
        dates = text(20, 400, 0xB3FFFFFF);
        col.addView(dates);

        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, 0, 1f);
        rlp.setMargins(0, dp(28), 0, 0);
        col.addView(rows, rlp);

        extra = text(18, 400, 0xD9FFFFFF);
        extra.setPadding(0, dp(18), 0, 0);
        col.addView(extra);

        status = text(15, 400, 0x80FFFFFF);
        status.setPadding(0, dp(8), 0, 0);
        status.setText("Loading…");
        col.addView(status);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(14), 0, 0);
        adhanBtn = button();
        adhanBtn.setOnClickListener(v -> {
            boolean on = !BoardStore.adhanOn(this);
            BoardStore.prefs(this).edit().putBoolean(BoardStore.KEY_ADHAN_ON, on).apply();
            AdhanScheduler.scheduleNext(this);
            updateAdhanButton();
        });
        buttons.addView(adhanBtn);
        testBtn = button();
        testBtn.setText("▶  Test adhan");
        testBtn.setOnClickListener(v -> AdhanService.start(this, "Test", "", ""));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.leftMargin = dp(16);
        buttons.addView(testBtn, tlp);
        TextView quranBtn = button();
        quranBtn.setText("\uD83D\uDCD6  Qur'an");
        quranBtn.setOnClickListener(v -> startActivity(new android.content.Intent(this, QuranActivity.class)));
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(-2, -2);
        qlp.leftMargin = dp(16);
        buttons.addView(quranBtn, qlp);
        LinearLayout buttons2 = new LinearLayout(this);
        buttons2.setOrientation(LinearLayout.HORIZONTAL);
        buttons2.setPadding(0, dp(12), 0, 0);
        TextView wakeBtn = button();
        Runnable wakeText = () -> wakeBtn.setText(BoardStore.fajrWake(this) ? "\u263C  Wake TV for Fajr: ON" : "\u263C  Wake TV for Fajr: OFF");
        wakeText.run();
        wakeBtn.setOnClickListener(v -> {
            BoardStore.prefs(this).edit().putBoolean("fajr_wake", !BoardStore.fajrWake(this)).apply();
            wakeText.run();
        });
        buttons2.addView(wakeBtn);
        TextView volBtn = button();
        Runnable volText = () -> volBtn.setText("\uD83D\uDD09  Adhan volume: " + BoardStore.prefs(this).getInt("adhan_volume", 60) + "%");
        volText.run();
        volBtn.setOnClickListener(v -> {
            int cur = BoardStore.prefs(this).getInt("adhan_volume", 60);
            int next = cur >= 100 ? 30 : cur + 10;
            BoardStore.prefs(this).edit().putInt("adhan_volume", next).apply();
            volText.run();
        });
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-2, -2);
        vlp.leftMargin = dp(16);
        buttons2.addView(volBtn, vlp);
        col.addView(buttons);
        col.addView(buttons2);
        updateAdhanButton();

        setContentView(root);

        // Show the last saved timetable straight away; the network answer replaces it.
        String cached = prefs().getString("json", null);
        if (cached != null) {
            try {
                parse(new JSONObject(cached).getJSONObject("data"));
                updated = prefs().getString("at", "");
                offline = true;
                AdhanScheduler.scheduleNext(getApplicationContext());
            } catch (Exception e) {
                Log.w(TAG, "cache: " + e);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
        render();
        updateAdhanButton();
        adhanBtn.requestFocus();
        main.postDelayed(tick, MINUTE_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        main.removeCallbacks(tick);
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("board", MODE_PRIVATE);
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
    }

    private static String hhmm() {
        return new SimpleDateFormat("HH:mm", Locale.ROOT).format(new Date());
    }

    private void load() {
        if (loading) return;
        loading = true;
        final String day = today();
        new Thread(() -> {
            String body = null;
            JSONObject json = null;
            try {
                body = BoardStore.fetchToday(this);
                json = new JSONObject(body);
                if (!json.optBoolean("ok", true)) json = null;
            } catch (Exception e) {
                Log.w(TAG, "fetch: " + e);
            }
            final JSONObject result = json;
            final String raw = body;
            main.post(() -> {
                loading = false;
                if (result == null) {
                    offline = true;
                    render();
                    return;
                }
                try {
                    parse(result.getJSONObject("data"));
                    loadedFor = day;
                    loadedAt = System.currentTimeMillis();
                    updated = hhmm();
                    offline = false;
                    prefs().edit().putString("json", raw).putString("at", day + " " + updated).apply();
                    AdhanScheduler.scheduleNext(getApplicationContext());
                    render();
                } catch (Exception e) {
                    Log.w(TAG, "parse: " + e);
                    offline = true;
                    render();
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

    private void parse(JSONObject d) {
        String[] a = {
                first(d, "fajrAthan", "fajrStarts"),
                clean(d.optString("sunrise")),
                clean(d.optString("dhuhrAthan")),
                clean(d.optString("asrAthan")),
                first(d, "maghribAthan", "sunset"),
                first(d, "eshaAthan", "eshaStarts"),
        };
        Calendar c = Calendar.getInstance();
        String dhuhrJ = clean(d.optString("dhuhrJamaah"));
        String sunday = clean(d.optString("zuhrSundays"));
        if (c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY && !sunday.isEmpty()) dhuhrJ = sunday;
        String[] j = {
                clean(d.optString("fajrJamaah")),
                "",
                dhuhrJ,
                clean(d.optString("asrJamaah")),
                clean(d.optString("maghribJamaah")),
                clean(d.optString("eshaJamaah")),
        };
        adhan = a;
        jamaah = j;

        StringBuilder jm = new StringBuilder();
        for (String k : new String[] {"jumuahTime1", "jumuahTime2", "jumuahTime3"}) {
            String t = clean(d.optString(k));
            if (!t.isEmpty()) jm.append(jm.length() == 0 ? "" : "  ·  ").append(t);
        }
        String khateeb = d.optString("jumuah_khateeb", "").trim();
        jumuah = jm.length() == 0 ? "" : "Jumu'ah  " + jm + (khateeb.isEmpty() ? "" : "   —  " + khateeb);

        StringBuilder ch = new StringBuilder();
        appendChange(ch, "Fajr", d.optString("fajrNextDate"), d.optString("fajrNextTime"));
        appendChange(ch, "Asr", d.optString("asrNextDate"), d.optString("asrNextTime"));
        appendChange(ch, "Isha", d.optString("eshaNextDate"), d.optString("eshaNextTime"));
        changes = ch.toString();

        JSONObject meta = d.optJSONObject("meta");
        if (meta != null) {
            String name = meta.optString("masjid", "").trim();
            String city = meta.optString("city", "").trim();
            if (!name.isEmpty()) {
                placeView.setText((name + (city.isEmpty() ? "" : "  ·  " + city)).toUpperCase(Locale.ROOT));
            }
        }
    }

    /** "Isha jamaah 20:00 from Sun 1 Nov" when the board announces a change. */
    private static void appendChange(StringBuilder sb, String name, String date, String time) {
        String t = clean(time);
        String dt = date == null ? "" : date.replace("&nbsp;", "").trim();
        if (t.isEmpty() || dt.isEmpty()) return;
        String when = dt;
        try {
            Date parsed = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.ENGLISH).parse(dt);
            if (parsed != null) when = new SimpleDateFormat("EEE d MMM", Locale.ENGLISH).format(parsed);
        } catch (Exception ignored) {
        }
        sb.append(sb.length() == 0 ? "Coming up:  " : "   ·   ").append(name).append(" jamaah ").append(t)
                .append(" from ").append(when);
    }

    private static int min(String hm) {
        if (hm == null || hm.isEmpty()) return -1;
        return Integer.parseInt(hm.substring(0, 2)) * 60 + Integer.parseInt(hm.substring(3, 5));
    }

    private String hijri() {
        try {
            IslamicCalendar ic = new IslamicCalendar(ULocale.ENGLISH);
            ic.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
            ic.setTime(new Date());
            return ic.get(IslamicCalendar.DAY_OF_MONTH) + " " + HIJRI_MONTHS[ic.get(IslamicCalendar.MONTH)] + " "
                    + ic.get(IslamicCalendar.YEAR) + " AH";
        } catch (Exception e) {
            return "";
        }
    }

    private void render() {
        String h = hijri();
        dates.setText(new SimpleDateFormat("EEEE d MMMM", Locale.ENGLISH).format(new Date())
                + (h.isEmpty() ? "" : "  ·  " + h));
        if (adhan == null) {
            if (offline) status.setText("Can't reach the masjid board. Check the TV's internet.");
            return;
        }
        Calendar c = Calendar.getInstance();
        boolean friday = c.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY;
        int now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);

        // The next thing to count down to: a prayer's adhan, then its jamaah.
        int nextIdx = -1;
        String nextLabel = "";
        int nextAt = -1;
        for (int i = 0; i < NAMES.length; i++) {
            if (i == 1) continue;
            int ad = min(adhan[i]);
            int jt = min(jamaah[i]);
            if (ad > now) { nextIdx = i; nextAt = ad; nextLabel = "adhan"; break; }
            if (jt > now) { nextIdx = i; nextAt = jt; nextLabel = "jamaah"; break; }
        }
        if (nextIdx >= 0) {
            int left = nextAt - now;
            String name = (nextIdx == 2 && friday) ? "Jumu'ah" : NAMES[nextIdx];
            next.setText(name + " " + nextLabel + " in " + (left >= 60 ? left / 60 + " h " : "") + left % 60 + " min");
        } else {
            next.setText("Fajr tomorrow · " + adhan[0]);
        }

        rows.removeAllViews();
        for (int i = 0; i < NAMES.length; i++) {
            boolean hl = i == nextIdx;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(dp(6), dp(16), dp(6), dp(16));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(22));
            bg.setColor(hl ? accent : 0x14FFFFFF);
            card.setBackground(bg);
            int dim = i == 1 ? 0x80FFFFFF : 0xFFFFFFFF;
            int fg = hl ? 0xFFFFFFFF : dim;
            ImageView icon = new ImageView(this);
            icon.setImageResource(ICONS[i]);
            icon.setImageTintList(ColorStateList.valueOf(hl ? 0xFFFFFFFF : (i == 1 ? 0x80FFFFFF : accent)));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(40), dp(40));
            ilp.bottomMargin = dp(14);
            card.addView(icon, ilp);
            TextView ar = centered(text(24, 400, fg));
            ar.setText(ARABIC[i]);
            card.addView(ar);
            TextView name = centered(text(19, 500, hl ? 0xE6FFFFFF : (i == 1 ? 0x80FFFFFF : 0xB3FFFFFF)));
            name.setText(i == 2 && friday ? "Jumu'ah" : NAMES[i]);
            name.setPadding(0, dp(6), 0, dp(12));
            card.addView(name);

            // Big number: the jamaah time (what you plan around); adhan underneath.
            boolean hasJ = !jamaah[i].isEmpty();
            TextView big = centered(text(40, hl ? 500 : 300, fg));
            big.setText(hasJ ? jamaah[i] : (adhan[i].isEmpty() ? "—" : adhan[i]));
            big.setMaxLines(1);
            big.setAutoSizeTextTypeUniformWithConfiguration(18, 40, 1, TypedValue.COMPLEX_UNIT_SP);
            card.addView(big, new LinearLayout.LayoutParams(-1, dp(48)));
            TextView small = centered(text(16, 400, hl ? 0xE6FFFFFF : 0x99FFFFFF));
            if (i == 1) small.setText(" ");
            else if (hasJ) small.setText("Jamaah · adhan " + adhan[i]);
            else small.setText("Adhan");
            small.setMaxLines(1);
            small.setAutoSizeTextTypeUniformWithConfiguration(10, 16, 1, TypedValue.COMPLEX_UNIT_SP);
            small.setPadding(0, dp(6), 0, 0);
            card.addView(small, new LinearLayout.LayoutParams(-1, dp(26)));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            lp.setMargins(i == 0 ? 0 : dp(7), 0, i == NAMES.length - 1 ? 0 : dp(7), 0);
            rows.addView(card, lp);
        }

        StringBuilder ex = new StringBuilder();
        if (!jumuah.isEmpty()) ex.append(jumuah);
        if (!changes.isEmpty()) ex.append(ex.length() == 0 ? "" : "\n").append(changes);
        extra.setText(ex.toString());
        status.setText("masjidboardlive.com" + (updated.isEmpty() ? "" : " · updated " + updated)
                + (offline ? " · offline, showing saved times" : ""));
    }

    private TextView button() {
        TextView b = text(18, 500, 0xFFFFFFFF);
        b.setFocusable(true);
        b.setClickable(true);
        b.setPadding(dp(22), dp(12), dp(22), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(24));
        bg.setColor(0x1FFFFFFF);
        bg.setStroke(dp(1), 0x40FFFFFF);
        b.setBackground(bg);
        b.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? accent : 0x1FFFFFFF);
            bg.setStroke(dp(1), has ? 0xFFFFFFFF : 0x40FFFFFF);
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(150).start();
        });
        return b;
    }

    private void updateAdhanButton() {
        boolean on = BoardStore.adhanOn(this);
        boolean overlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
        adhanBtn.setText(on ? "\uD83D\uDD0A  Adhan alerts: ON" : "\uD83D\uDD07  Adhan alerts: OFF");
        if (on && !overlay) adhanBtn.setText(adhanBtn.getText() + "  (banner needs permission)");
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

    /** Centred on its own line, whatever the script direction. */
    private static TextView centered(TextView t) {
        t.setGravity(Gravity.CENTER);
        t.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER);
        return t;
    }

    private TextView text(float sp, int weight, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setTypeface(Build.VERSION.SDK_INT >= 28 ? Typeface.create(font, weight, false) : font);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
