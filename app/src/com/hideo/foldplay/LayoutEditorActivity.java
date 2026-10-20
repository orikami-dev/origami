package com.hideo.foldplay;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Éditeur de manette : la moitié basse est la vraie zone de jeu, avec sa grille aimantée.
 * Chaque modification est enregistrée aussitôt dans la disposition choisie, qui devient l'active.
 */
public class LayoutEditorActivity extends Activity {
    private static final int REQ_IMAGE = 21;

    private Store store;
    private ControlDeckView deck;
    private final List<ControlLayout> layouts = new ArrayList<ControlLayout>();
    private ControlLayout current;
    private LinearLayout chips;
    private TextView info;
    private boolean ready;
    private LinearLayout styleBox;

    /** Teintes proposées : les couleurs maison d'abord, puis un nuancier large. */
    private static final int[] PALETTE = {
        Theme.PANEL0, Theme.STEEL0, Theme.CODEC0, 0xFF0B0C0F, 0xFF4A505C, 0xFF9AA0AA, 0xFFE8EAED,
        0xFFD84A4A, 0xFFF08C3A, 0xFFF2C94C, 0xFF7BC86C, 0xFF3CC8C8, 0xFF3A7BF0, 0xFF8A5CF0,
        0xFFF05CA8, 0xFF5B6B3A, 0xFF6B4A3A, 0xFF1E2A44,
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        I18n.attach(this);
        Theme.sync(this);
        Theme.orientation(this);
        store = new Store(this);

        LinearLayout root = vertical();
        // Mêmes proportions que la vue Flex : deux pilules séparées par un mince filet.
        root.setBackgroundColor(Theme.GAP);
        root.setPadding(dp(4), dp(4), dp(4), dp(4));

        // Moitié haute : les outils.
        LinearLayout tools = vertical();
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(12), dp(16), dp(4));
        TextView back = text("←", 24, Theme.TEXT);
        back.setPadding(dp(12), dp(4), dp(16), dp(4));
        back.setContentDescription("Retour");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        bar.addView(back);
        TextView title = text("MANETTE", 20, Theme.CODEC);
        title.setTypeface(Fonts.logo(this));
        bar.addView(title);
        tools.addView(bar);

        chips = new LinearLayout(this);
        chips.setPadding(dp(12), dp(4), dp(12), dp(4));
        tools.addView(scroller(chips));

        // Catégories : une seule page d'outils à la fois, plutôt qu'une longue liste.
        // Barre d'onglets fixe sous les dispositions ; seule la page de l'onglet défile.
        // Deux volets comme les réglages : les catégories à gauche, toujours visibles, la page de
        // l'onglet à droite. Sur un écran étroit, la colonne garde seulement les icônes.
        tabRow = vertical();
        tabRow.setPadding(dp(8), dp(6), dp(8), dp(8));
        railScroll = new ScrollView(this);
        railScroll.setVerticalScrollBarEnabled(false);
        // Largeur propre à la colonne : ajoutée sans paramètres, elle prenait tout l'écran (0.40).
        railScroll.addView(tabRow, new android.widget.FrameLayout.LayoutParams(-1, -2));
        LinearLayout pane = new LinearLayout(this);
        pane.setOrientation(LinearLayout.HORIZONTAL);
        pane.addView(railScroll, new LinearLayout.LayoutParams(-2, -1));
        View rule = new View(this);
        rule.setBackgroundColor(0x22FFFFFF);
        pane.addView(rule, new LinearLayout.LayoutParams(Math.max(1, dp(1)), -1));
        styleBox = vertical();
        styleBox.setPadding(0, dp(6), 0, dp(8));
        info = text("", 12, Theme.MUTED);
        info.setPadding(dp(16), dp(10), dp(16), dp(12));
        ScrollView sv = new ScrollView(this);
        sv.addView(styleBox);
        pane.addView(sv, new LinearLayout.LayoutParams(0, -1, 1f));
        tools.addView(pane, new LinearLayout.LayoutParams(-1, 0, 1f));
        tools.setBackground(Theme.pill(Theme.BG, dp(28)));
        tools.setClipToOutline(true);
        root.addView(tools, new LinearLayout.LayoutParams(-1, 0, 1f));

        root.addView(new View(this), new LinearLayout.LayoutParams(-1, dp(4)));

        // Moitié basse : la même zone que la manette en vue Flex.
        deck = new ControlDeckView(this);
        deck.setEditing(true);
        deck.setKeyListener(null, store.haptics());
        deck.setEditListener(new ControlDeckView.EditListener() {
            @Override
            public void onPicked(ControlLayout.Control c) {
                showInfo();
                renderStyle();
            }

            @Override
            public void onChanged() {
                save();
                showInfo();
            }
        });
        deck.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                if (!ready && r - l > 0) {
                    ready = true;
                    v.post(new Runnable() {
                        public void run() { loadLayouts(); }
                    });
                }
            }
        });
        root.addView(deck, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        Theme.fullscreen(this);
    }

    private void loadLayouts() {
        layouts.clear();
        layouts.addAll(store.layouts());
        if (layouts.isEmpty()) layouts.add(ControlLayout.classic(deck.columns(), deck.rows()));
        current = null;
        for (ControlLayout l : layouts) if (l.name.equals(store.activeLayout())) current = l;
        if (current == null) current = layouts.get(0);
        open(current);
    }

    private void open(ControlLayout l) {
        current = l;
        if (l.kind.equals("flex")) deck.setSystem(flexSystem);
        deck.setLayout(l);
        save();
        renderChips();
        showInfo();
        renderStyle();
    }

    private void save() {
        if (current == null) return;
        store.saveLayouts(layouts);
        store.setActiveLayout(current.name);
    }

    private void showInfo() {
        ControlLayout.Control c = deck.picked();
        String s = c == null
                ? "Touche une touche pour la choisir, fais-la glisser pour la déplacer, tire un des quatre coins verts ou pince "
                        + "pour la redimensionner. Sans touche choisie, glisser et pincer déplacent et zooment l'image de fond."
                : "Touche " + c.type + " · " + c.w + " × " + c.h + " cases. Glisse pour la déplacer, un coin vert ou "
                        + "pincement pour la taille.";
        info.setText(s + "\nLa disposition active est celle surlignée, elle est enregistrée à chaque changement.");
    }

    private void renderChips() {
        chips.removeAllViews();
        for (final ControlLayout l : layouts) {
            // Mini : en chantier (0.60.3), ni ouverte ni modifiable.
            if (l.floating()) {
                chips.addView(chip(l.name + " · en chantier", false, new Runnable() {
                    public void run() { toast("La manette Mini est en chantier."); }
                }));
                continue;
            }
            boolean on = l == current;
            chips.addView(chip(l.name, on, new Runnable() {
                public void run() { open(l); }
            }));
        }
        chips.addView(chip("＋ Nouvelle", false, new Runnable() {
            public void run() {
                ask("Nouvelle disposition", unique("Perso"), new Named() {
                    public void done(String name) {
                        ControlLayout l = ControlLayout.classic(deck.columns(), deck.rows());
                        l.name = name;
                        layouts.add(l);
                        open(l);
                    }
                });
            }
        }));
    }

    // ------------------------------------------------------------------ actions

    private void addControl() {
        CodecMenu m = new CodecMenu(this).title("Ajouter une touche", current == null ? null : current.name);
        for (final String type : ControlLayout.TYPES) {
            m.item(ControlLayout.label(type), null, new Runnable() {
                public void run() { deck.addControl(type); }
            });
        }
        m.show(CodecMenu.hostFor(this));
    }

    private static String[] typeLabels() {
        String[] out = new String[ControlLayout.TYPES.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = ControlLayout.label(ControlLayout.TYPES[i]);
        }
        return out;
    }

    private void rename() {
        if (current == null) return;
        ask("Renommer", current.name, new Named() {
            public void done(String name) {
                if (!name.equals(current.name)) current.name = unique(name);
                save();
                renderChips();
            }
        });
    }

    private void duplicate() {
        if (current == null) return;
        ControlLayout copy = current.copy(unique(current.name + " 2"));
        layouts.add(copy);
        open(copy);
    }

    /** Console dont on règle la Flex dans l'éditeur (0.62.3) : la réinitialisation remet son modèle. */
    private static String flexSystem = "ps3";

    /** Choix de la console : la Flex prend sa forme, et « Réinitialiser » rend son modèle exact. */
    private void consoles() {
        if (current == null || !current.kind.equals("flex") || current.split) return;
        styleBox.addView(label("CONSOLE"));
        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(12), dp(2), dp(12), dp(4));
        for (final String[] s : ControlDeckView.TOUR) {
            row.addView(chip(s[1], s[0].equals(flexSystem), new Runnable() {
                public void run() {
                    flexSystem = s[0];
                    deck.setSystem(flexSystem);
                    deck.setLayout(current);
                    renderStyle();
                }
            }));
        }
        styleBox.addView(scroller(row));
    }

    private void reset() {
        if (current == null) return;
        current.controls.clear();
        if (current.kind.equals("flex")) current.family = flexSystem;
        ControlLayout d = current.origin(deck.columns(), deck.rows());
        current.controls.addAll(d.controls);
        current.split = d.split;
        current.splitScale = 100;
        current.leftDx = current.leftDy = current.rightDx = current.rightDy = 0;
        current.refCols = d.refCols;
        current.refRows = d.refRows;
        current.stock = true;
        deck.setLayout(current);
        save();
        toast("Disposition d'origine remise.");
    }

    private void deleteLayout() {
        if (current == null) return;
        if (layouts.size() == 1) {
            toast("Il faut garder au moins une disposition.");
            return;
        }
        final ControlLayout doomed = current;
        new CodecMenu(this).title("Supprimer « " + doomed.name + " » ?", "DISPOSITION")
                .item("Annuler", null, null)
                .item("Supprimer", "Ses touches et son image de fond seront effacées", new Runnable() {
                    public void run() {
                        layouts.remove(doomed);
                        deleteBackgroundFile(doomed.background);
                        open(layouts.get(0));
                    }
                })
                .show(CodecMenu.hostFor(this));
    }

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");
        try {
            startActivityForResult(i, REQ_IMAGE);
        } catch (ActivityNotFoundException e) {
            toast("Sélecteur d'images indisponible.");
        }
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req != REQ_IMAGE || result != RESULT_OK || data == null || data.getData() == null || current == null) return;
        // On garde une copie : l'image reste même si l'original est déplacé ou supprimé.
        File dir = new File(getFilesDir(), "backgrounds");
        dir.mkdirs();
        File f = new File(dir, "bg-" + System.currentTimeMillis() + ".img");
        try {
            InputStream in = getContentResolver().openInputStream(data.getData());
            OutputStream out = new FileOutputStream(f);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.close();
            in.close();
        } catch (Exception e) {
            toast("Impossible de lire cette image.");
            return;
        }
        deleteBackgroundFile(current.background);
        current.background = Uri.fromFile(f).toString();
        current.bgX = 0;
        current.bgY = 0;
        current.bgScale = 1;
        deck.reloadBackground();
        save();
        toast("Fond ajouté : glisse pour le déplacer, pince pour zoomer.");
    }

    private void recenter() {
        if (current == null || current.background == null) return;
        current.bgX = 0;
        current.bgY = 0;
        current.bgScale = 1;
        deck.invalidate();
        save();
    }

    private void removeBackground() {
        if (current == null || current.background == null) return;
        deleteBackgroundFile(current.background);
        current.background = null;
        deck.reloadBackground();
        save();
    }

    /** Supprime la copie de l'image si plus aucune disposition ne s'en sert. */
    private void deleteBackgroundFile(String uri) {
        if (uri == null) return;
        for (ControlLayout l : layouts) if (l != current && uri.equals(l.background)) return;
        String path = Uri.parse(uri).getPath();
        if (path != null && path.startsWith(getFilesDir().getPath())) new File(path).delete();
    }

    private String unique(String base) {
        String name = base.trim().isEmpty() ? "Perso" : base.trim();
        String out = name;
        int n = 2;
        while (exists(out)) out = name + " " + n++;
        return out;
    }

    private boolean exists(String name) {
        for (ControlLayout l : layouts) if (l != current && l.name.equals(name)) return true;
        return false;
    }

    private interface Named {
        void done(String name);
    }

    private void ask(String title, String value, final Named cb) {
        final EditText e = new EditText(this);
        e.setText(value);
        e.setSelectAllOnFocus(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setTextColor(Theme.TEXT);
        Theme.tint(e);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(e, new LinearLayout.LayoutParams(-1, -2));
        Theme.show(new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(title)
                .setView(box)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String s = e.getText().toString().trim();
                        if (!s.isEmpty()) cb.done(s);
                    }
                })
                );
    }

    // ------------------------------------------------------------------ style

    private interface Pick {
        void color(int c);
    }

    private static final String[][] TABS = {
        {"keys", "Touches"}, {"layout", "Disposition"}, {"bg", "Fond"},
        {"haptics", "Vibrations"},
    };
    private static String tab = "keys";
    private LinearLayout tabRow;
    private ScrollView railScroll;
    /** Ce que la roue colore : la manette, les touches, l'accent ou la touche choisie. */

    /** Onglets et page de la catégorie choisie. */
    private void renderStyle() {
        if (styleBox == null || current == null) return;
        tabRow.removeAllViews();
        android.view.ViewGroup.LayoutParams rl = railScroll.getLayoutParams();
        int want = labels() ? dp(Math.max(180, Math.min(240, getResources().getConfiguration().screenWidthDp / 4))) : dp(66);
        if (rl != null && rl.width != want) {
            rl.width = want;
            railScroll.setLayoutParams(rl);
        }
        for (final String[] t : TABS) tabRow.addView(tabView(t[0], t[1]));
        styleBox.removeAllViews();
        if (info.getParent() != null) ((LinearLayout) info.getParent()).removeView(info);
        if (tab.equals("keys") && current.split) {
            styleBox.addView(hint("Touches figées : glisse une moitié pour la déplacer, pince pour la taille."));
        } else if (tab.equals("keys")) {
            styleBox.addView(scroller(buttons(
                    new String[] {"＋ Ajouter", "Retirer la touche", "− Taille", "＋ Taille"},
                    new Runnable[] {
                        new Runnable() { public void run() { addControl(); } },
                        new Runnable() { public void run() { deck.removePicked(); } },
                        new Runnable() { public void run() { deck.resizePicked(-1); } },
                        new Runnable() { public void run() { deck.resizePicked(1); } },
                    })));
            styleBox.addView(info);
        } else if (tab.equals("layout")) {
            styleBox.addView(scroller(buttons(
                    new String[] {"Renommer", "Dupliquer", "Réinitialiser", "Supprimer"},
                    new Runnable[] {
                        new Runnable() { public void run() { rename(); } },
                        new Runnable() { public void run() { duplicate(); } },
                        new Runnable() { public void run() { reset(); } },
                        new Runnable() { public void run() { deleteLayout(); } },
                    })));
            consoles();
            if (current.split) {
                final ControlLayout l = current;
                styleBox.addView(slider("Taille de la manette", l.splitScale, 60, 130, new Pick() {
                    public void color(int v) {
                        l.splitScale = v;
                        deck.requestLayout();
                        deck.invalidate();
                    }
                }));
                styleBox.addView(scroller(buttons(new String[] {
                    (l.splitLinked ? "□ Délier les deux moitiés" : "■ Délier les deux moitiés"), "Remettre en place"},
                    new Runnable[] {
                        new Runnable() {
                            public void run() {
                                l.splitLinked = !l.splitLinked;
                                save();
                                renderStyle();
                            }
                        },
                        new Runnable() {
                            public void run() {
                                l.leftDx = l.leftDy = l.rightDx = l.rightDy = 0;
                                l.splitScale = 100;
                                deck.requestLayout();
                                deck.invalidate();
                                save();
                                renderStyle();
                            }
                        },
                    })));
                styleBox.addView(hint("Disposition Flex : les touches sont figées. Fais glisser une moitié sur la "
                        + "manette pour la déplacer" + (l.splitLinked ? ", l'autre suit en miroir." : ".")
                        + " Le joystick gauche sert aussi de croix en jeu."));
            }
        } else if (tab.equals("bg")) {
            styleBox.addView(scroller(buttons(
                    new String[] {"Choisir une image", "Recentrer", "Retirer"},
                    new Runnable[] {
                        new Runnable() { public void run() { pickImage(); } },
                        new Runnable() { public void run() { recenter(); } },
                        new Runnable() { public void run() { removeBackground(); } },
                    })));
            styleBox.addView(hint("Sans touche choisie, glisse et pince sur la manette pour placer l'image."));
        } else {
            hapticsPage();
        }
    }

    /** Assez de place pour écrire le nom des catégories à côté de leur icône. */
    private boolean labels() {
        return getResources().getConfiguration().screenWidthDp >= 600;
    }

    /** Un onglet de la colonne des catégories : son icône, et son nom quand la place le permet. */
    private View tabView(final String id, String label) {
        return railRow(TAB_ICONS[indexOfTab(id)], labels() ? label : null, id.equals(tab), new Runnable() {
            public void run() {
                if (id.equals(tab)) return;
                tab = id;
                renderStyle();
            }
        });
    }

    private static final String[] TAB_ICONS = {"keys", "layouts", "covers", "colors", "vibration"};

    private static int indexOfTab(String id) {
        for (int i = 0; i < TABS.length; i++) if (TABS[i][0].equals(id)) return i;
        return 0;
    }

    private View railRow(String icon, String label, boolean on, final Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(10), dp(label != null ? 16 : 10), dp(10));
        row.setBackground(Theme.pill(on ? (Theme.CODEC & 0x00FFFFFF) | 0x2E000000 : 0x00000000, dp(16)));
        android.widget.ImageView ic = new android.widget.ImageView(this);
        ic.setImageDrawable(Icons.of(this, icon, on ? Theme.BG : Theme.CODEC, 20));
        ic.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
        dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        dot.setColor(on ? Theme.CODEC : Theme.PANEL_HI);
        ic.setBackground(dot);
        row.addView(ic, new LinearLayout.LayoutParams(dp(34), dp(34)));
        if (label != null) {
            TextView t = text(label, 14, on ? Theme.CODEC : Theme.TEXT);
            Theme.marquee(t);
            t.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD));
            t.setPadding(dp(12), 0, 0, 0);
            row.addView(t);
        }
        row.setContentDescription(label != null ? label : icon);
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { action.run(); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(2), 0, dp(2));
        row.setLayoutParams(lp);
        return row;
    }

    /** L1 / R1 : onglet précédent ou suivant. */
    private boolean tabKey(String k) {
        if (!k.equals("L") && !k.equals("R")) return false;
        int i = indexOfTab(tab);
        i = (i + (k.equals("R") ? 1 : TABS.length - 1)) % TABS.length;
        tab = TABS[i][0];
        renderStyle();
        return true;
    }

    private PadNav nav;

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        if (nav == null) nav = new PadNav(this).hooks(new PadNav.Hooks() {
            public boolean onKey(String k) { return tabKey(k); }
        });
        return nav.dispatchKey(e) || super.dispatchKeyEvent(e);
    }

    @Override
    public boolean dispatchGenericMotionEvent(android.view.MotionEvent e) {
        if (nav == null) nav = new PadNav(this).hooks(new PadNav.Hooks() {
            public boolean onKey(String k) { return tabKey(k); }
        });
        return nav.dispatchMotion(e) || super.dispatchGenericMotionEvent(e);
    }

    private TextView hint(String s) {
        TextView t = text(s, 12, Theme.MUTED);
        t.setPadding(dp(16), dp(8), dp(16), dp(12));
        return t;
    }

    private void hapticsPage() {
        int level = store.hapticLevel();
        String[] names = {"Coupée", "Légère", "Moyenne", "Forte"};
        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(12), dp(4), dp(12), dp(4));
        for (int i = 0; i < names.length; i++) {
            final int lv = i;
            row.addView(chip(names[i], i == level, new Runnable() {
                public void run() {
                    store.setHapticLevel(lv);
                    deck.setKeyListener(null, lv > 0);
                    Haptics.tap(deck);
                    renderStyle();
                }
            }));
        }
        styleBox.addView(scroller(row));

    }

    private void styleChanged() {
        deck.applyStyle();
        save();
        renderStyle();
    }

    private View swatches(String name, int selected, boolean withDefault, final Pick pick) {
        LinearLayout box = vertical();
        TextView t = text(name, 13, Theme.TEXT);
        t.setPadding(dp(18), dp(6), dp(16), dp(2));
        box.addView(t);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(2), dp(12), dp(4));
        if (withDefault) row.addView(chip("Comme les autres", selected == 0, new Runnable() {
            public void run() { pick.color(0); styleChanged(); }
        }));
        for (final int c : PALETTE) {
            View v = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(c);
            boolean on = c == selected;
            g.setStroke(dp(on ? 3 : 1), on ? Theme.TEXT : 0x55FFFFFF);
            v.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(on ? 38 : 32), dp(on ? 38 : 32));
            lp.setMargins(dp(4), dp(2), dp(4), dp(2));
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View x) {
                    pick.color(c);
                    styleChanged();
                }
            });
            row.addView(v, lp);
        }
        box.addView(scroller(row));
        return box;
    }

    private View slider(final String name, int value, final int min, final Pick pick) {
        return slider(name, value, min, 100, pick);
    }

    private View slider(final String name, int value, final int min, int max, final Pick pick) {
        LinearLayout box = vertical();
        final TextView t = text(name + " · " + value + " %", 13, Theme.TEXT);
        t.setPadding(dp(18), dp(6), dp(16), 0);
        box.addView(t);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        Theme.tint(sb);
        sb.setProgress(value - min);
        sb.setPadding(dp(24), dp(6), dp(24), dp(6));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (!user) return;
                pick.color(p + min);
                t.setText(name + " · " + (p + min) + " %");
                deck.applyStyle();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                save();
            }
        });
        box.addView(sb, new LinearLayout.LayoutParams(-1, -2));
        return box;
    }

    // ------------------------------------------------------------------ vues

    /**
     * Rangée de boutons : elle passe à la ligne au lieu de défiler à l'horizontale (0.62.3,
     * demande de BOSS : les longs boutons à faire glisser étaient pénibles).
     */
    private View scroller(View content) {
        if (!(content instanceof LinearLayout)) return content;
        LinearLayout row = (LinearLayout) content;
        FlowRow flow = new FlowRow(this, 0);
        flow.setPadding(row.getPaddingLeft(), row.getPaddingTop(), row.getPaddingRight(), row.getPaddingBottom());
        while (row.getChildCount() > 0) {
            View v = row.getChildAt(0);
            row.removeViewAt(0);
            flow.addView(v);
        }
        return flow;
    }

    private LinearLayout buttons(String[] labels, Runnable[] actions) {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(12), dp(2), dp(12), dp(4));
        for (int i = 0; i < labels.length; i++) row.addView(chip(labels[i], false, actions[i]));
        return row;
    }

    private TextView label(String s) {
        TextView t = text(s, 11, Theme.MUTED);
        t.setTypeface(Fonts.wide(this));
        t.setLetterSpacing(0.2f);
        t.setPadding(dp(18), dp(10), dp(16), dp(2));
        return t;
    }

    private TextView chip(String label, boolean on, final Runnable action) {
        TextView t = text(label, 15, on ? Theme.BG : Theme.TEXT);
        t.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        t.setPadding(dp(16), dp(10), dp(16), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        bg.setColor(on ? Theme.CODEC : Theme.PANEL_HI);
        t.setBackground(bg);
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(3), dp(2), dp(3), dp(2));
        t.setLayoutParams(lp);
        return t;
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private void toast(String msg) {
        I18n.toast(this, msg, Toast.LENGTH_LONG).show();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) Theme.fullscreen(this);
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        Theme.orientation(this);
        // Rotation ou pli : la colonne des catégories montre ou cache les noms selon la largeur.
        renderStyle();
    }

    /** Retour Android : ferme d'abord le menu codec ouvert (ou revient à son menu parent). */
    @Override
    public void onBackPressed() {
        CodecMenu open = CodecMenu.find(CodecMenu.hostFor(this));
        if (open != null) {
            open.key("B");
            return;
        }
        super.onBackPressed();
    }
}
