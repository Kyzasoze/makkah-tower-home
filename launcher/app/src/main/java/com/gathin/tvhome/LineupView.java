package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Both line-ups on one small pitch: the home side on the left attacking right, the away side mirrored. The pitch
 * markings draw themselves in, then the players pop into their formation row by row from each goalkeeper outwards,
 * and the shirts breathe softly afterwards. Fed with ESPN's match summary rosters (setTeams).
 */
public class LineupView extends View {
    private static final class Player {
        String number, name;
        float x, y;   // 0..1 across the pitch
        int row;
    }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float dp;
    private final List<Player> home = new ArrayList<>(), away = new ArrayList<>();
    private int homeColor = 0xFF1E5CC8, awayColor = 0xFFD62828;
    private long born;
    private Typeface font;

    public LineupView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        font = UiFont.get(c, R.string.font_nav);
    }

    /** The two rosters objects from the summary (home first), with team colours as hex without '#'. */
    void setTeams(JSONObject homeRoster, JSONObject awayRoster, String homeHex, String awayHex) {
        home.clear();
        away.clear();
        place(homeRoster, home, false);
        place(awayRoster, away, true);
        homeColor = colour(homeHex, 0xFF1E5CC8);
        awayColor = colour(awayHex, 0xFFD62828);
        if (Math.abs(lum(homeColor) - lum(awayColor)) < 0.05 && sameHue(homeColor, awayColor)) awayColor = 0xFFF2F2F2;
        born = System.currentTimeMillis();
        invalidate();
    }

    private static int colour(String hex, int fallback) {
        try {
            if (hex == null || hex.length() < 6) return fallback;
            int c = 0xFF000000 | Integer.parseInt(hex.substring(0, 6), 16);
            return lum(c) < 0.08 ? 0xFF3A3A40 : c;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static float lum(int c) {
        return (0.3f * ((c >> 16) & 255) + 0.59f * ((c >> 8) & 255) + 0.11f * (c & 255)) / 255f;
    }

    private static boolean sameHue(int a, int b) {
        return Math.abs(((a >> 16) & 255) - ((b >> 16) & 255)) < 40 && Math.abs(((a >> 8) & 255) - ((b >> 8) & 255)) < 40
                && Math.abs((a & 255) - (b & 255)) < 40;
    }

    /** Depth from the position code: goalkeeper 0, defenders 1, holding 2, centre 3, attacking 4, forwards 5. */
    private static float depth(String pos) {
        String s = pos == null ? "" : pos.toUpperCase();
        if (s.equals("G") || s.equals("GK")) return 0;
        if (s.contains("WB")) return 1.5f;
        if (s.contains("B") || s.startsWith("CD") || s.equals("SW") || s.equals("D")) return 1;
        if (s.contains("DM")) return 2;
        if (s.contains("AM")) return 4;
        if (s.endsWith("W") || s.equals("LW") || s.equals("RW")) return 4.5f;
        if (s.startsWith("F") || s.contains("CF") || s.equals("ST") || s.equals("S")) return 5;
        if (s.contains("M")) return 3;
        return 3;
    }

    /** -1 for the player's left, +1 for the right, 0 central. */
    private static int lateral(String pos) {
        String s = pos == null ? "" : pos.toUpperCase();
        if (s.startsWith("L") || s.endsWith("-L")) return -1;
        if (s.startsWith("R") || s.endsWith("-R")) return 1;
        return 0;
    }

    private void place(JSONObject roster, List<Player> out, boolean mirrored) {
        if (roster == null) return;
        JSONArray list = roster.optJSONArray("roster");
        if (list == null) return;
        List<JSONObject> starters = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o != null && o.optBoolean("starter")) starters.add(o);
        }
        if (starters.isEmpty()) return;
        Collections.sort(starters, (a, b) -> {
            float da = depth(pos(a)), db = depth(pos(b));
            if (da != db) return Float.compare(da, db);
            return Integer.compare(a.optInt("formationPlace", 99), b.optInt("formationPlace", 99));
        });
        // Rows: the goalkeeper, then the formation's lines ("4-2-3-1"), or one line per depth if it is missing.
        List<Integer> counts = new ArrayList<>();
        counts.add(1);
        String formation = roster.optString("formation", "");
        int sum = 0;
        for (String part : formation.split("-")) {
            try {
                int n = Integer.parseInt(part.trim());
                counts.add(n);
                sum += n;
            } catch (NumberFormatException ignored) {
            }
        }
        if (sum != starters.size() - 1) {
            counts.clear();
            float last = -1;
            for (JSONObject o : starters) {
                float d = depth(pos(o));
                if (d != last) {
                    counts.add(0);
                    last = d;
                }
                counts.set(counts.size() - 1, counts.get(counts.size() - 1) + 1);
            }
        }
        int idx = 0, rows = counts.size();
        for (int row = 0; row < rows && idx < starters.size(); row++) {
            List<JSONObject> line = new ArrayList<>(starters.subList(idx, Math.min(starters.size(), idx + counts.get(row))));
            idx += line.size();
            // Facing the other goal, a player's left is up the screen for the home side, down for the away side.
            Collections.sort(line, (a, b) -> Integer.compare(lateral(pos(a)), lateral(pos(b))));
            for (int k = 0; k < line.size(); k++) {
                JSONObject o = line.get(k);
                Player pl = new Player();
                pl.number = o.optString("jersey", "");
                JSONObject ath = o.optJSONObject("athlete");
                String nm = ath != null ? ath.optString("lastName", ath.optString("shortName", ath.optString("displayName"))) : "";
                if (nm.isEmpty() && ath != null) nm = ath.optString("displayName");
                pl.name = nm.length() > 11 ? nm.substring(0, 10) + "." : nm;
                float depthX = rows == 1 ? 0.5f : 0.05f + 0.40f * row / (rows - 1f);
                float lane = (k + 1f) / (line.size() + 1f);
                pl.x = mirrored ? 1f - depthX : depthX;
                pl.y = mirrored ? 1f - lane : lane;
                pl.row = row;
                out.add(pl);
            }
        }
    }

    private static String pos(JSONObject o) {
        JSONObject p = o.optJSONObject("position");
        return p != null ? p.optString("abbreviation") : "";
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w <= 0) return;
        float t = (System.currentTimeMillis() - born) / 1000f;
        float m = 6 * dp;
        r.set(m, m, w - m, h - m);
        // Turf with mowing stripes.
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, 0, 0, h, 0xFF1F7A3E, 0xFF12552A, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, 10 * dp, 10 * dp, p);
        p.setShader(null);
        p.setColor(0x14FFFFFF);
        for (int i = 0; i < 10; i += 2) c.drawRect(r.left + r.width() * i / 10f, r.top, r.left + r.width() * (i + 1) / 10f, r.bottom, p);
        // Markings, drawn in over the first half second.
        float draw = Math.min(1f, t / 0.5f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, 1.1f * dp));
        p.setColor(((int) (190 * draw) << 24) | 0xFFFFFF);
        c.drawRoundRect(r, 10 * dp, 10 * dp, p);
        float cx = r.centerX(), cy = r.centerY();
        c.drawLine(cx, r.top, cx, r.top + (r.bottom - r.top) * draw, p);
        c.drawCircle(cx, cy, r.height() * 0.16f * draw, p);
        float bw = r.width() * 0.13f, bh = r.height() * 0.56f;
        c.drawRect(r.left, cy - bh / 2, r.left + bw * draw, cy + bh / 2, p);
        c.drawRect(r.right - bw * draw, cy - bh / 2, r.right, cy + bh / 2, p);
        // Players, row by row from each keeper.
        drawSide(c, home, homeColor, t, r);
        drawSide(c, away, awayColor, t, r);
        if (t < 4f || isShown()) postInvalidateDelayed(t < 2.5f ? 16 : 120);
    }

    private void drawSide(Canvas c, List<Player> side, int colour, float t, RectF field) {
        float rad = 7.5f * dp;
        for (Player pl : side) {
            float start = 0.45f + pl.row * 0.16f;
            float k = Math.max(0f, Math.min(1f, (t - start) / 0.35f));
            if (k <= 0) continue;
            float pop = k < 1f ? (float) (1 + 0.35 * Math.sin(Math.PI * k)) * k : 1f;
            float breathe = 1f + 0.06f * (float) Math.sin(t * 2.2 + pl.x * 9 + pl.y * 5);
            float x = field.left + pl.x * field.width(), y = field.top + pl.y * field.height();
            float rr = rad * pop * breathe;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x55000000);
            c.drawCircle(x, y + 1.5f * dp, rr, p);
            p.setColor(colour);
            c.drawCircle(x, y, rr, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, 1.2f * dp));
            p.setColor(0xE6FFFFFF);
            c.drawCircle(x, y, rr, p);
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            if (font != null) p.setTypeface(font);
            p.setTextSize(7.5f * dp * pop);
            p.setColor(lum(colour) > 0.6f ? 0xFF111111 : 0xFFFFFFFF);
            c.drawText(pl.number, x, y + 2.6f * dp, p);
            p.setTextSize(6.6f * dp);
            p.setColor(((int) (235 * k) << 24) | 0xFFFFFF);
            p.setShadowLayer(2 * dp, 0, 0, 0xCC000000);
            c.drawText(pl.name, x, y + rad + 8 * dp, p);
            p.clearShadowLayer();
        }
    }
}
