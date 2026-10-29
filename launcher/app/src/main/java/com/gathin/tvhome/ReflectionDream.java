package com.gathin.tvhome;

import android.service.dreams.DreamService;

/** The same verses and hadith as the TV's own screensaver (Settings > Screen saver > Reflections). */
public class ReflectionDream extends DreamService {
    private ReflectionsView view;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        view = new ReflectionsView(this);
        setContentView(view);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        view.start();
    }

    @Override
    public void onDreamingStopped() {
        view.stop();
        super.onDreamingStopped();
    }
}
