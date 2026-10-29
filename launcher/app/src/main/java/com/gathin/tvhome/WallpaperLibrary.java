package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The wallpaper library, fetched on the TV itself: freely licensed Wikimedia Commons featured pictures listed in
 * assets/wallpapers.tsv (file, category, caption with the author's credit), up to 300 of them spread over the
 * categories. Each is cropped to 1920x1080 and saved next to the theme, with captions.txt and categories.txt, so
 * the normal wallpaper rotation, the categories and the credits work as they do with a pushed theme. The first
 * run also writes the share edition's theme (assets/share_theme.json). It resumes where it stopped.
 */
final class WallpaperLibrary {
    private static final String TAG = "WallpaperLibrary";
    private static final int LIMIT = 300;
    private static volatile boolean running;
    private static volatile int saved;

    private WallpaperLibrary() {}

    static int saved() {
        return saved;
    }

    static boolean running() {
        return running;
    }

    static synchronized void start(Context ctx) {
        if (running) return;
        Context c = ctx.getApplicationContext();
        File dir = Config.dir(c);
        if (dir == null) return;
        running = true;
        new Thread(() -> {
            try {
                writeTheme(c, dir);
                download(c, new File(dir, "wallpapers"));
            } catch (Exception e) {
                Log.w(TAG, "library: " + e);
            } finally {
                running = false;
            }
        }, "wallpapers").start();
    }

    private static void writeTheme(Context c, File dir) throws Exception {
        File theme = new File(dir, Config.FILE_NAME);
        if (theme.isFile()) return;
        dir.mkdirs();
        try (InputStream in = c.getAssets().open("share_theme.json"); FileOutputStream out = new FileOutputStream(theme)) {
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        }
    }

    private static void download(Context c, File folder) throws Exception {
        folder.mkdirs();
        // The manifest, picked round-robin across the categories so every category fills up together.
        Map<String, List<String[]>> byCat = new LinkedHashMap<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getAssets().open("wallpapers.tsv"), StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null; ) {
                String[] f = line.split("\t", 3);
                if (f.length < 3) continue;
                byCat.computeIfAbsent(f[1], k -> new ArrayList<>()).add(f);
            }
        }
        Random rnd = new Random(7);
        for (List<String[]> l : byCat.values()) Collections.shuffle(l, rnd);
        List<String[]> order = new ArrayList<>();
        for (int i = 0; order.size() < LIMIT; i++) {
            boolean any = false;
            for (List<String[]> l : byCat.values()) {
                if (i < l.size() && order.size() < LIMIT) {
                    order.add(l.get(i));
                    any = true;
                }
            }
            if (!any) break;
        }
        StringBuilder caps = new StringBuilder(), cats = new StringBuilder();
        saved = 0;
        for (int i = 0; i < order.size(); i++) {
            String[] f = order.get(i);
            String name = String.format(java.util.Locale.ROOT, "%03d.jpg", i + 1);
            File out = new File(folder, name);
            if (!(out.isFile() && out.length() > 0)) {
                try {
                    save(fetch("https://commons.wikimedia.org/wiki/Special:FilePath/" + f[0].substring(5) + "?width=1920"), out);
                    Thread.sleep(400);   // gentle on Wikimedia
                } catch (Exception e) {
                    Log.w(TAG, f[0] + ": " + e);
                    continue;
                }
            }
            saved++;
            caps.append(name).append('\t').append(f[2]).append('\n');
            cats.append(name).append('\t').append(f[1]).append('\n');
            if (saved % 10 == 0 || i == order.size() - 1) {
                Files.write(new File(folder, WallpaperCaptions.FILE_NAME).toPath(), caps.toString().getBytes(StandardCharsets.UTF_8));
                Files.write(new File(folder, WallpaperCategories.FILE_NAME).toPath(), cats.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    /** Crops to 16:9, scales to 1920x1080 and saves a JPEG. */
    private static void save(byte[] raw, File out) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(raw, 0, raw.length, o);
        int s = 1;
        while (o.outWidth / (s * 2) >= 1920 && o.outHeight / (s * 2) >= 1080) s *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = s;
        Bitmap b = BitmapFactory.decodeByteArray(raw, 0, raw.length, d);
        if (b == null) throw new Exception("not an image");
        float scale = Math.max(1920f / b.getWidth(), 1080f / b.getHeight());
        float w = 1920f / scale, h = 1080f / scale;
        int l = Math.round((b.getWidth() - w) / 2), t = Math.round((b.getHeight() - h) / 2);
        Bitmap fit = Bitmap.createBitmap(1920, 1080, Bitmap.Config.RGB_565);
        new Canvas(fit).drawBitmap(b, new Rect(l, t, l + Math.round(w), t + Math.round(h)), new Rect(0, 0, 1920, 1080),
                new Paint(Paint.FILTER_BITMAP_FLAG));
        b.recycle();
        File tmp = new File(out.getPath() + ".part");
        try (FileOutputStream fo = new FileOutputStream(tmp)) {
            fit.compress(Bitmap.CompressFormat.JPEG, 82, fo);
        }
        fit.recycle();
        if (!tmp.renameTo(out)) throw new Exception("could not save");
    }

    private static byte[] fetch(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "CleanYourTV-ShareEdition/1.0 (Android TV home screen; wallpaper library)");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            for (int n; (n = in.read(buf)) > 0; ) {
                out.write(buf, 0, n);
                if (out.size() > 20_000_000) throw new Exception("too large");
            }
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }
}
