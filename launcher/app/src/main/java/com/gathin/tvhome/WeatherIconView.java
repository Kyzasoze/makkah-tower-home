package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * A live weather icon drawn frame by frame: a sun with turning rays, a pulsing corona and heat haze rising under
 * it; a crescent moon among twinkling stars; clouds that drift; rain that falls in slanted streaks and splashes;
 * snow that tumbles; lightning that flashes out of a dark cloud; fog that slides; and, whenever it is windy, wind
 * streaks that curl across. Kind comes from the Open-Meteo weather code (see of()).
 */
public class WeatherIconView extends View {
    static final int CLEAR = 0, PARTLY = 1, CLOUDY = 2, FOG = 3, DRIZZLE = 4, RAIN = 5, SNOW = 6, STORM = 7;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    private final float dp;
    private int kind = CLEAR;
    private boolean day = true, windy;
    private boolean animate = true;
    private final long born = System.currentTimeMillis();

    public WeatherIconView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        p.setStrokeCap(Paint.Cap.ROUND);
    }

    static int of(int code) {
        if (code <= 1) return CLEAR;
        if (code == 2) return PARTLY;
        if (code == 3) return CLOUDY;
        if (code == 45 || code == 48) return FOG;
        if (code >= 51 && code <= 57) return DRIZZLE;
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return RAIN;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return SNOW;
        if (code >= 95) return STORM;
        return CLOUDY;
    }

    void set(int kind, boolean day, boolean windy) {
        this.kind = kind;
        this.day = day;
        this.windy = windy;
        invalidate();
    }

    /** Small copies (the forecast cards) can still move; false freezes them. */
    void setAnimated(boolean on) {
        animate = on;
        invalidate();
    }

    int kind() {
        return kind;
    }

    boolean isDay() {
        return day;
    }

    boolean isWindy() {
        return windy;
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float t = (System.currentTimeMillis() - born) / 1000f;
        float s = Math.min(w, h);
        float cx = w / 2f, cy = h / 2f;
        switch (kind) {
            case CLEAR:
                if (day) {
                    sun(c, cx, cy - s * 0.04f, s * 0.22f, t, true);
                    haze(c, cx, cy + s * 0.30f, s * 0.62f, t);
                } else {
                    stars(c, w, h, t);
                    moon(c, cx, cy, s * 0.24f, t);
                }
                break;
            case PARTLY:
                if (day) sun(c, cx + s * 0.14f, cy - s * 0.14f, s * 0.17f, t, false);
                else moon(c, cx + s * 0.16f, cy - s * 0.16f, s * 0.15f, t);
                cloud(c, cx - s * 0.06f + drift(t, s * 0.03f), cy + s * 0.10f, s * 0.62f, 0xFFF2F6FA, 0xFFB9C6D3);
                break;
            case CLOUDY:
                cloud(c, cx + s * 0.12f + drift(t + 2, s * 0.03f), cy - s * 0.10f, s * 0.48f, 0xFFB6C2CE, 0xFF7E8B98);
                cloud(c, cx - s * 0.06f + drift(t, s * 0.04f), cy + s * 0.08f, s * 0.66f, 0xFFEFF3F7, 0xFFAAB6C2);
                break;
            case FOG:
                cloud(c, cx, cy - s * 0.14f, s * 0.56f, 0xFFD6DDE4, 0xFF98A4B0);
                fog(c, cx, cy + s * 0.14f, s, t);
                break;
            case DRIZZLE:
            case RAIN:
                cloud(c, cx + drift(t, s * 0.02f), cy - s * 0.12f, s * 0.66f, 0xFFD3DCE5, 0xFF8494A3);
                rain(c, cx, cy + s * 0.10f, s, t, kind == RAIN ? 7 : 4, kind == RAIN ? 1f : 0.6f);
                break;
            case SNOW:
                cloud(c, cx + drift(t, s * 0.02f), cy - s * 0.12f, s * 0.66f, 0xFFF4F7FA, 0xFFB4C1CD);
                snow(c, cx, cy + s * 0.12f, s, t);
                break;
            case STORM:
                float flash = flash(t);
                rain(c, cx, cy + s * 0.12f, s, t, 6, 1f);
                bolt(c, cx + s * 0.04f, cy + s * 0.02f, s, flash);
                cloud(c, cx + drift(t, s * 0.02f), cy - s * 0.12f, s * 0.68f,
                        blend(0xFF6F7C8A, 0xFFE6ECF4, flash), blend(0xFF3C4652, 0xFF9AA8B6, flash));
                break;
            default:
                break;
        }
        if (windy) wind(c, w, h, t);
        if (animate && isShown()) postInvalidateDelayed(50);
    }

    // ---- pieces ------------------------------------------------------------------------------------

    private void sun(Canvas c, float x, float y, float rad, float t, boolean full) {
        float pulse = (float) (0.5 + 0.5 * Math.sin(t * 2.2));
        // Corona glow.
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(x, y, rad * (2.3f + 0.25f * pulse), new int[] {0x99FFC24A, 0x40FF9A2E, 0x00FF8A1E},
                new float[] {0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, rad * (2.3f + 0.25f * pulse), p);
        p.setShader(null);
        // Turning rays, alternating long and short.
        p.setStyle(Paint.Style.STROKE);
        int rays = 12;
        float spin = t * 22f;
        for (int i = 0; i < rays; i++) {
            double a = Math.toRadians(spin + i * 360.0 / rays);
            float r0 = rad * 1.30f, r1 = rad * ((i % 2 == 0) ? 1.85f + 0.12f * pulse : 1.58f);
            p.setStrokeWidth(rad * (i % 2 == 0 ? 0.16f : 0.11f));
            p.setColor(i % 2 == 0 ? 0xFFFFC94D : 0xCCFFB13B);
            c.drawLine(x + (float) Math.cos(a) * r0, y + (float) Math.sin(a) * r0,
                    x + (float) Math.cos(a) * r1, y + (float) Math.sin(a) * r1, p);
        }
        // Core: white-hot centre to orange edge.
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(x - rad * 0.25f, y - rad * 0.25f, rad * 1.25f,
                new int[] {0xFFFFFDE8, 0xFFFFD45A, 0xFFFF9A2A}, new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, rad, p);
        p.setShader(null);
        if (full) {
            // A bright glint that circles the rim.
            double g = Math.toRadians(t * 70);
            p.setColor(0xCCFFFFFF);
            c.drawCircle(x + (float) Math.cos(g) * rad * 0.62f, y + (float) Math.sin(g) * rad * 0.62f, rad * 0.12f, p);
        }
    }

    /** Heat haze: wavy lines that rise and fade. */
    private void haze(Canvas c, float x, float y, float width, float t) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, 1.3f * dp));
        for (int k = 0; k < 3; k++) {
            float life = ((t * 0.45f) + k / 3f) % 1f;
            float yy = y - life * width * 0.22f;
            int alpha = (int) (170 * Math.sin(Math.PI * life));
            p.setColor((alpha << 24) | 0xFFB347);
            path.reset();
            float half = width * (0.30f - 0.06f * k);
            for (int i = 0; i <= 16; i++) {
                float xx = x - half + 2 * half * i / 16f;
                float wy = yy + (float) Math.sin(i * 0.9 + t * 5 + k) * width * 0.025f;
                if (i == 0) path.moveTo(xx, wy);
                else path.lineTo(xx, wy);
            }
            c.drawPath(path, p);
        }
    }

    private void moon(Canvas c, float x, float y, float rad, float t) {
        float pulse = (float) (0.5 + 0.5 * Math.sin(t * 1.4));
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(x, y, rad * (2.2f + 0.2f * pulse), 0x66BFD8FF, 0x00BFD8FF, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, rad * (2.2f + 0.2f * pulse), p);
        p.setShader(new LinearGradient(x - rad, y - rad, x + rad, y + rad, 0xFFFFFBEA, 0xFFE3D9B8, Shader.TileMode.CLAMP));
        path.reset();
        path.addCircle(x, y, rad, Path.Direction.CW);
        Path cut = new Path();
        cut.addCircle(x + rad * 0.55f, y - rad * 0.30f, rad * 0.92f, Path.Direction.CW);
        path.op(cut, Path.Op.DIFFERENCE);
        c.drawPath(path, p);
        p.setShader(null);
    }

    private void stars(Canvas c, float w, float h, float t) {
        float[][] pos = {{0.18f, 0.22f}, {0.80f, 0.18f}, {0.86f, 0.62f}, {0.14f, 0.70f}, {0.66f, 0.84f}, {0.30f, 0.10f}};
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < pos.length; i++) {
            float tw = (float) (0.4 + 0.6 * Math.abs(Math.sin(t * (1.3 + i * 0.37) + i)));
            p.setColor(((int) (255 * tw) << 24) | 0xFFFFFF);
            c.drawCircle(pos[i][0] * w, pos[i][1] * h, (0.7f + 0.5f * tw) * dp, p);
        }
    }

    /** A soft cumulus: three lobes on a rounded base, lit from the top. */
    private void cloud(Canvas c, float x, float y, float width, int top, int bottom) {
        float hgt = width * 0.50f;
        path.reset();
        path.addCircle(x - width * 0.20f, y + hgt * 0.06f, width * 0.20f, Path.Direction.CW);
        path.addCircle(x + width * 0.04f, y - hgt * 0.16f, width * 0.27f, Path.Direction.CW);
        path.addCircle(x + width * 0.26f, y + hgt * 0.08f, width * 0.19f, Path.Direction.CW);
        r.set(x - width * 0.40f, y + hgt * 0.02f, x + width * 0.45f, y + hgt * 0.46f);
        path.addRoundRect(r, hgt * 0.22f, hgt * 0.22f, Path.Direction.CW);
        path.setFillType(Path.FillType.WINDING);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, y - hgt * 0.45f, 0, y + hgt * 0.46f, top, bottom, Shader.TileMode.CLAMP));
        c.drawPath(path, p);
        p.setShader(null);
    }

    private void rain(Canvas c, float x, float y, float s, float t, int drops, float strength) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, s * 0.035f * (0.7f + 0.3f * strength)));
        float slant = windy ? 0.45f : 0.18f;
        float fall = s * 0.42f;
        for (int i = 0; i < drops; i++) {
            float life = (t * (1.6f + 0.5f * strength) + i * 0.37f) % 1f;
            float dx = x - s * 0.26f + s * 0.52f * ((i * 0.618f) % 1f);
            float dy = y - s * 0.06f + life * fall;
            float len = s * (0.07f + 0.05f * strength);
            int a = (int) (230 * Math.min(1f, (1f - life) * 2.2f));
            p.setShader(new LinearGradient(dx, dy - len, dx, dy, 0x0089C9FF, (a << 24) | 0x9FD8FF, Shader.TileMode.CLAMP));
            c.drawLine(dx + slant * len, dy - len, dx, dy, p);
            p.setShader(null);
            if (life > 0.86f) {   // splash
                float k = (life - 0.86f) / 0.14f;
                p.setColor(((int) (160 * (1 - k)) << 24) | 0xBFE6FF);
                r.set(dx - s * 0.05f * k, dy - s * 0.012f, dx + s * 0.05f * k, dy + s * 0.012f);
                c.drawOval(r, p);
            }
        }
    }

    private void snow(Canvas c, float x, float y, float s, float t) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, s * 0.022f));
        for (int i = 0; i < 6; i++) {
            float life = (t * 0.55f + i * 0.29f) % 1f;
            float fx = x - s * 0.26f + s * 0.52f * ((i * 0.618f) % 1f) + (float) Math.sin(t * 2 + i) * s * 0.04f
                    + (windy ? life * s * 0.12f : 0);
            float fy = y - s * 0.05f + life * s * 0.40f;
            float fr = s * (0.035f + 0.012f * (i % 3));
            p.setColor(((int) (240 * Math.min(1f, (1f - life) * 2f)) << 24) | 0xFFFFFF);
            double rot = t * 1.5 + i;
            for (int k = 0; k < 3; k++) {
                double a = rot + k * Math.PI / 3;
                c.drawLine(fx - (float) Math.cos(a) * fr, fy - (float) Math.sin(a) * fr,
                        fx + (float) Math.cos(a) * fr, fy + (float) Math.sin(a) * fr, p);
            }
        }
    }

    /** 0..1 brightness of the lightning: a double flash every 3.6 s. */
    static float flash(float t) {
        float ph = t % 3.6f;
        if (ph < 0.08f) return 1f;
        if (ph < 0.16f) return 0.25f;
        if (ph < 0.24f) return 0.85f;
        if (ph < 0.45f) return 0.85f * (1f - (ph - 0.24f) / 0.21f);
        return 0f;
    }

    private void bolt(Canvas c, float x, float y, float s, float flash) {
        if (flash <= 0.01f) return;
        path.reset();
        path.moveTo(x + s * 0.02f, y - s * 0.10f);
        path.lineTo(x - s * 0.10f, y + s * 0.10f);
        path.lineTo(x - s * 0.01f, y + s * 0.10f);
        path.lineTo(x - s * 0.08f, y + s * 0.32f);
        path.lineTo(x + s * 0.12f, y + s * 0.04f);
        path.lineTo(x + s * 0.02f, y + s * 0.04f);
        path.lineTo(x + s * 0.10f, y - s * 0.10f);
        path.close();
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(x, y + s * 0.1f, s * 0.45f, ((int) (150 * flash) << 24) | 0xFFF3B0, 0x00FFF3B0,
                Shader.TileMode.CLAMP));
        c.drawCircle(x, y + s * 0.1f, s * 0.45f, p);
        p.setShader(null);
        p.setColor(((int) (255 * flash) << 24) | 0xFFF6C4);
        c.drawPath(path, p);
    }

    private void fog(Canvas c, float x, float y, float s, float t) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.06f);
        for (int k = 0; k < 3; k++) {
            float off = (float) Math.sin(t * 0.9 + k * 1.7) * s * 0.08f;
            float yy = y + k * s * 0.11f;
            float half = s * (0.30f - k * 0.04f);
            p.setColor(((int) (200 - k * 45) << 24) | 0xDCE4EC);
            c.drawLine(x - half + off, yy, x + half + off, yy, p);
        }
    }

    /** Wind streaks that sweep across and curl at the end. */
    private void wind(Canvas c, float w, float h, float t) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, 1.2f * dp));
        for (int k = 0; k < 3; k++) {
            float life = (t * 0.7f + k * 0.33f) % 1f;
            float y = h * (0.30f + 0.22f * k);
            float x0 = -w * 0.3f + life * w * 1.3f;
            float len = w * 0.45f;
            int a = (int) (200 * Math.sin(Math.PI * life));
            p.setShader(new LinearGradient(x0 - len, y, x0, y, 0x00E8F4FF, (a << 24) | 0xE8F4FF, Shader.TileMode.CLAMP));
            path.reset();
            path.moveTo(x0 - len, y);
            path.cubicTo(x0 - len * 0.5f, y - h * 0.05f, x0 - len * 0.2f, y + h * 0.05f, x0, y);
            path.quadTo(x0 + w * 0.08f, y - h * 0.02f, x0 + w * 0.04f, y - h * 0.08f);
            c.drawPath(path, p);
            p.setShader(null);
        }
    }

    private static float drift(float t, float amp) {
        return (float) Math.sin(t * 0.8) * amp;
    }

    static int blend(int a, int b, float f) {
        int out = 0;
        for (int sh = 0; sh <= 24; sh += 8) {
            int ca = (a >>> sh) & 0xFF, cb = (b >>> sh) & 0xFF;
            out |= ((int) (ca + (cb - ca) * f) & 0xFF) << sh;
        }
        return out;
    }
}
