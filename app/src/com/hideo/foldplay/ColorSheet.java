package com.hideo.foldplay;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.List;

/**
 * Panneau de couleurs posé sur la moitié haute de l'accueil, la manette restant visible dessous :
 * - « Couleurs de la manette » (menu Thème) : habillage, fond, touches, accent et touche par touche,
 *   appliqués en direct à la manette affichée (déplacé de l'éditeur de manette en 0.42.6) ;
 * - « Couleur du logo » : la grue suit le thème ou prend une couleur choisie, avec aperçu.
 * Le reste de l'interface se recolore quand le doigt se lève.
 */
final class ColorSheet extends FrameLayout {
    interface Host {
        /** La manette affichée à l'accueil (null si aucune). */
        ControlDeckView liveDeck();

        /** Recolore l'interface après un changement enregistré. */
        void restyled();
    }

    /** Teintes proposées : les couleurs maison d'abord, puis un nuancier large. */
    static final int[] PALETTE = {
        Theme.PANEL0, Theme.STEEL0, Theme.CODEC0, 0xFF0B0C0F, 0xFF4A505C, 0xFF9AA0AA, 0xFFE8EAED,
        0xFFD84A4A, 0xFFF08C3A, 0xFFF2C94C, 0xFF7BC86C, 0xFF3CC8C8, 0xFF3A7BF0, 0xFF8A5CF0,
        0xFFF05CA8, 0xFF5B6B3A, 0xFF6B4A3A, 0xFF1E2A44,
    };

    private final Activity act;
    private final Host host;
    private final Store store;
    /** Onglet affiché : le logo ou la manette (un seul panneau depuis 0.60.6, demande BOSS). */
    private boolean logoMode;
    private final float dp;
    private LinearLayout body;
    private List<ControlLayout> layouts;
    private ControlLayout pad;
    /** "deck", "button", "accent" ou "key". */
    private String target = "deck";
    private ControlLayout.Control key;

    private ColorSheet(Activity a, Host host, boolean logo) {
        super(a);
        this.act = a;
        this.host = host;
        this.logoMode = logo;
        this.store = new Store(a);
        this.dp = a.getResources().getDisplayMetrics().density;
        setClickable(true);
        layouts = store.layouts();
        for (ControlLayout l : layouts) if (l.name.equals(store.activeLayout())) pad = l;
        if (pad == null && !layouts.isEmpty()) pad = layouts.get(0);
        if (pad == null) logoMode = true;
    }

    /** Ouvre le panneau sur la moitié haute (ou un peu plus si la manette n'est pas en bas). */
    static void show(Activity a, Host host, boolean logo, int height) {
        FrameLayout content = (FrameLayout) a.findViewById(android.R.id.content);
        for (int i = content.getChildCount() - 1; i >= 0; i--) {
            if (content.getChildAt(i) instanceof ColorSheet) content.removeViewAt(i);
        }
        ColorSheet s = new ColorSheet(a, host, logo);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, height, Gravity.TOP);
        int m = Math.round(8 * s.dp);
        lp.setMargins(m, m, m, m);
        content.addView(s, lp);
        s.render();
        s.setAlpha(0f);
        s.animate().alpha(1f).setDuration(140).start();
    }

    /** Le panneau ouvert, s'il y en a un (pour le refermer avec la touche retour). */
    static ColorSheet find(Activity a) {
        FrameLayout content = (FrameLayout) a.findViewById(android.R.id.content);
        for (int i = content.getChildCount() - 1; i >= 0; i--) {
            if (content.getChildAt(i) instanceof ColorSheet) return (ColorSheet) content.getChildAt(i);
        }
        return null;
    }

    void close() {
        if (getParent() != null) ((FrameLayout) getParent()).removeView(this);
    }

    private int px(float v) {
        return Math.round(v * dp);
    }

    // ------------------------------------------------------------------ rendu

    void render() {
        removeAllViews();
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor((Theme.PANEL & 0x00FFFFFF) | 0xF2000000);
        bg.setCornerRadius(22 * dp);
        bg.setStroke(Math.round(1.5f * dp), Theme.alpha(Theme.CODEC, 0x66));
        card.setBackground(bg);
        card.setClipToOutline(true);
        card.setPadding(0, px(12), 0, px(8));

        LinearLayout head = new LinearLayout(act);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(px(18), 0, px(8), px(6));
        View bar = new View(act);
        bar.setBackgroundColor(Theme.CODEC);
        head.addView(bar, new LinearLayout.LayoutParams(px(4), px(24)));
        TextView t = label("Couleurs", 18, Theme.CODEC);
        t.setTypeface(Fonts.title(act));
        t.setPadding(px(12), 0, 0, 0);
        Theme.marquee(t);
        head.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView close = label("✕", 20, Theme.TEXT);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription(I18n.tr("Fermer"));
        close.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                close();
            }
        });
        head.addView(close, new LinearLayout.LayoutParams(px(48), px(48)));
        card.addView(head);

        ScrollView sv = new ScrollView(act);
        sv.setVerticalScrollBarEnabled(false);
        body = new LinearLayout(act);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(px(14), 0, px(14), px(8));
        sv.addView(body);
        card.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        addView(card, new FrameLayout.LayoutParams(-1, -1));
        if (pad != null) {
            // Les deux réglages dans le même panneau : le logo d'abord, puis la manette.
            LinearLayout tabs = row("RÉGLER");
            tabs.addView(chip("Logo", logoMode, new Runnable() {
                public void run() {
                    logoMode = true;
                    render();
                }
            }));
            tabs.addView(chip("Manette", !logoMode, new Runnable() {
                public void run() {
                    logoMode = false;
                    render();
                }
            }));
            body.addView(scroller(tabs));
        }
        if (logoMode) logoPage();
        else padPage();
    }

    private void padPage() {
        // Habillage : « Codec » (relief, liserés, trame) ou aplats « Uni ».
        LinearLayout skins = row("HABILLAGE");
        String[][] looks = {{"codec", "Codec"}, {"plain", "Uni"}};
        for (final String[] k : looks) {
            skins.addView(chip(k[1], k[0].equals(pad.skin), new Runnable() {
                public void run() {
                    pad.skin = k[0];
                    live();
                    commit();
                    render();
                }
            }));
        }
        body.addView(scroller(skins));
        LinearLayout targets = row("COULEUR");
        String[][] all = {{"deck", "Manette"}, {"button", "Touches"}, {"accent", "Accent"}};
        for (final String[] k : all) {
            targets.addView(chip(k[1], k[0].equals(target), new Runnable() {
                public void run() {
                    target = k[0];
                    key = null;
                    render();
                }
            }));
        }
        body.addView(scroller(targets));
        // Une touche précise : elle prend sa propre couleur.
        LinearLayout keys = row("UNE TOUCHE");
        for (final ControlLayout.Control c : pad.controls) {
            keys.addView(chip(ControlLayout.label(c.type), target.equals("key") && key == c, new Runnable() {
                public void run() {
                    target = "key";
                    key = c;
                    render();
                }
            }));
        }
        body.addView(scroller(keys));

        body.addView(picker(padColor(), new Pick() {
            public void color(int c, boolean done) {
                setPadColor(c);
                live();
                if (done) commit();
            }
        }));

        if (target.equals("deck")) {
            body.addView(slider("Opacité de la manette", pad.deckAlpha, 0, new Pick() {
                public void color(int v, boolean done) {
                    pad.deckAlpha = v;
                    live();
                    if (done) commit();
                }
            }));
        } else if (!target.equals("accent")) {
            body.addView(slider("Opacité des touches", pad.buttonAlpha, 10, new Pick() {
                public void color(int v, boolean done) {
                    pad.buttonAlpha = v;
                    live();
                    if (done) commit();
                }
            }));
        }
        LinearLayout acts = new LinearLayout(act);
        acts.setPadding(0, px(8), 0, 0);
        if (target.equals("key") && key != null && key.color != 0) {
            acts.addView(chip("Comme les autres touches", false, new Runnable() {
                public void run() {
                    key.color = 0;
                    live();
                    commit();
                    render();
                }
            }));
        }
        acts.addView(chip("Style d'origine", false, new Runnable() {
            public void run() {
                pad.deckColor = Theme.PANEL0;
                pad.buttonColor = Theme.STEEL0;
                pad.accentColor = Theme.CODEC0;
                pad.deckAlpha = 100;
                pad.buttonAlpha = 100;
                for (ControlLayout.Control c : pad.controls) c.color = 0;
                live();
                commit();
                render();
            }
        }));
        body.addView(scroller(acts));
    }

    private int padColor() {
        if (target.equals("button")) return pad.buttonColor;
        if (target.equals("accent")) return pad.accentColor;
        if (target.equals("key") && key != null) return key.color != 0 ? key.color : pad.buttonColor;
        return pad.deckColor;
    }

    private void setPadColor(int c) {
        if (target.equals("button")) pad.buttonColor = c;
        else if (target.equals("accent")) pad.accentColor = c;
        else if (target.equals("key") && key != null) key.color = c;
        else pad.deckColor = c;
    }

    /** La manette affichée prend le nouveau style tout de suite. */
    private void live() {
        ControlDeckView d = host.liveDeck();
        if (d == null || d.layout() == null) return;
        d.layout().copyStyle(pad);
        d.applyStyle();
    }

    /** Enregistre, puis l'interface (titres, logo, sélection…) reprend les couleurs. */
    private void commit() {
        store.saveLayouts(layouts);
        store.restyleScreenLayouts(pad);
        host.restyled();
    }

    private void logoPage() {
        final int cur = store.logoColor();
        final android.widget.ImageView preview = new android.widget.ImageView(act);
        final Crane.Logo crane = new Crane.Logo();
        preview.setImageDrawable(crane);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(px(120), px(84));
        pl.gravity = Gravity.CENTER_HORIZONTAL;
        body.addView(preview, pl);
        LinearLayout modes = row("LOGO");
        modes.addView(chip("Suivre le thème", cur == 0, new Runnable() {
            public void run() {
                store.setLogoColor(0);
                host.restyled();
                render();
            }
        }));
        modes.addView(chip("Couleur perso", cur != 0, new Runnable() {
            public void run() {
                if (store.logoColor() == 0) store.setLogoColor(Theme.CODEC | 0xFF000000);
                host.restyled();
                render();
            }
        }));
        body.addView(scroller(modes));
        body.addView(picker(cur != 0 ? cur : Theme.CODEC, new Pick() {
            public void color(int c, boolean done) {
                crane.accent = c;
                crane.fold = Theme.lighten(c, 0.7f);
                crane.invalidateSelf();
                if (done) {
                    store.setLogoColor(c | 0xFF000000);
                    host.restyled();
                    render();
                }
            }
        }));
        body.addView(note("L'icône de l'app sur l'écran d'accueil du téléphone garde la grue d'origine."));
    }

    // ------------------------------------------------------------------ pièces

    private interface Pick {
        void color(int value, boolean done);
    }

    /** Roue (teinte, saturation) et luminosité à gauche, teintes prêtes à droite. */
    private View picker(int color, final Pick pick) {
        LinearLayout pane = new LinearLayout(act);
        pane.setPadding(0, px(8), 0, px(4));
        pane.setGravity(Gravity.TOP);
        LinearLayout left = new LinearLayout(act);
        left.setOrientation(LinearLayout.VERTICAL);
        final ColorWheelView wheel = new ColorWheelView(act);
        wheel.setColor(color);
        left.addView(wheel, new LinearLayout.LayoutParams(px(170), px(170)));
        TextView lum = label("Luminosité", 12, Theme.MUTED);
        lum.setPadding(px(4), px(6), 0, 0);
        left.addView(lum);
        final SeekBar value = new SeekBar(act);
        value.setMax(100);
        Theme.tint(value);
        value.setProgress(Math.round(wheel.value() * 100));
        value.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean user) {
                if (user) wheel.setValue(v / 100f);
            }

            public void onStartTrackingTouch(SeekBar s) {
            }

            public void onStopTrackingTouch(SeekBar s) {
                pick.color(wheel.color(), true);
            }
        });
        left.addView(value, new LinearLayout.LayoutParams(px(170), -2));
        wheel.setListener(new ColorWheelView.Listener() {
            public void onColor(int c, boolean done) {
                value.setProgress(Math.round(wheel.value() * 100));
                pick.color(c, done);
            }
        });
        pane.addView(left);

        LinearLayout right = new LinearLayout(act);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(px(14), 0, 0, 0);
        LinearLayout line = null;
        for (int i = 0; i < PALETTE.length; i++) {
            if (i % 4 == 0) {
                line = new LinearLayout(act);
                right.addView(line);
            }
            final int c = PALETTE[i];
            View v = new View(act);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(c);
            boolean on = (c | 0xFF000000) == (color | 0xFF000000);
            g.setStroke(px(on ? 3 : 1), on ? Theme.TEXT : 0x55FFFFFF);
            v.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(34), px(34));
            lp.setMargins(px(4), px(4), px(4), px(4));
            v.setOnClickListener(new OnClickListener() {
                public void onClick(View x) {
                    pick.color(c, true);
                    render();
                }
            });
            line.addView(v, lp);
        }
        pane.addView(right);
        return scroller(pane);
    }

    private View slider(String name, int value, int min, final Pick pick) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, px(6), 0, 0);
        box.addView(label(name, 13, Theme.MUTED));
        SeekBar s = new SeekBar(act);
        s.setMax(100 - min);
        Theme.tint(s);
        s.setProgress(Math.max(0, value - min));
        final int base = min;
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int v, boolean user) {
                if (user) pick.color(base + v, false);
            }

            public void onStartTrackingTouch(SeekBar b) {
            }

            public void onStopTrackingTouch(SeekBar b) {
                pick.color(base + b.getProgress(), true);
            }
        });
        box.addView(s, new LinearLayout.LayoutParams(-1, -2));
        return box;
    }

    private LinearLayout row(String title) {
        LinearLayout r = new LinearLayout(act);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, px(4), 0, px(2));
        TextView t = label(title, 11, Theme.MUTED);
        t.setTypeface(Fonts.wide(act));
        t.setPadding(px(4), 0, px(8), 0);
        r.addView(t);
        return r;
    }

    private View scroller(View v) {
        HorizontalScrollView h = new HorizontalScrollView(act);
        h.setHorizontalScrollBarEnabled(false);
        h.addView(v);
        return h;
    }

    private TextView chip(String text, boolean on, final Runnable action) {
        TextView c = label(text, 15, on ? Theme.readableOn(Theme.CODEC) : Theme.TEXT);
        c.setPadding(px(16), px(9), px(16), px(9));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(20));
        g.setColor(on ? Theme.CODEC : 0x22FFFFFF);
        c.setBackground(g);
        c.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                action.run();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(px(3), px(3), px(3), px(3));
        c.setLayoutParams(lp);
        return c;
    }

    private TextView note(String s) {
        TextView t = label(s, 12, Theme.MUTED);
        t.setPadding(px(4), px(8), px(4), 0);
        return t;
    }

    private TextView label(String s, float sp, int color) {
        TextView t = new TextView(act);
        t.setText(I18n.tr(s));
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }
}
