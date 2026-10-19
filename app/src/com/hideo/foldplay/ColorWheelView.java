package com.hideo.foldplay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.MotionEvent;
import android.view.View;

/**
 * Palette ronde : la teinte tourne autour du cercle, la saturation va du centre (blanc) au bord.
 * La luminosité se règle à part (curseur sous la roue). Le doigt glisse librement dans le cercle.
 */
final class ColorWheelView extends View {
    interface Listener {
        /** Couleur choisie ; done = le doigt vient de se lever (moment d'enregistrer). */
        void onColor(int color, boolean done);
    }

    private final Paint hue = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sat = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] hsv = {0f, 0f, 1f};
    private final float dp;
    private Listener listener;

    ColorWheelView(Context ctx) {
        super(ctx);
        dp = ctx.getResources().getDisplayMetrics().density;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(3 * dp);
        ring.setColor(Color.WHITE);
        dim.setColor(Color.BLACK);
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setColor(int color) {
        Color.colorToHSV(color, hsv);
        invalidate();
    }

    /** Luminosité 0..1 (le curseur sous la roue). */
    void setValue(float v) {
        hsv[2] = Math.max(0f, Math.min(1f, v));
        invalidate();
        if (listener != null) listener.onColor(color(), false);
    }

    float value() {
        return hsv[2];
    }

    int color() {
        return Color.HSVToColor(hsv);
    }

    @Override
    protected void onMeasure(int w, int h) {
        int size = Math.min(MeasureSpec.getSize(w), MeasureSpec.getMode(h) == MeasureSpec.UNSPECIFIED
                ? MeasureSpec.getSize(w) : MeasureSpec.getSize(h));
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float cx = w / 2f;
        float cy = h / 2f;
        hue.setShader(new SweepGradient(cx, cy, new int[] {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF,
            0xFF0000FF, 0xFFFF00FF, 0xFFFF0000}, null));
        sat.setShader(new RadialGradient(cx, cy, Math.max(1f, radius()), 0xFFFFFFFF, 0x00FFFFFF,
                Shader.TileMode.CLAMP));
    }

    private float radius() {
        return Math.min(getWidth(), getHeight()) / 2f - 6 * dp;
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = radius();
        c.drawCircle(cx, cy, r, hue);
        c.drawCircle(cx, cy, r, sat);
        dim.setAlpha(Math.round(255 * (1 - hsv[2])));
        c.drawCircle(cx, cy, r, dim);
        // Repère : là où se trouve la couleur actuelle.
        double a = Math.toRadians(hsv[0]);
        float px = cx + (float) Math.cos(a) * hsv[1] * r;
        float py = cy + (float) Math.sin(a) * hsv[1] * r;
        dot.setColor(color());
        c.drawCircle(px, py, 11 * dp, dot);
        ring.setColor(hsv[2] > 0.6f && hsv[1] < 0.3f ? Color.BLACK : Color.WHITE);
        c.drawCircle(px, py, 11 * dp, ring);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int act = e.getActionMasked();
        if (act == MotionEvent.ACTION_DOWN) getParent().requestDisallowInterceptTouchEvent(true);
        float dx = e.getX() - getWidth() / 2f;
        float dy = e.getY() - getHeight() / 2f;
        float dist = (float) Math.hypot(dx, dy);
        float deg = (float) Math.toDegrees(Math.atan2(dy, dx));
        if (deg < 0) deg += 360;
        hsv[0] = deg;
        hsv[1] = Math.min(1f, dist / Math.max(1f, radius()));
        // Toucher une roue sombre la rallume : sinon on ne verrait pas ce qu'on choisit.
        if (hsv[2] < 0.15f) hsv[2] = 1f;
        invalidate();
        boolean done = act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL;
        if (listener != null) listener.onColor(color(), done);
        return true;
    }
}
