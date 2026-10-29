package com.gathin.tvhome;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Night mode, from 19:00 to 05:00: the home screen grows a little darker and, while "Everything" is the chosen
 * wallpaper category, the rotation keeps to calm night pictures (Space and Sunsets).
 */
final class NightMode {
    private NightMode() {}

    static boolean now() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h >= 19 || h < 5;
    }

    /** The day's dim, plus 18 % more at night. */
    static int dim(int dayDim) {
        if (!now()) return dayDim;
        int a = (dayDim >>> 24) & 0xFF;
        a = Math.min(200, a + 46);
        return a << 24;
    }

    static List<File> pick(Context c, List<File> playable) {
        if (!now() || !WallpaperCategories.ALL.equals(WallpaperCategories.selected(c))) return playable;
        List<File> night = new ArrayList<>(WallpaperCategories.in(playable, "space"));
        night.addAll(WallpaperCategories.in(playable, "skies"));
        return night.size() >= 6 ? night : playable;
    }
}
