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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Didacticiel à bulles, après l'assistant de démarrage : l'accueil s'assombrit, un cadre entoure
 * chaque commande de la barre du haut et une bulle dit à quoi elle sert. « Passer » le ferme à tout moment.
 */
final class Tour {
    /** Index dans la barre du haut (logo, vue, thème, manette, réglages) ; -1 : bulle au centre. */
    private static final int[] TARGET = {-1, 0, 1, 2, 3, 4, -1};
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
    private final FrameLayout root;
    private final List<View> bar;
    private final Runnable onEnd;
    private FrameLayout overlay;
    private int step;

    Tour(MainActivity host, FrameLayout root, List<View> bar, Runnable onEnd) {
        this.host = host;
        this.root = root;
        this.bar = bar;
        this.onEnd = onEnd;
    }

    void start() {
        overlay = new FrameLayout(host);
        overlay.setClickable(true);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        show();
    }

    private void end() {
        if (overlay != null) root.removeView(overlay);
        overlay = null;
        onEnd.run();
    }

    private int dp(float v) {
        return Math.round(v * host.getResources().getDisplayMetrics().density);
    }

    private void show() {
        overlay.removeAllViews();
        int idx = TARGET[step];
        View target = idx >= 0 && idx < bar.size() && bar.get(idx).isShown() ? bar.get(idx) : null;
        RectF hole = null;
        if (target != null) {
            int[] a = new int[2], b = new int[2];
            target.getLocationInWindow(a);
            root.getLocationInWindow(b);
            float pad = dp(6);
            hole = new RectF(a[0] - b[0] - pad, a[1] - b[1] - pad,
                    a[0] - b[0] + target.getWidth() + pad, a[1] - b[1] + target.getHeight() + pad);
        }
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
                    show();
                }
            }
        });
        row.addView(next);
        bubble.addView(row);

        // La bulle s'adapte à l'écran : largeur bornée, hauteur selon le texte, sous ou au-dessus de la cible.
        int w = Math.min(dp(340), root.getWidth() - dp(24));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (hole == null) {
            lp.gravity = Gravity.CENTER;
        } else {
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.leftMargin = (int) Math.max(dp(12), Math.min(root.getWidth() - w - dp(12), hole.centerX() - w / 2f));
            if (hole.centerY() < root.getHeight() / 2f) {
                lp.topMargin = (int) hole.bottom + dp(12);
            } else {
                lp.gravity = Gravity.BOTTOM | Gravity.START;
                lp.bottomMargin = root.getHeight() - (int) hole.top + dp(12);
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
