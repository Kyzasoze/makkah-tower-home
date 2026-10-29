package com.gathin.tvhome;

import android.content.Context;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Wallpaper categories: wallpapers/categories.txt next to the pictures ("001.jpg<TAB>mountains", one per line)
 * and the category chosen on the TV (the wallpaper button in the top bar). "hidden" keeps a picture out of every
 * rotation. A category with no pictures, or no file at all, falls back to the whole collection.
 */
final class WallpaperCategories {
    static final String FILE_NAME = "categories.txt";
    static final String ALL = "all", HIDDEN = "hidden";
    /** id, label, a one-word glyph for the card. */
    static final String[][] LIST = {
            {ALL, "Everything", ""},
            {"mountains", "Mountains", ""},
            {"countryside", "Countryside", ""},
            {"forests", "Forests", ""},
            {"sea", "Sea & Lakes", ""},
            {"skies", "Sunsets", ""},
            {"flowers", "Wildlife", ""},
            {"cities", "Cities", ""},
            {"space", "Space", ""}};

    private static String cachedKey = "";
    private static Map<String, String> cached = Collections.emptyMap();

    private WallpaperCategories() {}

    static synchronized Map<String, String> read(File folder) {
        File file = folder == null ? null : new File(folder, FILE_NAME);
        if (file == null || !file.isFile() || file.length() > 256 * 1024) return Collections.emptyMap();
        String key = file.getPath() + ':' + file.lastModified() + ':' + file.length();
        if (key.equals(cachedKey)) return cached;
        Map<String, String> map = new HashMap<>();
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                String[] parts = line.split("\t", 2);
                if (parts.length == 2 && !parts[0].trim().isEmpty()) {
                    map.put(parts[0].trim(), parts[1].trim().toLowerCase(Locale.ROOT));
                }
            }
        } catch (Exception e) {
            return Collections.emptyMap();
        }
        cachedKey = key;
        cached = map;
        return map;
    }

    static String selected(Context c) {
        return c.getSharedPreferences("wallpaper_category", Context.MODE_PRIVATE).getString("cat", ALL);
    }

    static void select(Context c, String id) {
        c.getSharedPreferences("wallpaper_category", Context.MODE_PRIVATE).edit().putString("cat", id).apply();
    }

    static String label(String id) {
        for (String[] e : LIST) if (e[0].equals(id)) return e[1];
        return LIST[0][1];
    }

    /** The pictures of one category (ALL: every picture that is not hidden). */
    static List<File> in(List<File> files, String id) {
        List<File> out = new ArrayList<>();
        if (files.isEmpty()) return out;
        Map<String, String> map = read(files.get(0).getParentFile());
        for (File f : files) {
            String k = map.get(f.getName());
            if (HIDDEN.equals(k)) continue;
            if (ALL.equals(id) || id.equals(k)) out.add(f);
        }
        return out;
    }

    /** What the rotation plays: the chosen category, else everything not hidden, else the list as it was. */
    static List<File> filter(Context c, List<File> files) {
        if (files.isEmpty() || read(files.get(0).getParentFile()).isEmpty()) return files;
        List<File> chosen = in(files, selected(c));
        if (!chosen.isEmpty()) return chosen;
        List<File> visible = in(files, ALL);
        return visible.isEmpty() ? files : visible;
    }
}
