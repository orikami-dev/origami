package com.hideo.foldplay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * Une touche de la manette du bas, dessinée à la main. En jeu, elle signale les appuis et
 * relâchements (la croix gère aussi les diagonales et le glissement d'une direction à l'autre).
 */
final class ControlView extends View {
    interface Listener {
        void onDown(String key);

        void onUp(String key);

        /** Joysticks (-1..1, vers le bas = positif) et pavé tactile (0..1 dans le pavé). */
        void onAxis(String key, float x, float y);
    }

    final ControlLayout.Control control;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path();
    private final float dp;
    private final Crane.Logo logo = new Crane.Logo();
    private Listener listener;
    private boolean haptics = true;
    private boolean pressed;
    private boolean editing;
    private boolean picked;
    /** Directions de la croix actuellement enfoncées. */
    private boolean up, down, left, right;
    /** Position du joystick (-1..1) ou du doigt sur le pavé (0..1). */
    private float ax, ay;

    ControlView(Context ctx, ControlLayout.Control control) {
        super(ctx);
        this.control = control;
        dp = ctx.getResources().getDisplayMetrics().density;
        text.setTextAlign(Paint.Align.CENTER);
        ring.setStyle(Paint.Style.STROKE);
        setContentDescription(control.type);
        // Home est un bouton : la grue brille régulièrement, dès le lancement du jeu (0.62).
        if (control.type.equals("HOME")) logo.animate(this, 6000);
    }

    void setListener(Listener l, boolean haptics) {
        this.listener = l;
        this.haptics = haptics;
    }

    void setEditing(boolean editing, boolean picked) {
        this.editing = editing;
        this.picked = picked;
        invalidate();
    }

    // ------------------------------------------------------------------ style

    private int steel = Theme.STEEL;
    private int panelHi = Theme.PANEL_HI;
    private int accent = Theme.CODEC;
    /** Habillage « codec » (relief, liserés, halo) ; sinon aplats simples. */
    private boolean skin = true;
    private final Paint gloss = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** Couleurs et opacité choisies dans l'éditeur (la touche peut avoir sa propre couleur). */
    /** Vrai si la disposition a des gâchettes L2/R2 : sinon les tranches s'appellent L et R. */
    private boolean triggers = true;

    void setStyle(ControlLayout l) {
        triggers = false;
        for (ControlLayout.Control k : l.controls) if (k.type.equals("L2") || k.type.equals("R2")) triggers = true;
        int own = Theme.keyColor(control);
        steel = own != 0 ? own : Theme.padButton(l);
        panelHi = Theme.darken(steel, 0.3f);
        accent = own != 0 && control.type.equals("A") ? own : Theme.padAccent(l);
        setAlpha(Math.max(0.1f, l.buttonAlpha / 100f));
        skin = !"plain".equals(l.skin);
        invalidate();
    }

    /** Remplissage d'une forme : dégradé vertical clair → sombre avec l'habillage, aplat sinon. */
    private Paint body(int color, float top, float bottom) {
        fill.setShader(null);
        fill.setColor(color);
        if (skin) {
            fill.setShader(new android.graphics.LinearGradient(0, top, 0, bottom,
                    Theme.lighten(color, 0.14f), Theme.darken(color, 0.22f), android.graphics.Shader.TileMode.CLAMP));
        }
        return fill;
    }

    private void plain(int color) {
        fill.setShader(null);
        fill.setColor(color);
    }

    /** Liseré clair en haut, ombre en bas : la touche semble bombée. */
    private void rimRound(Canvas c, float cx, float cy, float rad) {
        if (!skin) return;
        ring.setShader(null);
        ring.setStrokeWidth(1.2f * dp);
        ring.setColor(0x33FFFFFF);
        r.set(cx - rad + dp, cy - rad + dp, cx + rad - dp, cy + rad - dp);
        c.drawArc(r, 200, 140, false, ring);
        ring.setColor(0x66000000);
        c.drawArc(r, 20, 140, false, ring);
    }

    private void rimRect(Canvas c, RectF box, float rx) {
        if (!skin) return;
        ring.setShader(null);
        ring.setStrokeWidth(1f * dp);
        ring.setColor(0x26FFFFFF);
        r.set(box.left + dp / 2, box.top + dp / 2, box.right - dp / 2, box.bottom - dp / 2);
        c.save();
        c.clipRect(box.left, box.top, box.right, box.top + box.height() / 2);
        c.drawRoundRect(r, rx, rx, ring);
        c.restore();
        ring.setColor(0x55000000);
        c.save();
        c.clipRect(box.left, box.top + box.height() / 2, box.right, box.bottom);
        c.drawRoundRect(r, rx, rx, ring);
        c.restore();
    }

    /** Halo de la couleur d'accent quand on appuie (et autour de A au repos). */
    private void halo(Canvas c, float cx, float cy, float rad, float strength) {
        if (!skin) return;
        gloss.setShader(new android.graphics.RadialGradient(cx, cy, rad * 1.18f,
                new int[] {(accent & 0x00FFFFFF) | (Math.round(110 * strength) << 24), accent & 0x00FFFFFF},
                new float[] {0.75f, 1f}, android.graphics.Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, rad * 1.18f, gloss);
        gloss.setShader(null);
    }

    // ------------------------------------------------------------------ dessin

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        String t = control.type;
        if (control.cap != null && control.cap.startsWith("@c")) drawFace(c, w, h, t);
        else if (t.equals("DPAD")) drawDpad(c, w, h);
        else if (t.equals("A") || t.equals("B") || t.equals("X") || t.equals("Y")) drawFace(c, w, h, t);
        else if (t.equals("L3") || t.equals("R3")) drawFace(c, w, h, t);
        else if (t.equals("HOME")) drawHome(c, w, h);
        else if ((t.equals("L") || t.equals("R") || t.equals("L2") || t.equals("R2")) && control.w == control.h) {
            // Gâchette carrée = bouton rond (panneau d'arcade de la disposition 2D).
            drawFace(c, w, h, t.length() == 1 ? t + "1" : t);
        }
        else if (t.startsWith("STICK")) drawStick(c, w, h);
        else if (t.equals("TOUCHPAD")) drawPad(c, w, h);
        else if (t.equals("FAST") || t.equals("ASPECT")) drawCornerKey(c, w, h, t);
        else if (ControlLayout.pill(t)) drawPill(c, w, h, control.cap != null ? pillCap(control.cap) : pillLabel(t));
        else drawShoulder(c, w, h, t);

        if (editing) {
            ring.setColor(picked ? accent : 0x55FFFFFF);
            ring.setStrokeWidth((picked ? 2.5f : 1f) * dp);
            r.set(1, 1, w - 1, h - 1);
            c.drawRoundRect(r, 6 * dp, 6 * dp, ring);
            if (picked) {
                // Poignées de redimensionnement aux quatre coins.
                plain(accent);
                float s = Math.min(12 * dp, 0.3f * Math.min(w, h));
                path.reset();
                path.moveTo(w, h - s);
                path.lineTo(w, h);
                path.lineTo(w - s, h);
                path.close();
                path.moveTo(0, h - s);
                path.lineTo(0, h);
                path.lineTo(s, h);
                path.close();
                path.moveTo(0, s);
                path.lineTo(0, 0);
                path.lineTo(s, 0);
                path.close();
                path.moveTo(w, s);
                path.lineTo(w, 0);
                path.lineTo(w - s, 0);
                path.close();
                c.drawPath(path, fill);
            }
        }
    }

    private static String pillCap(String cap) {
        if (cap.equals("@-")) return "−";
        if (cap.equals("@+")) return "+";
        return cap;
    }

    private static String pillLabel(String t) {
        if (t.equals("FAST")) return "≫ ×2";
        if (t.equals("QSAVE")) return "SAVE";
        if (t.equals("QLOAD")) return "LOAD";
        if (t.equals("ASPECT")) return ScreenFormat.caption;
        return t;
    }

    private void drawStick(Canvas c, float w, float h) {
        float rad = Math.min(w, h) / 2f - dp;
        c.drawCircle(w / 2, h / 2, rad, body(Theme.darken(panelHi, 0.15f), h / 2 + rad, h / 2 - rad));
        ring.setShader(null);
        ring.setColor(pressed ? accent : steel);
        ring.setStrokeWidth(2 * dp);
        c.drawCircle(w / 2, h / 2, rad - dp, ring);
        if (skin) {
            // Crans des 8 directions sur la couronne.
            ring.setStrokeWidth(1.5f * dp);
            ring.setColor(0x40FFFFFF);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 4 * i;
                float c1 = rad * 0.82f;
                float c2 = rad * 0.92f;
                c.drawLine(w / 2 + (float) Math.cos(a) * c1, h / 2 + (float) Math.sin(a) * c1,
                        w / 2 + (float) Math.cos(a) * c2, h / 2 + (float) Math.sin(a) * c2, ring);
            }
        }
        float knob = rad * 0.42f;
        float travel = rad - knob;
        float kx = w / 2 + ax * travel;
        float ky = h / 2 + ay * travel;
        if (pressed) halo(c, kx, ky, knob, 0.8f);
        c.drawCircle(kx, ky, knob, body(pressed ? Theme.lighten(steel, 0.25f) : steel, ky - knob, ky + knob));
        rimRound(c, kx, ky, knob);
        if (skin) {
            ring.setColor(0x30000000);
            ring.setStrokeWidth(1.5f * dp);
            c.drawCircle(kx, ky, knob * 0.72f, ring);
        }
        text.setTypeface(Fonts.logo(getContext()));
        text.setTextSize(knob * 0.7f);
        text.setColor(Theme.MUTED);
        c.drawText(control.type.endsWith("L") ? "L" : "R", w / 2 + ax * travel,
                h / 2 + ay * travel - (text.descent() + text.ascent()) / 2, text);
    }

    private void drawPad(Canvas c, float w, float h) {
        r.set(dp, dp, w - dp, h - dp);
        c.drawRoundRect(r, 14 * dp, 14 * dp, body(panelHi, r.top, r.bottom));
        fill.setShader(null);
        ring.setColor(steel);
        ring.setStrokeWidth(1.5f * dp);
        c.drawRoundRect(r, 14 * dp, 14 * dp, ring);
        text.setTypeface(Fonts.wide(getContext()));
        text.setTextSize(Math.min(h * 0.12f, 12 * dp));
        text.setColor(Theme.MUTED);
        c.drawText("TACTILE", w / 2, h / 2 - (text.descent() + text.ascent()) / 2, text);
        if (pressed) {
            plain(accent);
            fill.setAlpha(140);
            c.drawCircle(ax * w, ay * h, 10 * dp, fill);
            fill.setAlpha(255);
        }
    }

    private void drawFace(Canvas c, float w, float h, String t) {
        boolean primary = t.equals("A");
        float rad = Math.min(w, h) / 2f - dp;
        if (skin) rad = rad / 1.18f;
        int base = pressed ? Theme.lighten(primary ? accent : steel, 0.25f) : primary ? accent : steel;
        if (pressed || primary) halo(c, w / 2, h / 2, rad, pressed ? 1f : 0.45f);
        c.drawCircle(w / 2, h / 2, rad, body(base, h / 2 - rad, h / 2 + rad));
        rimRound(c, w / 2, h / 2, rad);
        int ink = primary ? Theme.readableOn(accent) : Theme.readableOn(steel);
        String cap = control.cap != null ? control.cap : t;
        if (cap.startsWith("@")) {
            glyph(c, cap, w / 2, h / 2, rad * 0.46f, ink);
            return;
        }
        text.setTypeface(Fonts.logo(getContext()));
        text.setTextSize(rad * (cap.length() > 1 ? 0.7f : 0.9f));
        text.setColor(ink);
        c.drawText(cap, w / 2, h / 2 - (text.descent() + text.ascent()) / 2, text);
    }

    /**
     * Formes géométriques simples tracées à la main (pas de police, pas de couleur d'origine) :
     * @x croix, @o rond, @s carré, @t triangle, @cu/@cd/@cl/@cr flèche des boutons C.
     */
    private void glyph(Canvas c, String g, float cx, float cy, float a, int color) {
        ring.setShader(null);
        ring.setColor(color);
        ring.setStrokeWidth(Math.max(1.5f * dp, a * 0.24f));
        ring.setStrokeCap(Paint.Cap.ROUND);
        switch (g) {
            case "@x":
                c.drawLine(cx - a * 0.8f, cy - a * 0.8f, cx + a * 0.8f, cy + a * 0.8f, ring);
                c.drawLine(cx + a * 0.8f, cy - a * 0.8f, cx - a * 0.8f, cy + a * 0.8f, ring);
                break;
            case "@o":
                c.drawCircle(cx, cy, a * 0.85f, ring);
                break;
            case "@s":
                r.set(cx - a * 0.75f, cy - a * 0.75f, cx + a * 0.75f, cy + a * 0.75f);
                c.drawRect(r, ring);
                break;
            case "@t":
                path.reset();
                path.moveTo(cx, cy - a * 0.9f);
                path.lineTo(cx + a * 0.95f, cy + a * 0.7f);
                path.lineTo(cx - a * 0.95f, cy + a * 0.7f);
                path.close();
                c.drawPath(path, ring);
                break;
            default:
                plain(color);
                triangle(c, cx, cy, a * 0.75f, g.equals("@cu") ? 0 : g.equals("@cr") ? 1 : g.equals("@cd") ? 2 : 3);
                break;
        }
    }

    private void drawHome(Canvas c, float w, float h) {
        float rad = Math.min(w, h) / 2f - 2 * dp;
        if (skin) rad = rad / 1.18f;
        float glow = logo.shine >= 0f && logo.shine <= 1f ? (float) Math.sin(Math.PI * logo.shine) : 0f;
        halo(c, w / 2, h / 2, rad, pressed ? 1f : 0.3f + 0.5f * glow);
        c.drawCircle(w / 2, h / 2, rad, body(pressed ? panelHi : Theme.BG, h / 2 - rad, h / 2 + rad));
        ring.setColor(accent);
        ring.setStrokeWidth(2 * dp);
        c.drawCircle(w / 2, h / 2, rad, ring);
        // La grue occupe ~80 % du diamètre, centrée, comme l'emblème d'une manette de salon.
        float size = rad * 1.6f;
        c.save();
        path.reset();
        path.addCircle(w / 2, h / 2, rad - dp, Path.Direction.CW);
        c.clipPath(path);
        logo.setBounds(Math.round(w / 2 - size / 2), Math.round(h / 2 - size / 2),
                Math.round(w / 2 + size / 2), Math.round(h / 2 + size / 2));
        logo.accent = Theme.logo(accent);
        logo.draw(c);
        c.restore();
    }

    private void drawPill(Canvas c, float w, float h, String t) {
        r.set(dp, dp, w - dp, h - dp);
        RectF box = new RectF(r);
        c.drawRoundRect(r, r.height() / 2, r.height() / 2, body(pressed ? steel : panelHi, r.top, r.bottom));
        rimRect(c, box, box.height() / 2);
        text.setTypeface(Fonts.wide(getContext()));
        text.setTextSize(Math.min(r.height() * 0.38f, r.width() / (t.length() * 0.95f)));
        text.setColor(Theme.MUTED);
        c.drawText(t, w / 2, h / 2 - (text.descent() + text.ascent()) / 2, text);
    }

    /**
     * Format d'image et Accélérer (0.62.3) : petite touche ronde à icône, sans texte, pour un coin
     * de l'écran. Accélérer : deux flèches d'avance rapide ; Format : un cadre aux quatre angles.
     */
    private void drawCornerKey(Canvas c, float w, float h, String t) {
        r.set(dp, dp, w - dp, h - dp);
        // Elle épouse le coin arrondi de la manette (0.62.5) : grand arrondi côté coin (Format en
        // haut à gauche, Accélérer en haut à droite), petits arrondis vers l'intérieur.
        boolean left = t.equals("ASPECT");
        float big = Math.min(r.width(), r.height()) * 0.62f, small = 6 * dp;
        float[] radii = left ? new float[] {big, big, small, small, small, small, small, small}
                : new float[] {small, small, big, big, small, small, small, small};
        path.reset();
        path.addRoundRect(r, radii, android.graphics.Path.Direction.CW);
        c.drawPath(path, body(pressed ? steel : panelHi, r.top, r.bottom));
        ring.setShader(null);
        ring.setColor(0x4DFFFFFF);
        ring.setStrokeWidth(2f * dp);
        c.drawPath(path, ring);
        // Pictogramme centré dans le rond (0.65).
        float u = Math.min(w, h) * 0.22f, cx = w / 2, cy = h / 2;
        plain(Theme.MUTED);
        path.reset();
        if (t.equals("FAST")) {
            for (int i = 0; i < 2; i++) {
                float x0 = cx - u + i * u;
                path.moveTo(x0, cy - u * 0.8f);
                path.lineTo(x0 + u, cy);
                path.lineTo(x0, cy + u * 0.8f);
                path.close();
            }
            c.drawPath(path, fill);
        } else {
            ring.setColor(Theme.MUTED);
            ring.setStrokeWidth(Math.max(1.5f * dp, u * 0.22f));
            float a = u * 1.05f, b = u * 0.75f, l = u * 0.5f;
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sy = -1; sy <= 1; sy += 2) {
                    path.moveTo(cx + sx * a, cy + sy * (b - l));
                    path.lineTo(cx + sx * a, cy + sy * b);
                    path.lineTo(cx + sx * (a - l), cy + sy * b);
                }
            }
            c.drawPath(path, ring);
        }
    }

    /** Haricot (0.62.3) : rayon de courbure et épaisseur en fractions de la largeur, demi-angle en degrés. */
    private float beanR, beanTh, beanPhi;
    private final android.graphics.RectF arcBox = new android.graphics.RectF();

    void setBean(float rOverW, float thOverW, float phiDeg) {
        if (rOverW == beanR && thOverW == beanTh && phiDeg == beanPhi) return;
        beanR = rOverW;
        beanTh = thOverW;
        beanPhi = phiDeg;
        invalidate();
    }

    /** Gâchette en haricot, façon X et Y de la GameCube : une bande incurvée aux bouts arrondis. */
    private void drawBean(Canvas c, float w, float h, String t) {
        float R = beanR * w, th = beanTh * w, phi = beanPhi;
        float ro = R + th / 2, ri = R - th / 2;
        float cx = w / 2, cy = ro;                              // centre du groupe, sous la bande
        path.reset();
        arcBox.set(cx - ro, cy - ro, cx + ro, cy + ro);
        path.arcTo(arcBox, -90 - phi, 2 * phi, true);
        double e = Math.toRadians(-90 + phi);
        float ex = cx + (float) (R * Math.cos(e)), ey = cy + (float) (R * Math.sin(e));
        arcBox.set(ex - th / 2, ey - th / 2, ex + th / 2, ey + th / 2);
        path.arcTo(arcBox, -90 + phi, 180, false);
        arcBox.set(cx - ri, cy - ri, cx + ri, cy + ri);
        path.arcTo(arcBox, -90 + phi, -2 * phi, false);
        double sA = Math.toRadians(-90 - phi);
        float sx = cx + (float) (R * Math.cos(sA)), sy = cy + (float) (R * Math.sin(sA));
        arcBox.set(sx - th / 2, sy - th / 2, sx + th / 2, sy + th / 2);
        path.arcTo(arcBox, 90 - phi, 180, false);
        path.close();
        body(pressed ? Theme.lighten(steel, 0.25f) : steel, 0, h);
        c.drawPath(path, fill);
        ring.setShader(null);
        ring.setStrokeWidth(1.2f * dp);
        ring.setColor(pressed ? accent : 0x30FFFFFF);
        if (skin) {
            c.save();
            c.clipRect(0, 0, w, th * 0.45f);
            c.drawPath(path, ring);
            c.restore();
        }
        text.setTypeface(Fonts.logo(getContext()));
        text.setTextSize(Math.min(th * 0.5f, 17 * dp));
        text.setColor(Theme.TEXT);
        String cap = control.cap != null ? control.cap
                : t.equals("L") ? (triggers ? "L1" : "L") : t.equals("R") ? (triggers ? "R1" : "R") : t;
        c.drawText(cap, cx, th / 2 - (text.descent() + text.ascent()) / 2, text);
    }

    private void drawShoulder(Canvas c, float w, float h, String t) {
        if (beanR > 0) {
            drawBean(c, w, h, t);
            return;
        }
        body(pressed ? Theme.lighten(steel, 0.25f) : steel, dp, h - dp);
        boolean leftSide = t.startsWith("L");
        float big = Math.min(h * 0.6f, 22 * dp);
        float small = 6 * dp;
        float[] radii = leftSide
                ? new float[] {big, big, small, small, small, small, small, small}
                : new float[] {small, small, big, big, small, small, small, small};
        r.set(dp, dp, w - dp, h - dp);
        path.reset();
        path.addRoundRect(r, radii, Path.Direction.CW);
        c.drawPath(path, fill);
        if (skin) {
            // Liseré lumineux sur le bord haut, comme une gâchette moulée.
            ring.setShader(null);
            ring.setStrokeWidth(1.2f * dp);
            ring.setColor(pressed ? accent : 0x30FFFFFF);
            c.save();
            c.clipRect(0, 0, w, h * 0.35f);
            c.drawPath(path, ring);
            c.restore();
        }
        text.setTypeface(Fonts.logo(getContext()));
        text.setTextSize(Math.min(h * 0.45f, 18 * dp));
        text.setColor(Theme.TEXT);
        // L1/R1 seulement s'il y a des L2/R2 ; sur les manettes d'avant (une tranche par côté), L et R.
        String cap = control.cap != null ? control.cap
                : t.equals("L") ? (triggers ? "L1" : "L") : t.equals("R") ? (triggers ? "R1" : "R") : t;
        c.drawText(cap, w / 2, h / 2 - (text.descent() + text.ascent()) / 2, text);
    }

    private void drawDpad(Canvas c, float w, float h) {
        float s = Math.min(w, h);
        float ox = (w - s) / 2;
        float oy = (h - s) / 2;
        float k = s / 3f;
        float g = 1.5f * dp;
        drawArm(c, ox + k, oy + g, ox + 2 * k, oy + k, up);
        drawArm(c, ox + k, oy + 2 * k, ox + 2 * k, oy + s - g, down);
        drawArm(c, ox + g, oy + k, ox + k, oy + 2 * k, left);
        drawArm(c, ox + 2 * k, oy + k, ox + s - g, oy + 2 * k, right);
        plain(panelHi);
        c.drawRect(ox + k, oy + k, ox + 2 * k, oy + 2 * k, fill);
        if (skin) {
            // Creux central de la croix.
            plain(Theme.darken(panelHi, 0.35f));
            c.drawCircle(ox + 1.5f * k, oy + 1.5f * k, k * 0.28f, fill);
        }
        plain(Theme.TEXT);
        float a = k * 0.22f;
        triangle(c, ox + 1.5f * k, oy + 0.5f * k, a, 0);
        triangle(c, ox + 1.5f * k, oy + 2.5f * k, a, 2);
        triangle(c, ox + 0.5f * k, oy + 1.5f * k, a, 3);
        triangle(c, ox + 2.5f * k, oy + 1.5f * k, a, 1);
    }

    private void drawArm(Canvas c, float l, float t, float rr, float b, boolean on) {
        r.set(l, t, rr, b);
        RectF box = new RectF(r);
        c.drawRoundRect(r, 6 * dp, 6 * dp, body(on ? Theme.lighten(steel, 0.3f) : panelHi, t, b));
        rimRect(c, box, 6 * dp);
        if (on && skin) {
            ring.setShader(null);
            ring.setColor(accent);
            ring.setStrokeWidth(1.5f * dp);
            c.drawRoundRect(box, 6 * dp, 6 * dp, ring);
        }
    }

    /** Flèche : 0 haut, 1 droite, 2 bas, 3 gauche. */
    private void triangle(Canvas c, float cx, float cy, float a, int dir) {
        path.reset();
        switch (dir) {
            case 0:
                path.moveTo(cx, cy - a);
                path.lineTo(cx + a, cy + a * 0.7f);
                path.lineTo(cx - a, cy + a * 0.7f);
                break;
            case 1:
                path.moveTo(cx + a, cy);
                path.lineTo(cx - a * 0.7f, cy + a);
                path.lineTo(cx - a * 0.7f, cy - a);
                break;
            case 2:
                path.moveTo(cx, cy + a);
                path.lineTo(cx - a, cy - a * 0.7f);
                path.lineTo(cx + a, cy - a * 0.7f);
                break;
            default:
                path.moveTo(cx - a, cy);
                path.lineTo(cx + a * 0.7f, cy - a);
                path.lineTo(cx + a * 0.7f, cy + a);
                break;
        }
        path.close();
        c.drawPath(path, fill);
    }

    // ------------------------------------------------------------------ toucher (en jeu)

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (editing || listener == null) return false;
        int action = e.getActionMasked();
        boolean release = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
        if (control.type.equals("DPAD")) {
            dpadTouch(e.getX(), e.getY(), release);
            return true;
        }
        if (control.type.startsWith("STICK")) {
            stickTouch(e.getX(), e.getY(), action, release);
            return true;
        }
        if (control.type.equals("TOUCHPAD")) {
            ax = Math.max(0f, Math.min(1f, e.getX() / getWidth()));
            ay = Math.max(0f, Math.min(1f, e.getY() / getHeight()));
            listener.onAxis(control.type, ax, ay);
        }
        if (Theme.LATCH && (control.type.equals("L2") || control.type.equals("R2"))) {
            // Gâchette à bascule (Accessibilité) : un appui l'enfonce, le suivant la relâche.
            if (action == MotionEvent.ACTION_DOWN) {
                pressed = !pressed;
                DebugLog.event("[pad] gâchette à bascule " + control.type + " -> " + (pressed ? "enfoncée" : "relâchée"));
                if (haptics) Haptics.tap(this);
                if (pressed) listener.onDown(control.type);
                else listener.onUp(control.type);
                invalidate();
            }
            return true;
        }
        if (action == MotionEvent.ACTION_DOWN) {
            pressed = true;
            if (haptics) Haptics.tap(this);
            listener.onDown(control.type);
            invalidate();
        } else if (release && pressed) {
            pressed = false;
            listener.onUp(control.type);
            invalidate();
        }
        return true;
    }

    private long lastStickDown;
    private boolean stickClick;

    private void stickTouch(float x, float y, int action, boolean release) {
        if (action == MotionEvent.ACTION_DOWN) {
            pressed = true;
            if (haptics) Haptics.tap(this);
            // Double appui sur le joystick = clic (L3 à gauche, R3 à droite), tenu tant que le doigt reste.
            long now = android.os.SystemClock.uptimeMillis();
            if (now - lastStickDown < 300) {
                stickClick = true;
                if (haptics) Haptics.tap(this);
                listener.onDown(control.type.equals("STICK_R") ? "R3" : "L3");
            }
            lastStickDown = now;
        }
        if (release) {
            pressed = false;
            ax = ay = 0;
            if (stickClick) {
                stickClick = false;
                listener.onUp(control.type.equals("STICK_R") ? "R3" : "L3");
            }
        } else {
            // Course réduite (Accessibilité) : un petit geste suffit pour pousser à fond.
            float rad = Math.min(getWidth(), getHeight()) / 2f * 0.58f * Theme.STICK_REACH;
            float dx = (x - getWidth() / 2f) / rad;
            float dy = (y - getHeight() / 2f) / rad;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len > 1) {
                dx /= len;
                dy /= len;
            }
            ax = dx;
            ay = dy;
        }
        listener.onAxis(control.type, ax, ay);
        invalidate();
    }

    private void dpadTouch(float x, float y, boolean release) {
        boolean nu = false, nd = false, nl = false, nr = false;
        if (!release) {
            float dx = x - getWidth() / 2f;
            float dy = y - getHeight() / 2f;
            float dead = Math.min(getWidth(), getHeight()) * 0.12f;
            // Zone de diagonale : quand les deux axes sont franchement engagés.
            if (Math.abs(dx) > dead || Math.abs(dy) > dead) {
                double ang = Math.toDegrees(Math.atan2(dy, dx));
                nr = ang > -67.5 && ang < 67.5;
                nd = ang > 22.5 && ang < 157.5;
                nl = ang > 112.5 || ang < -112.5;
                nu = ang < -22.5 && ang > -157.5;
            }
        }
        boolean changed = false;
        if (nu != up) { up = nu; fire("UP", nu); changed = true; }
        if (nd != down) { down = nd; fire("DOWN", nd); changed = true; }
        if (nl != left) { left = nl; fire("LEFT", nl); changed = true; }
        if (nr != right) { right = nr; fire("RIGHT", nr); changed = true; }
        if (changed) invalidate();
    }

    private void fire(String key, boolean on) {
        if (on) {
            if (haptics) Haptics.tap(this);
            listener.onDown(key);
        } else {
            listener.onUp(key);
        }
    }
}
