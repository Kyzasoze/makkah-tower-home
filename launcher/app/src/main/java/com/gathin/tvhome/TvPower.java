package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Standby and when the remote was last used: the sleep timer, auto-off after three idle hours, and standby after the
 * Fajr adhan Salah Times woke the TV for. Standby goes through the key helper (HomeKeyService, an accessibility
 * service), whose "lock screen" action sends the TV to sleep; before it, a small banner counts down a minute and any
 * button cancels.
 */
final class TvPower {
    private static final String TAG = "TvPower";
    static final String ACTION_TIMER = "com.gathin.tvhome.SLEEP_TIMER";
    private static final long IDLE_OFF_MS = 3 * 60 * 60_000L;
    static volatile HomeKeyService service;
    static volatile long lastKeyAt = System.currentTimeMillis();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static View warning;
    private static boolean watching;

    private TvPower() {}

    static boolean sleepNow() {
        HomeKeyService s = service;
        if (s == null || Build.VERSION.SDK_INT < 28) {
            Log.w(TAG, "standby unavailable (key helper off)");
            return false;
        }
        return s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
    }

    // ---- sleep timer -------------------------------------------------------------------------------

    private static PendingIntent timerIntent(Context c) {
        return PendingIntent.getBroadcast(c, 7, new Intent(c, TimerReceiver.class).setAction(ACTION_TIMER),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Minutes from now (0 cancels). The banner appears a minute before the end. */
    static void setTimer(Context c, int minutes) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = timerIntent(c);
        am.cancel(pi);
        long end = minutes > 0 ? System.currentTimeMillis() + minutes * 60_000L : 0;
        c.getSharedPreferences("tv_power", Context.MODE_PRIVATE).edit().putLong("timer_end", end).apply();
        if (end == 0) return;
        long at = end - 60_000L;
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    /** Minutes left on the sleep timer, or 0. */
    static int timerLeft(Context c) {
        long end = c.getSharedPreferences("tv_power", Context.MODE_PRIVATE).getLong("timer_end", 0);
        long left = end - System.currentTimeMillis();
        return left > 0 ? (int) ((left + 59_999L) / 60_000L) : 0;
    }

    public static class TimerReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            c.getSharedPreferences("tv_power", Context.MODE_PRIVATE).edit().putLong("timer_end", 0).apply();
            warnThenSleep("Sleep timer");
        }
    }

    /** com.gathin.tvhome.SLEEP: Salah Times asks for standby after the Fajr adhan it woke the TV for. */
    public static class SleepReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            // Only if nobody has touched the remote in the last two minutes (someone got up and is watching).
            if (System.currentTimeMillis() - lastKeyAt > 2 * 60_000L) sleepNow();
        }
    }

    // ---- auto-off ----------------------------------------------------------------------------------

    /** Started by the key helper: every five minutes, three idle hours with nothing playing turn the TV off. */
    static void startWatchdog(HomeKeyService s) {
        if (watching) return;
        watching = true;
        lastKeyAt = System.currentTimeMillis();
        // Switching the TV on counts as using it (the power button may never reach the key helper).
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_USER_PRESENT);
        s.getApplicationContext().registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                lastKeyAt = System.currentTimeMillis();
            }
        }, f);
        Context app = s.getApplicationContext();
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    PowerManager pm = app.getSystemService(PowerManager.class);
                    AudioManager am = app.getSystemService(AudioManager.class);
                    boolean on = pm != null && pm.isInteractive();
                    boolean playing = am != null && am.isMusicActive();
                    if (on && !playing && warning == null && System.currentTimeMillis() - lastKeyAt > IDLE_OFF_MS) {
                        warnThenSleep("No buttons pressed for 3 hours");
                    }
                } catch (Exception e) {
                    Log.w(TAG, "watchdog: " + e);
                }
                main.postDelayed(this, 5 * 60_000L);
            }
        }, 5 * 60_000L);
    }

    // ---- the one-minute banner ---------------------------------------------------------------------

    static void warnThenSleep(String why) {
        main.post(() -> {
            HomeKeyService s = service;
            if (s == null) {
                sleepNow();
                return;
            }
            if (warning != null) return;
            WindowManager wm = s.getSystemService(WindowManager.class);
            float dp = s.getResources().getDisplayMetrics().density;
            LinearLayout box = new LinearLayout(s);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER_HORIZONTAL);
            box.setPadding(Math.round(30 * dp), Math.round(16 * dp), Math.round(30 * dp), Math.round(16 * dp));
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] {0xF01C1C20, 0xF7111114});
            bg.setCornerRadius(24 * dp);
            bg.setStroke(Math.round(1.5f * dp), 0xCCE0BD72);
            box.setBackground(bg);
            TextView title = new TextView(s);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            title.setTextColor(0xFFF4F1EA);
            title.setText("☾  " + why);
            box.addView(title);
            TextView sub = new TextView(s);
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            sub.setTextColor(0xCCF4F1EA);
            box.addView(sub);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.y = Math.round(60 * dp);
            try {
                wm.addView(box, lp);
            } catch (Exception e) {
                Log.w(TAG, "banner: " + e);
                sleepNow();
                return;
            }
            warning = box;
            box.setAlpha(0f);
            box.animate().alpha(1f).setDuration(300).start();
            final long keyMark = lastKeyAt;
            final long until = System.currentTimeMillis() + 60_000L;
            main.post(new Runnable() {
                @Override
                public void run() {
                    if (lastKeyAt != keyMark) {          // a button: stay on
                        remove(wm);
                        return;
                    }
                    long left = until - System.currentTimeMillis();
                    if (left <= 0) {
                        remove(wm);
                        sleepNow();
                        return;
                    }
                    sub.setText("The TV turns off in " + ((left + 999) / 1000) + " s  ·  press any button to keep watching");
                    main.postDelayed(this, 500);
                }
            });
        });
    }

    /**
     * A short banner in the top-right corner over whatever is on screen (the key helper's overlay), gone after 12 s.
     * Without the key helper it falls back to a toast.
     */
    static void notice(Context c, String title, String line) {
        main.post(() -> {
            HomeKeyService s = service;
            if (s == null) {
                android.widget.Toast.makeText(c, title + "\n" + line, android.widget.Toast.LENGTH_LONG).show();
                return;
            }
            WindowManager wm = s.getSystemService(WindowManager.class);
            float dp = s.getResources().getDisplayMetrics().density;
            LinearLayout box = new LinearLayout(s);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(Math.round(22 * dp), Math.round(12 * dp), Math.round(22 * dp), Math.round(12 * dp));
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {0xF0123A6B, 0xF0111114});
            bg.setCornerRadius(20 * dp);
            bg.setStroke(Math.round(1.5f * dp), 0xCCE0BD72);
            box.setBackground(bg);
            TextView t1 = new TextView(s);
            t1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            t1.setTextColor(0xFFF4F1EA);
            t1.setText(title);
            box.addView(t1);
            TextView t2 = new TextView(s);
            t2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            t2.setTextColor(0xCCF4F1EA);
            t2.setText(line);
            box.addView(t2);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.x = Math.round(32 * dp);
            lp.y = Math.round(28 * dp);
            try {
                wm.addView(box, lp);
            } catch (Exception e) {
                Log.w(TAG, "notice: " + e);
                return;
            }
            box.setAlpha(0f);
            box.setTranslationX(60 * dp);
            box.animate().alpha(1f).translationX(0).setDuration(380).start();
            main.postDelayed(() -> box.animate().alpha(0f).translationX(60 * dp).setDuration(300).withEndAction(() -> {
                try {
                    wm.removeView(box);
                } catch (Exception ignored) {
                }
            }).start(), 12_000L);
        });
    }

    private static void remove(WindowManager wm) {
        if (warning == null) return;
        try {
            wm.removeView(warning);
        } catch (Exception ignored) {
        }
        warning = null;
    }
}
