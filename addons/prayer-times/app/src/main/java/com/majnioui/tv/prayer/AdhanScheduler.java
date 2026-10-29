package com.majnioui.tv.prayer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.json.JSONObject;

import java.util.Calendar;

/**
 * Keeps exactly one alarm set: the next adhan from today's board, or, after Isha, a refresh just after
 * midnight that reads the new day's times and sets the next adhan.
 */
final class AdhanScheduler {
    private static final String TAG = "AdhanScheduler";
    static final String ACTION_ADHAN = "com.majnioui.tv.prayer.ADHAN";
    static final String ACTION_REFRESH = "com.majnioui.tv.prayer.REFRESH";
    static final String EXTRA_NAME = "name";
    static final String EXTRA_ADHAN = "adhan";
    static final String EXTRA_JAMAAH = "jamaah";

    private static final String[] NAMES = {"Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"};

    private AdhanScheduler() {
    }

    private static PendingIntent pi(Context c, String action, Intent extras) {
        Intent i = new Intent(c, AdhanReceiver.class).setAction(action);
        if (extras != null) i.putExtras(extras);
        return PendingIntent.getBroadcast(c, action.equals(ACTION_ADHAN) ? 1 : 2, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void cancelAll(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        am.cancel(pi(c, ACTION_ADHAN, null));
        am.cancel(pi(c, ACTION_REFRESH, null));
    }

    static void scheduleNext(Context c) {
        cancelAll(c);
        if (!BoardStore.adhanOn(c)) {
            Log.i(TAG, "adhan alerts are off");
            return;
        }
        AlarmManager am = c.getSystemService(AlarmManager.class);
        JSONObject d = BoardStore.cached(c);
        long now = System.currentTimeMillis();
        if (d == null || !BoardStore.cachedIsToday(c)) {
            set(am, now + 2 * 60_000L, pi(c, ACTION_REFRESH, null));
            Log.i(TAG, "no times for today yet; refresh in 2 min");
            return;
        }
        Calendar cal = Calendar.getInstance();
        boolean friday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY;
        String[] adhan = {
                BoardStore.first(d, "fajrAthan", "fajrStarts"),
                BoardStore.clean(d.optString("dhuhrAthan")),
                BoardStore.clean(d.optString("asrAthan")),
                BoardStore.first(d, "maghribAthan", "sunset"),
                BoardStore.first(d, "eshaAthan", "eshaStarts"),
        };
        String[] jamaah = {
                BoardStore.clean(d.optString("fajrJamaah")),
                BoardStore.clean(d.optString("dhuhrJamaah")),
                BoardStore.clean(d.optString("asrJamaah")),
                BoardStore.clean(d.optString("maghribJamaah")),
                BoardStore.clean(d.optString("eshaJamaah")),
        };
        for (int i = 0; i < NAMES.length; i++) {
            if (adhan[i].isEmpty()) continue;
            Calendar t = Calendar.getInstance();
            t.set(Calendar.HOUR_OF_DAY, Integer.parseInt(adhan[i].substring(0, 2)));
            t.set(Calendar.MINUTE, Integer.parseInt(adhan[i].substring(3, 5)));
            t.set(Calendar.SECOND, 0);
            t.set(Calendar.MILLISECOND, 0);
            if (t.getTimeInMillis() > now + 5_000L) {
                Intent x = new Intent();
                x.putExtra(EXTRA_NAME, (i == 1 && friday) ? "Jumu'ah" : NAMES[i]);
                x.putExtra(EXTRA_ADHAN, adhan[i]);
                x.putExtra(EXTRA_JAMAAH, jamaah[i]);
                set(am, t.getTimeInMillis(), pi(c, ACTION_ADHAN, x));
                Log.i(TAG, "next adhan: " + NAMES[i] + " " + adhan[i]);
                return;
            }
        }
        Calendar mid = Calendar.getInstance();
        mid.add(Calendar.DAY_OF_YEAR, 1);
        mid.set(Calendar.HOUR_OF_DAY, 0);
        mid.set(Calendar.MINUTE, 10);
        mid.set(Calendar.SECOND, 0);
        set(am, mid.getTimeInMillis(), pi(c, ACTION_REFRESH, null));
        Log.i(TAG, "all adhans done today; refresh after midnight");
    }

    private static void set(AlarmManager am, long at, PendingIntent p) {
        if (android.os.Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
            return;
        }
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
        }
    }
}
