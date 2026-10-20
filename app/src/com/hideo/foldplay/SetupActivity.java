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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Assistant de démarrage rapide, au premier lancement (et depuis Réglages > À propos) :
 * dossier de jeux, moteurs intégrés, émulateurs externes, manette, fiches. Chaque étape se passe.
 */
public class SetupActivity extends Activity {
    private static final int REQ_TREE = 31;
    private static final int REQ_BIOS = 32;
    private String biosReport;
    private static String demo;
    /**
     * Deux parcours (0.62) : l'installation simple (dossier de jeux et style, tout le reste réglé
     * pour toi) et l'installation avancée (moteurs, BIOS, manette, fiches en plus).
     */
    private static final String[] SIMPLE = {"Bienvenue", "Jeux", "Style", "Prêt"};
    private static final String[] ADVANCED = {"Bienvenue", "Jeux", "Moteurs", "BIOS", "Manette", "Style", "Fiches", "Prêt"};

    private String[] steps() {
        return store.setupAdvanced() ? ADVANCED : SIMPLE;
    }

    private String current() {
        return steps()[step];
    }

    private Store store;
    private int step;
    private LinearLayout page;
    private LinearLayout dots;
    private TextView next;
    private TextView back;
    private volatile boolean downloading;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        I18n.attach(this);
        Theme.sync(this);
        Theme.orientation(this);
        store = new Store(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Theme.backdrop(0));
        root.setPadding(dp(4), dp(4), dp(4), dp(4));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Theme.pill(Theme.BG, dp(28)));
        card.setClipToOutline(true);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(24), dp(20), dp(24), dp(4));
        TextView logo = text("ORIGAMI", 22, Theme.CODEC);
        logo.setTypeface(Fonts.logo(this));
        android.widget.ImageView mark = new android.widget.ImageView(this);
        mark.setImageDrawable(new Crane.Logo());
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(44), dp(44));
        mlp.rightMargin = dp(10);
        head.addView(mark, mlp);
        head.addView(logo, new LinearLayout.LayoutParams(0, -2, 1f));
        dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(dots);
        card.addView(head);

        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(12), dp(24), dp(12));
        ScrollView sv = new ScrollView(this);
        sv.addView(page);
        card.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(16), dp(8), dp(16), dp(18));
        back = button("Retour", false);
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                go(step - 1);
            }
        });
        nav.addView(back);
        nav.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        next = button("Suivant", true);
        next.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (step == steps().length - 1) finishSetup();
                else {
                    UiSound.play(SetupActivity.this, UiSound.NEXT);
                    go(step + 1);
                }
            }
        });
        nav.addView(next);
        card.addView(nav);
        Theme.mirror(card);

        root.addView(card, new LinearLayout.LayoutParams(-1, -1));
        setContentView(root);
        Theme.fullscreen(this);
        // Reprise là où l'on s'était arrêté (autre app ouverte entre-temps, app relancée par Android).
        go(state != null ? state.getInt("step", 0) : store.setupStep());
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("step", step);
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
    }

    @Override
    public void onBackPressed() {
        if (step > 0) go(step - 1);
        else super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Retour du sélecteur de fichiers : on met à jour les coches « importé ».
        if (current().equals("BIOS")) go(step);
    }

    private void finishSetup() {
        store.setSetupDone(true);
        store.setSetupStep(0);
        if (store.treeUri() != null) store.requestRescan();
        finish();
    }

    // ------------------------------------------------------------------ étapes

    private void go(int s) {
        String[] steps = steps();
        step = Math.max(0, Math.min(steps.length - 1, s));
        store.setSetupStep(step);
        page.removeAllViews();
        dots.removeAllViews();
        for (int i = 0; i < steps.length; i++) {
            View d = new View(this);
            d.setBackground(Theme.pill(i == step ? Theme.CODEC : i < step ? Theme.darken(Theme.CODEC, 0.5f)
                    : Theme.PANEL_HI, dp(4)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(i == step ? 22 : 8), dp(8));
            lp.setMargins(dp(3), 0, dp(3), 0);
            dots.addView(d, lp);
        }
        back.setVisibility(step == 0 ? View.INVISIBLE : View.VISIBLE);
        next.setText(step == 0 ? "Commencer" : step == steps.length - 1 ? "Jouer" : "Suivant");
        next.setVisibility(step == 0 ? View.GONE : View.VISIBLE);
        String name = steps[step];
        title(step == 0 ? name : name + "  " + step + "/" + (steps.length - 1));
        if (name.equals("Bienvenue")) welcome();
        else if (name.equals("Jeux")) folder();
        else if (name.equals("Moteurs")) cores();
        else if (name.equals("BIOS")) bios();
        else if (name.equals("Manette")) pad();
        else if (name.equals("Style")) style();
        else if (name.equals("Fiches")) sheets();
        else done();
    }

    private void welcome() {
        // Premier écran : la langue d'abord, pour lire la suite dans la sienne.
        label("LANGUE · LANGUAGE");
        row(I18n.choiceName(this), "Touche pour changer · Tap to change", new Runnable() {
            public void run() {
                I18n.pick(SetupActivity.this, new Runnable() {
                    public void run() { recreate(); }
                });
            }
        });
        if (!store.languageChosen()) store.setLanguage("system");
        para("Bienvenue. Origami range tes jeux par console et les lance dans ses émulateurs "
                + "intégrés, avec une manette à l'écran adaptée à ton Fold.");
        label("INSTALLATION");
        row("Simple  (recommandée)", "Deux étapes : ton dossier de jeux et ton style. Le reste est réglé pour toi, "
                + "les moteurs se téléchargent au premier jeu", new Runnable() {
            public void run() {
                store.setSetupAdvanced(false);
                go(1);
            }
        });
        row("Avancée", "Moteurs hors ligne, BIOS et firmware, manette, vibrations, fiches des jeux", new Runnable() {
            public void run() {
                store.setSetupAdvanced(true);
                go(1);
            }
        });
        note("Tout se change ensuite dans Réglages. Origami ne fournit ni jeux ni BIOS : utilise les copies de "
                + "tes propres jeux.");
    }

    private void folder() {
        para("Choisis ton dossier de jeux. Ranger par console (gba/, ps2/…) aide, mais Origami "
                + "reconnaît aussi les jeux par leur contenu.");
        String tree = store.treeUri();
        row(tree == null ? "Choisir le dossier de jeux" : "Dossier choisi  ✓",
                tree == null ? "Ouvre le sélecteur de fichiers d'Android" : readable(tree), new Runnable() {
                    public void run() {
                        try {
                            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_TREE);
                        } catch (ActivityNotFoundException e) {
                            note("Sélecteur de dossiers indisponible.");
                        }
                    }
                });
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQ_BIOS && result == RESULT_OK && data != null) {
            biosReport = BiosImport.fromIntent(this, data).text();
            go(step);
            return;
        }
        if (request != REQ_TREE || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri tree = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // L'accès vaut quand même pour cette session.
        }
        store.setTreeUri(tree.toString());
        store.requestRescan();
        go(1);
    }

    private void cores() {
        final CoreManager cm = new CoreManager(this);
        final List<String> all = CoreManager.allCores();
        para("Le moteur intégré fait tourner ces consoles dans Origami, avec des cœurs libretro "
                + "open source. Installe-les pour jouer hors ligne.");
        if (!Libretro.available()) {
            note("Demande un appareil 64 bits (arm64).");
            return;
        }
        int missing = 0;
        for (String c : all) {
            boolean has = cm.installed(c);
            if (!has) missing++;
            line((has ? "■  " : "□  ") + c, CoreManager.systemsOf(c), has);
        }
        final TextView status = text(missing == 0 ? "Tous les moteurs sont installés." : "", 13, Theme.MUTED);
        final ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        Theme.tint(bar);
        bar.setVisibility(View.GONE);
        if (missing > 0) {
            row(downloading ? "Installation en cours…" : "Tout installer (" + missing + ")",
                    "Téléchargés depuis libretro, le projet officiel", new Runnable() {
                        public void run() {
                            if (!downloading) downloadAll(cm, all, status, bar);
                        }
                    });
        }
        page.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        page.addView(status);
        note("Sinon, chaque moteur se télécharge au premier jeu.");
    }

    private void downloadAll(final CoreManager cm, final List<String> all, final TextView status, final ProgressBar bar) {
        downloading = true;
        bar.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            public void run() {
                int n = 0;
                int failed = 0;
                for (final String c : all) {
                    n++;
                    if (cm.installed(c)) continue;
                    final int index = n;
                    ui(status, "Installation de " + c + " (" + index + "/" + all.size() + ")…", bar, -1);
                    try {
                        cm.download(c, new CoreManager.Progress() {
                            public void onProgress(long done, long total) {
                                if (total > 0) ui(null, null, bar, (int) (done * 1000 / total));
                            }
                        });
                    } catch (Exception e) {
                        failed++;
                    }
                }
                downloading = false;
                final int f = failed;
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (current().equals("Moteurs")) go(step);
                        if (f > 0) note(f + " moteur(s) n'ont pas pu être téléchargés : vérifie la connexion, "
                                + "ils se réessaieront au premier jeu.");
                    }
                });
            }
        }, "foldplay-setup-cores").start();
    }

    private void ui(final TextView t, final String msg, final ProgressBar bar, final int value) {
        runOnUiThread(new Runnable() {
            public void run() {
                if (t != null && msg != null) t.setText(msg);
                if (value >= 0) bar.setProgress(value);
            }
        });
    }

    private void bios() {
        para("Certaines consoles demandent un fichier de ta propre console : le BIOS (PS1 en HD, PS2) et le "
                + "firmware de la PS3 (PS3UPDAT.PUP, sur le site de Sony). Origami les reconnaît et les range tout seul.");
        row("Importer des BIOS", "Un ou plusieurs fichiers, ou une archive .zip", new Runnable() {
            public void run() {
                try {
                    startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), REQ_BIOS);
                } catch (ActivityNotFoundException e) {
                    note("Sélecteur de fichiers indisponible sur cet appareil.");
                }
            }
        });
        row("Installer le firmware PS3" + (Ps3Core.firmwareInstalled(this) ? "  ✓" : ""), "PS3UPDAT.PUP",
                new Runnable() {
            public void run() {
                startActivity(new Intent(SetupActivity.this, Ps3Activity.class).putExtra(Ps3Activity.EXTRA_FIRMWARE, true));
            }
        });
        if (biosReport != null) note(biosReport);
        note("Rien à importer ? Passe : Origami te le redemandera au premier jeu qui en a besoin.");
    }

    /** Thème, bruits de l'interface et musique d'accueil : le style d'Origami, choisi d'un coup d'œil. */
    private void style() {
        para("Choisis l'ambiance d'Origami. Couleurs, bruits des menus et musique d'accueil suivent le thème.");
        String cur = store.theme();
        for (int i = 0; i < Theme.THEMES.length; i++) {
            final String id = Theme.THEMES[i];
            row((id.equals(cur) ? "■  " : "□  ") + Theme.THEME_NAMES[i], Theme.THEME_HELP[i], new Runnable() {
                public void run() {
                    store.setTheme(id);
                    store.setSyncUi(false);
                    Theme.queueIcon(SetupActivity.this, id);
                    // La musique du thème choisi démarre aussitôt (0.62.3).
                    UiSound.musicStart(SetupActivity.this);
                    UiSound.play(SetupActivity.this, UiSound.SELECT);
                    recreate();
                }
            });
        }
        sub("Options audio");
        final boolean sound = store.menuSound();
        row((sound ? "■  " : "□  ") + "Bruits de l'interface", "Déplacement, validation, lancement d'un jeu", new Runnable() {
            public void run() {
                store.setMenuSound(!sound);
                UiSound.play(SetupActivity.this, sound ? UiSound.OFF : UiSound.ON);
                go(step);
            }
        });
        final boolean music = store.musicOn();
        row((music ? "■  " : "□  ") + "Musique d'accueil", "Une boucle par thème, coupée pendant les parties", new Runnable() {
            public void run() {
                store.setMusicOn(!music);
                if (!music) UiSound.musicStart(SetupActivity.this);
                else UiSound.musicPause();
                go(step);
            }
        });
    }

    private void pad() {
        para("Choisis la manette à l'écran, modifiable ensuite dans l'éditeur.");
        final String active = store.activeLayout();
        final String[][] kinds = {
            {"Flex", "Notre manette phare : elle prend la forme de la manette de chaque console, touches comprises"},
            {"2D", "Touches d'arcade : levier à gauche, boutons ronds en deux rangées"},
            {"3D", "Manette moderne : deux joysticks, croix, gâchettes"},
        };
        for (final String[] k : kinds) {
            row((active.equals(k[0]) ? "■  " : "□  ") + k[0], k[1], new Runnable() {
                public void run() {
                    store.layouts(); // les trois profils existent dès la première lecture
                    store.setActiveLayout(k[0]);
                    go(step);
                }
            });
        }
        // Démonstration : un aperçu en direct de chaque manette, sous les choix.
        LinearLayout demoRow = new LinearLayout(this);
        for (final String[] kk : kinds) {
            final String k = kk[0];
            TextView b = button((k.equals(demo) ? "▼  " : "▶  ") + "Voir " + k, k.equals(demo));
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    demo = k.equals(demo) ? null : k;
                    go(step);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(0, dp(10), dp(8), 0);
            demoRow.addView(b, lp);
        }
        page.addView(demoRow);
        if (demo != null) {
            ControlDeckView preview = new ControlDeckView(this);
            // Flex : l'aperçu fait défiler la manette de chaque console, son nom au-dessus.
            TextView which = new TextView(this);
            which.setTypeface(Fonts.wide(this));
            which.setTextSize(12);
            which.setTextColor(Theme.CODEC);
            which.setLetterSpacing(0.08f);
            LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-2, -2);
            wl.setMargins(0, dp(10), 0, 0);
            page.addView(which, wl);
            preview.startTour(demo.equals("Flex") ? ControlLayout.flex() : demo.equals("2D") ? ControlLayout.arcade()
                    : ControlLayout.xbox(), which);
            preview.setKeyListener(new ControlView.Listener() {
                public void onDown(String key) {
                }

                public void onUp(String key) {
                }

                public void onAxis(String key, float x, float y) {
                }
            }, true);
            int w = getResources().getDisplayMetrics().widthPixels;
            int h = Math.min(Math.round(w * 0.62f), dp(360));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, h);
            lp.setMargins(0, dp(6), 0, dp(4));
            page.addView(preview, lp);
            note("Touche les boutons pour essayer. Chaque profil s'adapte à l'écran et à la rotation.");
        }
        final int level = store.hapticLevel();
        String[] names = {"Coupée", "Légère", "Moyenne", "Forte"};
        label("VIBRATION");
        LinearLayout chips = new LinearLayout(this);
        for (int i = 0; i < names.length; i++) {
            final int lv = i;
            TextView c = button(names[i], i == level);
            c.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    store.setHapticLevel(lv);
                    Haptics.tap(v);
                    go(step);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(0, 0, dp(6), 0);
            chips.addView(c, lp);
        }
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(chips);
        page.addView(hs);
    }

    private void sheets() {
        para("Jaquettes et fiches récupérées automatiquement.");
        final boolean covers = store.autoCovers();
        row((covers ? "■  " : "□  ") + "Jaquettes automatiques", "Base libretro-thumbnails", new Runnable() {
            public void run() {
                store.setAutoCovers(!covers);
                go(step);
            }
        });
        final boolean info = store.autoInfo();
        row((info ? "■  " : "□  ") + "Fiches des jeux", "Wikipédia, ou ScreenScraper avec un compte (Réglages)",
                new Runnable() {
                    public void run() {
                        store.setAutoInfo(!info);
                        go(step);
                    }
                });
    }

    private void done() {
        para(store.treeUri() == null
                ? "C'est prêt. Choisis ton dossier de jeux depuis l'accueil."
                : "C'est prêt. Origami lit ton dossier et range tes jeux par console.");
        para("En jeu, Home ouvre le menu ; deux appuis rapides "
                + "quittent le jeu.");
    }

    // ------------------------------------------------------------------ vues

    private void title(String s) {
        TextView t = text(s.toUpperCase(), 26, Theme.TEXT);
        t.setTypeface(Fonts.logo(this));
        t.setPadding(0, dp(8), 0, dp(10));
        page.addView(t);
    }

    private void para(String s) {
        TextView t = text(s, 16, Theme.TEXT);
        t.setLineSpacing(0, 1.15f);
        t.setPadding(0, dp(4), 0, dp(10));
        page.addView(t);
    }

    /** Sous-titre de groupe, façon codec. */
    private void sub(String s) {
        TextView t = text(s.toUpperCase(), 12, Theme.CODEC);
        t.setTypeface(Fonts.wide(this));
        t.setLetterSpacing(0.15f);
        t.setPadding(0, dp(18), 0, dp(6));
        page.addView(t);
    }

    private void note(String s) {
        TextView t = text(s, 13, Theme.MUTED);
        t.setPadding(0, dp(8), 0, dp(6));
        page.addView(t);
    }

    private void label(String s) {
        TextView t = text(s, 11, Theme.MUTED);
        t.setTypeface(Fonts.wide(this));
        t.setLetterSpacing(0.15f);
        t.setPadding(0, dp(14), 0, dp(6));
        page.addView(t);
    }

    private void line(String title, String sub, boolean on) {
        LinearLayout l = new LinearLayout(this);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(0, dp(4), 0, dp(4));
        TextView t = text(title, 15, on ? Theme.CODEC : Theme.TEXT);
        t.setTypeface(Typeface.MONOSPACE);
        l.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        l.addView(text(sub, 13, Theme.MUTED));
        page.addView(l);
    }

    /** Ligne touchable façon codec (carte sombre, liseré vert). */
    private void row(String title, String sub, final Runnable action) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(dp(16), dp(12), dp(16), dp(12));
        r.setMinimumHeight(dp(56));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        bg.setColor(Theme.PANEL);
        bg.setStroke(Math.max(1, dp(1)), (Theme.CODEC & 0x00FFFFFF) | 0x44000000);
        r.setBackground(bg);
        TextView t = text(title, 16, Theme.TEXT);
        t.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        r.addView(t);
        if (sub != null) r.addView(text(sub, 13, Theme.MUTED));
        r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Haptics.tap(v);
                if (action != null) action.run();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(5), 0, dp(5));
        page.addView(r, lp);
    }

    private TextView button(String label, boolean primary) {
        TextView t = text(label, 16, primary ? Theme.readableOn(Theme.CODEC) : Theme.TEXT);
        t.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        t.setPadding(dp(22), dp(12), dp(22), dp(12));
        t.setBackground(Theme.pill(primary ? Theme.CODEC : Theme.PANEL_HI, dp(24)));
        return t;
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private static String readable(String treeUri) {
        String id = Uri.decode(treeUri);
        int i = id.lastIndexOf("/tree/");
        if (i >= 0) id = id.substring(i + 6);
        return id.replace("primary:", "Stockage interne/");
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private PadNav nav;

    /** La manette pilote aussi cet écran : cadre Codec, A pour toucher, B pour revenir. */
    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        if (nav == null) nav = new PadNav(this);
        return nav.dispatchKey(e) || super.dispatchKeyEvent(e);
    }

    @Override
    public boolean dispatchGenericMotionEvent(android.view.MotionEvent e) {
        if (nav == null) nav = new PadNav(this);
        return nav.dispatchMotion(e) || super.dispatchGenericMotionEvent(e);
    }
}
