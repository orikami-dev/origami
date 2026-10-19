package com.hideo.foldplay;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Bandeau codec qui glisse du haut de l'écran quand une manette se connecte ou se déconnecte,
 * puis repart tout seul. Un seul à la fois : le suivant remplace le précédent.
 */
final class PadBanner {
    private PadBanner() {
    }

    private static final Object TAG = new Object();

    static void show(final FrameLayout host, String title, String detail, boolean connected) {
        if (host == null) return;
        Context ctx = host.getContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        for (int i = host.getChildCount() - 1; i >= 0; i--) {
            View v = host.getChildAt(i);
            if (v.getTag() == TAG) host.removeView(v);
        }
        final LinearLayout box = new LinearLayout(ctx);
        box.setTag(TAG);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Math.round(12 * dp);
        box.setPadding(pad * 2, pad, pad * 2, pad);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xE60B0D10);
        bg.setCornerRadius(18 * dp);
        bg.setStroke(Math.round(1.5f * dp), connected ? Theme.CODEC : 0xFFFF9A1F);
        box.setBackground(bg);
        TextView t = new TextView(ctx);
        t.setText(title);
        t.setTextColor(connected ? Theme.CODEC : 0xFFFF9A1F);
        t.setTextSize(12);
        t.setLetterSpacing(0.2f);
        t.setTypeface(Fonts.wide(ctx));
        box.addView(t);
        if (detail != null) {
            TextView d = new TextView(ctx);
            d.setText(detail);
            d.setTextColor(Theme.TEXT);
            d.setTextSize(14);
            d.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            d.setGravity(Gravity.CENTER_HORIZONTAL);
            box.addView(d);
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = Math.round(16 * dp);
        host.addView(box, lp);
        box.setElevation(12 * dp);
        box.setAlpha(0f);
        box.setTranslationY(-60 * dp);
        box.animate().alpha(1f).translationY(0).setDuration(260)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        final float up = -60 * dp;
        box.postDelayed(new Runnable() {
            public void run() {
                box.animate().alpha(0f).translationY(up).setDuration(240).withEndAction(new Runnable() {
                    public void run() {
                        if (box.getParent() == host) host.removeView(box);
                    }
                }).start();
            }
        }, 2400);
    }

    /** Transition douce des changements de mise en page qui suivent (manette ajoutée ou retirée). */
    static void animateLayout(android.view.ViewGroup scene) {
        if (scene == null) return;
        android.transition.AutoTransition t = new android.transition.AutoTransition();
        t.setDuration(320);
        t.setInterpolator(new android.view.animation.DecelerateInterpolator());
        android.transition.TransitionManager.beginDelayedTransition(scene, t);
    }
}
