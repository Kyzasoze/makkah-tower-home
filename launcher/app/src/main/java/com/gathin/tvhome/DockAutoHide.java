package com.gathin.tvhome;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

/**
 * The app dock rests out of sight below the screen. Any press on the remote brings it up (the first press only
 * reveals it, so nothing opens by accident); it slides away again after a few idle seconds, or soon after the focus
 * moves up to the clock and widgets. Back and Home always pass straight through.
 */
final class DockAutoHide {
    private static final long IDLE_MS = 8000L;
    private static final long AWAY_MS = 1500L;

    private final View dock;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean hidden;
    private int swallowUp = -1;
    private final Runnable hide = () -> setHidden(true, true);

    DockAutoHide(View dock) {
        this.dock = dock;
    }

    /** Called from dispatchKeyEvent. Returns true when the key only woke the dock and must not do anything else. */
    boolean onKey(KeyEvent e, boolean focusInDock) {
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_HOME || code == KeyEvent.KEYCODE_ESCAPE
                || code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN
                || code == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return false;
        }
        if (e.getAction() == KeyEvent.ACTION_UP) {
            if (code == swallowUp) {
                swallowUp = -1;
                return true;
            }
            return false;
        }
        main.removeCallbacks(hide);
        boolean consume = false;
        if (hidden) {
            setHidden(false, true);
            // Focus is on a (hidden) tile: this press only reveals the dock.
            if (focusInDock) {
                consume = true;
                swallowUp = code;
            }
        }
        main.postDelayed(hide, focusInDock ? IDLE_MS : AWAY_MS);
        return consume;
    }

    /** Focus moved: into the dock keeps it up; away from it lets it go soon. */
    void onFocusMoved(boolean inDock) {
        main.removeCallbacks(hide);
        if (inDock) {
            if (hidden) setHidden(false, true);
            main.postDelayed(hide, IDLE_MS);
        } else {
            main.postDelayed(hide, AWAY_MS);
        }
    }

    /** Home came to the front: show the dock for a moment, then let it rest. */
    void reveal() {
        main.removeCallbacks(hide);
        setHidden(false, true);
        main.postDelayed(hide, IDLE_MS);
    }

    void stop() {
        main.removeCallbacks(hide);
    }

    private void setHidden(boolean hide, boolean animate) {
        hidden = hide;
        float h = dock.getHeight() > 0 ? dock.getHeight() : 600f;
        dock.animate().cancel();
        if (!animate) {
            dock.setTranslationY(hide ? h : 0f);
            dock.setAlpha(hide ? 0f : 1f);
            return;
        }
        dock.animate()
                .translationY(hide ? h * 0.9f : 0f)
                .alpha(hide ? 0f : 1f)
                .setInterpolator(hide ? new AccelerateInterpolator(1.4f) : new DecelerateInterpolator(2.2f))
                .setDuration(hide ? 420 : 360)
                .start();
    }
}
