package com.hideo.foldplay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * Manette Mini en fenêtre flottante, comme les applis en fenêtre contextuelle d'Android : un cadre
 * arrondi avec une poignée en haut (glisser = déplacer) et les quatre coins pour la taille (le coin
 * opposé reste en place). Posée dans un FrameLayout ; place et taille gardées d'une fois à l'autre.
 */
final class FloatingPad extends FrameLayout {
    private final Store store;
    private final FrameLayout content;
    private final float dp;
    private final int bar;
    private final int corner;
    private final Paint handle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    /** 0 rien, 1 déplacer, 2..5 coin haut-gauche, haut-droit, bas-gauche, bas-droit. */
    private int mode;
    private float sx, sy;
    private int ox, oy, oside;

    FloatingPad(Context ctx, Store store) {
        super(ctx);
        this.store = store;
        dp = ctx.getResources().getDisplayMetrics().density;
        bar = Math.round(22 * dp);
        corner = Math.round(26 * dp);
        setWillNotDraw(false);
        setElevation(8 * dp);
        handle.setColor(0xCCFFFFFF);
        frame.setStyle(Paint.Style.STROKE);
        frame.setStrokeWidth(Math.max(1f, dp));
        frame.setColor(0x33FFFFFF);
        android.graphics.drawable.GradientDrawable bg = Theme.pill((Theme.PANEL & 0x00FFFFFF) | 0xB3000000, 20 * dp);
        setBackground(bg);
        setClipToOutline(true);
        content = new FrameLayout(ctx);
        LayoutParams lp = new LayoutParams(-1, -1);
        lp.topMargin = bar;
        addView(content, lp);
    }

    /** Où mettre la manette. */
    FrameLayout content() {
        return content;
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        // La poignée : une barre courte au milieu du haut, comme une fenêtre contextuelle.
        float w = 36 * dp, h = 4 * dp;
        r.set((getWidth() - w) / 2, (bar - h) / 2, (getWidth() + w) / 2, (bar + h) / 2);
        c.drawRoundRect(r, h / 2, h / 2, handle);
        r.set(frame.getStrokeWidth() / 2, frame.getStrokeWidth() / 2,
                getWidth() - frame.getStrokeWidth() / 2, getHeight() - frame.getStrokeWidth() / 2);
        c.drawRoundRect(r, 20 * dp, 20 * dp, frame);
    }

    private int zone(float x, float y) {
        boolean left = x < corner, right = x > getWidth() - corner;
        boolean top = y < corner, bottom = y > getHeight() - corner;
        if (top && left) return 2;
        if (top && right) return 3;
        if (bottom && left) return 4;
        if (bottom && right) return 5;
        if (y < bar) return 1;
        return 0;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        return e.getActionMasked() == MotionEvent.ACTION_DOWN && zone(e.getX(), e.getY()) != 0;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        ViewGroup.LayoutParams raw = getLayoutParams();
        if (!(raw instanceof FrameLayout.LayoutParams)) return false;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) raw;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mode = zone(e.getX(), e.getY());
                if (mode == 0) return false;
                sx = e.getRawX();
                sy = e.getRawY();
                ox = lp.leftMargin;
                oy = lp.topMargin;
                oside = lp.width;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getRawX() - sx, dy = e.getRawY() - sy;
                if (mode == 1) {
                    lp.leftMargin = Math.round(ox + dx);
                    lp.topMargin = Math.round(oy + dy);
                } else {
                    // Le coin tiré suit le doigt, le coin opposé ne bouge pas.
                    boolean left = mode == 2 || mode == 4, top = mode == 2 || mode == 3;
                    float d = Math.max(left ? -dx : dx, top ? -dy : dy);
                    int side = clampSide(Math.round(oside + d));
                    lp.leftMargin = left ? ox + oside - side : ox;
                    lp.topMargin = top ? oy + oside - side : oy;
                    lp.width = side;
                    lp.height = side + bar;
                }
                clamp(lp);
                setLayoutParams(lp);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mode = 0;
                save(lp);
                return true;
        }
        return false;
    }

    private int clampSide(int side) {
        View p = (View) getParent();
        int max = p == null ? side : Math.min(p.getWidth(), p.getHeight() - bar) - Math.round(8 * dp);
        return Math.max(Math.round(120 * dp), Math.min(Math.max(Math.round(120 * dp), max), side));
    }

    private void clamp(FrameLayout.LayoutParams lp) {
        View p = (View) getParent();
        if (p == null || p.getWidth() <= 0) return;
        lp.width = clampSide(lp.width);
        lp.height = lp.width + bar;
        int m = Math.round(4 * dp);
        lp.leftMargin = Math.max(m, Math.min(p.getWidth() - lp.width - m, lp.leftMargin));
        lp.topMargin = Math.max(m, Math.min(p.getHeight() - lp.height - m, lp.topMargin));
    }

    private void save(FrameLayout.LayoutParams lp) {
        View p = (View) getParent();
        if (p == null || p.getWidth() <= 0 || p.getHeight() <= 0) return;
        store.setMiniBox((float) lp.leftMargin / p.getWidth(), (float) lp.topMargin / p.getHeight(),
                Math.round(lp.width / dp));
    }

    /** Remet la manette à sa place enregistrée (en bas à droite la première fois). */
    void place() {
        final View p = (View) getParent();
        if (p == null) return;
        if (p.getWidth() <= 0 || p.getHeight() <= 0) {
            p.post(new Runnable() { public void run() { place(); } });
            return;
        }
        float[] box = store.miniBox();
        int side = Math.round(box[2] * dp);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(side, side + bar);
        lp.leftMargin = box[0] < 0 ? p.getWidth() - side - Math.round(16 * dp) : Math.round(box[0] * p.getWidth());
        lp.topMargin = box[1] < 0 ? p.getHeight() - side - bar - Math.round(16 * dp) : Math.round(box[1] * p.getHeight());
        clamp(lp);
        setLayoutParams(lp);
    }
}
