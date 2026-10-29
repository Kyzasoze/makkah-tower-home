package com.gathin.tvhome;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Match day: today's games for the teams chosen in the setup (their league, the European competitions and the
 * cups), with crests, the score and the minute while live. Hidden on days
 * without a game. Scores come from ESPN's public scoreboard; every 30 minutes, every few minutes in the 100 minutes
 * before kick-off (when the line-ups are announced), every minute while a game is on. When a line-up is confirmed, a
 * banner says so over whatever is on screen and the card shows a gold LINEUPS tag; selecting the card opens a small
 * animated pitch with both line-ups, and OK opens SportzX.
 */
public class MatchChip extends LinearLayout {
    private static final String TAG = "MatchChip";
    private final float dp;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final GradientDrawable bg = new GradientDrawable();
    private final Map<String, Bitmap> crests = new HashMap<>();
    private final Map<String, String> lastScore = new HashMap<>();
    private final List<ObjectAnimator> pulses = new ArrayList<>();
    private boolean busy;
    private final Runnable poll = this::check;
    /** Summary rosters per event id, once both starting elevens are known. */
    private final Map<String, JSONArray> lineups = new java.util.concurrent.ConcurrentHashMap<>();
    private List<JSONObject> current = new ArrayList<>();
    private android.widget.PopupWindow lineupWindow;

    public MatchChip(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setFocusable(true);
        setClickable(true);
        setPadding(px(12), px(6), px(14), px(6));
        bg.setCornerRadius(px(18));
        bg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        bg.setColors(new int[] {0x33318CE7, 0x14FFFFFF});
        bg.setStroke(Math.max(1, px(1)), 0x4DFFFFFF);
        setBackground(bg);
        setVisibility(GONE);
        setOnFocusChangeListener((v, has) -> {
            bg.setStroke(Math.max(1, px(has ? 1.5f : 1f)), has ? 0xE6FFFFFF : 0x4DFFFFFF);
            bg.setColors(has ? new int[] {0x66318CE7, 0x33FFFFFF} : new int[] {0x33318CE7, 0x14FFFFFF});
            animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(220).start();
            if (has) openLineups();
            else closeLineups();
        });
        setOnClickListener(v -> {
            android.content.Intent i = getContext().getPackageManager().getLeanbackLaunchIntentForPackage("com.sportzx.live");
            if (i == null) i = getContext().getPackageManager().getLaunchIntentForPackage("com.sportzx.live");
            if (i != null) getContext().startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        });
        setPivotX(0);
    }

    private static MatchChip instance;

    /**
     * com.gathin.tvhome.LINEUP_DEMO (--es league eng.1 --es event 123): shows a finished match's line-ups on the card
     * and its window, and the line-ups banner, so the look can be checked on a day without a game.
     */
    public static class DemoReceiver extends android.content.BroadcastReceiver {
        @Override
        public void onReceive(Context c, android.content.Intent i) {
            MatchChip m = instance;
            if (m != null) m.demo(i.getStringExtra("league"), i.getStringExtra("event"));
        }
    }

    private void demo(String league, String event) {
        if (league == null || event == null) return;
        new Thread(() -> {
            try {
                JSONObject sum = new JSONObject(get("https://site.api.espn.com/apis/site/v2/sports/soccer/" + league
                        + "/summary?event=" + event));
                JSONObject header = sum.getJSONObject("header");
                JSONArray comps = header.getJSONArray("competitions");
                JSONObject game = new JSONObject();
                game.put("id", event);
                game.put("_league", league);
                game.put("date", comps.getJSONObject(0).optString("date"));
                game.put("competitions", comps);
                game.put("status", comps.getJSONObject(0).optJSONObject("status"));
                JSONArray rosters = sum.getJSONArray("rosters");
                for (JSONObject t : competitors(game)) {
                    String logo = t.optJSONObject("team") != null ? t.optJSONObject("team").optString("logo") : "";
                    if (logo.isEmpty() && t.optJSONObject("team") != null) {
                        JSONArray logos = t.optJSONObject("team").optJSONArray("logos");
                        if (logos != null && logos.length() > 0) {
                            logo = logos.getJSONObject(0).optString("href");
                            t.optJSONObject("team").put("logo", logo);
                        }
                    }
                    if (!logo.isEmpty() && !crests.containsKey(logo)) crests.put(logo, crest(logo));
                }
                main.post(() -> {
                    lineups.put(event, rosters);
                    List<JSONObject> list = new ArrayList<>();
                    list.add(game);
                    current = list;
                    show(list);
                    getContext().getSharedPreferences("match_chip", Context.MODE_PRIVATE).edit().remove("told_" + event).apply();
                    announce(event);
                    main.postDelayed(this::requestFocus, 1500);
                });
            } catch (Exception e) {
                Log.w(TAG, "demo: " + e);
            }
        }).start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        instance = this;
        main.removeCallbacks(poll);
        main.postDelayed(poll, 3000);
    }

    @Override
    protected void onDetachedFromWindow() {
        main.removeCallbacksAndMessages(null);
        for (ObjectAnimator a : pulses) a.cancel();
        closeLineups();
        if (instance == this) instance = null;
        super.onDetachedFromWindow();
    }

    private void check() {
        if (busy) return;
        busy = true;
        String day = new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date());
        new Thread(() -> {
            List<JSONObject> games = new ArrayList<>();
            final List<String> teams = ShareSetup.teams(getContext());
            for (String league : ShareSetup.leagues(getContext())) {
                try {
                    JSONObject o = new JSONObject(get("https://site.api.espn.com/apis/site/v2/sports/soccer/" + league
                            + "/scoreboard?dates=" + day));
                    JSONArray events = o.optJSONArray("events");
                    if (events == null) continue;
                    for (int i = 0; i < events.length(); i++) {
                        JSONObject e = events.getJSONObject(i);
                        if (ours(e, teams) >= 0) {
                            e.put("_league", league);
                            games.add(e);
                        }
                    }
                } catch (Exception ex) {
                    Log.w(TAG, league + ": " + ex);
                }
            }
            Collections.sort(games, (a, b) -> {
                int fa = ours(a, teams), fb = ours(b, teams);
                if (fa != fb) return fa - fb;
                return a.optString("date").compareTo(b.optString("date"));
            });
            long nowMs = System.currentTimeMillis();
            List<String> fresh = new ArrayList<>();
            for (JSONObject g : games) {
                String id = g.optString("id");
                if (lineups.containsKey(id)) continue;
                String st = state(g);
                if ("pre".equals(st) && kickoff(g) - nowMs > 100 * 60_000L) continue;
                try {
                    JSONObject sum = new JSONObject(get("https://site.api.espn.com/apis/site/v2/sports/soccer/"
                            + g.optString("_league") + "/summary?event=" + id));
                    JSONArray rosters = sum.optJSONArray("rosters");
                    if (rosters != null && rosters.length() == 2 && starters(rosters.optJSONObject(0)) >= 11
                            && starters(rosters.optJSONObject(1)) >= 11) {
                        lineups.put(id, rosters);
                        fresh.add(id);
                    }
                } catch (Exception ex) {
                    Log.w(TAG, "summary " + id + ": " + ex);
                }
            }
            for (JSONObject g : games) {
                for (JSONObject t : competitors(g)) {
                    String logo = t.optJSONObject("team") != null ? t.optJSONObject("team").optString("logo") : "";
                    if (!logo.isEmpty() && !crests.containsKey(logo)) crests.put(logo, crest(logo));
                }
            }
            main.post(() -> {
                busy = false;
                current = games;
                show(games);
                for (String id : fresh) announce(id);
                boolean live = false, soon = false, lineupWindowOpen = false;
                long now = System.currentTimeMillis();
                for (JSONObject g : games) {
                    String state = state(g);
                    long until = kickoff(g) - now;
                    if ("in".equals(state)) live = true;
                    if ("pre".equals(state) && until < 20 * 60_000L) soon = true;
                    if ("pre".equals(state) && until < 100 * 60_000L && !lineups.containsKey(g.optString("id"))) lineupWindowOpen = true;
                }
                main.removeCallbacks(poll);
                main.postDelayed(poll, live || soon ? 60_000L : lineupWindowOpen ? 3 * 60_000L : 30 * 60_000L);
            });
        }).start();
    }

    /** The index of the first of our teams in this game (Chelsea first), or -1. */
    private static int ours(JSONObject e, List<String> TEAMS) {
        int best = -1;
        for (JSONObject t : competitors(e)) {
            JSONObject team = t.optJSONObject("team");
            String name = team != null ? team.optString("displayName") : "";
            for (int i = 0; i < TEAMS.size(); i++) {
                if (name.equals(TEAMS.get(i)) && (best < 0 || i < best)) best = i;
            }
        }
        return best;
    }

    private static List<JSONObject> competitors(JSONObject e) {
        List<JSONObject> out = new ArrayList<>();
        JSONArray comps = e.optJSONArray("competitions");
        JSONObject comp = comps != null ? comps.optJSONObject(0) : null;
        JSONArray cs = comp != null ? comp.optJSONArray("competitors") : null;
        if (cs == null) return out;
        JSONObject home = null, away = null;
        for (int i = 0; i < cs.length(); i++) {
            JSONObject c = cs.optJSONObject(i);
            if (c == null) continue;
            if ("home".equals(c.optString("homeAway"))) home = c;
            else away = c;
        }
        if (home != null) out.add(home);
        if (away != null) out.add(away);
        return out;
    }

    private static String state(JSONObject e) {
        JSONObject st = e.optJSONObject("status");
        JSONObject type = st != null ? st.optJSONObject("type") : null;
        return type != null ? type.optString("state") : "";
    }

    private static long kickoff(JSONObject e) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.ROOT);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(e.optString("date")).getTime();
        } catch (Exception ex) {
            return 0;
        }
    }

    private void show(List<JSONObject> games) {
        removeAllViews();
        for (ObjectAnimator a : pulses) a.cancel();
        pulses.clear();
        if (games.isEmpty()) {
            setVisibility(GONE);
            return;
        }
        boolean fresh = getVisibility() != VISIBLE;
        int shown = 0;
        for (JSONObject g : games) {
            if (shown++ == 3) break;
            List<JSONObject> cs = competitors(g);
            if (cs.size() < 2) continue;
            JSONObject h = cs.get(0), a = cs.get(1);
            String state = state(g);
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, px(3), 0, px(3));
            row.addView(crestView(h));
            String score = "pre".equals(state) ? "v" : h.optString("score", "0") + " – " + a.optString("score", "0");
            TextView line = text(15, R.string.font_nav, 0xFFF4F1EA);
            line.setText(abbr(h) + "  " + score + "  " + abbr(a));
            LayoutParams ll = new LayoutParams(-2, -2);
            ll.leftMargin = px(7);
            ll.rightMargin = px(7);
            row.addView(line, ll);
            row.addView(crestView(a));
            TextView status = text(11.5f, R.string.font_secondary, 0xCCF4F1EA);
            status.setLetterSpacing(0.06f);
            LayoutParams sl = new LayoutParams(-2, -2);
            sl.leftMargin = px(10);
            if ("in".equals(state)) {
                JSONObject st = g.optJSONObject("status");
                String clock = st != null ? st.optString("displayClock") : "";
                String detail = st != null && st.optJSONObject("type") != null ? st.optJSONObject("type").optString("shortDetail") : "";
                status.setText("● LIVE " + (detail.toUpperCase(Locale.ROOT).contains("HT") ? "HT" : clock));
                status.setTextColor(0xFFFF5A5A);
                ObjectAnimator p = ObjectAnimator.ofFloat(status, "alpha", 1f, 0.45f, 1f);
                p.setDuration(1400);
                p.setRepeatCount(ValueAnimator.INFINITE);
                p.start();
                pulses.add(p);
            } else if ("post".equals(state)) {
                status.setText("FT");
            } else {
                long k = kickoff(g);
                status.setText(k > 0 ? new SimpleDateFormat("HH:mm", Locale.ROOT).format(new Date(k)) : "Today");
            }
            row.addView(status, sl);
            if (lineups.containsKey(g.optString("id")) && !"post".equals(state)) {
                TextView tag = text(9, R.string.font_secondary, 0xFF141416);
                tag.setText("LINEUPS");
                tag.setLetterSpacing(0.1f);
                tag.setPadding(px(6), px(2), px(6), px(2));
                GradientDrawable tb = new GradientDrawable();
                tb.setCornerRadius(px(8));
                tb.setColor(0xFFE0BD72);
                tag.setBackground(tb);
                LayoutParams tl = new LayoutParams(-2, -2);
                tl.leftMargin = px(8);
                row.addView(tag, tl);
                ObjectAnimator glowTag = ObjectAnimator.ofFloat(tag, "alpha", 1f, 0.6f, 1f);
                glowTag.setDuration(2200);
                glowTag.setRepeatCount(ValueAnimator.INFINITE);
                glowTag.start();
                pulses.add(glowTag);
            }
            addView(row);
            // A goal for one of the sides since the last look: a short gold flash.
            String key = g.optString("id"), now = score;
            String before = lastScore.put(key, now);
            if (before != null && !before.equals(now) && "in".equals(state)) flash(row);
        }
        if (fresh) {
            setVisibility(VISIBLE);
            setAlpha(0f);
            setTranslationY(-px(6));
            animate().alpha(1f).translationY(0).setDuration(380).start();
        }
    }

    private static int starters(JSONObject roster) {
        JSONArray r = roster != null ? roster.optJSONArray("roster") : null;
        int n = 0;
        if (r != null) for (int i = 0; i < r.length(); i++) if (r.optJSONObject(i) != null && r.optJSONObject(i).optBoolean("starter")) n++;
        return n;
    }

    /** First time a line-up is known: a banner over whatever is on screen (once per match). */
    private void announce(String id) {
        android.content.SharedPreferences prefs = getContext().getSharedPreferences("match_chip", Context.MODE_PRIVATE);
        if (prefs.getBoolean("told_" + id, false)) return;
        prefs.edit().putBoolean("told_" + id, true).apply();
        for (JSONObject g : current) {
            if (!id.equals(g.optString("id"))) continue;
            List<JSONObject> cs = competitors(g);
            if (cs.size() < 2) return;
            long k = kickoff(g);
            String when = k > 0 ? new SimpleDateFormat("HH:mm", Locale.ROOT).format(new Date(k)) : "";
            TvPower.notice(getContext(), "\u26BD  Line-ups are out", abbr(cs.get(0)) + " v " + abbr(cs.get(1))
                    + (when.isEmpty() ? "" : "  ·  kick-off " + when) + "  ·  on Home, select the scores card to see them");
            return;
        }
    }

    // ---- the line-up window ------------------------------------------------------------------------

    private void openLineups() {
        closeLineups();
        if (getWindowToken() == null) return;
        JSONObject game = null;
        for (JSONObject g : current) {
            if (lineups.containsKey(g.optString("id"))) {
                game = g;
                break;
            }
        }
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(VERTICAL);
        box.setPadding(px(12), px(10), px(12), px(10));
        GradientDrawable wb = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xF01C1C20, 0xF7111114});
        wb.setCornerRadius(px(18));
        wb.setStroke(Math.max(1, px(1)), 0x4DFFFFFF);
        box.setBackground(wb);
        TextView head = text(11, R.string.font_secondary, 0xFFE0BD72);
        head.setLetterSpacing(0.16f);
        box.addView(head);
        if (game == null) {
            head.setText("LINE-UPS");
            TextView none = text(12, R.string.font_regular, 0xCCF4F1EA);
            none.setText("Not announced yet · usually about an hour before kick-off");
            none.setPadding(0, px(6), 0, 0);
            box.addView(none);
        } else {
            JSONArray rosters = lineups.get(game.optString("id"));
            JSONObject h = null, a = null;
            for (int i = 0; i < rosters.length(); i++) {
                JSONObject r = rosters.optJSONObject(i);
                if (r == null) continue;
                if ("home".equals(r.optString("homeAway"))) h = r;
                else a = r;
            }
            if (h == null) h = rosters.optJSONObject(0);
            if (a == null) a = rosters.optJSONObject(1);
            List<JSONObject> cs = competitors(game);
            String hc = cs.size() > 0 && cs.get(0).optJSONObject("team") != null ? cs.get(0).optJSONObject("team").optString("color") : "";
            String ac = cs.size() > 1 && cs.get(1).optJSONObject("team") != null ? cs.get(1).optJSONObject("team").optString("color") : "";
            head.setText(teamName(h) + "  " + h.optString("formation") + "     v     " + a.optString("formation") + "  " + teamName(a));
            LineupView pitch = new LineupView(getContext());
            pitch.setTeams(h, a, hc, ac);
            LayoutParams pl = new LayoutParams(px(296), px(170));   // clear of the clock's date lines
            pl.topMargin = px(8);
            box.addView(pitch, pl);
        }
        lineupWindow = new android.widget.PopupWindow(box, android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, false);
        lineupWindow.setTouchable(false);
        lineupWindow.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        lineupWindow.setElevation(px(16));
        int[] at = new int[2];
        getLocationInWindow(at);
        box.setAlpha(0f);
        box.setScaleX(0.85f);
        box.setScaleY(0.85f);
        box.setPivotX(0);
        box.setPivotY(0);
        try {
            lineupWindow.showAtLocation(getRootView(), android.view.Gravity.TOP | android.view.Gravity.START, at[0],
                    at[1] + getHeight() + px(8));
            box.animate().alpha(1f).scaleX(1f).scaleY(1f).setInterpolator(new android.view.animation.OvershootInterpolator(1.2f))
                    .setDuration(320).start();
        } catch (Exception e) {
            lineupWindow = null;
        }
    }

    private static String teamName(JSONObject roster) {
        JSONObject t = roster != null ? roster.optJSONObject("team") : null;
        if (t == null) return "";
        String a = t.optString("abbreviation");
        return (a.isEmpty() ? t.optString("shortDisplayName") : a).toUpperCase(Locale.ROOT);
    }

    private void closeLineups() {
        if (lineupWindow != null) {
            try {
                lineupWindow.dismiss();
            } catch (Exception ignored) {
            }
            lineupWindow = null;
        }
    }

    private void flash(View row) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(10));
        g.setColor(0x66FFD27A);
        row.setBackground(g);
        ValueAnimator a = ValueAnimator.ofInt(0x66, 0);
        a.setDuration(4000);
        a.addUpdateListener(v -> g.setColor(((int) v.getAnimatedValue() << 24) | 0xFFD27A));
        a.start();
    }

    private ImageView crestView(JSONObject competitor) {
        ImageView iv = new ImageView(getContext());
        JSONObject team = competitor.optJSONObject("team");
        Bitmap b = team != null ? crests.get(team.optString("logo")) : null;
        if (b != null) iv.setImageBitmap(b);
        iv.setLayoutParams(new LayoutParams(px(20), px(20)));
        return iv;
    }

    private static String abbr(JSONObject competitor) {
        JSONObject team = competitor.optJSONObject("team");
        if (team == null) return "?";
        String a = team.optString("abbreviation");
        return a.isEmpty() ? team.optString("shortDisplayName") : a;
    }

    private Bitmap crest(String url) {
        try {
            byte[] raw = bytes(url);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 4;
            return BitmapFactory.decodeByteArray(raw, 0, raw.length, o);
        } catch (Exception e) {
            return null;
        }
    }

    private static String get(String url) throws Exception {
        return new String(bytes(url), "UTF-8");
    }

    private static byte[] bytes(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11; Smart TV) CleanYourTV");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    private TextView text(float sp, int font, int color) {
        TextView t = new TextView(getContext());
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        Typeface tf = UiFont.get(getContext(), font);
        if (tf != null) t.setTypeface(tf);
        return t;
    }

    private int px(float v) {
        return Math.round(v * dp);
    }
}
