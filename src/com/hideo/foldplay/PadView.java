package com.hideo.foldplay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Une manette physique dessinée d'après sa famille (Xbox, PlayStation, Nintendo ou générique), qui
 * s'allume en direct : touches, croix, gâchettes analogiques et position des sticks.
 * Silhouettes inspirées des modèles officiels (manette PS5, Xbox, Pro Switch), mais sans aucun logo.
 */
final class PadView extends View {
    enum Family { XBOX, PLAYSTATION, NINTENDO, GENERIC }

    final int deviceId;
    final Family family;
    private final Set<Integer> down = new HashSet<Integer>();
    private float lx, ly, rx, ry, lt, rt, hatX, hatY;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;

    PadView(Context ctx, InputDevice d) {
        super(ctx);
        deviceId = d.getId();
        family = family(d);
        dp = ctx.getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(1.5f * dp);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF,
                android.graphics.Typeface.BOLD));
    }

    static Family family(InputDevice d) {
        int v = d.getVendorId();
        String n = String.valueOf(d.getName()).toLowerCase(Locale.ROOT);
        if (v == 0x057e || n.contains("pro controller") || n.contains("joy-con") || n.contains("nintendo")) return Family.NINTENDO;
        if (v == 0x054c || n.contains("dualsense") || n.contains("dualshock") || n.contains("wireless controller")
                || n.contains("playstation")) return Family.PLAYSTATION;
        if (v == 0x045e || n.contains("xbox")) return Family.XBOX;
        return Family.GENERIC;
    }

    static String familyLabel(Family f) {
        switch (f) {
            case XBOX: return "type Xbox";
            case PLAYSTATION: return "type PlayStation";
            case NINTENDO: return "type Nintendo";
            default: return "manette générique";
        }
    }

    // ------------------------------------------------------------------ entrées

    /** Rend vrai si l'évènement venait de cette manette. */
    boolean onKey(KeyEvent e) {
        if (e.getDeviceId() != deviceId) return false;
        if (e.getAction() == KeyEvent.ACTION_DOWN) down.add(e.getKeyCode());
        else if (e.getAction() == KeyEvent.ACTION_UP) down.remove(e.getKeyCode());
        invalidate();
        return true;
    }

    boolean onMotion(MotionEvent e) {
        if (e.getDeviceId() != deviceId) return false;
        lx = e.getAxisValue(MotionEvent.AXIS_X);
        ly = e.getAxisValue(MotionEvent.AXIS_Y);
        rx = e.getAxisValue(MotionEvent.AXIS_Z);
        ry = e.getAxisValue(MotionEvent.AXIS_RZ);
        lt = Math.max(e.getAxisValue(MotionEvent.AXIS_LTRIGGER), e.getAxisValue(MotionEvent.AXIS_BRAKE));
        rt = Math.max(e.getAxisValue(MotionEvent.AXIS_RTRIGGER), e.getAxisValue(MotionEvent.AXIS_GAS));
        hatX = e.getAxisValue(MotionEvent.AXIS_HAT_X);
        hatY = e.getAxisValue(MotionEvent.AXIS_HAT_Y);
        invalidate();
        return true;
    }

    private boolean is(int code) {
        return down.contains(code);
    }

    boolean isDown(int code) {
        return down.contains(code);
    }

    // ------------------------------------------------------------------ dessin

    @Override
    protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        setMeasuredDimension(width, Math.round(Math.min(width * 0.64f, 250 * dp)));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        // Boîte de dessin au rapport 1,6 centrée ; toutes les cotes sont en fraction de cette boîte.
        bw = Math.min(w, h * 1.6f);
        bh = bw / 1.6f;
        ox = (w - bw) / 2;
        oy = (h - bh) / 2;
        switch (family) {
            case PLAYSTATION: dualSense(c); break;
            case NINTENDO: proController(c); break;
            default: xbox(c); break;
        }
    }

    private float bw, bh, ox, oy;
    private static final int STEEL = 0xFF3A3F47;

    private float X(float f) { return ox + bw * f; }
    private float Y(float f) { return oy + bh * f; }

    private void body(Canvas c, Path p, int color, int edge) {
        fill.setColor(color);
        c.drawPath(p, fill);
        line.setColor(edge);
        c.drawPath(p, line);
    }

    /** Manette PS5 : bandeau large, pavé tactile central, poignées longues, sticks bas et symétriques. */
    private void dualSense(Canvas c) {
        float r = bw * 0.048f;
        trigger(c, X(0.13f), Y(0.0f), bw * 0.15f, bh * 0.08f, Math.max(lt, is(KeyEvent.KEYCODE_BUTTON_L2) ? 1 : 0), "L2");
        trigger(c, X(0.72f), Y(0.0f), bw * 0.15f, bh * 0.08f, Math.max(rt, is(KeyEvent.KEYCODE_BUTTON_R2) ? 1 : 0), "R2");
        bumper(c, X(0.12f), Y(0.09f), bw * 0.17f, bh * 0.06f, is(KeyEvent.KEYCODE_BUTTON_L1), "L1");
        bumper(c, X(0.71f), Y(0.09f), bw * 0.17f, bh * 0.06f, is(KeyEvent.KEYCODE_BUTTON_R1), "R1");

        // Coque : face blanche cassée et poignées sombres, comme le modèle d'origine.
        Path p = new Path();
        p.moveTo(X(0.20f), Y(0.16f));
        p.cubicTo(X(0.35f), Y(0.13f), X(0.65f), Y(0.13f), X(0.80f), Y(0.16f));
        p.cubicTo(X(0.92f), Y(0.18f), X(0.96f), Y(0.35f), X(0.98f), Y(0.62f));
        p.cubicTo(X(1.0f), Y(0.86f), X(0.97f), Y(1.0f), X(0.89f), Y(0.99f));
        p.cubicTo(X(0.82f), Y(0.98f), X(0.78f), Y(0.86f), X(0.72f), Y(0.77f));
        p.cubicTo(X(0.62f), Y(0.73f), X(0.38f), Y(0.73f), X(0.28f), Y(0.77f));
        p.cubicTo(X(0.22f), Y(0.86f), X(0.18f), Y(0.98f), X(0.11f), Y(0.99f));
        p.cubicTo(X(0.03f), Y(1.0f), X(0.0f), Y(0.86f), X(0.02f), Y(0.62f));
        p.cubicTo(X(0.04f), Y(0.35f), X(0.08f), Y(0.18f), X(0.20f), Y(0.16f));
        p.close();
        body(c, p, 0xFF15171B, 0xFF4A505A);
        Path face = new Path();
        face.moveTo(X(0.22f), Y(0.19f));
        face.cubicTo(X(0.36f), Y(0.165f), X(0.64f), Y(0.165f), X(0.78f), Y(0.19f));
        face.cubicTo(X(0.86f), Y(0.21f), X(0.90f), Y(0.33f), X(0.91f), Y(0.50f));
        face.cubicTo(X(0.85f), Y(0.56f), X(0.76f), Y(0.70f), X(0.70f), Y(0.74f));
        face.cubicTo(X(0.60f), Y(0.70f), X(0.40f), Y(0.70f), X(0.30f), Y(0.74f));
        face.cubicTo(X(0.24f), Y(0.70f), X(0.15f), Y(0.56f), X(0.09f), Y(0.50f));
        face.cubicTo(X(0.10f), Y(0.33f), X(0.14f), Y(0.21f), X(0.22f), Y(0.19f));
        face.close();
        body(c, face, 0xFFD9DCE1, 0xFFB5BAC2);

        // Pavé tactile (cliquable) bordé de la barre lumineuse.
        Path pad = new Path();
        pad.moveTo(X(0.33f), Y(0.18f));
        pad.lineTo(X(0.67f), Y(0.18f));
        pad.lineTo(X(0.65f), Y(0.45f));
        pad.quadTo(X(0.5f), Y(0.49f), X(0.35f), Y(0.45f));
        pad.close();
        boolean touch = false;
        fill.setColor(touch ? Theme.CODEC : 0xFF15171B);
        c.drawPath(pad, fill);
        line.setColor(Theme.CODEC);
        c.drawLine(X(0.335f), Y(0.19f), X(0.355f), Y(0.44f), line);
        c.drawLine(X(0.665f), Y(0.19f), X(0.645f), Y(0.44f), line);

        dpad(c, X(0.205f), Y(0.36f), r * 1.15f, 0xFF2A2E35, 0xFFB5BAC2);
        String[] labels = {"✕", "○", "□", "△"};
        diamond(c, X(0.795f), Y(0.36f), r, labels, 0xFF2A2E35, 0xFFE6E8EB);

        stick(c, X(0.35f), Y(0.60f), r, lx, ly, is(KeyEvent.KEYCODE_BUTTON_THUMBL));
        stick(c, X(0.65f), Y(0.60f), r, rx, ry, is(KeyEvent.KEYCODE_BUTTON_THUMBR));
        // Bouton central (sans logo), micro dessous, Create et Options de part et d'autre du pavé.
        face(c, X(0.5f), Y(0.585f), r * 0.42f, is(KeyEvent.KEYCODE_BUTTON_MODE), "", 0xFF2A2E35, 0xFFE6E8EB);
        pill(c, X(0.5f), Y(0.665f), r * 0.32f, false, null, 0xFF2A2E35);
        smallKey(c, X(0.285f), Y(0.21f), r * 0.32f, is(KeyEvent.KEYCODE_BUTTON_SELECT) || is(KeyEvent.KEYCODE_BACK), 0xFF2A2E35);
        smallKey(c, X(0.715f), Y(0.21f), r * 0.32f, is(KeyEvent.KEYCODE_BUTTON_START), 0xFF2A2E35);
    }

    /** Manette Xbox : coque arrondie d'une pièce, stick gauche en haut, croix et stick droit en bas. */
    private void xbox(Canvas c) {
        float r = bw * 0.05f;
        boolean generic = family == Family.GENERIC;
        trigger(c, X(0.15f), Y(0.0f), bw * 0.15f, bh * 0.09f, Math.max(lt, is(KeyEvent.KEYCODE_BUTTON_L2) ? 1 : 0), generic ? "L2" : "LT");
        trigger(c, X(0.70f), Y(0.0f), bw * 0.15f, bh * 0.09f, Math.max(rt, is(KeyEvent.KEYCODE_BUTTON_R2) ? 1 : 0), generic ? "R2" : "RT");
        bumper(c, X(0.12f), Y(0.10f), bw * 0.22f, bh * 0.07f, is(KeyEvent.KEYCODE_BUTTON_L1), generic ? "L1" : "LB");
        bumper(c, X(0.66f), Y(0.10f), bw * 0.22f, bh * 0.07f, is(KeyEvent.KEYCODE_BUTTON_R1), generic ? "R1" : "RB");

        Path p = new Path();
        p.moveTo(X(0.22f), Y(0.17f));
        p.cubicTo(X(0.36f), Y(0.15f), X(0.42f), Y(0.21f), X(0.5f), Y(0.21f));
        p.cubicTo(X(0.58f), Y(0.21f), X(0.64f), Y(0.15f), X(0.78f), Y(0.17f));
        p.cubicTo(X(0.92f), Y(0.19f), X(0.97f), Y(0.40f), X(0.99f), Y(0.66f));
        p.cubicTo(X(1.0f), Y(0.90f), X(0.94f), Y(1.0f), X(0.86f), Y(0.97f));
        p.cubicTo(X(0.79f), Y(0.94f), X(0.75f), Y(0.80f), X(0.67f), Y(0.76f));
        p.cubicTo(X(0.58f), Y(0.73f), X(0.42f), Y(0.73f), X(0.33f), Y(0.76f));
        p.cubicTo(X(0.25f), Y(0.80f), X(0.21f), Y(0.94f), X(0.14f), Y(0.97f));
        p.cubicTo(X(0.06f), Y(1.0f), X(0.0f), Y(0.90f), X(0.01f), Y(0.66f));
        p.cubicTo(X(0.03f), Y(0.40f), X(0.08f), Y(0.19f), X(0.22f), Y(0.17f));
        p.close();
        body(c, p, generic ? 0xFF22252B : 0xFF1A1C20, 0xFF4A505A);

        stick(c, X(0.24f), Y(0.40f), r, lx, ly, is(KeyEvent.KEYCODE_BUTTON_THUMBL));
        dpad(c, X(0.375f), Y(0.62f), r * 1.05f, STEEL, 0xFF8A919C);
        stick(c, X(0.625f), Y(0.62f), r, rx, ry, is(KeyEvent.KEYCODE_BUTTON_THUMBR));
        String[] labels = {"A", "B", "X", "Y"};
        if (generic) diamond(c, X(0.76f), Y(0.40f), r, labels, STEEL, 0xFFE6E8EB);
        else diamondColored(c, X(0.76f), Y(0.40f), r, labels,
                new int[] {0xFF5DBB4C, 0xFFE0463E, 0xFF3D8BE0, 0xFFF2C230});
        // Bouton central rond en haut (sans logo), Vue / Menu, Partage au centre.
        face(c, X(0.5f), Y(0.29f), r * 0.55f, is(KeyEvent.KEYCODE_BUTTON_MODE), "", STEEL, 0xFFE6E8EB);
        smallKey(c, X(0.43f), Y(0.42f), r * 0.32f, is(KeyEvent.KEYCODE_BUTTON_SELECT) || is(KeyEvent.KEYCODE_BACK), STEEL);
        smallKey(c, X(0.57f), Y(0.42f), r * 0.32f, is(KeyEvent.KEYCODE_BUTTON_START), STEEL);
        if (!generic) pill(c, X(0.5f), Y(0.50f), r * 0.28f, false, null, STEEL);
    }

    /** Manette Pro Switch : coque plus droite, poignées courtes, ZL/ZR plats, − + en haut, maison et capture. */
    private void proController(Canvas c) {
        float r = bw * 0.05f;
        trigger(c, X(0.14f), Y(0.0f), bw * 0.17f, bh * 0.08f, Math.max(lt, is(KeyEvent.KEYCODE_BUTTON_L2) ? 1 : 0), "ZL");
        trigger(c, X(0.69f), Y(0.0f), bw * 0.17f, bh * 0.08f, Math.max(rt, is(KeyEvent.KEYCODE_BUTTON_R2) ? 1 : 0), "ZR");
        bumper(c, X(0.12f), Y(0.09f), bw * 0.22f, bh * 0.07f, is(KeyEvent.KEYCODE_BUTTON_L1), "L");
        bumper(c, X(0.66f), Y(0.09f), bw * 0.22f, bh * 0.07f, is(KeyEvent.KEYCODE_BUTTON_R1), "R");

        Path p = new Path();
        p.moveTo(X(0.16f), Y(0.17f));
        p.lineTo(X(0.84f), Y(0.17f));
        p.cubicTo(X(0.95f), Y(0.17f), X(0.98f), Y(0.32f), X(0.99f), Y(0.60f));
        p.cubicTo(X(1.0f), Y(0.86f), X(0.95f), Y(0.96f), X(0.87f), Y(0.95f));
        p.cubicTo(X(0.80f), Y(0.94f), X(0.77f), Y(0.82f), X(0.70f), Y(0.78f));
        p.lineTo(X(0.30f), Y(0.78f));
        p.cubicTo(X(0.23f), Y(0.82f), X(0.20f), Y(0.94f), X(0.13f), Y(0.95f));
        p.cubicTo(X(0.05f), Y(0.96f), X(0.0f), Y(0.86f), X(0.01f), Y(0.60f));
        p.cubicTo(X(0.02f), Y(0.32f), X(0.05f), Y(0.17f), X(0.16f), Y(0.17f));
        p.close();
        body(c, p, 0xFF1E2126, 0xFF4A505A);
        // Façade translucide légèrement plus claire, comme le modèle d'origine.
        fill.setColor(0x22FFFFFF);
        c.drawRoundRect(new RectF(X(0.06f), Y(0.21f), X(0.94f), Y(0.74f)), bw * 0.06f, bw * 0.06f, fill);

        stick(c, X(0.23f), Y(0.40f), r, lx, ly, is(KeyEvent.KEYCODE_BUTTON_THUMBL));
        dpad(c, X(0.37f), Y(0.62f), r * 1.0f, STEEL, 0xFF8A919C);
        stick(c, X(0.63f), Y(0.62f), r, rx, ry, is(KeyEvent.KEYCODE_BUTTON_THUMBR));
        // Losange Nintendo : A à droite, B en bas, X en haut, Y à gauche (Android : B, A, Y, X).
        String[] labels = {"B", "A", "Y", "X"};
        diamond(c, X(0.77f), Y(0.40f), r, labels, STEEL, 0xFFE6E8EB);
        smallSign(c, X(0.40f), Y(0.27f), r * 0.32f, is(KeyEvent.KEYCODE_BUTTON_SELECT) || is(KeyEvent.KEYCODE_BACK), "−");
        smallSign(c, X(0.60f), Y(0.27f), r * 0.32f, is(KeyEvent.KEYCODE_BUTTON_START), "+");
        // Capture (carré) et maison (rond).
        fill.setColor(STEEL);
        float q = r * 0.3f;
        c.drawRoundRect(new RectF(X(0.44f) - q, Y(0.40f) - q, X(0.44f) + q, Y(0.40f) + q), q * 0.3f, q * 0.3f, fill);
        face(c, X(0.56f), Y(0.40f), r * 0.36f, is(KeyEvent.KEYCODE_BUTTON_MODE), "⌂", STEEL, 0xFFE6E8EB);
    }

    private void diamond(Canvas c, float x, float y, float r, String[] labels, int steel, int ink) {
        float g = r * 1.0f, br = r * 0.5f;
        face(c, x, y + g, br, is(KeyEvent.KEYCODE_BUTTON_A), labels[0], steel, ink);
        face(c, x + g, y, br, is(KeyEvent.KEYCODE_BUTTON_B), labels[1], steel, ink);
        face(c, x - g, y, br, is(KeyEvent.KEYCODE_BUTTON_X), labels[2], steel, ink);
        face(c, x, y - g, br, is(KeyEvent.KEYCODE_BUTTON_Y), labels[3], steel, ink);
    }

    /** Losange aux lettres de couleur (vert, rouge, bleu, jaune) sur boutons sombres. */
    private void diamondColored(Canvas c, float x, float y, float r, String[] labels, int[] inks) {
        float g = r * 1.0f, br = r * 0.5f;
        face(c, x, y + g, br, is(KeyEvent.KEYCODE_BUTTON_A), labels[0], 0xFF2A2E35, inks[0]);
        face(c, x + g, y, br, is(KeyEvent.KEYCODE_BUTTON_B), labels[1], 0xFF2A2E35, inks[1]);
        face(c, x - g, y, br, is(KeyEvent.KEYCODE_BUTTON_X), labels[2], 0xFF2A2E35, inks[2]);
        face(c, x, y - g, br, is(KeyEvent.KEYCODE_BUTTON_Y), labels[3], 0xFF2A2E35, inks[3]);
    }

    private void smallKey(Canvas c, float x, float y, float r, boolean on, int steel) {
        fill.setColor(on ? Theme.CODEC : steel);
        c.drawRoundRect(new RectF(x - r * 0.7f, y - r, x + r * 0.7f, y + r), r * 0.6f, r * 0.6f, fill);
    }

    private void smallSign(Canvas c, float x, float y, float r, boolean on, String sign) {
        fill.setColor(on ? Theme.CODEC : STEEL);
        c.drawCircle(x, y, r * 1.1f, fill);
        text.setColor(on ? 0xFF0B0D10 : 0xFFE6E8EB);
        text.setTextSize(r * 2f);
        c.drawText(sign, x, y + r * 0.7f, text);
    }

    private void face(Canvas c, float x, float y, float r, boolean on, String label, int steel, int ink) {
        fill.setColor(on ? Theme.CODEC : steel);
        c.drawCircle(x, y, r, fill);
        if (label.isEmpty()) return;
        text.setColor(on ? 0xFF0B0D10 : ink);
        text.setTextSize(r * 1.05f);
        c.drawText(label, x, y + r * 0.38f, text);
    }

    private void pill(Canvas c, float x, float y, float r, boolean on, String label, int steel) {
        fill.setColor(on ? Theme.CODEC : steel);
        c.drawRoundRect(new RectF(x - r * 1.6f, y - r * 0.6f, x + r * 1.6f, y + r * 0.6f), r * 0.6f, r * 0.6f, fill);
    }

    private void stick(Canvas c, float x, float y, float r, float ax, float ay, boolean click) {
        fill.setColor(0xFF0C0E11);
        c.drawCircle(x, y, r * 1.3f, fill);
        boolean moved = Math.abs(ax) > 0.15f || Math.abs(ay) > 0.15f;
        line.setColor(moved ? Theme.CODEC : 0xFF4A505A);
        c.drawCircle(x, y, r * 1.3f, line);
        float cx = x + ax * r * 0.55f, cy = y + ay * r * 0.55f;
        fill.setColor(click ? Theme.CODEC : 0xFF2A2E35);
        c.drawCircle(cx, cy, r * 0.85f, fill);
        line.setColor(0xFF555C66);
        c.drawCircle(cx, cy, r * 0.6f, line);
    }

    private void dpad(Canvas c, float x, float y, float r, int steel, int edge) {
        boolean up = is(KeyEvent.KEYCODE_DPAD_UP) || hatY < -0.5f, dn = is(KeyEvent.KEYCODE_DPAD_DOWN) || hatY > 0.5f;
        boolean lf = is(KeyEvent.KEYCODE_DPAD_LEFT) || hatX < -0.5f, rg = is(KeyEvent.KEYCODE_DPAD_RIGHT) || hatX > 0.5f;
        float a = r * 0.36f;
        fill.setColor(steel);
        c.drawRoundRect(new RectF(x - a, y - r, x + a, y + r), a * 0.4f, a * 0.4f, fill);
        c.drawRoundRect(new RectF(x - r, y - a, x + r, y + a), a * 0.4f, a * 0.4f, fill);
        fill.setColor(Theme.CODEC);
        if (up) c.drawRect(x - a, y - r, x + a, y - a, fill);
        if (dn) c.drawRect(x - a, y + a, x + a, y + r, fill);
        if (lf) c.drawRect(x - r, y - a, x - a, y + a, fill);
        if (rg) c.drawRect(x + a, y - a, x + r, y + a, fill);
        line.setColor(edge);
        line.setStrokeWidth(0.8f * dp);
        c.drawCircle(x, y, a * 0.5f, line);
        line.setStrokeWidth(1.5f * dp);
    }

    private void bumper(Canvas c, float x, float y, float w, float h, boolean on, String label) {
        fill.setColor(on ? Theme.CODEC : STEEL);
        c.drawRoundRect(new RectF(x, y, x + w, y + h), h / 2, h / 2, fill);
        text.setColor(on ? 0xFF0B0D10 : 0xFFE6E8EB);
        text.setTextSize(h * 0.7f);
        c.drawText(label, x + w / 2, y + h * 0.75f, text);
    }

    private void trigger(Canvas c, float x, float y, float w, float h, float v, String label) {
        RectF rr = new RectF(x, y, x + w, y + h);
        fill.setColor(STEEL);
        c.drawRoundRect(rr, h / 3, h / 3, fill);
        if (v > 0.02f) {
            fill.setColor(Theme.CODEC);
            c.save();
            c.clipRect(x, y, x + w * Math.min(1f, v), y + h);
            c.drawRoundRect(rr, h / 3, h / 3, fill);
            c.restore();
        }
        text.setColor(v > 0.5f ? 0xFF0B0D10 : 0xFFE6E8EB);
        text.setTextSize(h * 0.7f);
        c.drawText(label, x + w / 2, y + h * 0.75f, text);
    }
}
