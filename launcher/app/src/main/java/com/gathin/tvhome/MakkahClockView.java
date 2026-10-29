package com.gathin.tvhome;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import java.util.Calendar;

/**
 * A small clock in the likeness of the Makkah clock tower: a gold spire with a crescent, a dark tower block with gold
 * edges, and an ivory dial with gold markers in a softly pulsing green ring. Live hands, with a sweeping second hand.
 */
public class MakkahClockView extends View {
    private static final int GOLD = 0xFFD4AF55, GOLD_DARK = 0xFF9C7A2E, IVORY = 0xFFF6F2E6, GREEN = 0xFF2BD67B;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final float dp;
    private Shader blockShader, spireShader;
    private int shaderW = -1, shaderH = -1;

    public MakkahClockView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        glow.setStyle(Paint.Style.STROKE);
        setLayerType(LAYER_TYPE_SOFTWARE, null);   // BlurMaskFilter for the green glow
        setFocusable(false);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), cx = w / 2f;
        if (w <= 0 || h <= 0) return;
        if (shaderW != (int) w || shaderH != (int) h) {
            blockShader = new LinearGradient(0, h * 0.34f, 0, h, 0xFF2C2C31, 0xFF121214, Shader.TileMode.CLAMP);
            spireShader = new LinearGradient(0, 0, 0, h * 0.34f, 0xFFF3DE9C, GOLD_DARK, Shader.TileMode.CLAMP);
            shaderW = (int) w;
            shaderH = (int) h;
        }
        long now = System.currentTimeMillis();
        float pulse = (float) (0.5 + 0.5 * Math.sin(now / 900.0));

        // Spire, tapering up to the crescent.
        path.reset();
        path.moveTo(cx - w * 0.025f, h * 0.07f);
        path.lineTo(cx + w * 0.025f, h * 0.07f);
        path.lineTo(cx + w * 0.09f, h * 0.29f);
        path.lineTo(cx - w * 0.09f, h * 0.29f);
        path.close();
        fill.setShader(spireShader);
        c.drawPath(path, fill);
        fill.setShader(null);

        // Crescent on top.
        float cr = w * 0.075f, cy0 = h * 0.045f;
        Path moon = new Path();
        moon.addCircle(cx, cy0, cr, Path.Direction.CW);
        Path bite = new Path();
        bite.addCircle(cx, cy0 - cr * 0.48f, cr * 0.82f, Path.Direction.CW);
        moon.op(bite, Path.Op.DIFFERENCE);
        fill.setColor(GOLD);
        c.drawPath(moon, fill);

        // Crown under the spire.
        path.reset();
        path.moveTo(cx - w * 0.16f, h * 0.29f);
        path.lineTo(cx + w * 0.16f, h * 0.29f);
        path.lineTo(cx + w * 0.30f, h * 0.35f);
        path.lineTo(cx - w * 0.30f, h * 0.35f);
        path.close();
        fill.setColor(GOLD_DARK);
        c.drawPath(path, fill);

        // Tower block with gold edges and faint vertical light lines.
        rect.set(cx - w * 0.43f, h * 0.34f, cx + w * 0.43f, h);
        fill.setShader(blockShader);
        c.drawRoundRect(rect, 3 * dp, 3 * dp, fill);
        fill.setShader(null);
        stroke.setColor(GOLD);
        stroke.setStrokeWidth(Math.max(1f, 1.1f * dp));
        c.drawRoundRect(rect, 3 * dp, 3 * dp, stroke);
        stroke.setStrokeWidth(Math.max(1f, 0.6f * dp));
        stroke.setColor(0x33D4AF55);
        for (int i = 1; i <= 3; i++) {
            float x = rect.left + rect.width() * i / 4f;
            c.drawLine(x, h * 0.86f, x, h - 2 * dp, stroke);
        }

        // Green light band above the dial (the tower's illuminated script).
        float fcx = cx, fcy = h * 0.60f, r = w * 0.33f;
        glow.setColor(GREEN);
        glow.setAlpha((int) (120 + 100 * pulse));
        glow.setStrokeWidth(1.6f * dp);
        glow.setMaskFilter(new BlurMaskFilter(2.5f * dp, BlurMaskFilter.Blur.NORMAL));
        c.drawLine(cx - w * 0.28f, h * 0.385f, cx + w * 0.28f, h * 0.385f, glow);

        // Dial: green glow ring, gold rim, ivory face.
        glow.setStrokeWidth(2.4f * dp);
        glow.setAlpha((int) (90 + 120 * pulse));
        glow.setMaskFilter(new BlurMaskFilter(3.5f * dp, BlurMaskFilter.Blur.NORMAL));
        c.drawCircle(fcx, fcy, r + 2.2f * dp, glow);
        glow.setMaskFilter(null);
        fill.setColor(IVORY);
        c.drawCircle(fcx, fcy, r, fill);
        stroke.setColor(GOLD);
        stroke.setStrokeWidth(1.4f * dp);
        c.drawCircle(fcx, fcy, r, stroke);

        // Gold hour markers.
        stroke.setStrokeWidth(Math.max(1f, 1.1f * dp));
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            float in = i % 3 == 0 ? r * 0.70f : r * 0.80f;
            float out = r * 0.92f;
            c.drawLine(fcx + (float) Math.sin(a) * in, fcy - (float) Math.cos(a) * in,
                    fcx + (float) Math.sin(a) * out, fcy - (float) Math.cos(a) * out, stroke);
        }

        // Hands.
        Calendar t = Calendar.getInstance();
        float sec = t.get(Calendar.SECOND) + t.get(Calendar.MILLISECOND) / 1000f;
        float min = t.get(Calendar.MINUTE) + sec / 60f;
        float hr = (t.get(Calendar.HOUR) + min / 60f);
        hand(c, fcx, fcy, hr / 12f, r * 0.50f, 2.2f * dp, 0xFF1B1B1D);
        hand(c, fcx, fcy, min / 60f, r * 0.78f, 1.5f * dp, 0xFF1B1B1D);
        hand(c, fcx, fcy, sec / 60f, r * 0.84f, Math.max(1f, 0.8f * dp), 0xFF12A35A);
        fill.setColor(GOLD);
        c.drawCircle(fcx, fcy, 1.6f * dp, fill);

        postInvalidateDelayed(66);
    }

    private void hand(Canvas c, float x, float y, float turn, float len, float width, int color) {
        double a = Math.PI * 2 * turn;
        stroke.setColor(color);
        stroke.setStrokeWidth(width);
        c.drawLine(x, y, x + (float) Math.sin(a) * len, y - (float) Math.cos(a) * len, stroke);
    }
}
