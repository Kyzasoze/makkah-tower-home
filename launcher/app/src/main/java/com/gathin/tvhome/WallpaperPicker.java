package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The wallpaper button's panel: a glass strip that drops from the top with one card per category, each showing a
 * picture from it and how many it has. Left/Right move, OK chooses (the wallpaper changes at once and the rotation
 * keeps to that category), Back closes.
 */
final class WallpaperPicker {
    private WallpaperPicker() {}

    static void show(Activity a, List<File> files, Runnable onChosen) {
        float dp = a.getResources().getDisplayMetrics().density;
        Handler main = new Handler(Looper.getMainLooper());
        ExecutorService io = Executors.newSingleThreadExecutor();
        String current = WallpaperCategories.selected(a);

        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(px(dp, 18), px(dp, 14), px(dp, 18), px(dp, 16));
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xF01C1C20, 0xF7101013});
        bg.setCornerRadius(px(dp, 26));
        bg.setStroke(Math.max(1, px(dp, 1)), 0x4DFFFFFF);
        panel.setBackground(bg);

        TextView title = text(a, 12, R.string.font_secondary, 0xFFE0BD72);
        title.setText("WALLPAPERS");
        title.setLetterSpacing(0.3f);
        panel.addView(title);
        TextView sub = text(a, 13, R.string.font_regular, 0xA6F4F1EA);
        sub.setText("Choose what the background shows · now: " + WallpaperCategories.label(current));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.topMargin = px(dp, 4);
        panel.addView(sub, sl);

        // The cards scroll sideways inside the panel; focus moving along scrolls them into view.
        android.widget.HorizontalScrollView scroller = new android.widget.HorizontalScrollView(a);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setHorizontalFadingEdgeEnabled(true);
        scroller.setFadingEdgeLength(px(dp, 28));
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        scroller.setPadding(0, px(dp, 6), 0, px(dp, 6));
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(px(dp, 6), 0, px(dp, 6), 0);
        scroller.addView(row, new ViewGroup.LayoutParams(-2, -2));
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.topMargin = px(dp, 8);
        panel.addView(scroller, rl);

        int width = Math.min(a.getResources().getDisplayMetrics().widthPixels - px(dp, 56), px(dp, 900));
        PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(px(dp, 20));
        popup.setOnDismissListener(io::shutdownNow);

        List<View> cards = new ArrayList<>();
        View first = null;
        for (String[] cat : WallpaperCategories.LIST) {
            List<File> pics = WallpaperCategories.in(files, cat[0]);
            if (pics.isEmpty()) continue;
            boolean isCurrent = cat[0].equals(current);
            LinearLayout card = new LinearLayout(a);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setFocusable(true);
            card.setClickable(true);
            card.setPadding(px(dp, 6), px(dp, 6), px(dp, 6), px(dp, 8));
            GradientDrawable cbg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] {0x1FFFFFFF, 0x0AFFFFFF});
            cbg.setCornerRadius(px(dp, 16));
            cbg.setStroke(Math.max(1, px(dp, isCurrent ? 1.6f : 1f)), isCurrent ? 0xB3E0BD72 : 0x26FFFFFF);
            card.setBackground(cbg);

            FrameLayout thumbBox = new FrameLayout(a);
            ImageView thumb = new ImageView(a);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setBackgroundColor(0xFF22252B);
            int r = px(dp, 11);
            thumb.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
                }
            });
            thumb.setClipToOutline(true);
            thumbBox.addView(thumb, new FrameLayout.LayoutParams(px(dp, 100), px(dp, 58)));
            if (isCurrent) {
                TextView badge = text(a, 9, R.string.font_secondary, 0xFF141416);
                badge.setText("SHOWING");
                badge.setLetterSpacing(0.12f);
                badge.setPadding(px(dp, 6), px(dp, 2), px(dp, 6), px(dp, 2));
                GradientDrawable bb = new GradientDrawable();
                bb.setCornerRadius(px(dp, 8));
                bb.setColor(0xFFE0BD72);
                badge.setBackground(bb);
                FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
                bl.topMargin = px(dp, 5);
                bl.rightMargin = px(dp, 5);
                thumbBox.addView(badge, bl);
            }
            card.addView(thumbBox);
            TextView name = text(a, 13, R.string.font_label, 0xFFF4F1EA);
            name.setText(cat[1]);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(px(dp, 100), -2);
            nl.topMargin = px(dp, 7);
            card.addView(name, nl);
            TextView count = text(a, 10.5f, R.string.font_secondary, 0x8CF4F1EA);
            count.setText(pics.size() + (pics.size() == 1 ? " picture" : " pictures"));
            card.addView(count);

            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
            if (!cards.isEmpty()) cl.leftMargin = px(dp, 9);
            row.addView(card, cl);
            cards.add(card);
            if (isCurrent || first == null) first = card;

            card.setOnFocusChangeListener((v, has) -> {
                cbg.setColors(has ? new int[] {0x3DFFFFFF, 0x14FFFFFF} : new int[] {0x1FFFFFFF, 0x0AFFFFFF});
                if (!isCurrent) cbg.setStroke(Math.max(1, px(dp, has ? 1.4f : 1f)), has ? 0xD9F4F1EA : 0x26FFFFFF);
                v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).translationY(has ? -px(dp, 3) : 0)
                        .setInterpolator(new OvershootInterpolator(2f)).setDuration(240).start();
                thumb.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(600)
                        .setInterpolator(new DecelerateInterpolator()).start();
            });
            card.setOnClickListener(v -> {
                WallpaperCategories.select(a, cat[0]);
                sub.setText("Now showing: " + cat[1]);
                v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).withEndAction(() ->
                        v.animate().scaleX(1.08f).scaleY(1.08f).setInterpolator(new OvershootInterpolator(3f))
                                .setDuration(240).start()).start();
                if (onChosen != null) onChosen.run();
                main.postDelayed(() -> {
                    try {
                        popup.dismiss();
                    } catch (Exception ignored) {
                    }
                }, 450);
            });
            File pic = pics.get(0);
            io.execute(() -> {
                Bitmap b = thumbOf(pic);
                if (b != null) main.post(() -> thumb.setImageBitmap(b));
            });
        }
        // Left/Right stop at the ends instead of leaving the panel.
        for (int i = 0; i < cards.size(); i++) {
            final int at = i;
            cards.get(i).setOnKeyListener((v, keyCode, e) -> {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && at == 0) return true;
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && at == cards.size() - 1) return true;
                return keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
            });
        }

        panel.setAlpha(0f);
        panel.setTranslationY(-px(dp, 24));
        popup.showAtLocation(a.getWindow().getDecorView(), Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, px(dp, 74));
        panel.animate().alpha(1f).translationY(0).setInterpolator(new DecelerateInterpolator(1.8f)).setDuration(320).start();
        for (int i = 0; i < cards.size(); i++) {
            View c = cards.get(i);
            c.setAlpha(0f);
            c.setTranslationY(-px(dp, 10));
            c.animate().alpha(1f).translationY(0).setStartDelay(80L + 45L * i).setDuration(280)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
        View focus = first;
        if (focus != null) panel.post(focus::requestFocus);
    }

    private static Bitmap thumbOf(File f) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int s = 1;
            while (o.outWidth / (s * 2) >= 260) s *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = s;
            d.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(f.getPath(), d);
        } catch (Throwable e) {
            return null;
        }
    }

    private static TextView text(Activity a, float sp, int font, int color) {
        TextView t = new TextView(a);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        Typeface tf = UiFont.get(a, font);
        if (tf != null) t.setTypeface(tf);
        return t;
    }

    private static int px(float dp, float v) {
        return Math.round(v * dp);
    }
}
