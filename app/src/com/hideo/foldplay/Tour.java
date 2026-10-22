package com.hideo.foldplay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Didacticiel à bulles, après l'assistant de démarrage : l'accueil s'assombrit, un cadre entoure
 * chaque commande de la barre du haut et une bulle dit à quoi elle sert. « Passer » le ferme à tout moment.
 * Le cadre et la bulle restent accrochés à la commande : chaque passe de mise en page (rotation, pli ou
 * dépli du Fold, reconstruction de l'accueil) recalcule sa place.
 */
final class Tour {
    /** Commande visée, repérée par le début de sa description (pas par sa place : la barre change selon la vue) ; null : bulle au centre. */
    private static final String[] TARGET = {null, "Applications", "Passer en mode", "Thème", "Options contrôleur", "Réglages", null};
    private static final String[] TITLE = {
        "Bienvenue dans Origami",
        "Le logo : tes applications",
        "Washi ou Tsuru",
        "Le thème",
        "La manette",
        "Les réglages",
        "À toi de jouer",
    };
    private static final String[] TEXT = {
        "Un petit tour de 30 secondes pour te montrer où tout se trouve. Touche « Suivant » pour avancer, « Passer » pour quitter.",
        "Touche la grue pour ouvrir les applications du téléphone. Les jeux et les émulateurs s'y lancent aussi.",
        "Ce bouton change l'accueil : Washi montre la bibliothèque en grille, Tsuru en frise avec le détail du jeu.",
        "Couleurs, fond, icônes, sons et musique : chaque thème change toute l'interface sans redémarrer.",
        "Choisis la manette à l'écran, sa disposition et son retour de vibration. Elle se modifie doigt par doigt dans l'éditeur.",
        "Dossier de jeux, moteurs intégrés, mises à jour, accessibilité. Tu peux relancer ce didacticiel depuis À propos.",
        "Touche une jaquette pour lancer un jeu. Un appui long sur une jaquette ouvre ses options. Bon jeu !",
    };

    private final MainActivity host;
    private final List<View> bar;
    private final Runnable onEnd;
    private FrameLayout overlay;
    private int step;
    /** Dernière place connue de la cible dans l'overlay : la bulle n'est redessinée que si elle a bougé. */
    private RectF shown;
    private boolean shownCentered;
    private int shownW, shownH;
    private boolean needsShow;
    private ViewTreeObserver.OnGlobalLayoutListener follow;

    Tour(MainActivity host, FrameLayout root, List<View> bar, Runnable onEnd) {
        this.host = host;
        this.bar = bar;
        this.onEnd = onEnd;
    }

    void start() {
        // Dans le contenu de la fenêtre et non dans la racine de l'accueil : reconstruire l'accueil (pli, rotation) ne l'efface pas.
        ViewGroup content = (ViewGroup) host.findViewById(android.R.id.content);
        overlay = new FrameLayout(host);
        overlay.setClickable(true);
        overlay.setElevation(dp(20));
        content.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        needsShow = true;
        follow = new ViewTreeObserver.OnGlobalLayoutListener() {
            public void onGlobalLayout() {
                relayout();
            }
        };
        overlay.getViewTreeObserver().addOnGlobalLayoutListener(follow);
        overlay.requestLayout();
    }

    private void end() {
        if (overlay != null) {
            if (overlay.getViewTreeObserver().isAlive()) overlay.getViewTreeObserver().removeOnGlobalLayoutListener(follow);
            ViewGroup parent = (ViewGroup) overlay.getParent();
            if (parent != null) parent.removeView(overlay);
        }
        overlay = null;
        onEnd.run();
    }

    private int dp(float v) {
        return Math.round(v * host.getResources().getDisplayMetrics().density);
    }

    /** La commande visée à cette étape, si elle est affichée. */
    private View target() {
        String key = TARGET[step];
        if (key == null) return null;
        for (View v : bar) {
            CharSequence d = v.getContentDescription();
            if (d == null || !v.isShown() || v.getWidth() == 0) continue;
            String s = d.toString();
            if (s.startsWith(key) || s.startsWith(I18n.tr(key))) return v;
        }
        return null;
    }

    private RectF holeOf(View target) {
        int[] a = new int[2], b = new int[2];
        target.getLocationInWindow(a);
        overlay.getLocationInWindow(b);
        float pad = dp(6);
        return new RectF(a[0] - b[0] - pad, a[1] - b[1] - pad,
                a[0] - b[0] + target.getWidth() + pad, a[1] - b[1] + target.getHeight() + pad);
    }

    /** À chaque passe de mise en page : la cible a-t-elle bougé (rotation, pli, dépli, nouvelle barre) ? Si oui, on replace cadre et bulle. */
    private void relayout() {
        if (overlay == null || overlay.getWidth() == 0 || overlay.getHeight() == 0) return;
        View t = target();
        boolean centered = t == null;
        RectF hole = centered ? null : holeOf(t);
        boolean same = !needsShow && centered == shownCentered
                && (centered || (shown != null && shown.equals(hole)))
                && shownW == overlay.getWidth() && shownH == overlay.getHeight();
        if (same) return;
        needsShow = false;
        shownCentered = centered;
        shown = hole;
        shownW = overlay.getWidth();
        shownH = overlay.getHeight();
        show(hole);
    }

    private void show(RectF hole) {
        overlay.removeAllViews();
        overlay.addView(new Scrim(host, hole), new FrameLayout.LayoutParams(-1, -1));

        LinearLayout bubble = new LinearLayout(host);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(18), dp(16), dp(18), dp(14));
        bubble.setBackground(Theme.pill(Theme.BG, dp(18)));
        bubble.setElevation(dp(12));
        TextView title = label(I18n.tr(TITLE[step]), 18, Theme.CODEC);
        title.setTypeface(Fonts.wide(host));
        bubble.addView(title);
        TextView text = label(I18n.tr(TEXT[step]), 14, Theme.TEXT);
        text.setPadding(0, dp(8), 0, dp(12));
        text.setLineSpacing(0, 1.15f);
        bubble.addView(text);

        LinearLayout row = new LinearLayout(host);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView count = label((step + 1) + " / " + TARGET.length, 12, Theme.MUTED);
        row.addView(count, new LinearLayout.LayoutParams(0, -2, 1f));
        if (step < TARGET.length - 1) {
            TextView skip = label(I18n.tr("Passer"), 14, Theme.MUTED);
            skip.setPadding(dp(14), dp(10), dp(14), dp(10));
            skip.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    end();
                }
            });
            row.addView(skip);
        }
        final boolean last = step == TARGET.length - 1;
        TextView next = label(I18n.tr(last ? "Terminer" : "Suivant"), 14, Theme.BG);
        next.setBackground(Theme.pill(Theme.CODEC, dp(18)));
        next.setPadding(dp(18), dp(10), dp(18), dp(10));
        next.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (last) {
                    end();
                } else {
                    step++;
                    needsShow = true;
                    relayout();
                }
            }
        });
        row.addView(next);
        bubble.addView(row);

        // La bulle s'adapte à l'écran : largeur bornée, hauteur selon le texte, sous ou au-dessus de la cible.
        int W = overlay.getWidth(), H = overlay.getHeight();
        int w = Math.min(dp(340), W - dp(24));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (hole == null) {
            lp.gravity = Gravity.CENTER;
        } else {
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.leftMargin = (int) Math.max(dp(12), Math.min(W - w - dp(12), hole.centerX() - w / 2f));
            if (hole.centerY() < H / 2f) {
                lp.topMargin = (int) hole.bottom + dp(12);
            } else {
                lp.gravity = Gravity.BOTTOM | Gravity.START;
                lp.bottomMargin = H - (int) hole.top + dp(12);
            }
        }
        overlay.addView(bubble, lp);
    }

    private TextView label(String s, float sp, int color) {
        TextView t = new TextView(host);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    /** Voile sombre avec une ouverture arrondie sur la commande expliquée, cernée par l'accent du thème. */
    private static final class Scrim extends View {
        private final RectF hole;
        private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cut = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);

        Scrim(Context ctx, RectF hole) {
            super(ctx);
            this.hole = hole;
            setLayerType(LAYER_TYPE_HARDWARE, null);
            dim.setColor(0xCC000000);
            cut.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(ctx.getResources().getDisplayMetrics().density * 2.5f);
            ring.setColor(Theme.CODEC);
        }

        @Override
        protected void onDraw(Canvas c) {
            c.drawRect(0, 0, getWidth(), getHeight(), dim);
            if (hole == null) return;
            float r = Math.min(hole.width(), hole.height()) / 2f;
            c.drawRoundRect(hole, r, r, cut);
            c.drawRoundRect(hole, r, r, ring);
        }
    }
}
