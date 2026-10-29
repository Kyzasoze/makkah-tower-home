package com.majnioui.tv.prayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Plays the adhan once, pausing whatever else is playing (transient audio focus), with a glowing banner at the
 * bottom of the screen. OK or Back stops it; it also ends by itself when the recording does.
 */
public class AdhanService extends Service {
    private static final String TAG = "AdhanService";
    private static final String CHANNEL = "adhan";
    private static final long MAX_MS = 7 * 60_000L;
    static final String EXTRA_WOKE = "woke";

    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private View overlay;
    private boolean stopping;
    /** The TV was woken from standby for this adhan; userActed: someone pressed a key since. */
    private boolean woke, userActed;
    private String prayerName = "";
    private boolean takbirDone;
    private TextView subText, hintText;
    private String jamaahTime;
    private int savedVolume = -1;
    private boolean savedMute;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startAsForeground();
        if (player != null) return START_NOT_STICKY;   // already playing
        String name = intent != null ? intent.getStringExtra(AdhanScheduler.EXTRA_NAME) : null;
        String time = intent != null ? intent.getStringExtra(AdhanScheduler.EXTRA_ADHAN) : null;
        String jamaah = intent != null ? intent.getStringExtra(AdhanScheduler.EXTRA_JAMAAH) : null;
        if (name == null) name = "Salah";
        woke = intent != null && intent.getBooleanExtra(EXTRA_WOKE, false);
        prayerName = name;

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        audio = getSystemService(AudioManager.class);
        if (audio != null && Build.VERSION.SDK_INT >= 26) {
            // Full focus: Netflix, YouTube and the rest pause and stay paused when the adhan ends.
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(ch -> { })
                    .build();
            audio.requestAudioFocus(focus);
        }
        setAdhanVolume();
        try {
            int sound = R.raw.adhan;
            if ("Fajr".equals(name)) {
                int fajr = getResources().getIdentifier("adhan_fajr", "raw", getPackageName());
                if (fajr != 0) sound = fajr;
            }
            player = MediaPlayer.create(this, sound, attrs,
                    audio != null ? audio.generateAudioSessionId() : AudioManager.AUDIO_SESSION_ID_GENERATE);
            player.setOnCompletionListener(mp -> afterAdhan());
            player.start();
        } catch (Exception e) {
            Log.w(TAG, "play: " + e);
            finish();
            return START_NOT_STICKY;
        }
        jamaahTime = jamaah;
        showOverlay(name, time, jamaah);
        main.postDelayed(this::finish, MAX_MS);
        return START_NOT_STICKY;
    }

    private void startAsForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Adhan", NotificationManager.IMPORTANCE_LOW));
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        Notification n = b.setContentTitle("Adhan").setSmallIcon(R.drawable.ic_maghrib).build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(1, n);
    }

    private void showOverlay(String name, String time, String jamaah) {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) return;
        WindowManager wm = getSystemService(WindowManager.class);
        if (wm == null) return;
        int accent = getColor(R.color.accent);
        Typeface font = getResources().getFont(R.font.outfit);

        FrameLayout root = new FrameLayout(this) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                int k = e.getKeyCode();
                userActed = true;
                if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_MUTE) {
                    return false;
                }
                if (e.getAction() == KeyEvent.ACTION_UP && (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_DPAD_CENTER
                        || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_ESCAPE || k == KeyEvent.KEYCODE_HOME)) {
                    finish();
                }
                return true;
            }
        };
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(48), dp(26), dp(48), dp(26));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {0xF0050A0E, (accent & 0x00FFFFFF) | 0xC0000000, 0xF0050A0E});
        bg.setCornerRadius(dp(28));
        bg.setStroke(dp(2), (accent & 0x00FFFFFF) | 0xCC000000);
        panel.setBackground(bg);
        panel.setElevation(dp(12));

        TextView title = text(font, 40, 500, 0xFFFFFFFF);
        title.setText("الأذان   ·   " + name);
        panel.addView(title);
        TextView sub = text(font, 22, 400, 0xE6FFFFFF);
        StringBuilder s = new StringBuilder("It is time for ").append(name);
        if (time != null && !time.isEmpty()) s.append("  ·  adhan ").append(time);
        if (jamaah != null && !jamaah.isEmpty()) s.append("  ·  jamaah ").append(jamaah);
        s.append("  ·  ").append(BoardStore.place(this));
        sub.setText(s);
        subText = sub;
        sub.setPadding(0, dp(6), 0, 0);
        panel.addView(sub);
        TextView hint = text(font, 16, 400, 0x99FFFFFF);
        hint.setText("Press OK or Back to stop the adhan");
        hintText = hint;
        hint.setPadding(0, dp(10), 0, 0);
        panel.addView(hint);

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        plp.bottomMargin = dp(48);
        root.addView(panel, plp);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM;
        try {
            wm.addView(root, lp);
            overlay = root;
            root.requestFocus();
            panel.setAlpha(0f);
            panel.setTranslationY(dp(40));
            panel.animate().alpha(1f).translationY(0).setDuration(450).start();
        } catch (Exception e) {
            Log.w(TAG, "overlay: " + e);
        }
    }

    /** The adhan's own volume (Salah Times setting, 60 % by default), even when the TV was muted or turned down. */
    private void setAdhanVolume() {
        if (audio == null) return;
        try {
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            savedVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            savedMute = Build.VERSION.SDK_INT >= 23 && audio.isStreamMute(AudioManager.STREAM_MUSIC);
            int pct = BoardStore.prefs(this).getInt("adhan_volume", 60);
            int target = Math.max(1, Math.round(max * pct / 100f));
            if (savedMute) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
        } catch (Exception e) {
            Log.w(TAG, "volume: " + e);
            savedVolume = -1;
        }
    }

    private void restoreVolume() {
        if (audio == null || savedVolume < 0) return;
        try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0);
            if (savedMute) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
        } catch (Exception ignored) {
        }
        savedVolume = -1;
    }

    /** The adhan has ended: the TV's own volume comes back, and the banner counts down to jamaah for a little while. */
    private void afterAdhan() {
        // Eid morning: the takbir after the Fajr adhan, if a takbir recording was added (res/raw/takbir.mp3).
        if (!takbirDone && "Fajr".equals(prayerName) && isEid()) {
            takbirDone = true;
            int takbir = getResources().getIdentifier("takbir", "raw", getPackageName());
            if (takbir != 0) {
                try {
                    if (player != null) player.release();
                    player = MediaPlayer.create(this, takbir);
                    player.setOnCompletionListener(mp -> afterAdhan());
                    player.start();
                    if (subText != null) subText.setText("Eid Mubarak  ·  \u0639\u064A\u062F \u0645\u0628\u0627\u0631\u0643");
                    return;
                } catch (Exception e) {
                    Log.w(TAG, "takbir: " + e);
                }
            }
        }
        restoreVolume();
        long jm = jamaahMillis(jamaahTime);
        long left = jm - System.currentTimeMillis();
        if (subText == null || jm <= 0 || left <= 60_000L || left > 60 * 60_000L) {
            finish();
            return;
        }
        if (hintText != null) hintText.setText("Press OK or Back to close");
        Runnable tick = new Runnable() {
            @Override
            public void run() {
                long l = jamaahMillis(jamaahTime) - System.currentTimeMillis();
                if (l <= 0) {
                    finish();
                    return;
                }
                subText.setText("Jamaah at " + jamaahTime + "  ·  in " + ((l + 59_999L) / 60_000L) + " min  ·  "
                        + BoardStore.place(AdhanService.this));
                main.postDelayed(this, 20_000L);
            }
        };
        tick.run();
        main.postDelayed(this::finish, 90_000L);
    }

    /** Eid al-Fitr (1 Shawwal) or Eid al-Adha (10 Dhul Hijjah) by the Umm al-Qura calendar. */
    private static boolean isEid() {
        try {
            android.icu.util.IslamicCalendar ic = new android.icu.util.IslamicCalendar();
            ic.setCalculationType(android.icu.util.IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
            ic.setTimeInMillis(System.currentTimeMillis());
            int m = ic.get(android.icu.util.IslamicCalendar.MONTH), d = ic.get(android.icu.util.IslamicCalendar.DAY_OF_MONTH);
            return (m == 9 && d == 1) || (m == 11 && d == 10);
        } catch (Exception e) {
            return false;
        }
    }

    private static long jamaahMillis(String hhmm) {
        if (hhmm == null || !hhmm.matches("\\d{2}:\\d{2}")) return 0;
        java.util.Calendar t = java.util.Calendar.getInstance();
        t.set(java.util.Calendar.HOUR_OF_DAY, Integer.parseInt(hhmm.substring(0, 2)));
        t.set(java.util.Calendar.MINUTE, Integer.parseInt(hhmm.substring(3, 5)));
        t.set(java.util.Calendar.SECOND, 0);
        t.set(java.util.Calendar.MILLISECOND, 0);
        return t.getTimeInMillis();
    }

    private void finish() {
        if (stopping) return;
        stopping = true;
        main.removeCallbacksAndMessages(null);
        if (woke && !userActed) {
            // Woken for Fajr and nobody pressed anything: back to standby (Home's key helper does it).
            try {
                sendBroadcast(new Intent("com.gathin.tvhome.SLEEP")
                        .setComponent(new android.content.ComponentName("com.makkahtower.home", "com.gathin.tvhome.TvPower$SleepReceiver")));
            } catch (Exception ignored) {
            }
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
        restoreVolume();
        if (audio != null && focus != null && Build.VERSION.SDK_INT >= 26) audio.abandonAudioFocusRequest(focus);
        if (overlay != null) {
            try {
                getSystemService(WindowManager.class).removeView(overlay);
            } catch (Exception ignored) {
            }
            overlay = null;
        }
        super.onDestroy();
    }

    private TextView text(Typeface font, float sp, int weight, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setTypeface(Build.VERSION.SDK_INT >= 28 ? Typeface.create(font, weight, false) : font);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    static void start(Context c, String name, String adhan, String jamaah) {
        Intent s = new Intent(c, AdhanService.class)
                .putExtra(AdhanScheduler.EXTRA_NAME, name)
                .putExtra(AdhanScheduler.EXTRA_ADHAN, adhan)
                .putExtra(AdhanScheduler.EXTRA_JAMAAH, jamaah);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s);
        else c.startService(s);
    }
}
