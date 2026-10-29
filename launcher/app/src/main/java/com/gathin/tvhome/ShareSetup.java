package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the first-time setup (SetupActivity) asked: the city (weather, prayer times, clocks), how prayer times are
 * worked out (a calculation method, or a mosque's MasjidBoardLive board), and the football teams to follow. The
 * Salah Times app gets the same settings through its CONFIG broadcast.
 */
final class ShareSetup {
    /** {ESPN display name, league code} for the teams offered in the setup. */
    static final String[][] TEAMS = {
            {"Arsenal", "eng.1"}, {"Aston Villa", "eng.1"}, {"Chelsea", "eng.1"}, {"Liverpool", "eng.1"},
            {"Manchester City", "eng.1"}, {"Manchester United", "eng.1"}, {"Newcastle United", "eng.1"},
            {"Tottenham Hotspur", "eng.1"}, {"Real Madrid", "esp.1"}, {"Barcelona", "esp.1"},
            {"Atlético Madrid", "esp.1"}, {"Bayern Munich", "ger.1"}, {"Borussia Dortmund", "ger.1"},
            {"Juventus", "ita.1"}, {"Internazionale", "ita.1"}, {"AC Milan", "ita.1"}, {"Napoli", "ita.1"},
            {"Paris Saint-Germain", "fra.1"}, {"Al Nassr", "ksa.1"}, {"Al Hilal", "ksa.1"},
            {"Kaizer Chiefs", "rsa.1"}, {"Orlando Pirates", "rsa.1"}, {"Mamelodi Sundowns", "rsa.1"}};
    private static final String[] CUPS = {"uefa.champions", "uefa.europa", "eng.fa", "eng.league_cup", "esp.copa_del_rey",
            "ger.dfb_pokal", "ita.coppa_italia", "fra.coupe_de_france", "fifa.cwc", "uefa.super_cup"};

    /** {Aladhan method number, name}. */
    static final String[][] METHODS = {
            {"3", "Muslim World League"}, {"4", "Umm al-Qura, Makkah"}, {"2", "ISNA (North America)"},
            {"5", "Egyptian General Authority"}, {"1", "University of Islamic Sciences, Karachi"},
            {"8", "Gulf Region"}, {"16", "Dubai"}, {"13", "Diyanet, Turkey"}, {"11", "MUIS, Singapore"},
            {"15", "Moonsighting Committee"}};

    private ShareSetup() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("share_setup", Context.MODE_PRIVATE);
    }

    static boolean done(Context c) {
        return prefs(c).getBoolean("done", false);
    }

    static double lat(Context c) {
        return Double.longBitsToDouble(prefs(c).getLong("lat", Double.doubleToLongBits(21.4225)));
    }

    static double lon(Context c) {
        return Double.longBitsToDouble(prefs(c).getLong("lon", Double.doubleToLongBits(39.8262)));
    }

    static String place(Context c) {
        return prefs(c).getString("place", "Makkah");
    }

    static int method(Context c) {
        return prefs(c).getInt("method", 4);
    }

    /** The MasjidBoardLive board id, or "" for calculated times. */
    static String board(Context c) {
        return prefs(c).getString("board", "");
    }

    static List<String> teams(Context c) {
        return new ArrayList<>(prefs(c).getStringSet("teams", new HashSet<>()));
    }

    static List<String> leagues(Context c) {
        Set<String> out = new LinkedHashSet<>();
        List<String> chosen = teams(c);
        for (String[] t : TEAMS) if (chosen.contains(t[0])) out.add(t[1]);
        if (!out.isEmpty()) out.addAll(Arrays.asList(CUPS));
        return new ArrayList<>(out);
    }

    /** Hands the city and prayer settings to the Salah Times app (if installed). */
    static void tellSalahTimes(Context c) {
        try {
            c.sendBroadcast(new Intent("com.majnioui.tv.prayer.CONFIG")
                    .setComponent(new ComponentName("com.majnioui.tv.prayer", "com.majnioui.tv.prayer.ConfigReceiver"))
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    .putExtra("lat", lat(c)).putExtra("lon", lon(c)).putExtra("place", place(c))
                    .putExtra("method", method(c)).putExtra("board", board(c)));
        } catch (Exception ignored) {
        }
    }
}
