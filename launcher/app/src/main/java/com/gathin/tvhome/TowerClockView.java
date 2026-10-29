package com.gathin.tvhome;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

import java.util.Calendar;

/**
 * The Makkah clock tower: a pre-rendered night illustration (res/drawable-nodpi/tower_art.png, drawn by
 * make_tower.py) with live hands on its dial, a softly breathing green halo round the dial and a shimmer on the
 * crescent. Focus brightens the lights. The dial position below must match make_tower.py.
 */
public class TowerClockView extends View {
    static final float DIAL_CY = 0.497f;   // dial centre, fraction of the art's height
    static final float DIAL_R = 0.335f;    // dial radius, fraction of the art's width
    static final float CRESCENT_CY = 0.023f, CRESCENT_R = 0.090f;
    static final float ART_ASPECT = 360f / 1000f;
    /** The dark band above the dial (make_tower.py prints it) and its half width, where "Allahu Akbar" glows. */
    static final float BAND_CY = 0.3547f, BAND_HALF_W = 0.30f, BAND_H = 0.030f;
    private static final String TAKBIR = "\u0627\u0644\u0644\u0647 \u0623\u0643\u0628\u0631";   // الله أكبر

    private final Drawable art;
    private final Paint hand = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint script = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Rect textBounds = new android.graphics.Rect();
    private final RectF box = new RectF();
    private final float dp;
    private boolean lit;
    private float glowFor = -1;
    private final BlurMaskFilter haloBlur, shineBlur;

    public TowerClockView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        int id = c.getResources().getIdentifier("tower_art", "drawable", c.getPackageName());
        art = id != 0 ? c.getDrawable(id) : null;
        hand.setStyle(Paint.Style.STROKE);
        hand.setStrokeCap(Paint.Cap.ROUND);
        halo.setStyle(Paint.Style.STROKE);
        halo.setColor(0xFF3CE682);
        shine.setColor(0xFFFFE6A0);
        // Glows are radial gradients built once per size (no blur filters, so the GPU draws the view).
        haloBlur = null;
        shineBlur = null;
        halo.setStyle(Paint.Style.FILL);
        script.setTextAlign(Paint.Align.LEFT);
        script.setFakeBoldText(true);
        setFocusable(false);
    }

    /** Brighter lights while the tower is focused. */
    void setLit(boolean on) {
        lit = on;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float vw = getWidth(), vh = getHeight();
        if (vw <= 0 || vh <= 0) return;
        // Fit the art (keep its aspect), centred.
        float aw = Math.min(vw, vh * ART_ASPECT), ah = aw / ART_ASPECT;
        box.set((vw - aw) / 2f, 0, (vw + aw) / 2f, ah);
        if (art != null) {
            art.setBounds((int) box.left, (int) box.top, (int) box.right, (int) box.bottom);
            art.draw(c);
        }
        float cx = box.centerX(), cy = box.top + ah * DIAL_CY, r = aw * DIAL_R;
        long now = System.currentTimeMillis();
        float pulse = (float) (0.5 + 0.5 * Math.sin(now / 1100.0));

        // Breathing green halo round the dial, and the crescent's shimmer: soft rings made once per size.
        float outer = r + 10f * dp, crY = box.top + ah * CRESCENT_CY, crR = aw * CRESCENT_R * 1.8f;
        if (glowFor != vw * 31 + vh) {
            glowFor = vw * 31 + vh;
            halo.setShader(new android.graphics.RadialGradient(cx, cy, outer,
                    new int[] {0x003CE682, 0x003CE682, 0xFF3CE682, 0x003CE682},
                    new float[] {0f, (r - 1f * dp) / outer, (r + 3.5f * dp) / outer, 1f}, android.graphics.Shader.TileMode.CLAMP));
            shine.setShader(new android.graphics.RadialGradient(cx, crY, crR, 0xFFFFE6A0, 0x00FFE6A0,
                    android.graphics.Shader.TileMode.CLAMP));
        }
        halo.setAlpha((int) ((lit ? 150 : 70) + (lit ? 100 : 80) * pulse));
        c.drawCircle(cx, cy, outer, halo);
        shine.setAlpha((int) ((lit ? 110 : 45) + 60 * pulse));
        c.drawCircle(cx, crY, crR, shine);

        // "Allahu Akbar" in glowing green across the band, sized to fit it, breathing with the halo.
        float bandH = ah * BAND_H, bandW = aw * BAND_HALF_W * 2f;
        script.setTextSize(bandH);
        script.getTextBounds(TAKBIR, 0, TAKBIR.length(), textBounds);
        float fit = Math.min(bandH / Math.max(1, textBounds.height()), bandW / Math.max(1, textBounds.width()));
        script.setTextSize(bandH * fit * 0.98f);
        script.getTextBounds(TAKBIR, 0, TAKBIR.length(), textBounds);
        int glowA = (int) ((lit ? 200 : 120) + 55 * pulse);
        script.setShadowLayer((lit ? 5f : 3.5f) * dp, 0, 0, (glowA << 24) | 0x3CE682);
        script.setColor(lit ? 0xFF9BFFC4 : 0xFF5EF09A);
        c.drawText(TAKBIR, cx - textBounds.exactCenterX(), box.top + ah * BAND_CY - textBounds.exactCenterY(), script);

        // Live hands: hour, minute, sweeping second.
        Calendar t = Calendar.getInstance();
        float sec = t.get(Calendar.SECOND) + t.get(Calendar.MILLISECOND) / 1000f;
        float min = t.get(Calendar.MINUTE) + sec / 60f;
        float hr = t.get(Calendar.HOUR) + min / 60f;
        drawHand(c, cx, cy, hr / 12f, r * 0.52f, r * 0.075f, 0xFF16130E);
        drawHand(c, cx, cy, min / 60f, r * 0.80f, r * 0.050f, 0xFF16130E);
        drawHand(c, cx, cy, sec / 60f, r * 0.86f, Math.max(1f, r * 0.018f), 0xFF0F9A50);
        hand.setStyle(Paint.Style.FILL);
        hand.setColor(0xFFC9A14A);
        c.drawCircle(cx, cy, r * 0.055f, hand);
        hand.setStyle(Paint.Style.STROKE);

        // Smooth while the tower is selected; a calm 8 frames a second otherwise (the TV has other work to do).
        postInvalidateDelayed(lit ? 50 : 125);
    }

    private void drawHand(Canvas c, float x, float y, float turn, float len, float width, int color) {
        double a = Math.PI * 2 * turn;
        hand.setColor(color);
        hand.setStrokeWidth(width);
        float tail = len * 0.14f;
        c.drawLine(x - (float) Math.sin(a) * tail, y + (float) Math.cos(a) * tail,
                x + (float) Math.sin(a) * len, y - (float) Math.cos(a) * len, hand);
    }
}
