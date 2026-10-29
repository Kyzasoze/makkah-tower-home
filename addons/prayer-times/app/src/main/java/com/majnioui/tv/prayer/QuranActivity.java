package com.majnioui.tv.prayer;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Qur'an player: pick a surah and it is recited verse by verse by Sheikh Abdul Basit 'Abd us-Samad (murattal),
 * with the Arabic and an English translation (Sahih International) on screen. Text and audio come from
 * alquran.cloud and its CDN. OK pauses or plays, Left/Right go back or forward a verse, Back returns to the list.
 */
public class QuranActivity extends Activity {
    private static final String TAG = "Quran";
    private final Handler main = new Handler(Looper.getMainLooper());
    private Typeface font;
    private int accent;
    private FrameLayout root;
    private SharedPreferences prefs;

    // player state
    private JSONArray arabic, english, audio;
    private int surah, index, count;
    private String surahName = "";
    private MediaPlayer player;
    private boolean playing, inPlayer;
    private AudioManager audioManager;
    private AudioFocusRequest focus;
    private TextView arView, enView, headView, stateView;
    private View progress;
    private int requestId;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        font = getResources().getFont(R.font.outfit);
        accent = getColor(R.color.accent);
        prefs = getSharedPreferences("quran", MODE_PRIVATE);
        audioManager = getSystemService(AudioManager.class);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0xFF0B1A14, 0xFF050607, 0xFF0A0C10}));
        setContentView(root);
        showList();
    }

    // ---- surah list ------------------------------------------------------------------------------

    private void showList() {
        inPlayer = false;
        stopAudio();
        root.removeAllViews();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(72), dp(36), dp(72), dp(24));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        TextView title = text(14, 500, 0xFFE0BD72);
        title.setLetterSpacing(0.3f);
        title.setText("THE NOBLE QUR'AN  ·  RECITED BY SHEIKH ABDUL BASIT");
        col.addView(title);
        TextView sub = text(16, 400, 0x99FFFFFF);
        sub.setText("Choose a surah · OK pauses · Left/Right move a verse · Back returns here");
        sub.setPadding(0, dp(6), 0, dp(16));
        col.addView(sub);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(4), dp(8), dp(40));
        scroll.addView(list);
        col.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        int lastSurah = prefs.getInt("surah", 0), lastAyah = prefs.getInt("ayah", 0);
        View first = null;
        if (lastSurah > 0) {
            TextView resume = row("▶", "Continue  ·  " + prefs.getString("surah_name", "Surah " + lastSurah)
                    + "  ·  verse " + (lastAyah + 1), "", "");
            resume.setOnClickListener(v -> openSurah(lastSurah, lastAyah));
            list.addView(resume, rowLp());
            first = resume;
        }
        JSONArray surahs = surahList();
        if (surahs == null) {
            TextView wait = text(18, 400, 0xCCFFFFFF);
            wait.setText("Loading the list of surahs…");
            list.addView(wait);
            new Thread(() -> {
                try {
                    String body = BoardStore.fetch("https://api.alquran.cloud/v1/surah");
                    new JSONObject(body).getJSONArray("data");
                    prefs.edit().putString("surahs", body).apply();
                    main.post(() -> {
                        if (!inPlayer) showList();
                    });
                } catch (Exception e) {
                    main.post(() -> wait.setText("No internet connection: the Qur'an player needs it to load the surahs."));
                }
            }).start();
        } else {
            for (int i = 0; i < surahs.length(); i++) {
                JSONObject s = surahs.optJSONObject(i);
                if (s == null) continue;
                int n = s.optInt("number");
                TextView r = row(String.valueOf(n), s.optString("englishName") + "  ·  " + s.optString("englishNameTranslation"),
                        s.optInt("numberOfAyahs") + " verses", s.optString("name"));
                r.setOnClickListener(v -> openSurah(n, 0));
                list.addView(r, rowLp());
                if (first == null) first = r;
            }
        }
        if (first != null) first.requestFocus();
    }

    private JSONArray surahList() {
        try {
            String s = prefs.getString("surahs", null);
            return s == null ? null : new JSONObject(s).getJSONArray("data");
        } catch (Exception e) {
            return null;
        }
    }

    private LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        return lp;
    }

    private TextView row(String num, String name, String meta, String arabicName) {
        TextView t = text(20, 400, 0xFFF4F1EA);
        t.setFocusable(true);
        t.setClickable(true);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        String sep = meta.isEmpty() ? "" : "     " + meta;
        t.setText(num + "     " + name + sep + (arabicName.isEmpty() ? "" : "     " + arabicName));
        t.setPadding(dp(22), dp(12), dp(22), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(0x14FFFFFF);
        bg.setStroke(dp(1), 0x1FFFFFFF);
        t.setBackground(bg);
        t.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? (accent & 0x00FFFFFF) | 0xCC000000 : 0x14FFFFFF);
            bg.setStroke(dp(1), has ? 0xFFFFFFFF : 0x1FFFFFFF);
            v.animate().scaleX(has ? 1.02f : 1f).scaleY(has ? 1.02f : 1f).setDuration(140).start();
        });
        return t;
    }

    // ---- player ------------------------------------------------------------------------------------

    private void openSurah(int n, int startAt) {
        inPlayer = true;
        surah = n;
        root.removeAllViews();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(90), dp(40), dp(90), dp(30));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        headView = text(15, 500, 0xFFE0BD72);
        headView.setLetterSpacing(0.25f);
        headView.setText("LOADING…");
        col.addView(headView);
        View spacer = new View(this);
        col.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));
        arView = text(36, 400, 0xFFF3DFA8);
        arView.setTypeface(Typeface.DEFAULT);
        arView.setGravity(Gravity.CENTER);
        arView.setLineSpacing(0, 1.35f);
        arView.setTextDirection(View.TEXT_DIRECTION_RTL);
        arView.setShadowLayer(dp(12), 0, 0, 0x55E0BD72);
        col.addView(arView, new LinearLayout.LayoutParams(-1, -2));
        enView = text(21, 400, 0xE6F4F1EA);
        enView.setGravity(Gravity.CENTER);
        enView.setLineSpacing(0, 1.3f);
        enView.setPadding(0, dp(26), 0, 0);
        col.addView(enView, new LinearLayout.LayoutParams(-1, -2));
        View spacer2 = new View(this);
        col.addView(spacer2, new LinearLayout.LayoutParams(1, 0, 1f));
        stateView = text(14, 400, 0x99FFFFFF);
        stateView.setText("OK  pause / play     ◀ ▶  verse     BACK  surahs");
        col.addView(stateView);
        FrameLayout bar = new FrameLayout(this);
        bar.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(3));
        blp.topMargin = dp(12);
        col.addView(bar, blp);
        progress = new View(this);
        progress.setBackgroundColor(accent);
        progress.setPivotX(0);
        progress.setScaleX(0f);
        bar.addView(progress, new FrameLayout.LayoutParams(-1, -1));

        final int req = ++requestId;
        new Thread(() -> {
            try {
                JSONArray data = new JSONObject(BoardStore.fetch("https://api.alquran.cloud/v1/surah/" + n
                        + "/editions/quran-simple,en.sahih,ar.abdulbasitmurattal")).getJSONArray("data");
                JSONObject ar = data.getJSONObject(0), en = data.getJSONObject(1), au = data.getJSONObject(2);
                main.post(() -> {
                    if (req != requestId || !inPlayer) return;
                    arabic = ar.optJSONArray("ayahs");
                    english = en.optJSONArray("ayahs");
                    audio = au.optJSONArray("ayahs");
                    count = arabic == null ? 0 : arabic.length();
                    surahName = ar.optString("englishName") + "  ·  " + ar.optString("name");
                    requestFocusFor();
                    play(Math.min(startAt, Math.max(0, count - 1)));
                });
            } catch (Exception e) {
                Log.w(TAG, "surah: " + e);
                main.post(() -> {
                    if (req == requestId && headView != null) headView.setText("COULD NOT LOAD THIS SURAH · CHECK THE INTERNET");
                });
            }
        }).start();
    }

    private void requestFocusFor() {
        root.setFocusable(true);
        root.requestFocus();
    }

    private void play(int i) {
        if (count == 0) return;
        index = Math.max(0, Math.min(count - 1, i));
        JSONObject a = arabic.optJSONObject(index), e = english != null ? english.optJSONObject(index) : null;
        headView.setText((surahName + "  ·  VERSE " + (index + 1) + " OF " + count).toUpperCase());
        arView.setText(a != null ? a.optString("text") : "");
        enView.setText(e != null ? e.optString("text") : "");
        arView.setAlpha(0f);
        enView.setAlpha(0f);
        arView.setTranslationY(dp(14));
        arView.animate().alpha(1f).translationY(0).setDuration(500).start();
        enView.animate().alpha(1f).setStartDelay(200).setDuration(500).start();
        progress.animate().scaleX((index + 1f) / count).setDuration(400).start();
        prefs.edit().putInt("surah", surah).putInt("ayah", index)
                .putString("surah_name", surahName.split("  ·  ")[0]).apply();
        stopAudio();
        String url = audio != null && audio.optJSONObject(index) != null ? audio.optJSONObject(index).optString("audio") : "";
        if (url.isEmpty()) return;
        try {
            AudioAttributes attrs = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
            takeFocus(attrs);
            MediaPlayer p = new MediaPlayer();
            p.setAudioAttributes(attrs);
            p.setDataSource(url);
            p.setOnPreparedListener(mp -> {
                if (mp == player) {
                    mp.start();
                    playing = true;
                }
            });
            p.setOnCompletionListener(mp -> {
                if (mp != player) return;
                if (index + 1 < count) play(index + 1);
                else {
                    playing = false;
                    headView.setText((surahName + "  ·  COMPLETE").toUpperCase());
                    prefs.edit().putInt("ayah", 0).apply();
                }
            });
            p.setOnErrorListener((mp, what, extra) -> {
                Log.w(TAG, "audio error " + what);
                if (mp == player && index + 1 < count) main.postDelayed(() -> play(index + 1), 800);
                return true;
            });
            player = p;
            p.prepareAsync();
        } catch (Exception ex) {
            Log.w(TAG, "play: " + ex);
        }
    }

    private void takeFocus(AudioAttributes attrs) {
        if (focus != null || audioManager == null || Build.VERSION.SDK_INT < 26) return;
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(ch -> {
                    if (ch == AudioManager.AUDIOFOCUS_LOSS || ch == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) togglePause(true);
                }).build();
        audioManager.requestAudioFocus(focus);
    }

    private void togglePause(boolean forcePause) {
        if (player == null) return;
        try {
            if (playing || forcePause) {
                if (player.isPlaying()) player.pause();
                playing = false;
                stateView.setText("PAUSED  ·  OK to continue");
            } else {
                player.start();
                playing = true;
                stateView.setText("OK  pause / play     ◀ ▶  verse     BACK  surahs");
            }
        } catch (Exception ignored) {
        }
    }

    private void stopAudio() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
        playing = false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (!inPlayer) return super.dispatchKeyEvent(e);
        int k = e.getKeyCode();
        if (e.getAction() != KeyEvent.ACTION_DOWN) {
            return k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                    || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT || super.dispatchKeyEvent(e);
        }
        switch (k) {
            case KeyEvent.KEYCODE_BACK:
                showList();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                togglePause(false);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                play(index - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                play(index + 1);
                return true;
            default:
                return super.dispatchKeyEvent(e);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAudio();
        if (audioManager != null && focus != null && Build.VERSION.SDK_INT >= 26) audioManager.abandonAudioFocusRequest(focus);
        focus = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (inPlayer && count > 0 && player == null) play(index);
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        stopAudio();
        super.onDestroy();
    }

    private TextView text(float sp, int weight, int color) {
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
}
