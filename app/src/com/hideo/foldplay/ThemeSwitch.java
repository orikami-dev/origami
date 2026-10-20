package com.hideo.foldplay;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Interrupteur dessiné à la main, commun à tous les réglages (Réglages et menus codec).
 * Piste arrondie à liseré d'accent, bouton qui glisse en ~150 ms ; allumé : piste teintée + halo,
 * éteint : liseré terne et bouton discret. Le codec ajoute un petit « ON / OFF » monospace.
 * La ligne qui le contient reste cliquable : la vue elle-même ne capte rien.
 */
final class ThemeSwitch extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float dp;
    private final boolean still; // « Réduire les animations » : pas de glissement
    private boolean on;
    private float pos; // 0 = éteint, 1 = allumé
    private ValueAnimator anim;

    ThemeSwitch(Context ctx, boolean on) {
        super(ctx);
        dp = ctx.getResources().getDisplayMetrics().density;
        boolean s = false;
        try {
            s = new Store(ctx).reduceMotion();
        } catch (RuntimeException e) {
            // préférences illisibles : on anime
        }
        still = s;
        this.on = on;
        pos = on ? 1f : 0f;
        setClickable(false);
        setFocusable(false);
        setMinimumWidth(Math.round(48 * dp));
        setMinimumHeight(Math.round(48 * dp));
        describe();
    }

    boolean isOn() {
        return on;
    }

    /** Change l'état (avec glissement sauf si les animations sont réduites). */
    void setOn(boolean v) {
        if (v == on) return;
        on = v;
        describe();
        if (anim != null) anim.cancel();
        if (still) {
            pos = on ? 1f : 0f;
            invalidate();
            return;
        }
        anim = ValueAnimator.ofFloat(pos, on ? 1f : 0f);
        anim.setDuration(150);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                pos = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        anim.start();
        sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED);
    }

    private void describe() {
        setContentDescription(on ? "Activé" : "Désactivé");
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Switch");
        info.setCheckable(true);
        info.setChecked(on);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(resolve(Math.round(48 * dp), w), resolve(Math.round(48 * dp), h));
    }

    private static int resolve(int want, int spec) {
        int mode = MeasureSpec.getMode(spec);
        int size = MeasureSpec.getSize(spec);
        if (mode == MeasureSpec.EXACTLY) return size;
        if (mode == MeasureSpec.AT_MOST) return Math.min(want, size);
        return want;
    }

    @Override
    protected void onDraw(Canvas c) {
        float tw = 44 * dp, th = 24 * dp;
        float l = (getWidth() - tw) / 2f, t = (getHeight() - th) / 2f;
        // Style selon le thème : codec nerveux, art déco carré, les autres arrondis.
        float rad = "ambre".equals(Theme.THEME_ID) ? 4 * dp : "codec".equals(Theme.THEME_ID) ? 6 * dp : th / 2f;
        int acc = Theme.CODEC;
        int line = Theme.blend(Theme.alpha(Theme.MUTED, 0xFF), acc, pos);

        if (pos > 0.02f) { // halo discret quand c'est allumé
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3 * dp);
            p.setColor(Theme.alpha(acc, Math.round(0x38 * pos)));
            r.set(l - 1.5f * dp, t - 1.5f * dp, l + tw + 1.5f * dp, t + th + 1.5f * dp);
            c.drawRoundRect(r, rad + 1.5f * dp, rad + 1.5f * dp, p);
        }
        r.set(l, t, l + tw, t + th);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Theme.blend(Theme.PANEL, acc, 0.30f * pos));
        c.drawRoundRect(r, rad, rad, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f * dp);
        p.setColor(line);
        r.inset(0.75f * dp, 0.75f * dp);
        c.drawRoundRect(r, rad, rad, p);

        float kr = th / 2f - 4 * dp;
        float kx = l + 4 * dp + kr + pos * (tw - 8 * dp - 2 * kr);
        float ky = t + th / 2f;

        if ("codec".equals(Theme.THEME_ID)) { // étiquette du côté libre de la piste
            p.setStyle(Paint.Style.FILL);
            p.setTypeface(Typeface.MONOSPACE);
            p.setFakeBoldText(true);
            p.setTextSize(8.5f * dp);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(Theme.alpha(on ? acc : Theme.MUTED, 0xCC));
            float cx = on ? l + (tw - 16 * dp) / 2f : l + tw - (tw - 16 * dp) / 2f;
            c.drawText(on ? "ON" : "OFF", cx, ky + 3 * dp, p);
            p.setFakeBoldText(false);
            p.setTypeface(Typeface.DEFAULT);
        }

        p.setStyle(Paint.Style.FILL);
        p.setColor(Theme.blend(Theme.MUTED, acc, pos));
        c.drawCircle(kx, ky, kr, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1 * dp);
        p.setColor(Theme.alpha(Theme.darken(acc, 0.5f), 0x80));
        c.drawCircle(kx, ky, kr - 0.5f * dp, p);
    }
}
