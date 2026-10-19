package com.hideo.foldplay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import java.util.Random;

/**
 * Une cartouche dessinée à la main : coque teintée de la couleur de la console, étiquette avec
 * la jaquette si elle existe, sinon un sprite pixel généré à partir du titre (toujours le même).
 */
final class CartridgeView extends View implements CoverLoader.Target {
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pixel = new Paint();
    private final Paint ridge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint badge = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint title = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final RectF lr = new RectF();
    private final Rect src = new Rect();
    private final float dp;

    private Game game;
    private Bitmap cover;
    private boolean[][] sprite;
    private int spriteA;
    private int spriteB;
    private boolean selected;
    private boolean favorite;
    private boolean showTitle = true;

    CartridgeView(Context ctx) {
        super(ctx);
        dp = ctx.getResources().getDisplayMetrics().density;
        ridge.setColor(0x33000000);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(3 * dp);
        ring.setColor(Theme.CODEC);
        badgeBg.setColor(0xCC0E0F13);
        badge.setColor(Color.WHITE);
        badge.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        title.setColor(Color.WHITE);
        title.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
    }

    void setGame(Game g, boolean fav) {
        if (g != game) {
            game = g;
            buildSprite();
        }
        favorite = fav;
        invalidate();
    }

    @Override
    public void setCover(Bitmap b) {
        cover = b;
        invalidate();
    }

    private boolean keepAspect;

    /** Garde les proportions d'une cartouche (largeur = 0,8 × hauteur) au lieu de s'étirer. */
    void setKeepAspect(boolean keep) {
        keepAspect = keep;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        if (!keepAspect) return;
        int w = getMeasuredWidth();
        int h = getMeasuredHeight();
        if (h * 0.8f > w) h = (int) (w / 0.8f);
        else w = (int) (h * 0.8f);
        setMeasuredDimension(w, h);
    }

    void setShowTitle(boolean show) {
        showTitle = show;
        invalidate();
    }

    void setPicked(boolean picked) {
        if (selected != picked) {
            selected = picked;
            invalidate();
        }
    }

    private void buildSprite() {
        if (game == null) return;
        Random rnd = new Random(game.title.hashCode() * 31L + game.system.id.hashCode());
        sprite = new boolean[8][8];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 4; x++) {
                boolean on = rnd.nextFloat() < (y == 0 || y == 7 ? 0.3f : 0.55f);
                sprite[y][x] = on;
                sprite[y][7 - x] = on;
            }
        }
        spriteA = Theme.lighten(game.system.color, 0.55f);
        spriteB = rnd.nextBoolean() ? Theme.YELLOW : Color.WHITE;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (game == null) return;
        float w = getWidth();
        float h = getHeight();
        float pad = 4 * dp;
        r.set(pad, pad, w - pad, h - pad);
        float radius = Math.min(w, h) * 0.07f;

        if (game.isApp()) {
            drawTile(c, radius);
            return;
        }
        body.setColor(Theme.darken(game.system.color, 0.45f));
        c.drawRoundRect(r, radius, radius, body);

        // Rainures en haut de la cartouche.
        float rw = r.width();
        for (int i = 0; i < 5; i++) {
            float x = r.left + rw * 0.3f + i * rw * 0.1f;
            c.drawRect(x, r.top + 3 * dp, x + rw * 0.04f, r.top + r.height() * 0.07f, ridge);
        }

        float titleZone = showTitle ? r.height() * 0.24f : 0;
        lr.set(r.left + rw * 0.09f, r.top + r.height() * 0.1f,
                r.right - rw * 0.09f, r.bottom - titleZone - r.height() * 0.05f);

        if (cover != null) {
            drawCover(c);
        } else {
            label.setShader(new LinearGradient(0, lr.top, 0, lr.bottom,
                    Theme.lighten(game.system.color, 0.15f), Theme.darken(game.system.color, 0.2f),
                    Shader.TileMode.CLAMP));
            c.drawRoundRect(lr, radius * 0.6f, radius * 0.6f, label);
            label.setShader(null);
            drawSprite(c);
        }

        // Pastille console.
        badge.setTextSize(Math.max(9 * dp, lr.width() * 0.11f));
        String tag = game.system.shortName;
        float bw = badge.measureText(tag) + 8 * dp;
        float bh = badge.getTextSize() + 6 * dp;
        c.drawRoundRect(lr.left + 4 * dp, lr.top + 4 * dp, lr.left + 4 * dp + bw, lr.top + 4 * dp + bh,
                4 * dp, 4 * dp, badgeBg);
        c.drawText(tag, lr.left + 8 * dp, lr.top + 4 * dp + bh - 4 * dp - badge.descent() * 0.5f, badge);
        if (favorite) {
            badge.setColor(Theme.YELLOW);
            c.drawText("★", lr.right - badge.getTextSize() - 4 * dp, lr.top + bh, badge);
            badge.setColor(Color.WHITE);
        }

        if (showTitle) {
            title.setTextSize(Math.max(11 * dp, Math.min(15 * dp, rw * 0.085f)));
            CharSequence t = TextUtils.ellipsize(game.title, title, lr.width(), TextUtils.TruncateAt.END);
            c.drawText(t, 0, t.length(), lr.left, r.bottom - titleZone * 0.4f, title);
        }

        if (selected) c.drawRoundRect(r, radius, radius, ring);
    }

    private Icons glyph;
    private String glyphName;

    /**
     * Application ou action (0.60.5) : une tuile aux couleurs de la catégorie plutôt qu'une
     * cartouche, avec l'icône de l'app installée (jamais redessinée) ou un pictogramme maison.
     */
    private void drawTile(Canvas c, float radius) {
        float rad = Math.min(r.width(), r.height()) * 0.18f;
        boolean action = game.uri.startsWith("action:");
        label.setShader(new LinearGradient(0, r.top, 0, r.bottom,
                Theme.darken(game.system.color, action ? 0.2f : 0.35f), Theme.darken(game.system.color, 0.65f),
                Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, label);
        label.setShader(null);
        float side = Math.min(r.width(), r.height()) * (cover != null ? 0.74f : 0.56f);
        float cx = r.centerX();
        float cy = r.centerY() - (showTitle && r.width() > 80 * dp ? r.height() * 0.07f : 0);
        if (cover != null) {
            lr.set(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f);
            src.set(0, 0, cover.getWidth(), cover.getHeight());
            c.drawBitmap(cover, src, lr, null);
        } else if (RemoteEmblem.handles(game.uri)) {
            RemoteEmblem.draw(c, game.uri, cx, cy, side * 1.2f);
        } else {
            // Action : flèches d'actualisation ; client pas encore installé : pictogramme d'apps, en retrait.
            String name = !action ? "apps" : game.uri.startsWith("action:addclient") ? "plus" : "refresh";
            int color = action ? Theme.CODEC : Theme.lighten(game.system.color, 0.5f);
            if (glyph == null || !name.equals(glyphName)) {
                glyph = Icons.of(getContext(), name, color, 24);
                glyphName = name;
            }
            glyph.setBounds(Math.round(cx - side / 2f), Math.round(cy - side / 2f),
                    Math.round(cx + side / 2f), Math.round(cy + side / 2f));
            glyph.setAlpha(action ? 255 : 150);
            glyph.draw(c);
        }
        if (!action && r.width() > 80 * dp) {
            badge.setTextSize(Math.max(9 * dp, r.width() * 0.09f));
            String tag = game.system.shortName;
            float bw = badge.measureText(tag) + 8 * dp;
            float bh = badge.getTextSize() + 6 * dp;
            c.drawRoundRect(r.left + 6 * dp, r.top + 6 * dp, r.left + 6 * dp + bw, r.top + 6 * dp + bh,
                    4 * dp, 4 * dp, badgeBg);
            c.drawText(tag, r.left + 10 * dp, r.top + 6 * dp + bh - 4 * dp - badge.descent() * 0.5f, badge);
        }
        if (favorite) {
            badge.setTextSize(Math.max(9 * dp, r.width() * 0.11f));
            badge.setColor(Theme.YELLOW);
            c.drawText("★", r.right - badge.getTextSize() - 4 * dp, r.top + badge.getTextSize() + 4 * dp, badge);
            badge.setColor(Color.WHITE);
        }
        if (showTitle && r.width() > 80 * dp) {
            title.setTextSize(Math.max(11 * dp, Math.min(15 * dp, r.width() * 0.085f)));
            CharSequence t = TextUtils.ellipsize(game.title, title, r.width() - 16 * dp, TextUtils.TruncateAt.END);
            c.drawText(t, 0, t.length(), r.left + 8 * dp, r.bottom - 8 * dp, title);
        }
        if (selected) c.drawRoundRect(r, rad, rad, ring);
    }

    private void drawCover(Canvas c) {
        // Recadrage centré pour remplir l'étiquette.
        float target = lr.width() / lr.height();
        int bw = cover.getWidth();
        int bh = cover.getHeight();
        if ((float) bw / bh > target) {
            int cw = (int) (bh * target);
            src.set((bw - cw) / 2, 0, (bw + cw) / 2, bh);
        } else {
            int ch = (int) (bw / target);
            src.set(0, (bh - ch) / 2, bw, (bh + ch) / 2);
        }
        c.save();
        c.clipRect(lr);
        c.drawBitmap(cover, src, lr, null);
        c.restore();
    }

    private void drawSprite(Canvas c) {
        if (sprite == null) return;
        float size = Math.min(lr.width(), lr.height()) * 0.62f;
        float cell = (float) Math.floor(size / 8f);
        if (cell < 1) return;
        float ox = lr.centerX() - cell * 4;
        float oy = lr.centerY() - cell * 4 + cell * 0.5f;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                if (!sprite[y][x]) continue;
                pixel.setColor((y + x) % 5 == 0 ? spriteB : spriteA);
                c.drawRect(ox + x * cell, oy + y * cell, ox + (x + 1) * cell, oy + (y + 1) * cell, pixel);
            }
        }
    }
}
