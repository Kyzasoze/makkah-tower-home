package com.gathin.tvhome;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.List;
import java.util.TimeZone;

/**
 * The centre of the home screen: the Makkah clock tower with the time and date under it. Focusing it lifts the
 * tower, brightens its lights and unfolds a slim glass strip of three world clocks under it. Each clock has a small
 * live dial whose face is that city's sky right now. Down steps into the strip, Left/Right move between the cities,
 * OK on a city changes it, Up returns to the tower and Down again goes to the apps. The strip folds away when the
 * focus leaves both.
 */
public class TowerWidget extends LinearLayout {
    private static final String[] DEFAULTS = {"Makkah", "Madinah", "London"};

    private final float dp;
    private final TowerClockView tower;
    private final GradientDrawable focusBg = new GradientDrawable();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable sync = this::syncPanel;
    private final CityCard[] cards = new CityCard[3];
    private final FrameLayout[] slots = new FrameLayout[3];
    private final List<android.animation.Animator> flights = new java.util.ArrayList<>();
    private LinearLayout panel;
    private boolean shown;
    private float restY;
    private Runnable exitDown;
    private TextView hijri, coming;
    private final Runnable dates = new Runnable() {
        @Override
        public void run() {
            refreshDates();
            main.postDelayed(this, 10 * 60_000L);
        }
    };

    /** The Hijri line and the upcoming line, re-read every ten minutes (the date turns at midnight). */
    private void refreshDates() {
        try {
            hijri.setText(IslamicDates.today(getContext()));
            String eid = IslamicDates.eid(getContext());
            if (eid != null) {
                // Eid: a glowing gold greeting in place of the coming-days line.
                coming.setVisibility(VISIBLE);
                coming.setText((eid.equals("fitr") ? "EID AL-FITR" : "EID AL-ADHA") + "  ·  EID MUBARAK  ·  \u0639\u064A\u062F \u0645\u0628\u0627\u0631\u0643");
                coming.setTextColor(0xFFFFD27A);
                coming.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
                coming.setShadowLayer(px(10), 0, 0, 0xCCFFB347);
                return;
            }
            coming.setTextColor(0xB3F4F1EA);
            coming.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
            coming.setShadowLayer(px(8), 0, px(1), 0x99000000);
            String next = IslamicDates.upcoming(getContext());
            coming.setVisibility(next == null ? GONE : VISIBLE);
            coming.setText(next == null ? "" : "\u25C6  " + next);
        } catch (Exception e) {
            hijri.setVisibility(GONE);
            coming.setVisibility(GONE);
        }
    }

    public TowerWidget(Context c, int towerWidthDp) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setFocusable(true);
        setClickable(true);
        setClipChildren(false);
        setClipToPadding(false);
        setPadding(px(14), px(6), px(14), px(10));
        focusBg.setCornerRadius(px(26));
        focusBg.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        focusBg.setColors(new int[] {0x00FFFFFF, 0x00FFFFFF});
        setBackground(focusBg);

        tower = new TowerClockView(c);
        int tw = px(towerWidthDp);
        addView(tower, new LayoutParams(tw, Math.round(tw / TowerClockView.ART_ASPECT)));

        TextClock time = new TextClock(c);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("h:mm");
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        time.setTextColor(0xFFF4F1EA);
        time.setIncludeFontPadding(false);
        time.setLetterSpacing(0.01f);
        time.setShadowLayer(px(10), 0, px(2), 0x99000000);
        Typeface heading = UiFont.get(c, R.string.font_heading);
        if (heading != null) time.setTypeface(heading);
        LayoutParams tl = new LayoutParams(-2, -2);
        tl.topMargin = -px(62);   // the time sits over the tower's faded base
        addView(time, tl);

        TextClock day = new TextClock(c);
        day.setFormat24Hour("EEEE d MMMM");
        day.setFormat12Hour("EEEE d MMMM");
        day.setAllCaps(true);
        day.setLetterSpacing(0.16f);
        day.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        day.setTextColor(0xCCF4F1EA);
        day.setIncludeFontPadding(false);
        day.setShadowLayer(px(8), 0, px(1), 0x99000000);
        Typeface secondary = UiFont.get(c, R.string.font_secondary);
        if (secondary != null) day.setTypeface(secondary);
        LayoutParams dl = new LayoutParams(-2, -2);
        dl.topMargin = px(5);
        addView(day, dl);

        // The Hijri date in gold, and the Islamic day coming up.
        hijri = new TextView(c);
        hijri.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        hijri.setTextColor(0xFFE0BD72);
        hijri.setLetterSpacing(0.14f);
        hijri.setAllCaps(true);
        hijri.setIncludeFontPadding(false);
        hijri.setShadowLayer(px(8), 0, px(1), 0x99000000);
        if (secondary != null) hijri.setTypeface(secondary);
        LayoutParams hl = new LayoutParams(-2, -2);
        hl.topMargin = px(5);
        addView(hijri, hl);
        coming = new TextView(c);
        coming.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        coming.setTextColor(0xB3F4F1EA);
        coming.setLetterSpacing(0.06f);
        coming.setIncludeFontPadding(false);
        coming.setShadowLayer(px(8), 0, px(1), 0x99000000);
        if (secondary != null) coming.setTypeface(secondary);
        LayoutParams cl2 = new LayoutParams(-2, -2);
        cl2.topMargin = px(4);
        addView(coming, cl2);
        refreshDates();

        setOnFocusChangeListener((v, has) -> {
            focusBg.setColors(has ? new int[] {0x2EFFFFFF, 0x0DFFFFFF} : new int[] {0x00FFFFFF, 0x00FFFFFF});
            focusBg.setStroke(has ? Math.max(1, px(1.2f)) : 0, 0x99FFFFFF);
            tower.setLit(has || (panel != null && panel.hasFocus()));
            animate().scaleX(has ? 1.04f : 1f).scaleY(has ? 1.04f : 1f).translationY(restY - (has ? px(3) : 0))
                    .setInterpolator(new OvershootInterpolator(1.6f)).setDuration(320).start();
            queueSync();
        });
        setOnClickListener(v -> chooseSlot());
        setPivotY(0);
    }

    /** Where the tower rests (MainActivity lines it up with the header); focus lifts it a little from here. */
    void setRestY(float y) {
        restY = y;
        setTranslationY(y);
        if (shown) placePanel();
    }

    /** Down from the city strip: MainActivity sends the focus to the apps. */
    void setOnExitDown(Runnable r) {
        exitDown = r;
    }

    /** Down from the tower: into the city strip (the middle city). False when there is no strip to go to. */
    boolean enterPanel() {
        if (panel == null) return false;
        if (!shown) showPanel();
        return cards[1].requestFocus();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        main.removeCallbacks(dates);
        main.postDelayed(dates, 10 * 60_000L);
        // Built after this attach finishes: adding a sibling from inside the parent's attach pass is avoided.
        post(() -> {
            if (panel == null && getParent() instanceof FrameLayout) buildPanel((FrameLayout) getParent());
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        main.removeCallbacksAndMessages(null);
        if (panel != null && panel.getParent() instanceof ViewGroup) ((ViewGroup) panel.getParent()).removeView(panel);
        panel = null;
        shown = false;
        super.onDetachedFromWindow();
    }

    // ---- the city strip --------------------------------------------------------------------------

    private void buildPanel(FrameLayout parent) {
        panel = new LinearLayout(getContext());
        panel.setOrientation(HORIZONTAL);
        // No strip behind them: the three small glass cards float on their own, out of the tower's dial.
        panel.setPadding(px(6), px(4), px(6), px(6));
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        for (int i = 0; i < 3; i++) {
            cards[i] = new CityCard(i);
            FrameLayout slot = new FrameLayout(getContext());
            slot.setClipChildren(false);
            slot.addView(cards[i], new FrameLayout.LayoutParams(px(80), ViewGroup.LayoutParams.WRAP_CONTENT));
            slots[i] = slot;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.leftMargin = px(7);
            panel.addView(slot, lp);
        }
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        panel.setVisibility(INVISIBLE);
        parent.addView(panel, fl);
    }

    private void queueSync() {
        main.removeCallbacks(sync);
        main.postDelayed(sync, 90);   // moving tower -> city fires blur then focus; wait for it to settle
    }

    private void syncPanel() {
        if (panel == null) return;
        boolean want = hasFocus() || panel.hasFocus();
        tower.setLit(want);
        if (want && !shown) showPanel();
        else if (!want && shown) hidePanel();
    }

    private void placePanel() {
        panel.setTranslationY(restY + getHeight() * 1.04f + px(2));
    }

    private void showPanel() {
        shown = true;
        for (CityCard card : cards) card.bind();
        placePanel();
        panel.animate().cancel();
        panel.setAlpha(1f);
        panel.setScaleX(1f);
        panel.setScaleY(1f);
        panel.setVisibility(VISIBLE);
        for (FrameLayout s : slots) {
            s.setAlpha(0f);   // hidden until the flight starts, so nothing flashes in place first
        }
        panel.post(() -> fly(true));
    }

    private void hidePanel() {
        shown = false;
        fly(false);
    }

    /**
     * The cities float out of the tower's dial: each card starts small at the dial, rises along a gentle arc
     * (the side cards swing outwards, tilting as they go) and settles into place; going away they are drawn
     * back into the dial.
     */
    private void fly(boolean out) {
        for (android.animation.Animator a : flights) a.cancel();
        flights.clear();
        if (panel == null) return;
        float dialY = restY + getPaddingTop() + tower.getHeight() * TowerClockView.DIAL_CY - panel.getTranslationY();
        float centreX = panel.getWidth() / 2f;
        for (int i = 0; i < 3; i++) {
            FrameLayout s = slots[i];
            float sx = s.getLeft() + s.getWidth() / 2f, sy = s.getTop() + s.getHeight() / 2f;
            float dx = centreX - sx, dy = dialY - sy;
            int side = i - 1;
            android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(out ? 620 : 300);
            a.setStartDelay(out ? 60L + 90L * i : 40L * (2 - i));
            a.setInterpolator(out ? new OvershootInterpolator(1.4f) : new android.view.animation.AccelerateInterpolator(1.6f));
            a.addUpdateListener(v -> {
                float f = (float) v.getAnimatedValue();
                float e = out ? f : 1f - f;                        // 0 at the dial, 1 in place
                float lin = Math.max(0f, Math.min(1f, e));
                float arc = (float) Math.sin(Math.PI * lin);
                s.setTranslationX(dx * (1 - e) + side * px(26) * arc);
                s.setTranslationY(dy * (1 - e) - px(14) * arc);
                float sc = 0.2f + 0.8f * e;
                s.setScaleX(sc);
                s.setScaleY(sc);
                s.setRotation(side * -22f * (1 - lin));
                s.setAlpha(Math.min(1f, lin * 1.8f));
            });
            if (!out && i == 0) {
                a.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(android.animation.Animator anim) {
                        if (!shown && panel != null) panel.setVisibility(INVISIBLE);
                    }
                });
            }
            flights.add(a);
            a.start();
        }
    }

    // ---- choosing cities ----------------------------------------------------------------------------

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences("city_chips", Context.MODE_PRIVATE);
    }

    private String[] city(int slot) {
        String n = prefs().getString("slot" + slot, DEFAULTS[slot]);
        for (String[] c : CityChip.CITIES) if (c[0].equals(n)) return c;
        return CityChip.CITIES[0];
    }

    private void chooseSlot() {
        String[] slots = new String[3];
        for (int i = 0; i < 3; i++) slots[i] = "Clock " + (i + 1) + ":   " + city(i)[2] + "  " + city(i)[0];
        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("World clocks — choose one to change")
                .setItems(slots, (d, which) -> chooseCity(which))
                .setNegativeButton("Close", null)
                .show();
    }

    private void chooseCity(int slot) {
        String[] names = new String[CityChip.CITIES.length];
        int sel = 0;
        String cur = city(slot)[0];
        for (int i = 0; i < names.length; i++) {
            names[i] = CityChip.CITIES[i][2] + "   " + CityChip.CITIES[i][0];
            if (CityChip.CITIES[i][0].equals(cur)) sel = i;
        }
        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Clock " + (slot + 1))
                .setSingleChoiceItems(names, sel, (d, which) -> {
                    prefs().edit().putString("slot" + slot, CityChip.CITIES[which][0]).apply();
                    d.dismiss();
                    if (cards[slot] != null) {
                        cards[slot].bind();
                        cards[slot].flash();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---- one city ---------------------------------------------------------------------------------

    private final class CityCard extends LinearLayout {
        private final int slot;
        private final CityDialView dial;
        private final TextView name, offset;
        private final TextClock time;
        private final GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x24FFFFFF, 0x08FFFFFF});

        CityCard(int slot) {
            super(TowerWidget.this.getContext());
            this.slot = slot;
            Context c = getContext();
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setFocusable(true);
            setClickable(true);
            setPadding(px(5), px(4), px(6), px(4));
            bg.setCornerRadius(px(12));
            bg.setStroke(Math.max(1, px(1)), 0x2EFFFFFF);
            setBackground(bg);

            dial = new CityDialView(c);
            addView(dial, new LayoutParams(px(24), px(24)));

            LinearLayout col = new LinearLayout(c);
            col.setOrientation(VERTICAL);
            LayoutParams cl = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            cl.leftMargin = px(5);
            addView(col, cl);
            name = text(7.5f, R.string.font_secondary, 0xBFF4F1EA);
            name.setAllCaps(true);
            name.setLetterSpacing(0.14f);
            col.addView(name);
            time = new TextClock(c);
            time.setFormat24Hour("HH:mm");
            time.setFormat12Hour("HH:mm");
            time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            time.setTextColor(0xFFF4F1EA);
            time.setIncludeFontPadding(false);
            Typeface nav = UiFont.get(c, R.string.font_nav);
            if (nav != null) time.setTypeface(nav);
            LayoutParams tl = new LayoutParams(-2, -2);
            tl.topMargin = px(1);
            col.addView(time, tl);
            offset = text(7.5f, R.string.font_secondary, 0x8CF4F1EA);
            LayoutParams ol = new LayoutParams(-2, -2);
            ol.topMargin = px(1);
            col.addView(offset, ol);

            setOnFocusChangeListener((v, has) -> {
                bg.setColors(has ? new int[] {0x47FFFFFF, 0x14FFFFFF} : new int[] {0x24FFFFFF, 0x08FFFFFF});
                bg.setStroke(Math.max(1, px(has ? 1.4f : 1f)), has ? 0xD9E9C77B : 0x2EFFFFFF);
                dial.setActive(has);
                animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).alpha(has ? 1f : 0.82f)
                        .translationY(has ? -px(2) : 0).setStartDelay(0)
                        .setInterpolator(new OvershootInterpolator(2f)).setDuration(260).start();
                if (has) {
                    dial.setScaleX(0.8f);
                    dial.setScaleY(0.8f);
                    dial.animate().scaleX(1f).scaleY(1f).setDuration(420)
                            .setInterpolator(new OvershootInterpolator(3f)).start();
                }
                queueSync();
            });
            setOnClickListener(v -> chooseCity(slot));
            setOnKeyListener((v, keyCode, e) -> {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        if (slot > 0) cards[slot - 1].requestFocus();
                        else nudge(-1);
                        return true;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        if (slot < 2) cards[slot + 1].requestFocus();
                        else nudge(1);
                        return true;
                    case KeyEvent.KEYCODE_DPAD_UP:
                        TowerWidget.this.requestFocus();
                        return true;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        if (exitDown != null) exitDown.run();
                        return true;
                    default:
                        return false;
                }
            });
            bind();
        }

        void bind() {
            String[] c = city(slot);
            TimeZone tz = TimeZone.getTimeZone(c[1]);
            name.setText(c[0]);
            time.setTimeZone(c[1]);
            dial.setTimeZone(tz);
            long now = System.currentTimeMillis();
            long diffMin = (tz.getOffset(now) - TimeZone.getDefault().getOffset(now)) / 60_000L;
            if (diffMin == 0) {
                offset.setText("same time");
            } else {
                long h = Math.abs(diffMin) / 60, m = Math.abs(diffMin) % 60;
                offset.setText((diffMin > 0 ? "+" : "−") + h + (m != 0 ? ":" + (m < 10 ? "0" : "") + m : "") + "h");
            }
        }

        /** A quick shimmer after the city changes. */
        void flash() {
            dial.setScaleX(0.6f);
            dial.setScaleY(0.6f);
            dial.animate().scaleX(1f).scaleY(1f).setDuration(520).setInterpolator(new OvershootInterpolator(3f)).start();
            time.setAlpha(0f);
            time.animate().alpha(1f).setDuration(500).start();
        }

        /** A small bump at either end of the strip, so the remote press is answered. */
        private void nudge(int dir) {
            animate().cancel();
            animate().translationX(dir * px(5)).setDuration(90).withEndAction(() ->
                    animate().translationX(0).setInterpolator(new OvershootInterpolator(3f)).setDuration(220).start())
                    .start();
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
