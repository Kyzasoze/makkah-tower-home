package com.majnioui.tv.prayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * com.majnioui.tv.prayer.CONFIG from the home screen's setup: the city, the calculation method and the mosque's
 * MasjidBoardLive board ("" for calculated times). Saved, then today's times are read and the next adhan is set.
 */
public class ConfigReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        BoardStore.prefs(c).edit()
                .putLong("cfg_lat", Double.doubleToLongBits(i.getDoubleExtra("lat", 21.4225)))
                .putLong("cfg_lon", Double.doubleToLongBits(i.getDoubleExtra("lon", 39.8262)))
                .putString("cfg_place", i.getStringExtra("place") == null ? "" : i.getStringExtra("place"))
                .putInt("cfg_method", i.getIntExtra("method", 4))
                .putString("cfg_board", i.getStringExtra("board") == null ? "" : i.getStringExtra("board"))
                .remove("json").remove("at").apply();
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
