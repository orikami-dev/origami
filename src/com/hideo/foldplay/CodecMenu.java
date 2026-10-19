package com.hideo.foldplay;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu maison façon « codec » : une carte sombre posée dans la moitié haute, la manette du bas
 * reste libre et pilote le menu (▲ ▼ pour choisir, A pour valider, B pour revenir).
 * Remplace les fenêtres Android standard en jeu et en vue Flex.
 */
final class CodecMenu extends FrameLayout {
    /** Une option choisie dans une rangée d'icônes. */
    interface Pick {
        void picked(int index);
    }

    interface Listener {
        /** Le menu vient de se fermer (pour reprendre le jeu). */
        void onClosed();
    }

    private static final class Item {
        final String label;
        final String detail;
        int mark; // 0 rien, 1 coché, 2 décoché, 3 point choisi, 4 point libre
        final Runnable action;
        final boolean keepOpen;
        android.graphics.drawable.Drawable icon;
        // Choix par icônes (profils) : ◀ ▶ changent, A ou « ? » ouvre l'explication.
        String[] icons;
        String[] names;
        String[] helps;
        int sel;
        Pick pick;
        boolean helpOpen;

        Item(String label, String detail, int mark, Runnable action, boolean keepOpen) {
            this.label = label;
            this.detail = detail;
            this.mark = mark;
            this.action = action;
            this.keepOpen = keepOpen;
        }
    }

    private final float dp;
    private final List<Item> items = new ArrayList<Item>();
    private final List<View> rows = new ArrayList<View>();
    private String title = "";
    private String subtitle;
    private String message;
    private TextView messageView;
    private Runnable back;
    private int focus;
    private int columns = 1;
    /** Entrées en tuiles (icône au-dessus du nom), comme un lanceur d'apps. */
    private boolean grid;
    private android.graphics.drawable.Drawable headIcon;
    private String headDesc;
    private Runnable headAction;
    private Capped scroll;
    private LinearLayout card;
    private FrameLayout host;
    private Listener listener;

    CodecMenu(Context ctx) {
        super(ctx);
        dp = ctx.getResources().getDisplayMetrics().density;
        // Voile : un appui à côté de la carte ferme le menu.
        setBackgroundColor(0x99000000);
        setClickable(true);
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                goBack();
            }
        });
    }

    // ------------------------------------------------------------------ construction

    CodecMenu title(String t, String sub) {
        title = t;
        subtitle = sub;
        return this;
    }

    /** Une entrée qui ferme le menu puis lance l'action. */
    CodecMenu item(String label, String detail, Runnable action) {
        items.add(new Item(label, detail, 0, action, false));
        return this;
    }

    /** Une entrée avec une icône (le choix d'un émulateur, par exemple). */
    CodecMenu item(android.graphics.drawable.Drawable icon, String label, String detail, Runnable action) {
        Item it = new Item(label, detail, 0, action, false);
        it.icon = icon;
        items.add(it);
        return this;
    }

    /** Une entrée visible mais fermée (fonction annoncée, « en chantier ») : rien ne s'ouvre. */
    CodecMenu soon(android.graphics.drawable.Drawable icon, String label, String detail) {
        Item it = new Item(label, detail, 0, null, true);
        it.icon = icon;
        items.add(it);
        return this;
    }

    /**
     * Une rangée de boutons-icônes (un seul choisi) et un bouton « ? » qui explique chacun.
     * À la manette : ◀ ▶ changent le choix, A ouvre ou ferme l'explication.
     */
    CodecMenu choice(String label, String[] icons, String[] names, String[] helps, int selected, Pick pick) {
        Item it = new Item(label, null, 0, null, true);
        it.icons = icons;
        it.names = names;
        it.helps = helps;
        it.sel = selected;
        it.pick = pick;
        items.add(it);
        return this;
    }

    /** Affiche les entrées en grille de tuiles plutôt qu'en liste. */
    CodecMenu grid(boolean on) {
        grid = on;
        return this;
    }

    /** Un bouton-icône en haut à droite de l'en-tête (changer la disposition, par exemple). */
    CodecMenu headButton(android.graphics.drawable.Drawable icon, String description, Runnable action) {
        headIcon = icon;
        headDesc = description;
        headAction = action;
        return this;
    }

    /** Un paragraphe sous le titre (avertissement, explication). */
    CodecMenu message(String m) {
        message = m;
        return this;
    }

    /** La zone d'affichage par défaut d'une activité : tout son contenu. */
    static FrameLayout hostFor(android.app.Activity a) {
        if (a instanceof MainActivity) return ((MainActivity) a).menuHost();
        if (a instanceof GameActivity) return ((GameActivity) a).menuHost();
        return (FrameLayout) a.findViewById(android.R.id.content);
    }

    /** Une entrée qui ouvre un sous-menu (le menu reste à l'écran le temps de la remplacer). */
    CodecMenu sub(String label, String detail, Runnable action) {
        items.add(new Item(label + "  ›", detail, 0, action, true));
        return this;
    }

    CodecMenu check(String label, String detail, boolean on, Runnable toggle) {
        items.add(new Item(label, detail, on ? 1 : 2, toggle, true));
        return this;
    }

    /** Un choix parmi plusieurs : le menu reste ouvert et le point passe sur le choix (0.60.6). */
    CodecMenu radio(String label, String detail, boolean on, Runnable pick) {
        items.add(new Item(label, detail, on ? 3 : 4, pick, true));
        return this;
    }

    /** Ce que fait B / le voile : revenir au menu parent, sinon fermer. */
    CodecMenu onBack(Runnable r) {
        back = r;
        return this;
    }

    CodecMenu listener(Listener l) {
        listener = l;
        return this;
    }

    // ------------------------------------------------------------------ affichage

    /** Affiche le menu dans la zone donnée, en remplaçant celui qui y serait déjà. */
    void show(FrameLayout where) {
        host = where;
        boolean replacing = false;
        for (int i = where.getChildCount() - 1; i >= 0; i--) {
            View v = where.getChildAt(i);
            if (v instanceof CodecMenu) {
                if (listener == null) listener = ((CodecMenu) v).listener;
                where.removeViewAt(i);
                replacing = true;
            }
        }
        build();
        where.addView(this, new FrameLayout.LayoutParams(-1, -1));
        if (replacing) {
            // Sous-menu : simple fondu, sans relancer l'appel.
            card.setAlpha(0f);
            card.animate().alpha(1f).setDuration(110).start();
        } else {
            // Ouverture façon appel radio : un trait lumineux qui s'ouvre en écran.
            setAlpha(0f);
            animate().alpha(1f).setDuration(140).start();
            card.setScaleY(0.02f);
            card.setScaleX(0.7f);
            card.setAlpha(0.6f);
            card.animate().scaleX(1f).setDuration(110).withEndAction(new Runnable() {
                public void run() {
                    card.animate().scaleY(1f).alpha(1f).setDuration(170)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                }
            }).start();
            CodecSound.call(getContext());
        }
        setFocus(0);
    }

    static CodecMenu find(FrameLayout where) {
        if (where == null) return null;
        for (int i = where.getChildCount() - 1; i >= 0; i--) {
            if (where.getChildAt(i) instanceof CodecMenu) return (CodecMenu) where.getChildAt(i);
        }
        return null;
    }

    /** Change le paragraphe d'un menu déjà affiché (progression d'un téléchargement), sans le rouvrir. */
    void updateMessage(String m) {
        message = m;
        if (messageView != null) messageView.setText(m);
    }

    void dismiss() {
        if (host != null) host.removeView(this);
        host = null;
        if (listener != null) listener.onClosed();
    }

    private boolean padConnected;

    private void build() {
        removeAllViews();
        padConnected = new Pads(getContext()).any();
        rows.clear();
        card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        // Carte translucide : on devine le jeu en pause derrière.
        bg.setColor(0xD9121419);
        bg.setCornerRadius(22 * dp);
        bg.setStroke(Math.round(1.5f * dp), Theme.alpha(Theme.CODEC, 0x66));
        card.setBackground(bg);
        card.setClipToOutline(true);
        card.setClickable(true);
        card.setPadding(0, px(14), 0, px(10));

        // En-tête : barre verte, titre au pochoir, sous-titre.
        LinearLayout head = new LinearLayout(getContext());
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(px(18), 0, px(18), px(8));
        View bar = new View(getContext());
        bar.setBackgroundColor(Theme.CODEC);
        head.addView(bar, new LinearLayout.LayoutParams(px(4), px(26)));
        LinearLayout titles = new LinearLayout(getContext());
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(px(12), 0, 0, 0);
        TextView t = new TextView(getContext());
        t.setText(title.toUpperCase());
        t.setTypeface(Fonts.logo(getContext()));
        t.setTextSize(18);
        t.setTextColor(Theme.CODEC);
        Theme.marquee(t);
        titles.addView(t);
        if (subtitle != null) {
            TextView st = new TextView(getContext());
            st.setText(subtitle);
            st.setTextSize(11);
            st.setTypeface(Fonts.wide(getContext()));
            st.setLetterSpacing(0.12f);
            st.setTextColor(Theme.MUTED);
            titles.addView(st);
        }
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        if (headIcon != null) {
            android.widget.ImageView hb = new android.widget.ImageView(getContext());
            hb.setImageDrawable(headIcon);
            hb.setScaleType(android.widget.ImageView.ScaleType.CENTER);
            hb.setContentDescription(I18n.tr(headDesc));
            hb.setBackground(Theme.pill(Theme.alpha(Theme.CODEC, 0x22), px(12)));
            hb.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    headAction.run();
                }
            });
            head.addView(hb, new LinearLayout.LayoutParams(px(44), px(44)));
        }
        card.addView(head);
        messageView = null;
        if (message != null) {
            TextView msg = new TextView(getContext());
            messageView = msg;
            msg.setText(message);
            msg.setTextSize(14);
            msg.setTextColor(Theme.TEXT);
            msg.setLineSpacing(0, 1.15f);
            msg.setPadding(px(18), px(2), px(18), px(10));
            card.addView(msg);
        }

        LinearLayout list = new LinearLayout(getContext());
        list.setOrientation(LinearLayout.VERTICAL);
        int hw = host != null && host.getWidth() > 0 ? host.getWidth() : px(600);
        // Toute la pilule du haut : deux colonnes dès qu'il y a la place, pour voir plus d'entrées.
        columns = hw >= px(620) && items.size() > 3 ? 2 : 1;
        for (Item it : items) if (it.icons != null) columns = 1;
        if (grid) columns = Math.max(3, Math.min(8, (hw - px(40)) / px(96)));
        for (int i = 0; i < items.size(); i += columns) {
            if (columns == 1) {
                list.addView(row(i));
                continue;
            }
            LinearLayout line = new LinearLayout(getContext());
            for (int k = i; k < i + columns; k++) {
                View cell = k < items.size() ? (grid ? tile(k) : row(k)) : new View(getContext());
                line.addView(cell, new LinearLayout.LayoutParams(0, -1, 1f));
            }
            list.addView(line, new LinearLayout.LayoutParams(-1, -2));
        }
        scroll = new Capped(getContext());
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(list);
        card.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView hint = new TextView(getContext());
        // Avec une manette physique, on parle de ses touches ; sinon, du doigt et de la manette à l'écran.
        hint.setText(padConnected ? "✚ / STICK CHOISIR   Ⓐ VALIDER   Ⓑ RETOUR   ⌂ FERMER"
                : "TOUCHE UNE ENTRÉE   ·   ✚ CHOISIR   Ⓐ VALIDER   Ⓑ RETOUR");
        hint.setTypeface(Fonts.wide(getContext()));
        hint.setTextSize(9);
        hint.setLetterSpacing(0.1f);
        hint.setTextColor(Theme.MUTED);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, px(8), 0, 0);
        card.addView(hint);

        // Manette physique : le jeu est en plein écran, la carte se centre sans le masquer en entier.
        FrameLayout.LayoutParams lp = padConnected
                ? new FrameLayout.LayoutParams(Math.min(hw - px(24), px(720)), -1, Gravity.CENTER)
                : new FrameLayout.LayoutParams(-1, -1);
        if (padConnected) lp.setMargins(0, px(24), 0, px(24));
        else lp.setMargins(px(10), px(10), px(10), px(10));
        addView(card, lp);
    }

    /** Liste qui défile au-delà d'une hauteur maximale. */
    private static final class Capped extends ScrollView {
        int max = Integer.MAX_VALUE;

        Capped(Context c) {
            super(c);
        }

        @Override
        protected void onMeasure(int w, int h) {
            if (max == Integer.MAX_VALUE) super.onMeasure(w, h);
            else super.onMeasure(w, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
        }
    }

    private View row(final int i) {
        Item it = items.get(i);
        if (it.icons != null) return choiceRow(i);
        LinearLayout r = new LinearLayout(getContext());
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(px(18), px(11), px(18), px(11));
        r.setMinimumHeight(px(48));
        if (it.icon != null) {
            android.widget.ImageView iv = new android.widget.ImageView(getContext());
            iv.setImageDrawable(it.icon);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(px(34), px(34));
            ip.setMargins(0, 0, px(12), 0);
            r.addView(iv, ip);
        }
        if (it.mark != 0) {
            TextView m = new TextView(getContext());
            m.setText(it.mark == 1 ? "■" : it.mark == 2 ? "□" : it.mark == 3 ? "●" : "○");
            m.setTextSize(15);
            m.setTextColor(it.mark == 1 || it.mark == 3 ? Theme.CODEC : Theme.MUTED);
            m.setPadding(0, 0, px(12), 0);
            r.addView(m);
        }
        LinearLayout texts = new LinearLayout(getContext());
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView l = new TextView(getContext());
        l.setText(it.label);
        l.setTextSize(16);
        l.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        l.setTextColor(Theme.TEXT);
        texts.addView(l);
        if (it.detail != null && !it.detail.isEmpty()) {
            TextView d = new TextView(getContext());
            d.setText(it.detail);
            d.setTextSize(12);
            d.setTextColor(Theme.MUTED);
            d.setMaxLines(2);
            d.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(d);
        }
        r.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        r.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) setFocus(i);
                return false;
            }
        });
        r.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                activate(i);
            }
        });
        rows.add(r);
        return r;
    }

    /** Tuile de grille : icône, nom en dessous sur deux lignes au plus. */
    private View tile(final int i) {
        Item it = items.get(i);
        LinearLayout r = new LinearLayout(getContext());
        r.setOrientation(LinearLayout.VERTICAL);
        r.setGravity(Gravity.CENTER_HORIZONTAL);
        r.setPadding(px(4), px(10), px(4), px(8));
        if (it.icon != null) {
            android.widget.ImageView iv = new android.widget.ImageView(getContext());
            iv.setImageDrawable(it.icon);
            r.addView(iv, new LinearLayout.LayoutParams(px(48), px(48)));
        }
        TextView l = new TextView(getContext());
        l.setText(it.label);
        l.setTextSize(12);
        l.setTextColor(Theme.TEXT);
        l.setGravity(Gravity.CENTER);
        l.setMaxLines(2);
        l.setEllipsize(android.text.TextUtils.TruncateAt.END);
        l.setPadding(0, px(6), 0, 0);
        r.addView(l, new LinearLayout.LayoutParams(-1, -2));
        r.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) setFocus(i);
                return false;
            }
        });
        r.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                activate(i);
            }
        });
        rows.add(r);
        return r;
    }

    private View choiceRow(final int i) {
        final Item it = items.get(i);
        LinearLayout r = new LinearLayout(getContext());
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(px(18), px(10), px(18), px(10));
        TextView l = new TextView(getContext());
        l.setText(it.label + " : " + it.names[it.sel]);
        l.setTextSize(16);
        l.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        l.setTextColor(Theme.TEXT);
        r.addView(l);
        LinearLayout line = new LinearLayout(getContext());
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(0, px(8), 0, 0);
        for (int k = 0; k < it.icons.length; k++) {
            final int idx = k;
            boolean on = k == it.sel;
            android.widget.ImageView b = new android.widget.ImageView(getContext());
            b.setImageDrawable(new Icons(it.icons[k], on ? Theme.BG : Theme.TEXT, px(28)));
            b.setScaleType(android.widget.ImageView.ScaleType.CENTER);
            b.setContentDescription(it.names[k]);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(px(16));
            bg.setColor(on ? Theme.CODEC : 0x22FFFFFF);
            b.setBackground(bg);
            b.setOnClickListener(new OnClickListener() {
                public void onClick(View v) { choose(i, idx); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(64), px(52));
            lp.setMargins(0, 0, px(10), 0);
            line.addView(b, lp);
        }
        android.widget.ImageView help = new android.widget.ImageView(getContext());
        help.setImageDrawable(new Icons("help", it.helpOpen ? Theme.CODEC : Theme.MUTED, px(26)));
        help.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        help.setContentDescription("Explications");
        help.setOnClickListener(new OnClickListener() {
            public void onClick(View v) { toggleHelp(i); }
        });
        line.addView(help, new LinearLayout.LayoutParams(px(48), px(52)));
        r.addView(line);
        if (it.helpOpen) {
            for (int k = 0; k < it.icons.length; k++) {
                LinearLayout h = new LinearLayout(getContext());
                h.setGravity(Gravity.CENTER_VERTICAL);
                h.setPadding(0, px(8), 0, 0);
                android.widget.ImageView ic = new android.widget.ImageView(getContext());
                ic.setImageDrawable(new Icons(it.icons[k], k == it.sel ? Theme.CODEC : Theme.MUTED, px(20)));
                h.addView(ic, new LinearLayout.LayoutParams(px(20), px(20)));
                TextView d = new TextView(getContext());
                d.setText(it.names[k] + " · " + it.helps[k]);
                d.setTextSize(12);
                d.setTextColor(k == it.sel ? Theme.TEXT : Theme.MUTED);
                d.setPadding(px(10), 0, 0, 0);
                h.addView(d, new LinearLayout.LayoutParams(0, -2, 1f));
                r.addView(h);
            }
        }
        r.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) setFocus(i);
                return false;
            }
        });
        rows.add(r);
        return r;
    }

    private void choose(int i, int idx) {
        Item it = items.get(i);
        if (idx < 0 || idx >= it.icons.length || idx == it.sel) return;
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        it.sel = idx;
        if (it.pick != null) it.pick.picked(idx);
        rebuildAt(i);
    }

    private void toggleHelp(int i) {
        items.get(i).helpOpen = !items.get(i).helpOpen;
        rebuildAt(i);
    }

    private void rebuildAt(int i) {
        int y = scroll != null ? scroll.getScrollY() : 0;
        build();
        setFocus(i);
        if (scroll != null) {
            final int sy = y;
            scroll.post(new Runnable() { public void run() { scroll.scrollTo(0, sy); } });
        }
    }

    // ------------------------------------------------------------------ pilotage

    private void setFocus(int i) {
        if (rows.isEmpty()) return;
        focus = Math.max(0, Math.min(rows.size() - 1, i));
        for (int k = 0; k < rows.size(); k++) {
            View r = rows.get(k);
            if (k == focus) {
                GradientDrawable sel = new GradientDrawable();
                sel.setColor(Theme.alpha(Theme.CODEC, 0x33));
                r.setBackground(sel);
            } else {
                r.setBackground(null);
            }
        }
        final View target = rows.get(focus);
        if (scroll != null) {
            scroll.post(new Runnable() {
                public void run() {
                    int dy = ((View) target.getParent()).getTop();
                    int top = target.getTop() + dy;
                    int bottom = target.getBottom() + dy;
                    if (top < scroll.getScrollY()) scroll.smoothScrollTo(0, top);
                    else if (bottom > scroll.getScrollY() + scroll.getHeight()) {
                        scroll.smoothScrollTo(0, bottom - scroll.getHeight());
                    }
                }
            });
        }
    }

    private void activate(int i) {
        if (i < 0 || i >= items.size()) return;
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        Item it = items.get(i);
        if (!it.keepOpen) {
            // On ferme d'abord : l'action peut ouvrir un autre menu à la place.
            Listener l = listener;
            listener = null;
            if (host != null) host.removeView(this);
            FrameLayout h = host;
            host = null;
            if (it.action != null) it.action.run();
            if (find(h) == null && l != null) l.onClosed();
        } else if (it.mark >= 3) {
            // Choix validé : le menu reste ouvert (demande BOSS, 0.60.6) ; seuls Retour et ✕ le ferment.
            FrameLayout before = host;
            if (it.action != null) it.action.run();
            if (host == null) return; // l'action a fermé le menu elle-même (autre panneau ouvert)
            for (int j = i; j >= 0 && items.get(j).mark >= 3; j--) items.get(j).mark = 4;
            for (int j = i + 1; j < items.size() && items.get(j).mark >= 3; j++) items.get(j).mark = 4;
            it.mark = 3;
            if (getParent() != null) {
                if (find(host) == this) {
                    build();
                    setFocus(i);
                }
            } else if (getContext() instanceof android.app.Activity) {
                // L'écran s'est reconstruit (thème changé) : le menu revient à sa place, aux nouvelles couleurs.
                FrameLayout now = hostFor((android.app.Activity) getContext());
                if (now != null && find(now) == null && before != null) {
                    host = now;
                    build();
                    now.addView(this, new FrameLayout.LayoutParams(-1, -1));
                    setFocus(i);
                }
            }
        } else if (it.action != null) {
            it.action.run();
        }
    }

    private void goBack() {
        if (back != null) back.run();
        else dismiss();
    }

    /** Touches de la manette Origami (à l'écran ou physique). Renvoie vrai si le menu l'a prise. */
    boolean key(String k) {
        Item f = focus >= 0 && focus < items.size() ? items.get(focus) : null;
        if (f != null && f.icons != null) {
            if (k.equals("LEFT")) { choose(focus, f.sel - 1); return true; }
            if (k.equals("RIGHT")) { choose(focus, f.sel + 1); return true; }
            if (k.equals("A") || k.equals("X") || k.equals("Y")) { toggleHelp(focus); return true; }
        }
        if (k.equals("UP")) setFocus(focus - columns);
        else if (k.equals("DOWN")) setFocus(focus + columns);
        else if (k.equals("LEFT")) setFocus(focus - 1);
        else if (k.equals("RIGHT")) setFocus(focus + 1);
        else if (k.equals("A") || k.equals("START")) activate(focus);
        else if (k.equals("B")) goBack();
        else if (k.equals("HOME")) dismiss();
        else return false;
        return true;
    }

    private int px(float v) {
        return Math.round(v * dp);
    }
}
