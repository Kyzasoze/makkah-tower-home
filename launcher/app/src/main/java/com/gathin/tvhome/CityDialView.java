package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * A small live dial for one world clock. Its face is that city's sky right now: deep blue with twinkling stars at
 * night, warm amber at dawn and dusk, soft blue by day, with the sun or a crescent moon resting in it. Hour, minute
 * and a sweeping green second hand run on the city's own time. Focus (setActive) turns the rim gold and lifts the
 * glow.
 */
public class CityDialView extends View {
    private static final int[] NIGHT = {12, 20, 46}, DUSK = {214, 122, 78}, DAY = {84, 156, 218};
    private static final float[][] STARS = {{-0.45f, -0.30f}, {0.38f, -0.42f}, {0.52f, 0.12f}, {-0.20f, 0.48f},
            {-0.58f, 0.20f}, {0.12f, -0.62f}, {0.30f, 0.44f}};

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;
    private TimeZone tz = TimeZone.getDefault();
    private boolean active;
    private float glow;            // 0..1, eased toward active
    private RadialGradient faceShader;
    private int faceColor = 0;

    public CityDialView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        setFocusable(false);
    }

    void setTimeZone(TimeZone zone) {
        tz = zone;
        faceColor = 0;
        invalidate();
    }

    void setActive(boolean on) {
        active = on;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float cx = w / 2f, cy = h / 2f, r = Math.min(w, h) / 2f - 2.5f * dp;
        long now = System.currentTimeMillis();
        glow += ((active ? 1f : 0f) - glow) * 0.18f;

        Calendar t = Calendar.getInstance(tz);
        t.setTimeInMillis(now);
        float sec = t.get(Calendar.SECOND) + t.get(Calendar.MILLISECOND) / 1000f;
        float min = t.get(Calendar.MINUTE) + sec / 60f;
        float hr24 = t.get(Calendar.HOUR_OF_DAY) + min / 60f;

        // Sky colour from a simple sun height: -1 at midnight, +1 at noon.
        float e = (float) -Math.cos(2 * Math.PI * hr24 / 24.0);
        int[] col = e < 0 ? mix(NIGHT, DUSK, clamp((e + 0.35f) / 0.35f)) : mix(DUSK, DAY, clamp(e / 0.35f));
        int face = 0xFF000000 | (col[0] << 16) | (col[1] << 8) | col[2];
        if (face != faceColor || faceShader == null) {
            faceColor = face;
            int lighter = 0xFF000000 | (Math.min(255, col[0] + 40) << 16) | (Math.min(255, col[1] + 40) << 8)
                    | Math.min(255, col[2] + 40);
            faceShader = new RadialGradient(cx, cy - r * 0.4f, r * 1.4f, lighter, face, Shader.TileMode.CLAMP);
        }

        // Outer glow when focused.
        if (glow > 0.02f) {
            p.setStyle(Paint.Style.FILL);
            p.setShader(null);
            p.setColor(0xE9C77B);
            p.setAlpha((int) (70 * glow));
            c.drawCircle(cx, cy, r + 2.5f * dp, p);
        }
        // Face.
        p.setStyle(Paint.Style.FILL);
        p.setShader(faceShader);
        c.drawCircle(cx, cy, r, p);
        p.setShader(null);

        // Stars at night, twinkling.
        if (e < -0.1f) {
            float vis = clamp((-e - 0.1f) / 0.4f);
            for (int i = 0; i < STARS.length; i++) {
                float tw = (float) (0.55 + 0.45 * Math.sin(now / (520.0 + i * 90) + i * 1.7));
                p.setColor(0xFFFFFFFF);
                p.setAlpha((int) (210 * vis * tw));
                c.drawCircle(cx + STARS[i][0] * r, cy + STARS[i][1] * r, Math.max(0.8f, 0.9f * dp), p);
            }
        }
        // Sun or crescent moon, low in the face so the hands stay readable.
        float bx = cx + r * 0.36f, by = cy + r * 0.34f, br = r * 0.20f;
        if (e >= 0) {
            float pulse = (float) (0.5 + 0.5 * Math.sin(now / 700.0));
            p.setColor(0xFFFFD27A);
            p.setAlpha((int) (60 + 50 * pulse));
            c.drawCircle(bx, by, br * 1.7f, p);
            p.setAlpha(255);
            c.drawCircle(bx, by, br, p);
        } else {
            p.setColor(0xFFF2EBD8);
            c.drawCircle(bx, by, br, p);
            p.setColor(face);
            c.drawCircle(bx + br * 0.45f, by - br * 0.25f, br * 0.92f, p);
        }

        // Rim and hour dots.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.4f * dp);
        p.setColor(blend(0x80FFFFFF, 0xFFE9C77B, glow));
        c.drawCircle(cx, cy, r, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xCCFFFFFF);
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            float rr = r * 0.82f;
            c.drawCircle(cx + (float) Math.sin(a) * rr, cy - (float) Math.cos(a) * rr, (i % 3 == 0 ? 1.1f : 0.6f) * dp, p);
        }

        // Hands.
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStyle(Paint.Style.STROKE);
        hand(c, cx, cy, (hr24 % 12) / 12f, r * 0.48f, 2.0f * dp, 0xFFF7F3EA);
        hand(c, cx, cy, min / 60f, r * 0.72f, 1.5f * dp, 0xFFF7F3EA);
        hand(c, cx, cy, sec / 60f, r * 0.78f, 0.9f * dp, 0xFF3CE682);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFE9C77B);
        c.drawCircle(cx, cy, 1.8f * dp, p);

        if (isShown()) postInvalidateDelayed(active ? 50 : 125);
    }

    private void hand(Canvas c, float x, float y, float turn, float len, float width, int color) {
        double a = Math.PI * 2 * turn;
        p.setColor(color);
        p.setStrokeWidth(width);
        c.drawLine(x, y, x + (float) Math.sin(a) * len, y - (float) Math.cos(a) * len, p);
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static int[] mix(int[] a, int[] b, float t) {
        return new int[] {(int) (a[0] + (b[0] - a[0]) * t), (int) (a[1] + (b[1] - a[1]) * t), (int) (a[2] + (b[2] - a[2]) * t)};
    }

    private static int blend(int a, int b, float t) {
        int r = 0;
        for (int s = 0; s <= 24; s += 8) {
            int ca = (a >>> s) & 0xFF, cb = (b >>> s) & 0xFF;
            r |= ((int) (ca + (cb - ca) * t) & 0xFF) << s;
        }
        return r;
    }
}
