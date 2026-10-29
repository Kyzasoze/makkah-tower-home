package com.majnioui.tv.prayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

/** Adhan time: play it (only while the TV screen is on). Boot, update, clock change or midnight: re-read and re-arm. */
public class AdhanReceiver extends BroadcastReceiver {
    private static final String TAG = "AdhanReceiver";

    @Override
    public void onReceive(Context c, Intent intent) {
        String action = intent.getAction();
        Log.i(TAG, "received " + action);
        if (AdhanScheduler.ACTION_ADHAN.equals(action)) {
            PowerManager pm = c.getSystemService(PowerManager.class);
            boolean interactive = pm != null && pm.isInteractive();
            boolean woke = false;
            // Fajr with the TV in standby: switch the screen on for the adhan (Salah Times setting, on by default).
            if (!interactive && pm != null && BoardStore.adhanOn(c) && BoardStore.fajrWake(c)
                    && "Fajr".equals(intent.getStringExtra(AdhanScheduler.EXTRA_NAME))) {
                try {
                    @SuppressWarnings("deprecation")
                    PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK
                            | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "prayer:fajr");
                    wl.acquire(30_000L);
                    woke = true;
                    Log.i(TAG, "woke the TV for Fajr");
                } catch (Exception e) {
                    Log.w(TAG, "wake: " + e);
                }
            }
            if (BoardStore.adhanOn(c) && (interactive || woke)) {
                Intent s = new Intent(c, AdhanService.class).putExtras(intent).putExtra(AdhanService.EXTRA_WOKE, woke);
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s);
                else c.startService(s);
            }
            AdhanScheduler.scheduleNext(c);
            return;
        }
        final PendingResult result = goAsync();
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                BoardStore.refresh(app);
                AdhanScheduler.scheduleNext(app);
            } finally {
                result.finish();
            }
        }).start();
    }
}
