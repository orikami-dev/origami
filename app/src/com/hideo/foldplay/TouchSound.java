package com.hideo.foldplay;

import android.app.Activity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

/**
 * Sons du thème au doigt (0.62.5) : les clics d'Android jouaient le son système du téléphone
 * sur certains menus. Chaque vue touchée perd ce son système, et un appui sur une vue cliquable
 * joue le son « valider » du thème, sauf si l'écran a déjà joué le sien.
 */
final class TouchSound {
    private TouchSound() {
    }

    static void install(Activity a) {
        Window w = a.getWindow();
        Window.Callback cb = w.getCallback();
        if (cb == null || cb instanceof Wrap) return;
        w.setCallback(new Wrap(a, cb));
    }

    /** Vue cliquable la plus profonde sous le doigt (x, y dans le repère de v). */
    private static View target(View v, float x, float y) {
        if (v.getVisibility() != View.VISIBLE || x < 0 || y < 0 || x >= v.getWidth() || y >= v.getHeight()) return null;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = g.getChildCount() - 1; i >= 0; i--) {
                View c = g.getChildAt(i);
                View t = target(c, x + v.getScrollX() - c.getLeft(), y + v.getScrollY() - c.getTop());
                if (t != null) return t;
            }
        }
        return v.isClickable() ? v : null;
    }

    private static final class Wrap extends WindowCallbackProxy {
        private final Activity activity;
        private View down;

        Wrap(Activity a, Window.Callback base) {
            super(base);
            activity = a;
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent e) {
            int act = e.getActionMasked();
            if (act == MotionEvent.ACTION_DOWN) {
                View root = activity.getWindow().getDecorView();
                down = target(root, e.getX(), e.getY());
                if (down != null) down.setSoundEffectsEnabled(false);
            }
            boolean r = super.dispatchTouchEvent(e);
            if (act == MotionEvent.ACTION_UP && down != null) {
                final View v = down;
                down = null;
                // Les manettes et les moteurs ont leur propre retour : pas de son de menu.
                if (!(v instanceof ControlView) && !(v instanceof ControlDeckView) && !(v instanceof android.view.SurfaceView)) {
                    v.post(new Runnable() {
                        public void run() {
                            if (!UiSound.playedRecently(180)) UiSound.play(activity, UiSound.SELECT);
                        }
                    });
                }
            } else if (act == MotionEvent.ACTION_CANCEL) {
                down = null;
            }
            return r;
        }
    }
}
