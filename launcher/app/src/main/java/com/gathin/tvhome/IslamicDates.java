package com.gathin.tvhome;

import android.content.Context;
import android.icu.util.IslamicCalendar;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Calendar;
import java.util.Locale;

/**
 * The Hijri date (Umm al-Qura calendar) and the Islamic days coming up: Ramadan, the last ten nights and their odd
 * nights, both Eids, the first ten days of Dhul Hijjah, Arafah, the new year, Ashura, the white days and the Monday
 * and Thursday sunnah fasts. Local moon sighting can differ by a day: "hijri_offset" in the theme's config.json
 * (for example -1 or 1) shifts every date.
 */
final class IslamicDates {
    static final String[] MONTHS = {"Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani", "Jumada al-Ula",
            "Jumada al-Akhirah", "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhul Qa'dah", "Dhul Hijjah"};
    static final int RAMADAN = 8, SHAWWAL = 9, DHUL_HIJJAH = 11, MUHARRAM = 0;

    private static long offsetAt;
    private static int offset;

    private IslamicDates() {}

    /** {year, month (0-11), day} for the day {@code daysAhead} from today. */
    static int[] hijri(Context c, int daysAhead) {
        Calendar g = Calendar.getInstance();
        g.add(Calendar.DAY_OF_YEAR, daysAhead + offset(c));
        IslamicCalendar ic = new IslamicCalendar();
        ic.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
        ic.setTimeInMillis(g.getTimeInMillis());
        return new int[] {ic.get(IslamicCalendar.YEAR), ic.get(IslamicCalendar.MONTH), ic.get(IslamicCalendar.DAY_OF_MONTH)};
    }

    /** "18 Jumada al-Ula 1448". */
    static String today(Context c) {
        int[] h = hijri(c, 0);
        return h[2] + " " + MONTHS[h[1]] + " " + h[0];
    }

    static boolean ramadan(Context c) {
        return hijri(c, 0)[1] == RAMADAN;
    }

    static int ramadanDay(Context c) {
        int[] h = hijri(c, 0);
        return h[1] == RAMADAN ? h[2] : 0;
    }

    /** "fitr" or "adha" on the Eid days (Eid al-Fitr's first three days, Eid al-Adha and the days of tashreeq). */
    static String eid(Context c) {
        int[] h = hijri(c, 0);
        if (h[1] == SHAWWAL && h[2] <= 3) return "fitr";
        if (h[1] == DHUL_HIJJAH && h[2] >= 10 && h[2] <= 13) return "adha";
        return null;
    }

    /** One short line about what is coming, or null when there is nothing worth saying. */
    static String upcoming(Context c) {
        int[] now = hijri(c, 0);
        if (now[1] == RAMADAN) {
            String s = "Ramadan · day " + now[2];
            if (now[2] >= 20 && now[2] % 2 == 0 && now[2] < 30) s += " · odd night tonight";
            else if (now[2] >= 21) s += " · last ten nights";
            return s;
        }
        String major = null;
        int majorIn = 99;
        String white = null;
        int whiteIn = 99;
        for (int i = 0; i <= 30; i++) {
            int[] h = hijri(c, i);
            String name = major(h);
            if (name != null && i < majorIn) {
                major = name;
                majorIn = i;
            }
            if (h[2] == 13 && h[1] != RAMADAN && h[1] != DHUL_HIJJAH && i < whiteIn) {
                white = "White days fast (13–15)";
                whiteIn = i;
            }
            if (majorIn < 99 && whiteIn < 99) break;
        }
        if (now[2] >= 13 && now[2] <= 15 && now[1] != RAMADAN && now[1] != DHUL_HIJJAH) {
            if (majorIn > 2) return "White days · fast day " + (now[2] - 12) + " of 3";
        }
        if (majorIn <= 10) return when(major, majorIn);
        if (whiteIn <= 2) return when(white, whiteIn);
        int dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        if (dow == Calendar.SUNDAY) return "Sunnah fast tomorrow · Monday";
        if (dow == Calendar.WEDNESDAY) return "Sunnah fast tomorrow · Thursday";
        if (majorIn <= 30) return when(major, majorIn);
        return null;
    }

    private static String major(int[] h) {
        int m = h[1], d = h[2];
        if (m == RAMADAN && d == 1) return "Ramadan begins";
        if (m == SHAWWAL && d == 1) return "Eid al-Fitr";
        if (m == DHUL_HIJJAH && d == 1) return "First ten days of Dhul Hijjah";
        if (m == DHUL_HIJJAH && d == 9) return "Day of Arafah";
        if (m == DHUL_HIJJAH && d == 10) return "Eid al-Adha";
        if (m == MUHARRAM && d == 1) return "Islamic New Year " + h[0];
        if (m == MUHARRAM && d == 9) return "Tasu'a & Ashura fasts";
        if (m == MUHARRAM && d == 10) return "Day of Ashura";
        return null;
    }

    private static String when(String what, int in) {
        if (in == 0) return "Today · " + what;
        if (in == 1) return "Tomorrow · " + what;
        return what + " in " + in + " days";
    }

    static synchronized int offset(Context c) {
        try {
            File f = new File(Config.dir(c), Config.FILE_NAME);
            long now = System.currentTimeMillis();
            if (now - offsetAt > 10 * 60_000L) {
                offsetAt = now;
                offset = 0;
                JSONObject j = f.isFile()
                        ? new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)) : new JSONObject();
                if (j.has("hijri_offset")) {
                    offset = Math.max(-2, Math.min(2, j.optInt("hijri_offset", 0)));
                } else {
                    // Otherwise the mosque's own adjustment from its board (islamicDateAdjust), so dates match it.
                    String board = c.getSharedPreferences("salah_chip", Context.MODE_PRIVATE).getString("json", null);
                    if (board != null) {
                        JSONObject d = new JSONObject(board).optJSONObject("data");
                        if (d != null) offset = Math.max(-2, Math.min(2, Integer.parseInt(d.optString("islamicDateAdjust", "0").trim())));
                    }
                }
            }
        } catch (Exception e) {
            offset = 0;
        }
        return offset;
    }

    static String upper(String s) {
        return s == null ? null : s.toUpperCase(Locale.ROOT);
    }
}
