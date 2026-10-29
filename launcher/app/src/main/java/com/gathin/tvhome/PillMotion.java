package com.gathin.tvhome;

import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/** Focus animations for the top buttons: the gear turns, Wi-Fi pulses, Inputs hops; others lift a little. */
final class PillMotion {
    private PillMotion() {
    }

    static void focus(View v, boolean focused) {
        float dp = v.getResources().getDisplayMetrics().density;
        v.animate().cancel();
        if (!focused) {
            v.animate().scaleX(1f).scaleY(1f).translationY(0f).rotation(0f)
                    .setInterpolator(new DecelerateInterpolator()).setDuration(220).start();
            return;
        }
        int id = v.getId();
        if (id == R.id.btnSettings) {
            v.setRotation(0f);
            v.animate().rotation(135f).scaleX(1.14f).scaleY(1.14f)
                    .setInterpolator(new OvershootInterpolator(1.3f)).setDuration(560).start();
        } else if (id == R.id.btnWifi) {
            v.animate().scaleX(1.24f).scaleY(1.24f).setInterpolator(new DecelerateInterpolator()).setDuration(150)
                    .withEndAction(() -> {
                        if (v.isFocused()) {
                            v.animate().scaleX(1.12f).scaleY(1.12f).setInterpolator(new OvershootInterpolator(3f))
                                    .setDuration(320).start();
                        }
                    }).start();
        } else if (id == R.id.btnWallpapers) {
            v.setRotation(-14f);
            v.animate().rotation(0f).scaleX(1.14f).scaleY(1.14f).translationY(-2 * dp)
                    .setInterpolator(new OvershootInterpolator(3.2f)).setDuration(480).start();
        } else if (id == R.id.btnInputs) {
            v.animate().translationY(-7 * dp).scaleX(1.14f).scaleY(1.14f)
                    .setInterpolator(new DecelerateInterpolator()).setDuration(150)
                    .withEndAction(() -> {
                        if (v.isFocused()) {
                            v.animate().translationY(-2 * dp).setInterpolator(new OvershootInterpolator(4f))
                                    .setDuration(340).start();
                        }
                    }).start();
        } else {
            v.animate().scaleX(1.1f).scaleY(1.1f).setInterpolator(new OvershootInterpolator(2f)).setDuration(240).start();
        }
    }
}
