package com.gathin.tvhome;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The idle screen: a softened, slowly drifting home wallpaper under a deep shade, and in the middle one passage at a
 * time, a Qur'an verse (Arabic over its English translation) or a hadith, with its reference. Passages change by
 * themselves, with a gentle fade and rise, after a time that suits their length; the wallpaper crossfades every
 * other passage. The text block shifts a little each time so nothing stays burnt into the screen.
 */
public class ReflectionsView extends FrameLayout {
    private final float dp;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private final ReflectionSource source;
    private final ImageView[] backs = new ImageView[2];
    private int backShown;
    private final List<File> wallpapers = new ArrayList<>();
    private final LinearLayout block;
    private final TextView kind, arabic, text, ref, hint;
    private final Ornament ornament;
    private final View progress;
    private ValueAnimator drift;
    private ObjectAnimator progressAnim;
    private boolean running;
    private int count;
    private final Runnable advance = this::advance;

    public ReflectionsView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        setBackgroundColor(0xFF07080B);
        source = new ReflectionSource(c);

        for (int i = 0; i < 2; i++) {
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setAlpha(0f);
            addView(iv, new LayoutParams(-1, -1));
            backs[i] = iv;
        }
        View shade = new View(c);
        GradientDrawable sg = new GradientDrawable();
        sg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        sg.setGradientRadius(c.getResources().getDisplayMetrics().widthPixels * 0.75f);
        sg.setColors(new int[] {0xA6060709, 0xE6040506});
        shade.setBackground(sg);
        addView(shade, new LayoutParams(-1, -1));

        block = new LinearLayout(c);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setGravity(Gravity.CENTER_HORIZONTAL);
        LayoutParams bl = new LayoutParams(px(780), -2, Gravity.CENTER);
        addView(block, bl);

        kind = text(12, R.string.font_secondary, 0xFFE0BD72);
        kind.setLetterSpacing(0.32f);
        kind.setAllCaps(true);
        block.addView(kind, wrap(0));
        ornament = new Ornament(c);
        LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(px(240), px(20));
        ol.topMargin = px(10);
        block.addView(ornament, ol);
        arabic = new TextView(c);
        arabic.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        arabic.setTextColor(0xFFF0D9A2);
        arabic.setGravity(Gravity.CENTER);
        arabic.setLineSpacing(0, 1.25f);
        arabic.setShadowLayer(px(10), 0, px(2), 0xAA000000);
        arabic.setTextDirection(View.TEXT_DIRECTION_RTL);
        arabic.setMaxLines(3);
        arabic.setEllipsize(TextUtils.TruncateAt.END);
        block.addView(arabic, wrap(18));
        text = text(24, R.string.font_regular, 0xFFF4F1EA);
        text.setSingleLine(false);
        text.setGravity(Gravity.CENTER);
        text.setLineSpacing(0, 1.28f);
        text.setMaxLines(7);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setShadowLayer(px(10), 0, px(2), 0xAA000000);
        block.addView(text, wrap(18));
        ref = text(13, R.string.font_secondary, 0xB3F4F1EA);
        ref.setLetterSpacing(0.10f);
        block.addView(ref, wrap(20));

        // Corner clock, quiet.
        LinearLayout corner = new LinearLayout(c);
        corner.setOrientation(LinearLayout.VERTICAL);
        TextClock clock = new TextClock(c);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("h:mm");
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        clock.setTextColor(0xD9F4F1EA);
        Typeface heading = UiFont.get(c, R.string.font_heading);
        if (heading != null) clock.setTypeface(heading);
        corner.addView(clock);
        TextClock date = new TextClock(c);
        date.setFormat24Hour("EEEE d MMMM");
        date.setFormat12Hour("EEEE d MMMM");
        date.setAllCaps(true);
        date.setLetterSpacing(0.14f);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        date.setTextColor(0x99F4F1EA);
        Typeface sec = UiFont.get(c, R.string.font_secondary);
        if (sec != null) date.setTypeface(sec);
        corner.addView(date);
        LayoutParams cl = new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        cl.leftMargin = px(48);
        cl.bottomMargin = px(36);
        addView(corner, cl);

        hint = text(11, R.string.font_secondary, 0x80F4F1EA);
        hint.setText("Press any button to return");
        hint.setLetterSpacing(0.12f);
        LayoutParams hl = new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        hl.rightMargin = px(48);
        hl.bottomMargin = px(40);
        addView(hint, hl);

        progress = new View(c);
        progress.setBackgroundColor(0x66E0BD72);
        progress.setPivotX(0);
        progress.setScaleX(0f);
        addView(progress, new LayoutParams(-1, Math.max(1, px(1.5f)), Gravity.BOTTOM));

        loadWallpaperList(c);
        block.setAlpha(0f);
    }

    void start() {
        if (running) return;
        running = true;
        showWallpaper(0);
        drift = ValueAnimator.ofFloat(0f, 1f);   // not ObjectAnimator by name: R8 renames the setter
        drift.addUpdateListener(a -> setDriftPhase((float) a.getAnimatedValue()));
        drift.setDuration(90_000);
        drift.setRepeatCount(ValueAnimator.INFINITE);
        drift.setRepeatMode(ValueAnimator.REVERSE);
        drift.setInterpolator(new LinearInterpolator());
        drift.start();
        hint.setAlpha(1f);
        hint.animate().alpha(0f).setStartDelay(7000).setDuration(1500).start();
        show(source.cached());
        source.next(item -> pending = item);
    }

    void stop() {
        running = false;
        main.removeCallbacksAndMessages(null);
        if (drift != null) drift.cancel();
        if (progressAnim != null) progressAnim.cancel();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        source.close();
        super.onDetachedFromWindow();
    }

    // ---- passages -----------------------------------------------------------------------------

    private ReflectionSource.Item pending;

    private void advance() {
        if (!running) return;
        final ReflectionSource.Item next = pending != null ? pending : source.cached();
        pending = null;
        source.next(item -> pending = item);
        block.animate().alpha(0f).translationY(block.getTranslationY() - px(14)).setStartDelay(0).setDuration(800)
                .setInterpolator(new AccelerateInterpolator()).withEndAction(() -> {
                    if (!running) return;
                    if (++count % 2 == 0) showWallpaper(1 - backShown);
                    show(next);
                }).start();
    }

    private void show(ReflectionSource.Item item) {
        kind.setText(item.label != null ? item.label : item.quran ? "From the Qur'an" : "Hadith");
        boolean hasArabic = item.arabic != null && !item.arabic.isEmpty();
        arabic.setVisibility(hasArabic ? VISIBLE : GONE);
        arabic.setText(hasArabic ? item.arabic : "");
        arabic.setTextSize(TypedValue.COMPLEX_UNIT_SP, item.arabic != null && item.arabic.length() > 140 ? 24 : 30);
        String body = item.quran ? "“" + item.text + "”" : item.text;
        text.setText(body);
        int len = item.text.length();
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, len > 300 ? 19 : len > 200 ? 21 : 24);
        ref.setText(item.source);

        // A slightly different place each time (burn-in), then a soft rise.
        float baseX = (rnd.nextFloat() - 0.5f) * px(48), baseY = (rnd.nextFloat() - 0.5f) * px(30);
        block.setTranslationX(baseX);
        block.setTranslationY(baseY + px(18));
        block.animate().alpha(1f).translationY(baseY).setStartDelay(150).setDuration(1100)
                .setInterpolator(new DecelerateInterpolator(1.6f)).withEndAction(null).start();
        for (int i = 0; i < block.getChildCount(); i++) {
            View v = block.getChildAt(i);
            v.setAlpha(0f);
            v.setTranslationY(px(10));
            v.animate().alpha(1f).translationY(0).setStartDelay(250L + 140L * i).setDuration(900)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
        ornament.spin();

        long hold = Math.max(22_000L, Math.min(60_000L, 14_000L + len * 110L));
        main.removeCallbacks(advance);
        main.postDelayed(advance, hold);
        if (progressAnim != null) progressAnim.cancel();
        progress.setScaleX(0f);
        progressAnim = ObjectAnimator.ofFloat(progress, "scaleX", 0f, 1f);
        progressAnim.setDuration(hold);
        progressAnim.setInterpolator(new LinearInterpolator());
        progressAnim.start();
    }

    // ---- background -----------------------------------------------------------------------------

    private void loadWallpaperList(Context c) {
        try {
            Config cfg = Config.load(c);
            wallpapers.addAll(WallpaperCategories.filter(c, cfg.wallpapers));
            String shown = c.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE).getString("image", "");
            File current = WallpaperRotation.find(wallpapers, shown);
            if (current != null) {
                wallpapers.remove(current);
                wallpapers.add(0, current);
            }
        } catch (RuntimeException ignored) {
            // plain dark background
        }
    }

    private void showWallpaper(int slot) {
        if (wallpapers.isEmpty()) return;
        File f = count == 0 ? wallpapers.get(0) : wallpapers.get(rnd.nextInt(wallpapers.size()));
        new Thread(() -> {
            Bitmap bm = decodeSoft(f);
            if (bm == null) return;
            main.post(() -> {
                if (!running) return;
                ImageView in = backs[slot], out = backs[1 - slot];
                in.setImageBitmap(bm);
                in.animate().alpha(1f).setDuration(2200).start();
                out.animate().alpha(0f).setDuration(2200).start();
                backShown = slot;
            });
        }).start();
    }

    /** A small decode (about 320 px wide) with a real blur, so the view's scaling up stays smooth. */
    private static Bitmap decodeSoft(File f) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= 320) sample *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            Bitmap b = BitmapFactory.decodeFile(f.getPath(), d);
            if (b == null) return null;
            int w = 320, h = Math.max(8, Math.round(320f * b.getHeight() / b.getWidth()));
            Bitmap small = Bitmap.createScaledBitmap(b, w, h, true);
            if (small != b) b.recycle();
            int[] px = new int[w * h];
            small.getPixels(px, 0, w, 0, 0, w, h);
            for (int pass = 0; pass < 3; pass++) {   // three box blurs ~ a gaussian
                boxBlur(px, w, h, 5, true);
                boxBlur(px, w, h, 5, false);
            }
            Bitmap out = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
            small.recycle();
            return out;
        } catch (Throwable e) {
            return null;
        }
    }

    /** One separable box-blur pass (horizontal or vertical) with clamped edges. */
    private static void boxBlur(int[] px, int w, int h, int r, boolean horizontal) {
        int lines = horizontal ? h : w, len = horizontal ? w : h;
        int[] line = new int[len];
        int win = r * 2 + 1;
        for (int l = 0; l < lines; l++) {
            for (int i = 0; i < len; i++) line[i] = px[horizontal ? l * w + i : i * w + l];
            int sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int c = line[Math.max(0, Math.min(len - 1, i))];
                sr += (c >> 16) & 0xFF;
                sg += (c >> 8) & 0xFF;
                sb += c & 0xFF;
            }
            for (int i = 0; i < len; i++) {
                px[horizontal ? l * w + i : i * w + l] = 0xFF000000 | ((sr / win) << 16) | ((sg / win) << 8) | (sb / win);
                int add = line[Math.min(len - 1, i + r + 1)], sub = line[Math.max(0, i - r)];
                sr += ((add >> 16) & 0xFF) - ((sub >> 16) & 0xFF);
                sg += ((add >> 8) & 0xFF) - ((sub >> 8) & 0xFF);
                sb += (add & 0xFF) - (sub & 0xFF);
            }
        }
    }

    /** A slow drift and zoom of the background (Ken Burns). */
    private void setDriftPhase(float t) {
        for (ImageView iv : backs) {
            float s = 1.08f + 0.10f * t;
            iv.setScaleX(s);
            iv.setScaleY(s);
            iv.setTranslationX((t - 0.5f) * px(40));
            iv.setTranslationY((0.5f - t) * px(18));
        }
    }

    // ---- helpers ----------------------------------------------------------------------------------

    private LinearLayout.LayoutParams wrap(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(topDp);
        return lp;
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

    /** A thin gold rule fading at both ends, with an eight-pointed star in the middle that turns on each change. */
    private static final class Ornament extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path star = new Path();
        private final float d;

        Ornament(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
        }

        void spin() {
            setRotationY(0);
            animate().cancel();
            setAlpha(0f);
            animate().alpha(1f).setStartDelay(300).setDuration(900).start();
            ValueAnimator a = ValueAnimator.ofFloat(0f, 45f);
            a.setDuration(1600);
            a.setInterpolator(new DecelerateInterpolator(2f));
            a.addUpdateListener(v -> {
                angle = (float) v.getAnimatedValue();
                invalidate();
            });
            a.start();
        }

        private float angle;

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), cy = h / 2f, cx = w / 2f, r = h * 0.38f;
            if (w <= 0) return;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, d));
            p.setShader(new LinearGradient(0, 0, cx - r * 1.8f, 0, 0x00E0BD72, 0xCCE0BD72, Shader.TileMode.CLAMP));
            c.drawLine(0, cy, cx - r * 1.8f, cy, p);
            p.setShader(new LinearGradient(cx + r * 1.8f, 0, w, 0, 0xCCE0BD72, 0x00E0BD72, Shader.TileMode.CLAMP));
            c.drawLine(cx + r * 1.8f, cy, w, cy, p);
            p.setShader(null);
            p.setColor(0xFFE0BD72);
            c.save();
            c.rotate(angle, cx, cy);
            star.reset();
            star.addRect(cx - r * 0.72f, cy - r * 0.72f, cx + r * 0.72f, cy + r * 0.72f, Path.Direction.CW);
            c.drawPath(star, p);
            c.rotate(45f, cx, cy);
            c.drawPath(star, p);
            c.restore();
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(cx, cy, r * 0.22f, p);
        }
    }
}
