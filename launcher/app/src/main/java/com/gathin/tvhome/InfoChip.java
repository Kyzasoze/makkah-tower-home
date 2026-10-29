package com.gathin.tvhome;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A slim glass line under the next-salah chip for what matters today: Ramadan's iftar or suhoor countdown,
 * Jumu'ah, or a notice from the mosque's board. Display only (it takes no focus); hidden when there is nothing.
 */
public class InfoChip extends LinearLayout {
    private final float dp;
    private final TextView icon, text;
    private final GradientDrawable bg = new GradientDrawable();
    private ObjectAnimator pulse;
    private String shown = "";

    public InfoChip(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setFocusable(false);
        setPadding(px(12), px(4), px(14), px(4));
        bg.setCornerRadius(px(16));
        bg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        setBackground(bg);
        icon = new TextView(c);
        icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        icon.setIncludeFontPadding(false);
        addView(icon);
        text = new TextView(c);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        text.setTextColor(0xE6F4F1EA);
        text.setIncludeFontPadding(false);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        text.setMarqueeRepeatLimit(-1);
        text.setSelected(true);
        text.setMaxWidth(px(300));
        text.setLetterSpacing(0.03f);
        Typeface tf = UiFont.get(c, R.string.font_secondary);
        if (tf != null) text.setTypeface(tf);
        LayoutParams tl = new LayoutParams(-2, -2);
        tl.leftMargin = px(7);
        addView(text, tl);
        setVisibility(GONE);
    }

    /** Shows a line (null hides it). accent tints the glass and the icon. */
    void show(String glyph, String line, int accent) {
        if (line == null) {
            if (getVisibility() != GONE) animate().alpha(0f).setDuration(250).withEndAction(() -> setVisibility(GONE)).start();
            shown = "";
            return;
        }
        String key = glyph + line + accent;
        if (key.equals(shown) && getVisibility() == VISIBLE) return;
        boolean fresh = getVisibility() != VISIBLE;
        shown = key;
        icon.setText(glyph);
        icon.setTextColor(accent);
        text.setText(line);
        bg.setColors(new int[] {(accent & 0x00FFFFFF) | 0x33000000, 0x14FFFFFF});
        bg.setStroke(Math.max(1, px(1)), (accent & 0x00FFFFFF) | 0x66000000);
        if (fresh) {
            setVisibility(VISIBLE);
            setAlpha(0f);
            setTranslationY(-px(6));
            animate().alpha(1f).translationY(0).setDuration(350).start();
        }
        if (pulse == null) {
            pulse = ObjectAnimator.ofFloat(icon, "alpha", 1f, 0.5f, 1f);
            pulse.setDuration(2400);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (pulse != null) pulse.cancel();
        pulse = null;
        super.onDetachedFromWindow();
    }

    private int px(float v) {
        return Math.round(v * dp);
    }
}
