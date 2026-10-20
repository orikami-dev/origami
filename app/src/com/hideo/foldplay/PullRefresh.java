package com.hideo.foldplay;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Tirer vers le bas pour actualiser (0.65.27) : enveloppe la liste ou la grille de la bibliothèque
 * en mode Washi. Le geste ne démarre que lorsque la liste est tout en haut ; passé le seuil, relâcher
 * lance l'action, sinon la liste revient à sa place.
 */
final class PullRefresh extends FrameLayout {
    private final View content;
    private final TextView hint;
    private final Runnable action;
    private final int slop, max, trigger;
    private float downY;
    private boolean pulling;

    PullRefresh(Context c, View content, Runnable action) {
        super(c);
        this.content = content;
        this.action = action;
        float d = c.getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        max = Math.round(110 * d);
        trigger = Math.round(72 * d);
        hint = new TextView(c);
        hint.setTextSize(12);
        hint.setTypeface(Fonts.wide(c));
        hint.setLetterSpacing(0.12f);
        hint.setTextColor(Theme.CODEC);
        hint.setGravity(android.view.Gravity.CENTER);
        hint.setAlpha(0f);
        addView(hint, new LayoutParams(-1, Math.round(44 * d), android.view.Gravity.TOP));
        addView(content, new LayoutParams(-1, -1));
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = e.getY();
                pulling = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!pulling && e.getY() - downY > slop && !content.canScrollVertically(-1)) {
                    pulling = true;
                    downY = e.getY();
                    return true;
                }
                break;
            default:
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!pulling) return false;
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_MOVE) {
            float pull = Math.max(0f, Math.min(max, (e.getY() - downY) * 0.5f));
            content.setTranslationY(pull);
            hint.setAlpha(Math.min(1f, pull / trigger));
            hint.setText(pull >= trigger ? "RELÂCHE POUR ACTUALISER" : "TIRE POUR ACTUALISER");
            return true;
        }
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            boolean go = a == MotionEvent.ACTION_UP && content.getTranslationY() >= trigger;
            pulling = false;
            content.animate().translationY(0).setDuration(180).start();
            hint.animate().alpha(0f).setDuration(180).start();
            if (go) {
                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                UiSound.play(getContext(), UiSound.NEXT);
                action.run();
            }
            return true;
        }
        return true;
    }
}
