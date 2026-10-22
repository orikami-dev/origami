package com.hideo.foldplay;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Page de réglages, une section à la fois, ouverte directement par l'engrenage. */
public class SettingsActivity extends Activity {
    static final String EXTRA_SECTION = "section";
    /** Ouvre directement les réglages avancés de ce cœur (depuis le menu Home d'un jeu). */
    static final String EXTRA_CORE_OPTIONS = "core_options";
    /** Jeu concerné : toute modification à la main fait passer son profil en Personnalisé. */
    static final String EXTRA_GAME_ID = "game_id";
    private static final int REQ_TREE = 11;

    static final String[][] SECTIONS = {
        {"library", "Bibliothèque"},
        {"emulators", "Émulateurs"},
        {"display", "Affichage"},
        {"sound", "Son"},
        {"a11y", "Accessibilité"},
        {"monitoring", "Monitoring"},
        {"updates", "Version"},
    };

    private Store store;
    private String section;
    private LinearLayout body;
    /** Menus codec communs avec le XMB (émulateurs, BIOS, sauvegardes, manettes). */
    private SettingsMenus menus;
    /** Emplacement du résultat de « Rechercher une mise à jour » dans la page courante. */
    private LinearLayout updateResult;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        I18n.attach(this);
        Theme.sync(this);
        Theme.orientation(this);
        DebugLog.sync(this);
        store = new Store(this);
        section = getIntent().getStringExtra(EXTRA_SECTION);
        // Ouvert sans catégorie précise sur un écran étroit : la liste d'abord.
        listOnly = section == null && getResources().getConfiguration().screenWidthDp < 600;
        if (section == null) section = "library";
        if (section.equals("cores")) section = "emulators"; // ancienne catégorie fusionnée
        if (section.equals("about")) section = "updates"; // Mises à jour et À propos réunis (BOSS, 0.66.7)
        menus = new SettingsMenus(host);
        nav = new PadNav(this).hooks(new PadNav.Hooks() {
            public boolean onKey(String k) { return sectionKey(k); }
        });
        render();
        // Réglages avancés d'un cœur hors jeu : la liste vient du dernier lancement du cœur.
        String oc = getIntent().getStringExtra(EXTRA_CORE_OPTIONS);
        if (oc != null) {
            String[] specs = store.coreSpecs(oc);
            if (specs.length == 0) {
                toast("Lance un jeu de cette console pour voir ses réglages.");
                finish();
            } else {
                menus.coreOptions(oc, specs, new Runnable() { public void run() { finish(); } },
                        getIntent().getStringExtra(EXTRA_GAME_ID));
            }
        }
    }

    /** L'hôte des lignes de réglages : ici, la page classique redessinée en entier à chaque changement. */
    private final SettingsRows.Host host = new SettingsRows.Host() {
        public Activity activity() { return SettingsActivity.this; }

        public Store store() { return store; }

        public SettingsMenus menus() { return menus; }

        public void refresh() { render(); }

        public void restyle() { render(); }

        public void relayout() { }

        public void toast(String msg) { SettingsActivity.this.toast(msg); }

        public void pickFolder() {
            try {
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_TREE);
            } catch (ActivityNotFoundException e) {
                toast("Sélecteur de dossiers indisponible.");
            }
        }

        public void rescan() {
            store.requestRescan();
            toast("Bibliothèque relue au retour à l'accueil.");
        }

        public void forgetFolder() {
            store.setTreeUri(null);
            store.requestRescan();
            render();
        }

        public void languageChanged(String sec) {
            startActivity(new Intent(SettingsActivity.this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
            startActivity(new Intent(SettingsActivity.this, SettingsActivity.class)
                    .putExtra(EXTRA_SECTION, sec));
        }

        public void checkUpdate() { SettingsActivity.this.checkUpdate(); }
    };

    /** « Rechercher une mise à jour » : le résultat s'écrit sous la ligne, avec le bouton de téléchargement. */
    private void checkUpdate() {
        final LinearLayout result = updateResult;
        if (result == null) return;
        result.removeAllViews();
        result.addView(text(I18n.tr("Recherche…"), 14, Theme.MUTED));
        Updater.check(SettingsActivity.this, new Updater.Done() {
            public void done(final Updater.Release r) {
                result.removeAllViews();
                if (r.error != null) {
                    result.addView(text(I18n.tr(r.error), 14, Theme.MUTED));
                } else if (!r.newer) {
                    result.addView(text(I18n.tr("Origami est à jour."), 14, Theme.TEXT));
                    if (r.info != null) result.addView(text(r.info, 11, Theme.MUTED));
                } else {
                    result.addView(text("Origami " + r.version, 18, Theme.CODEC));
                    if (r.summary != null && !r.summary.isEmpty()) result.addView(text(r.summary, 14, Theme.TEXT));
                    TextView go = text(I18n.tr("Télécharger et installer") + (r.apkSize > 0 ? " · " + Updater.mo(r.apkSize) + " Mo" : ""), 16, Theme.BG);
                    go.setBackground(Theme.pill(Theme.CODEC, dp(20)));
                    go.setPadding(dp(18), dp(10), dp(18), dp(10));
                    final TextView goBtn = go;
                    go.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            goBtn.setOnClickListener(null);
                            goBtn.setTextColor(Theme.TEXT);
                            goBtn.setText(I18n.tr("Téléchargement… 0 %"));
                            Updater.install(SettingsActivity.this, r, new Updater.Progress() {
                                public void progress(int pct, String detail) {
                                    goBtn.setText(I18n.tr("Téléchargement…") + " " + pct + " % · " + detail);
                                    goBtn.setBackground(Theme.progressPill(pct / 100f, dp(20)));
                                }

                                public void done(boolean ok) {
                                    UiSound.play(SettingsActivity.this, ok ? UiSound.DONE : UiSound.ERROR);
                                    goBtn.setText(I18n.tr(ok ? "Installation : confirme dans la fenêtre Android"
                                            : "Échec du téléchargement"));
                                }
                            });
                        }
                    });
                    // Barre pleine largeur, texte centré (20/10).
                    go.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                    lp.topMargin = dp(12);
                    result.addView(go, lp);
                }
            }
        });
    }

    private PadNav nav;

    /** Écran étroit (couverture, téléphone) : la liste des catégories s'affiche seule. */
    private boolean listOnly;
    private int lastWidthDp;

    /**
     * Deux volets façon réglages Samsung : la liste des catégories reste à gauche (icône et nom),
     * leur contenu à droite. En dessous de 600 dp de large, la liste puis la catégorie en plein
     * écran, avec une flèche pour revenir à la liste. Suit la rotation et le pli à chaud.
     */
    private void render() {
        int wDp = getResources().getConfiguration().screenWidthDp;
        lastWidthDp = wDp;
        boolean twoPane = wDp >= 600;
        if (twoPane) listOnly = false;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackground(Theme.backdrop(0));
        // Droitier : la liste des catégories passe à droite, sous le pouce (Réglages > Accessibilité).
        boolean listRight = twoPane && Theme.RIGHT_HANDED;
        View list = null;
        int listW = twoPane ? Math.max(240, Math.min(340, Math.round(wDp * 0.32f))) : -1;
        if (twoPane || listOnly) {
            list = categories(twoPane);
            if (!listRight) root.addView(list, new LinearLayout.LayoutParams(listW < 0 ? -1 : dp(listW), -1));
        }
        if (!listOnly) {
            LinearLayout main = vertical();
            LinearLayout bar = new LinearLayout(this);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(dp(twoPane ? 20 : 8), dp(16), dp(16), dp(4));
            if (!twoPane) {
                // Étroit : la flèche ramène à la liste des catégories.
                TextView back = text("←", 24, Theme.TEXT);
                back.setPadding(dp(12), dp(4), dp(16), dp(4));
                back.setContentDescription("Retour");
                back.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { toList(); }
                });
                bar.addView(back);
            }
            TextView title = text(sectionTitle(section).toUpperCase(), 20, Theme.CODEC);
            title.setTypeface(Fonts.title(this));
            title.setPadding(dp(6), 0, dp(6), 0);
            if (Theme.RIGHT_HANDED) {
                // Droitier : flèche et titre se lisent depuis le bord droit, sous le pouce (0.65).
                bar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                title.setTextDirection(View.TEXT_DIRECTION_LTR);
                if (bar.getChildCount() > 0) ((TextView) bar.getChildAt(0)).setText("→");
            }
            bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
            if (Theme.RIGHT_HANDED) bar.setGravity(Gravity.CENTER_VERTICAL);
            Theme.mirror(main);
            main.addView(bar);
            body = vertical();
            body.setPadding(dp(twoPane ? 20 : 16), dp(8), dp(twoPane ? 24 : 16), dp(32));
            ScrollView sv = new ScrollView(this);
            sv.addView(body);
            main.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
            root.addView(main, new LinearLayout.LayoutParams(0, -1, 1f));
            if (listRight && list != null) root.addView(list, new LinearLayout.LayoutParams(dp(listW), -1));

            // Les lignes sont décrites une fois pour toutes dans SettingsRows (le XMB de Tsuru les reprend).
            if (section.equals("pads")) pads();
            else renderRows(SettingsRows.build(host, section));
        }
        setContentView(root);
        Theme.fullscreen(this);
        if (nav != null) nav.reset();
    }

    private void toList() {
        listOnly = true;
        render();
    }

    private static String sectionTitle(String id) {
        for (String[] s : SECTIONS) if (s[0].equals(id)) return s[1];
        return id.equals("pads") ? "Manettes" : "Réglages";
    }

    /** Volet des catégories : titre « Réglages », puis une ligne par catégorie dans une carte. */
    private View categories(boolean twoPane) {
        LinearLayout col = vertical();
        col.setPadding(dp(12), dp(16), dp(12), dp(16));
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, 0, 0, dp(12));
        TextView back = text("←", 24, Theme.TEXT);
        back.setPadding(dp(8), dp(4), dp(14), dp(4));
        back.setContentDescription("Retour");
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });
        TextView title = text("RÉGLAGES", 20, Theme.CODEC);
        title.setTypeface(Fonts.title(this));
        title.setPadding(dp(6), 0, dp(6), 0);
        if (Theme.RIGHT_HANDED) {
            // Droitier : flèche et titre depuis le bord droit (0.65).
            bar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            title.setTextDirection(View.TEXT_DIRECTION_LTR);
            back.setText("→");
        }
        bar.addView(back);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(bar);
        Theme.mirror(col);

        LinearLayout card = vertical();
        card.setPadding(dp(6), dp(6), dp(6), dp(6));
        GradientDrawable cbg = new GradientDrawable();
        cbg.setCornerRadius(dp(22));
        cbg.setColor(Theme.PANEL);
        cbg.setStroke(Math.round(dp(1)), Theme.alpha(Theme.CODEC, 0x33));
        card.setBackground(cbg);
        // « Manettes » s'ouvre depuis Émulateurs : c'est elle qui reste allumée.
        String lit = section.equals("pads") ? "emulators" : section;
        for (final String[] s : SECTIONS) {
            card.addView(categoryRow(s[0], s[1], twoPane && s[0].equals(lit), new Runnable() {
                public void run() {
                    section = s[0];
                    listOnly = false;
                    render();
                }
            }));
        }
        col.addView(card, new LinearLayout.LayoutParams(-1, -2));
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setFillViewport(true);
        sv.addView(col);
        return sv;
    }

    private View categoryRow(String icon, String label, boolean on, final Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(14), dp(16), dp(14));
        row.setMinimumHeight(dp(56));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        bg.setColor(on ? (Theme.CODEC & 0x00FFFFFF) | 0x2E000000 : 0);
        GradientDrawable pressed = new GradientDrawable();
        pressed.setCornerRadius(dp(18));
        pressed.setColor(Theme.alpha(Theme.CODEC, 0x33));
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[] {android.R.attr.state_pressed}, pressed);
        states.addState(new int[0], bg);
        row.setBackground(states);
        // Pastille ronde derrière l'icône, comme les réglages Samsung, aux couleurs Codec.
        android.widget.ImageView ic = new android.widget.ImageView(this);
        ic.setImageDrawable(Icons.of(this, icon, on ? Theme.BG : Theme.CODEC, 22));
        ic.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(on ? Theme.CODEC : Theme.PANEL_HI);
        ic.setBackground(dot);
        row.addView(ic, new LinearLayout.LayoutParams(dp(38), dp(38)));
        TextView t = text(label, 16, on ? Theme.CODEC : Theme.TEXT);
        t.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        Theme.marquee(t);
        t.setPadding(dp(14), 0, 0, 0);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { action.run(); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(2), 0, dp(2));
        row.setLayoutParams(lp);
        return row;
    }

    /** L1 / R1 : section précédente ou suivante. */
    private boolean sectionKey(String k) {
        if (!k.equals("L") && !k.equals("R")) return false;
        int i = 0;
        for (int j = 0; j < SECTIONS.length; j++) if (SECTIONS[j][0].equals(section)) i = j;
        i = (i + (k.equals("R") ? 1 : SECTIONS.length - 1)) % SECTIONS.length;
        section = SECTIONS[i][0];
        listOnly = false;
        render();
        return true;
    }

    // ------------------------------------------------------------------ lignes

    /** Dessine les lignes d'une section à la façon du menu classique ; les cases de grille se regroupent. */
    private void renderRows(List<SettingsRows.Row> rows) {
        updateResult = null;
        for (SettingsRows.Row r : rows) {
            if (!r.tile && !gridTiles.isEmpty()) gridEnd();
            switch (r.type) {
                case SettingsRows.Row.HEADER:
                    header(r.title);
                    break;
                case SettingsRows.Row.NOTE:
                    note(r.title);
                    break;
                case SettingsRows.Row.TOGGLE:
                    toggle(r.title, r.desc, r.on, r.toggle);
                    break;
                case SettingsRows.Row.CHOICE:
                    choice(r.title, r.labels, r.index, r.choice);
                    break;
                case SettingsRows.Row.SLIDER:
                    slider(r.title, r.percent, r.choice);
                    break;
                case SettingsRows.Row.ACTION:
                    if (r.tile) tile(r.title, r.desc, r.action);
                    else action(r.title, r.desc, r.action);
                    break;
                case SettingsRows.Row.SLOT:
                    updateResult = vertical();
                    body.addView(updateResult);
                    break;
                case SettingsRows.Row.LOGO: {
                    android.widget.ImageView mark = new android.widget.ImageView(this);
                    mark.setImageDrawable(new Crane.Logo());
                    SettingsRows.bindLogo(mark, SettingsActivity.this);
                    LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(120), dp(120));
                    mlp.gravity = Gravity.CENTER_HORIZONTAL;
                    mlp.topMargin = dp(16);
                    body.addView(mark, mlp);
                    break;
                }
                default:
                    break;
            }
        }
        if (!gridTiles.isEmpty()) gridEnd();
    }

    // ------------------------------------------------------------------ manettes physiques

    private Pads pads;
    private final List<PadView> padViews = new java.util.ArrayList<PadView>();

    private void pads() {
        if (pads == null) pads = new Pads(this);
        toggle("Plein écran avec une manette", "Dès qu'une manette est branchée, jeu plein écran "
                + "et manette à l'écran masquée", store.padFullscreen(), new SettingsRows.Toggle() {
            public void set(boolean on) {
                store.setPadFullscreen(on);
            }
        });
        header("Connectées");
        List<android.view.InputDevice> now = pads.connected();
        java.util.Set<String> seen = new java.util.HashSet<String>();
        if (now.isEmpty()) note("Aucune manette. Associe-la en Bluetooth dans les réglages Android : "
                + "elle apparaîtra ici.");
        else note("Teste les touches : la manette ne pilote pas l'app. "
                + "Pour quitter : touche l'écran ou Select + Start.");
        padViews.clear();
        for (final android.view.InputDevice d : now) {
            final Pads.Profile p = pads.profile(d);
            seen.add(p.id);
            // La manette dessinée, qui réagit en direct à ses touches et à ses sticks.
            PadView pv = new PadView(this, d);
            padViews.add(pv);
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, -2);
            plp.setMargins(0, dp(8), 0, 0);
            body.addView(pv, plp);
            action(p.name, PadView.familyLabel(PadView.family(d)) + " · joueur " + p.player + (p.nintendo ? " · disposition Nintendo" : "")
                    + (p.remap.isEmpty() ? " · touches automatiques" : " · touches réattribuées")
                    + " · toucher pour régler", new Runnable() {
                public void run() { menus.padMenu(d, p); }
            });
        }
        boolean header = false;
        for (final Pads.Profile p : pads.known()) {
            if (seen.contains(p.id)) continue;
            if (!header) {
                header("Déjà vues");
                header = true;
            }
            action(p.name, "Joueur " + p.player + " · toucher pour oublier ses réglages", new Runnable() {
                public void run() {
                    pads.forget(p);
                    render();
                }
            });
        }
        note("Mappage auto : Android reconnaît Xbox, PlayStation, 8BitDo et la plupart des "
                + "manettes Bluetooth ; Nintendo détectées, A/B corrigés. "
                + "Jusqu'à 4 joueurs : la première manette est le joueur 1.");
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        if (section.equals("pads")) for (PadView v : padViews) v.onKey(e);
        android.view.InputDevice d = e.getDevice();
        // Réattribution des touches d'une manette en cours : la touche pressée est retenue.
        if (menus.wizardKey(e)) return true;
        // Une manette pilote aussi les menus codec des réglages.
        CodecMenu open = CodecMenu.find(CodecMenu.hostFor(this));
        if (open != null && Pads.isPad(d)) {
            if (pads == null) pads = new Pads(this);
            String k = Pads.key(pads.profile(d), e.getKeyCode());
            if (k == null && e.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK) k = "B";
            if (k != null) {
                if (e.getAction() == android.view.KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) open.key(k);
                return true;
            }
        }
        // Page des manettes : la manette sert au test des touches et ne pilote pas l'app
        // (rond, B ou Retour ne ferment plus la page). Select + Start ensemble pour quitter.
        if (section.equals("pads") && open == null && Pads.isPad(d)) {
            if (e.getAction() == android.view.KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                for (PadView v : padViews)
                    if (v.deviceId == d.getId() && v.isDown(android.view.KeyEvent.KEYCODE_BUTTON_SELECT)
                            && v.isDown(android.view.KeyEvent.KEYCODE_BUTTON_START)) finish();
            }
            return true;
        }
        if (nav == null) nav = new PadNav(this).hooks(new PadNav.Hooks() {
            public boolean onKey(String k) { return sectionKey(k); }
        });
        if (nav.dispatchKey(e)) return true;
        return super.dispatchKeyEvent(e);
    }

    private final android.hardware.input.InputManager.InputDeviceListener padListener =
            new android.hardware.input.InputManager.InputDeviceListener() {
                public void onInputDeviceAdded(int id) {
                    if (section.equals("pads") && !menus.wizardActive()) render();
                }

                public void onInputDeviceRemoved(int id) {
                    if (section.equals("pads") && !menus.wizardActive()) render();
                }

                public void onInputDeviceChanged(int id) {
                }
            };

    @Override
    protected void onResume() {
        super.onResume();
        if (pads == null) pads = new Pads(this);
        pads.input().registerInputDeviceListener(padListener, null);
        UiSound.musicStart(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        pads.input().unregisterInputDeviceListener(padListener);
        UiSound.musicPause();
    }

    /** Version de Dolphin (libretro) compilée dans l'APK (native/build-dolphin.sh). */
    static final String DOLPHIN_COMMIT = "5466da350f2fd143ee3e7b921e8c33aee64f7065";

    /** Version d'ARMSX2 compilée dans l'APK (native/build-armsx2.sh). */
    static final String ARMSX2_COMMIT = "25e7084fdefcf1e9069481a583c38c103b03e8f1";

    // ------------------------------------------------------------------ résultats

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (menus.onResult(request, result, data)) return;
        if (request != REQ_TREE || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri tree = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // L'accès vaut quand même pour cette session.
        }
        store.setTreeUri(tree.toString());
        store.requestRescan();
        render();
    }

    /** Nom affiché d'un document choisi par le sélecteur Android. */
    static String displayName(android.content.Context ctx, Uri u) {
        String name = u.getLastPathSegment() != null ? new File(u.getLastPathSegment()).getName() : "fichier";
        try {
            android.database.Cursor c = ctx.getContentResolver().query(u,
                    new String[] {android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null) {
                try {
                    if (c.moveToFirst() && c.getString(0) != null) name = new File(c.getString(0)).getName();
                } finally {
                    c.close();
                }
            }
        } catch (Exception ignored) {
            // On garde le nom tiré de l'URI.
        }
        return name;
    }

    // ------------------------------------------------------------------ briques

    /** Réglage à plusieurs valeurs : chaque appui passe à la suivante. */
    private void choice(String title, final String[] labels, int value, final SettingsRows.Choice c) {
        LinearLayout row = row(title, null);
        final TextView v = text(labels[Math.max(0, Math.min(labels.length - 1, value))], 14, Theme.CODEC);
        v.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        v.setPadding(dp(12), 0, dp(4), 0);
        row.addView(v);
        final int[] at = {Math.max(0, Math.min(labels.length - 1, value))};
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                at[0] = (at[0] + 1) % labels.length;
                v.setText(labels[at[0]]);
                c.set(at[0]);
            }
        });
    }

    /** Curseur de volume par paliers de 20 % (0.62.2) ; c reçoit le pourcentage. */
    private void slider(String title, int value, final SettingsRows.Choice c) {
        LinearLayout row = row(title, null);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.NO_GRAVITY);
        View texts = row.getChildAt(0);
        texts.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        final android.widget.SeekBar bar = new android.widget.SeekBar(this);
        bar.setMax(5);
        bar.setProgress(Math.max(0, Math.min(5, Math.round(value / 20f))));
        android.content.res.ColorStateList tint = android.content.res.ColorStateList.valueOf(Theme.CODEC);
        bar.setProgressTintList(tint);
        bar.setThumbTintList(tint);
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Theme.alpha(Theme.CODEC, 0x44)));
        bar.setTickMark(null);
        final TextView v = text(bar.getProgress() * 20 + " %", 14, Theme.CODEC);
        v.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        v.setGravity(Gravity.END);
        line.addView(bar, new LinearLayout.LayoutParams(0, dp(40), 1f));
        line.addView(v, new LinearLayout.LayoutParams(dp(60), -2));
        row.addView(line, new LinearLayout.LayoutParams(-1, -2));
        bar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar sb, int p, boolean fromUser) {
                v.setText(p * 20 + " %");
                if (fromUser) c.set(p * 20);
            }

            public void onStartTrackingTouch(android.widget.SeekBar sb) {
            }

            public void onStopTrackingTouch(android.widget.SeekBar sb) {
            }
        });
    }

    /** Titre de section façon codec : barre verte et lettrage large. */
    private void header(String label) {
        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(0, dp(20), 0, dp(8));
        View bar = new View(this);
        bar.setBackgroundColor(Theme.CODEC);
        h.addView(bar, new LinearLayout.LayoutParams(dp(4), dp(18)));
        TextView t = text(label.toUpperCase(), 12, Theme.CODEC);
        t.setTypeface(Fonts.wide(this));
        t.setLetterSpacing(0.15f);
        t.setPadding(dp(10), 0, 0, 0);
        h.addView(t);
        body.addView(h);
    }

    private LinearLayout row(String title, String subtitle) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setMinimumHeight(dp(56));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        bg.setColor(0xF2121419);
        bg.setStroke(Math.round(dp(1)), Theme.alpha(Theme.CODEC, 0x33));
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        GradientDrawable pressed = new GradientDrawable();
        pressed.setCornerRadius(dp(18));
        pressed.setColor(Theme.alpha(Theme.CODEC, 0x33));
        pressed.setStroke(Math.round(dp(1.5f)), Theme.CODEC);
        states.addState(new int[] {android.R.attr.state_pressed}, pressed);
        states.addState(new int[0], bg);
        row.setBackground(states);
        LinearLayout texts = vertical();
        TextView t = text(title, 16, Theme.TEXT);
        t.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        texts.addView(t);
        if (subtitle != null) texts.addView(text(subtitle, 13, Theme.MUTED));
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(4), 0, dp(4));
        body.addView(row, lp);
        return row;
    }

    // ------------------------------------------------------------------ grille condensée (0.65)

    private final List<View> gridTiles = new ArrayList<View>();

    private void gridStart() {
        gridTiles.clear();
    }

    /** Résumé court pour une case : « Intégré · » et « · au premier lancement » ne servent plus en grille. */
    private static String condensed(String summary) {
        return summary.replace("Intégré · ", "").replace(" · au premier lancement", " · à télécharger")
                .replace(" · livré", " · livré ✓").replace(" · ", "\n");
    }

    /** Une case : nom en gras, puis une à trois lignes d'état serrées. */
    private void tile(String title, String info, final Runnable run) {
        LinearLayout t = vertical();
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        t.setMinimumHeight(dp(64));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(0xF2121419);
        bg.setStroke(Math.round(dp(1)), Theme.alpha(Theme.CODEC, 0x33));
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        GradientDrawable pressed = new GradientDrawable();
        pressed.setCornerRadius(dp(16));
        pressed.setColor(Theme.alpha(Theme.CODEC, 0x33));
        pressed.setStroke(Math.round(dp(1.5f)), Theme.CODEC);
        states.addState(new int[] {android.R.attr.state_pressed}, pressed);
        states.addState(new int[0], bg);
        t.setBackground(states);
        TextView n = text(title, 14, Theme.TEXT);
        n.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        n.setMaxLines(2);
        t.addView(n);
        if (info != null) {
            TextView i = text(info, 11, Theme.MUTED);
            i.setLineSpacing(0, 1.05f);
            i.setMaxLines(3);
            t.addView(i);
        }
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { run.run(); }
        });
        gridTiles.add(t);
    }

    /** Pose les cases en lignes de N colonnes égales ; N suit la largeur (2 à 4), donc la rotation et le pli. */
    private void gridEnd() {
        int wDp = getResources().getConfiguration().screenWidthDp;
        int avail = wDp >= 600 ? Math.round(wDp * 0.68f) : wDp;
        int cols = Math.max(2, Math.min(4, avail / 170));
        for (int i = 0; i < gridTiles.size(); i += cols) {
            LinearLayout line = new LinearLayout(this);
            for (int k = i; k < i + cols; k++) {
                View cell = k < gridTiles.size() ? gridTiles.get(k) : new View(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
                lp.setMargins(dp(3), dp(3), dp(3), dp(3));
                line.addView(cell, lp);
            }
            body.addView(line, new LinearLayout.LayoutParams(-1, -2));
        }
        gridTiles.clear();
    }

    private void action(String title, String subtitle, final Runnable run) {
        LinearLayout row = row(title, subtitle);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                run.run();
            }
        });
    }

    private void toggle(String title, String subtitle, boolean value, final SettingsRows.Toggle t) {
        LinearLayout row = row(title, subtitle);
        // Interrupteur maison (ThemeSwitch), aux couleurs du thème.
        final ThemeSwitch box = new ThemeSwitch(this, value);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(48), dp(48));
        bp.setMargins(dp(8), 0, 0, 0);
        row.addView(box, bp);
        final boolean[] on = {value};
        // Seul l'interrupteur est cliquable : la ligne ne réagit pas (évite les faux appuis).
        box.setClickable(true);
        box.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                on[0] = !on[0];
                box.setOn(on[0]);
                t.set(on[0]);
                UiSound.play(SettingsActivity.this, on[0] ? UiSound.ON : UiSound.OFF);
            }
        });
    }

    private void note(String s) {
        TextView t = text(s, 13, Theme.MUTED);
        t.setPadding(dp(4), dp(10), dp(4), dp(4));
        body.addView(t);
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
        Theme.fullscreen(this);
        Theme.orientation(this);
        // Rotation, pli ou écran externe : les volets se recalculent pour la nouvelle largeur.
        if (c.screenWidthDp != lastWidthDp && !menus.wizardActive()) render();
    }

    /** Retour Android : ferme d'abord le menu codec ouvert (ou revient à son menu parent). */
    @Override
    public void onBackPressed() {
        CodecMenu open = CodecMenu.find(CodecMenu.hostFor(this));
        if (open != null) {
            open.key("B");
            return;
        }
        if (!listOnly && getResources().getConfiguration().screenWidthDp < 600) {
            toList();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean dispatchGenericMotionEvent(android.view.MotionEvent e) {
        boolean used = false;
        if (section.equals("pads")) for (PadView v : padViews) used |= v.onMotion(e);
        if (section.equals("pads") && Pads.isPad(e.getDevice())) return true;
        if (!used && nav != null && nav.dispatchMotion(e)) return true;
        return used || super.dispatchGenericMotionEvent(e);
    }

}
