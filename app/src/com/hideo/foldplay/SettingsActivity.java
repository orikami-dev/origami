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
import java.util.List;

/** Page de réglages, une section à la fois, ouverte directement par l'engrenage. */
public class SettingsActivity extends Activity {
    static final String EXTRA_SECTION = "section";
    private static final int REQ_TREE = 11;
    private static final int REQ_BIOS = 12;
    private static final int REQ_SAVES = 13;
    private String saveSystem;

    static final String[][] SECTIONS = {
        {"library", "Bibliothèque"},
        {"emulators", "Émulateurs"},
        {"covers", "Jaquettes et fiches"},
        {"display", "Affichage"},
        {"monitoring", "Monitoring"},
        {"sound", "Son"},
        {"a11y", "Accessibilité"},
        {"updates", "Mises à jour"},
        {"about", "À propos"},
    };

    private Store store;
    private String section;
    private LinearLayout body;

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
        nav = new PadNav(this).hooks(new PadNav.Hooks() {
            public boolean onKey(String k) { return sectionKey(k); }
        });
        render();
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
            title.setTypeface(Fonts.logo(this));
            title.setPadding(dp(6), 0, dp(6), 0);
            if (Theme.RIGHT_HANDED) {
                // Droitier : flèche et titre se lisent depuis le bord droit, sous le pouce (0.65).
                bar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                title.setTextDirection(View.TEXT_DIRECTION_LTR);
                if (bar.getChildCount() > 0) ((TextView) bar.getChildAt(0)).setText("→");
            }
            bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
            if (Theme.RIGHT_HANDED) bar.setGravity(Gravity.CENTER_VERTICAL);
            main.addView(bar);
            body = vertical();
            body.setPadding(dp(twoPane ? 20 : 16), dp(8), dp(twoPane ? 24 : 16), dp(32));
            ScrollView sv = new ScrollView(this);
            sv.addView(body);
            main.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
            root.addView(main, new LinearLayout.LayoutParams(0, -1, 1f));
            if (listRight && list != null) root.addView(list, new LinearLayout.LayoutParams(dp(listW), -1));

            if (section.equals("library")) library();
            else if (section.equals("emulators")) emulators();
            else if (section.equals("covers")) covers();
            else if (section.equals("display")) display();
            else if (section.equals("monitoring")) monitoring();
            else if (section.equals("sound")) sound();
            else if (section.equals("a11y")) accessibility();
            else if (section.equals("updates")) updates();
            else if (section.equals("pads")) pads();
            else about();
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
        title.setTypeface(Fonts.logo(this));
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

    // ------------------------------------------------------------------ sections

    private void library() {
        String tree = store.treeUri();
        action("Dossier de jeux", tree == null ? "Aucun dossier choisi" : readable(tree), new Runnable() {
            public void run() {
                try {
                    startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_TREE);
                } catch (ActivityNotFoundException e) {
                    toast("Sélecteur de dossiers indisponible.");
                }
            }
        });
        if (tree != null) {
            action("Rescanner la bibliothèque", "Relit le dossier et range les jeux par console", new Runnable() {
                public void run() {
                    store.requestRescan();
                    toast("La bibliothèque sera relue en revenant à l'accueil.");
                }
            });
            action("Oublier ce dossier", "Origami n'aura plus accès à ce dossier", new Runnable() {
                public void run() {
                    store.setTreeUri(null);
                    store.requestRescan();
                    render();
                }
            });
        }
        note("Astuce : range tes jeux par console (ps2/, gamecube/, switch/…). Les .iso, .chd et .rvz "
                + "sont aussi reconnus par leur contenu.");
    }

    private void emulators() {
        if (!Libretro.available()) note("Le moteur intégré demande un appareil 64 bits (arm64).");
        final CoreManager cores = new CoreManager(this);
        final Launcher launcher = new Launcher(this, store);

        // 1. Une ligne par console : tout ce qui la concerne s'ouvre dans son menu.
        header("Consoles");
        for (final SystemInfo s : SystemInfo.ALL) {
            String summary = consoleSummary(s, cores, launcher);
            if (summary == null) continue;
            action(s.name, summary, new Runnable() {
                public void run() { consoleMenu(s, cores, launcher); }
            });
        }

        // 2. Les cœurs : une page chacun (téléchargement, version, réglages, licence).
        coreList(cores);

        // 3. Fichiers communs à toutes les consoles.
        header("BIOS et sauvegardes");
        final boolean ps2 = Libretro.available() && cores.hasPs2Bios();
        action("Importer un BIOS", (ps2 ? "BIOS PS2 présent ✓ · " : "") + "PS1, PS2, Saturn, Dreamcast, GBA, DS, Mega-CD, PC Engine CD, Neo Geo… fichiers ou .zip tirés de ta console",
                new Runnable() {
                    public void run() { pickBios(); }
                });
        if (Ps3Core.present(this)) {
            action("Importer le firmware PS3", (Ps3Core.firmwareInstalled(this) ? "Installé ✓ · " : "")
                    + "PS3UPDAT.PUP, téléchargé sur le site de PlayStation", new Runnable() {
                        public void run() { pickPs3Firmware(); }
                    });
        }
        action("Importer des sauvegardes", "Depuis un autre émulateur, converties au bon format", new Runnable() {
            public void run() { pickSaveSystem(); }
        });
        action("Exporter des sauvegardes", "Vers une autre app, Quick Share, un PC ou un dossier", new Runnable() {
            public void run() { pickExportSystem(); }
        });
        note("Origami est aussi dans le menu Partager d'Android : envoie-lui des sauvegardes depuis une autre app "
                + "pour les importer. Le fichier porte le nom du jeu (Zelda.sav pour Zelda.z64).");

        // 4. Entretien.
        header("Entretien");
        if (Libretro.available()) {
            action("Retélécharger les cœurs", "Dernière version de libretro, un cœur ou tous", new Runnable() {
                public void run() { pickRedownload(cores); }
            });
            action("Supprimer les cœurs téléchargés", (cores.installedSize() / (1024 * 1024)) + " Mo utilisés",
                    new Runnable() {
                        public void run() {
                            deleteRecursive(new File(getFilesDir(), "cores"));
                            toast("Cœurs supprimés, ils reviendront au prochain lancement.");
                            render();
                        }
                    });
        }
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            boolean all = android.os.Environment.isExternalStorageManager();
            action("Accès à tous les fichiers", all ? "Accordé : .cue/.bin et gros disques lus directement"
                    : "Nécessaire pour les jeux en plusieurs fichiers (.cue + .bin)", new Runnable() {
                public void run() {
                    try {
                        startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:" + getPackageName())));
                    } catch (ActivityNotFoundException e) {
                        toast("Réglage introuvable sur cet appareil.");
                    }
                }
            });
        }
        note("Sauvegardes et BIOS : Android/data/com.hideo.foldplay/files (saves/ et system/).");
    }

    private static boolean bundled(String core) {
        for (String b : CoreManager.BUNDLED) if (b.equals(core)) return true;
        return false;
    }

    /** Résumé d'une console pour sa ligne, ou null si Origami n'a rien à proposer pour elle. */
    private String consoleSummary(SystemInfo s, CoreManager cores, Launcher launcher) {
        String core = Libretro.available() ? CoreManager.coreFor(s) : null;
        String ext = launcher.emulatorLabel(s);
        StringBuilder b = new StringBuilder();
        if (core != null) {
            b.append("Intégré · ").append(core);
            if (bundled(core)) b.append(" · livré");
            else b.append(cores.installed(core) ? " ✓" : " · au premier lancement");
            if ("ps2".equals(s.id)) b.append(cores.hasPs2Bios() ? " · BIOS ✓" : " · BIOS manquant");
        }
        if (ext != null) b.append(b.length() > 0 ? " · " : "").append("Choix : ").append(ext);
        if (b.length() == 0) return null;
        return b.toString();
    }

    private void consoleMenu(final SystemInfo s, final CoreManager cores, final Launcher launcher) {
        final Runnable back = new Runnable() {
            public void run() { consoleMenu(s, cores, launcher); }
        };
        CodecMenu m = new CodecMenu(this).title(s.name, "CONSOLE · " + s.shortName);
        final String core = Libretro.available() ? CoreManager.coreFor(s) : null;
        if (core != null) {
            boolean b = bundled(core);
            m.item("Moteur intégré : " + CoreCatalog.name(core), b ? "Livré avec l'app" : cores.installed(core) ? "Installé ✓"
                    : "Se télécharge au premier lancement d'un jeu", null);
            if (!b) m.item(cores.installed(core) ? "Retélécharger le cœur" : "Télécharger le cœur maintenant",
                    "Dernière version de libretro", new Runnable() {
                        public void run() { redownload(cores, java.util.Collections.singletonList(core)); }
                    });
        }
        if ("ps2".equals(s.id) || "psx".equals(s.id) || "ps1".equals(s.id) || "dc".equals(s.id)) {
            String state = "ps2".equals(s.id) ? (cores.hasPs2Bios() ? "BIOS présent ✓" : "BIOS requis") : "Facultatif ou selon le cœur";
            m.item("Importer un BIOS", state + " · fichier tiré de ta console", new Runnable() {
                public void run() { pickBios(); }
            });
        }
        if ("ps3".equals(s.id) && Ps3Core.present(this)) {
            m.item("Importer le firmware PS3", Ps3Core.firmwareInstalled(this) ? "Installé ✓" : "Requis · PS3UPDAT.PUP",
                    new Runnable() {
                        public void run() { pickPs3Firmware(); }
                    });
        }
        boolean saves = false;
        for (String id : SaveImport.SYSTEMS) if (id.equals(s.id)) saves = true;
        if (saves) {
            m.item("Importer des sauvegardes", SaveImport.formats(s.id), new Runnable() {
                public void run() { pickSaves(s.id); }
            });
            m.sub("Exporter des sauvegardes", "Partager ou enregistrer dans un dossier", new Runnable() {
                public void run() { SaveTransfer.export(SettingsActivity.this, s.id, null, back); }
            });
        }
        String ext = launcher.emulatorLabel(s);
        if (ext != null) m.item("Oublier le choix : " + ext, "Le prochain lancement te redemandera l'émulateur",
                new Runnable() {
                    public void run() {
                        store.setEmulator(s.id, (String) null);
                        toast("Le prochain lancement " + s.shortName + " te redemandera l'émulateur.");
                        render();
                    }
                });
        if (core != null) m.sub("Page du cœur " + CoreCatalog.name(core), "Réglages, licence, code source", new Runnable() {
            public void run() { corePage(core, back); }
        });
        m.show(CodecMenu.hostFor(this));
    }

    private void pickBios() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(i, REQ_BIOS);
        } catch (ActivityNotFoundException e) {
            toast("Sélecteur de fichiers introuvable.");
        }
    }

    private void pickSaves(String id) {
        saveSystem = id;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(i, REQ_SAVES);
        } catch (ActivityNotFoundException e) {
            toast("Sélecteur de fichiers introuvable.");
        }
    }

    // ------------------------------------------------------------------ cœurs

    /** Les cœurs intégrés, une ligne chacun : leur page s'ouvre au toucher. */
    private void coreList(final CoreManager cores) {
        if (!Libretro.available()) return;
        header("Cœurs");
        for (final CoreCatalog.Info i : CoreCatalog.ALL) {
            String v = store.coreVariant(i.core);
            action(i.name + (v.equals(i.core) ? "" : " → " + CoreCatalog.variantLabel(i.core, v))
                    + (cores.installed(i.core) ? "  ✓" : ""), CoreManager.systemsOf(i.core) + " · "
                    + coreState(cores, i.core), new Runnable() {
                public void run() { corePage(i.core, null); }
            });
        }
        note("Switch : Eden, intégré comme les autres cœurs, bientôt.");
    }

    private String coreState(CoreManager cores, String core) {
        if (CoreManager.bundled(core)) return "livré avec l'app";
        if (!cores.installed(core)) return "à télécharger";
        File f = cores.file(core);
        return String.format(java.util.Locale.ROOT, "installé · %.1f Mo", f.length() / 1048576f);
    }

    /** Page d'un cœur : présentation, état, téléchargement, réglages hors jeu, licence et source. */
    private void corePage(final String core, final Runnable back) {
        final CoreManager cores = new CoreManager(this);
        final CoreCatalog.Info info = CoreCatalog.get(core);
        final Runnable self = new Runnable() {
            public void run() { corePage(core, back); }
        };
        CodecMenu m = new CodecMenu(this).title(CoreCatalog.name(core), "CŒUR · " + CoreManager.systemsOf(core))
                .message((info != null ? info.about + "\n\n" : "") + "État : " + coreState(cores, core));
        if (back != null) m.onBack(back);
        if (!CoreManager.bundled(core)) {
            m.item(cores.installed(core) ? "Mettre à jour" : "Télécharger", "Dernière version depuis libretro",
                    new Runnable() {
                        public void run() { redownload(cores, java.util.Collections.singletonList(core)); }
                    });
            if (cores.installed(core)) m.item("Supprimer", "Il reviendra au prochain lancement d'un jeu", new Runnable() {
                public void run() {
                    cores.delete(core);
                    toast(CoreCatalog.name(core) + " supprimé.");
                    render();
                }
            });
        }
        String inote = store.coreInstallNote(core);
        if (inote != null) m.item("Installation", inote, null);
        final java.util.List<String[]> variants = CoreCatalog.variants(core);
        if (variants.size() > 1) {
            m.sub("Version : " + CoreCatalog.variantLabel(core, store.coreVariant(core)),
                    variants.size() + " versions disponibles · choisis ta préférée", new Runnable() {
                public void run() { pickVariant(core, variants, self); }
            });
        }
        if (cores.hasPrevious(core)) m.item("Revenir à la version précédente", "Celle d'avant la dernière mise à jour",
                new Runnable() {
                    public void run() {
                        try {
                            cores.rollback(core);
                            store.setCoreInstallNote(core, "Version précédente rétablie");
                            toast("Version précédente rétablie.");
                        } catch (java.io.IOException e) {
                            toast("Retour impossible : " + e.getMessage());
                        }
                        self.run();
                    }
                });
        if (cores.installed(core)) m.item("Relancer l'installation", "Vérifie et réoptimise pour cet appareil",
                new Runnable() {
                    public void run() { runInstaller(core, self); }
                });
        final String[] specs = store.coreSpecs(core);
        m.sub("Réglages du cœur", specs.length == 0 ? "Lance un jeu une fois pour les voir apparaître"
                : (specs.length / 3) + " réglages · appliqués au prochain lancement", new Runnable() {
            public void run() {
                if (specs.length == 0) {
                    toast("Lance un jeu de cette console une fois : ses réglages apparaîtront ici.");
                    self.run();
                } else {
                    coreOptions(core, specs, self);
                }
            }
        });
        if (info != null) {
            m.item("Licence : " + info.licence, info.nonCommercial
                    ? "Usage non commercial uniquement" : "Logiciel libre", null);
            m.item("Code source", info.source.replace("https://", ""), new Runnable() {
                public void run() {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(info.source)));
                    } catch (ActivityNotFoundException e) {
                        toast("Aucun navigateur.");
                    }
                }
            });
        }
        m.show(CodecMenu.hostFor(this));
    }

    private void pickVariant(final String core, java.util.List<String[]> variants, final Runnable back) {
        String current = store.coreVariant(core);
        CodecMenu m = new CodecMenu(this).title("Version", CoreCatalog.name(core).toUpperCase()).onBack(back);
        for (final String[] v : variants) {
            m.radio(v[2], v[3] + " · " + v[4], v[1].equals(current), new Runnable() {
                public void run() {
                    if (v[1].equals(store.coreVariant(core))) {
                        back.run();
                        return;
                    }
                    // Autre émulateur : ses réglages ne sont pas ceux du précédent.
                    store.setCoreVariant(core, v[1]);
                    store.clearCoreOptions(core);
                    store.setCoreSpecs(core, new String[0]);
                    store.setCoreInstallNote(core, null);
                    redownload(new CoreManager(SettingsActivity.this), java.util.Collections.singletonList(core));
                }
            });
        }
        m.show(CodecMenu.hostFor(this));
    }

    private void runInstaller(final String core, final Runnable done) {
        final CodecMenu dialog = new CodecMenu(this).title("Installation", CoreCatalog.name(core).toUpperCase())
                .message("Préparation…").onBack(new Runnable() { public void run() { } });
        dialog.show(CodecMenu.hostFor(this));
        new Thread(new Runnable() {
            public void run() {
                String result;
                try {
                    result = CoreInstaller.run(SettingsActivity.this, core, new CoreInstaller.Log() {
                        public void step(final String line) {
                            runOnUiThread(new Runnable() {
                                public void run() { dialog.updateMessage(line); }
                            });
                        }
                    });
                } catch (Exception e) {
                    result = "Échec : " + e.getMessage();
                }
                final String r = result;
                runOnUiThread(new Runnable() {
                    public void run() {
                        dialog.dismiss();
                        toast(r);
                        done.run();
                    }
                });
            }
        }).start();
    }

    /** Options d'un cœur hors jeu, rangées par thème : la liste vient du dernier lancement. */
    private void coreOptions(final String core, final String[] vars, final Runnable back) {
        CodecMenu m = new CodecMenu(this).title("Réglages", CoreCatalog.name(core).toUpperCase()).onBack(back);
        final Runnable self = new Runnable() {
            public void run() { coreOptions(core, vars, back); }
        };
        for (final java.util.Map.Entry<String, java.util.List<Integer>> g : CoreOptionGroups.split(vars).entrySet()) {
            m.sub(g.getKey() + " (" + g.getValue().size() + ")", CoreOptionGroups.detail(g.getKey()), new Runnable() {
                public void run() { coreOptionGroup(core, vars, g.getKey(), g.getValue(), self); }
            });
        }
        m.item("Tout remettre par défaut", null, new Runnable() {
            public void run() {
                store.clearCoreOptions(core);
                toast("Réglages par défaut au prochain lancement.");
                self.run();
            }
        });
        m.show(CodecMenu.hostFor(this));
    }

    private void coreOptionGroup(final String core, final String[] vars, final String group,
            final java.util.List<Integer> idx, final Runnable back) {
        java.util.Map<String, String> chosen = store.coreOptions(core);
        CodecMenu m = new CodecMenu(this).title(group, CoreCatalog.name(core).toUpperCase()).onBack(back);
        final Runnable self = new Runnable() {
            public void run() { coreOptionGroup(core, vars, group, idx, back); }
        };
        for (int n : idx) {
            final int i = n * 3;
            final String key = vars[i], spec = vars[i + 1];
            int semi = spec.indexOf(';');
            final String label = CoreOptionGroups.label(key, spec);
            final String[] values = (semi >= 0 ? spec.substring(semi + 1) : spec).trim().split("\\|");
            final String current = chosen.containsKey(key) ? chosen.get(key) : vars[i + 2];
            m.sub(label, current, new Runnable() {
                public void run() {
                    CodecMenu v = new CodecMenu(SettingsActivity.this).title(label, group.toUpperCase()).onBack(self);
                    for (final String val : values) {
                        v.radio(val, null, val.equals(current), new Runnable() {
                            public void run() {
                                store.setCoreOption(core, key, val);
                                self.run();
                            }
                        });
                    }
                    v.show(CodecMenu.hostFor(SettingsActivity.this));
                }
            });
        }
        m.show(CodecMenu.hostFor(this));
    }

    private void covers() {
        toggle("Jaquettes automatiques", "Télécharge les jaquettes manquantes après chaque scan",
                store.autoCovers(), new Toggle() {
                    public void set(boolean on) {
                        store.setAutoCovers(on);
                    }
                });
        toggle("Fiches de jeux", "Synopsis et année de sortie depuis Wikipédia, affichés dans le mode Tsuru",
                store.autoInfo(), new Toggle() {
                    public void set(boolean on) {
                        store.setAutoInfo(on);
                    }
                });
        toggle("Illustrations de fond", "Quand le curseur s'arrête sur un jeu, l'accueil affiche une image du jeu "
                + "derrière : illustration large ScreenScraper (compte requis), sinon les couleurs floutées de sa jaquette", store.backdrops(), new Toggle() {
            public void set(boolean on) {
                store.setBackdrops(on);
            }
        });
        String[] ss = store.screenScraper();
        action("Compte ScreenScraper", ss[0].isEmpty()
                ? "Pour les synopsis officiels en français : identifiants développeur et compte"
                : "Saisi · synopsis officiels activés", new Runnable() {
            public void run() {
                screenScraperDialog();
            }
        });
        action("Retélécharger les fiches", "Efface les fiches gardées, elles reviendront à la sélection", new Runnable() {
            public void run() {
                new GameInfo(SettingsActivity.this).clear();
                toast("Fiches effacées.");
            }
        });
        action("Retélécharger toutes les jaquettes", "Jaquettes et illustrations de fond : efface le cache puis relance la récupération", new Runnable() {
            public void run() {
                deleteRecursive(new File(getFilesDir(), "covers"));
                deleteRecursive(new File(getFilesDir(), "backdrops"));
                store.requestRescan();
                toast("Cache vidé. Les jaquettes reviendront au prochain passage à l'accueil.");
            }
        });
        note("Source : base communautaire libretro-thumbnails. Les jaquettes appartiennent à leurs éditeurs "
                + "et restent sur ton appareil.");
    }

    // ------------------------------------------------------------------ manettes physiques

    private Pads pads;
    private final List<PadView> padViews = new java.util.ArrayList<PadView>();

    private void pads() {
        if (pads == null) pads = new Pads(this);
        toggle("Plein écran avec une manette", "Dès qu'une manette est branchée, le jeu prend tout l'écran "
                + "et la manette à l'écran disparaît", store.padFullscreen(), new Toggle() {
            public void set(boolean on) {
                store.setPadFullscreen(on);
            }
        });
        header("Connectées");
        List<android.view.InputDevice> now = pads.connected();
        java.util.Set<String> seen = new java.util.HashSet<String>();
        if (now.isEmpty()) note("Aucune manette pour l'instant. Associe-la en Bluetooth dans les réglages Android : "
                + "elle apparaîtra ici toute seule.");
        else note("Appuie sur les touches pour les tester : ici la manette ne pilote pas l'app. "
                + "Pour quitter, touche l'écran ou appuie sur Select et Start ensemble.");
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
                public void run() { padMenu(d, p); }
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
        note("Mappage automatique : Android reconnaît les manettes Xbox, PlayStation, 8BitDo et la plupart des "
                + "manettes Bluetooth ; les manettes Nintendo sont détectées et leurs A/B remis à leur place. "
                + "Jusqu'à 4 joueurs : la première manette est le joueur 1 (avec la manette à l'écran).");
    }

    private void padMenu(final android.view.InputDevice d, final Pads.Profile p) {
        CodecMenu m = new CodecMenu(this).title(p.name, "MANETTE · JOUEUR " + p.player);
        m.sub("Joueur", "Joueur " + p.player, new Runnable() {
            public void run() {
                CodecMenu r = new CodecMenu(SettingsActivity.this).title("Joueur", p.name);
                for (int i = 1; i <= 4; i++) {
                    final int n = i;
                    r.radio("Joueur " + i, i == 1 ? "Partage la manette à l'écran et pilote les menus" : null,
                            p.player == i, new Runnable() {
                                public void run() {
                                    p.player = n;
                                    pads.save(p);
                                    render();
                                }
                            });
                }
                r.onBack(new Runnable() { public void run() { padMenu(d, p); } });
                r.show(CodecMenu.hostFor(SettingsActivity.this));
            }
        });
        m.check("Disposition Nintendo", "A à droite, B en bas (Switch Pro, Joy-Con…)", p.nintendo, new Runnable() {
            public void run() {
                p.nintendo = !p.nintendo;
                pads.save(p);
                padMenu(d, p);
            }
        });
        m.sub("Zone morte des sticks", p.deadzone + " %", new Runnable() {
            public void run() {
                CodecMenu r = new CodecMenu(SettingsActivity.this).title("Zone morte", p.name);
                int[] values = {5, 10, 15, 20, 30};
                for (final int v : values) {
                    r.radio(v + " %", v == 15 ? "Par défaut" : null, p.deadzone == v, new Runnable() {
                        public void run() {
                            p.deadzone = v;
                            pads.save(p);
                            render();
                        }
                    });
                }
                r.onBack(new Runnable() { public void run() { padMenu(d, p); } });
                r.show(CodecMenu.hostFor(SettingsActivity.this));
            }
        });
        m.item("Réattribuer les touches", "Appuie sur chaque touche à son tour", new Runnable() {
            public void run() {
                p.remap.clear();
                wizard = p;
                wizardDevice = d.getId();
                wizardStep = 0;
                wizardPrompt();
            }
        });
        if (!p.remap.isEmpty()) {
            m.item("Revenir aux touches automatiques", null, new Runnable() {
                public void run() {
                    p.remap.clear();
                    pads.save(p);
                    render();
                }
            });
        }
        m.show(CodecMenu.hostFor(this));
    }

    private Pads.Profile wizard;
    private int wizardDevice;
    private int wizardStep;

    private void wizardPrompt() {
        if (wizard == null) return;
        if (wizardStep >= Pads.FUNCTIONS.length) {
            pads.save(wizard);
            toast("Touches enregistrées pour " + wizard.name + ".");
            wizard = null;
            render();
            return;
        }
        String f = Pads.FUNCTIONS[wizardStep];
        new CodecMenu(this).title("Touche " + ControlLayout.label(f), wizard.name + " · " + (wizardStep + 1) + "/"
                + Pads.FUNCTIONS.length)
                .message("Appuie sur la touche de la manette qui doit servir de « " + ControlLayout.label(f) + " ».")
                .sub("Passer", "Garder la touche automatique", new Runnable() {
                    public void run() {
                        wizardStep++;
                        wizardPrompt();
                    }
                })
                .item("Terminer", null, new Runnable() {
                    public void run() {
                        wizardStep = Pads.FUNCTIONS.length;
                        wizardPrompt();
                    }
                })
                .onBack(new Runnable() {
                    public void run() {
                        wizard = null;
                        render();
                    }
                })
                .show(CodecMenu.hostFor(this));
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        if (section.equals("pads")) for (PadView v : padViews) v.onKey(e);
        android.view.InputDevice d = e.getDevice();
        if (wizard != null && d != null && d.getId() == wizardDevice) {
            if (e.getAction() == android.view.KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                wizard.remap.put(e.getKeyCode(), Pads.FUNCTIONS[wizardStep]);
                wizardStep++;
                wizardPrompt();
            }
            return true;
        }
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
                    if (section.equals("pads") && wizard == null) render();
                }

                public void onInputDeviceRemoved(int id) {
                    if (section.equals("pads") && wizard == null) render();
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

    private void display() {
        header("Langue");
        action("Langue · Language", I18n.choiceName(this), new Runnable() {
            public void run() {
                I18n.pick(SettingsActivity.this, new Runnable() {
                    public void run() {
                        // Toute l'app repart dans la nouvelle langue, sur cette même page.
                        startActivity(new Intent(SettingsActivity.this, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
                        startActivity(new Intent(SettingsActivity.this, SettingsActivity.class)
                                .putExtra(EXTRA_SECTION, "display"));
                    }
                });
            }
        });
        header("Interface");
        toggle("Plein écran hors jeu", "Masque les barres d'état et de navigation dans les menus (les jeux sont toujours en plein écran)",
                store.appFullscreen(), new Toggle() {
                    public void set(boolean on) {
                        store.setAppFullscreen(on);
                        Theme.fullscreen(SettingsActivity.this);
                    }
                });
        toggle("Mode Tsuru par défaut", "Sur l'écran interne ; sinon le mode Washi (liste ou grille). "
                + "L'écran externe d'un pliant ouvre toujours Washi", store.flexView(), new Toggle() {
            public void set(boolean on) {
                store.setFlexView(on);
            }
        });
        toggle("Mode Tsuru à l'ouverture", "Déplier le pliant pendant qu'Origami est à l'écran passe directement en mode jeu",
                store.tsuruOnOpen(), new Toggle() {
            public void set(boolean on) {
                store.setTsuruOnOpen(on);
            }
        });
        action("Origami comme lanceur par défaut", HomeApps.isDefault(this)
                ? "Déjà le lanceur du téléphone. Touche pour changer"
                : "Le bouton Accueil du téléphone ouvre Origami ; tes applis restent dans le menu Applications",
                new Runnable() {
            public void run() {
                HomeApps.askDefault(SettingsActivity.this);
            }
        });
        toggle("Écran titre au démarrage", "L'animation de la grue au lancement", store.intro(), new Toggle() {
            public void set(boolean on) {
                store.setIntro(on);
            }
        });
        toggle("Fonds animés", "Le décor de chaque thème bouge doucement, en boucle", store.animatedBackdrop(), new Toggle() {
            public void set(boolean on) {
                store.setAnimatedBackdrop(on);
                Theme.ANIMATE = on;
                render();
            }
        });
        toggle("Effet cathodique", "Lignes de balayage sur la moitié haute", store.scanlines(), new Toggle() {
            public void set(boolean on) {
                store.setScanlines(on);
            }
        });
        note("Manette tactile, manettes physiques et vibrations : icône manette en haut de l'accueil.");
    }

    /** Monitoring : l'ATH en jeu façon RTSS, son style et les mesures affichées. */
    private void monitoring() {
        header("ATH en jeu");
        toggle("ATH de monitoring en jeu", "Mesures en direct, sur une ligne en haut de l'écran façon RTSS",
                store.hud(), new Toggle() {
                    public void set(boolean on) {
                        store.setHud(on);
                    }
                });
        choice("Style", new String[] {"RTSS", "Bloc sur plusieurs lignes"},
                "block".equals(store.hudStyle()) ? 1 : 0, new Choice() {
                    public void set(int i) {
                        store.setHudStyle(i == 1 ? "block" : "line");
                    }
                });
        choice("Position", Hud.POS_LABELS, Hud.index(Hud.POS_KEYS, store.hudPos()), new Choice() {
            public void set(int i) {
                store.setHudPos(Hud.POS_KEYS[i]);
            }
        });
        choice("Taille du texte", Hud.SIZE_LABELS, store.hudSize(), new Choice() {
            public void set(int i) {
                store.setHudSize(i);
            }
        });
        choice("Couleur des libellés", Hud.COLOR_LABELS, Hud.index(Hud.COLOR_KEYS, store.hudColor()), new Choice() {
            public void set(int i) {
                store.setHudColor(Hud.COLOR_KEYS[i]);
            }
        });
        choice("Unité de température", Hud.UNIT_LABELS, Hud.index(Hud.UNIT_KEYS, store.tempUnit()), new Choice() {
            public void set(int i) {
                store.setTempUnit(Hud.UNIT_KEYS[i]);
            }
        });
        int rate = 1;
        for (int i = 0; i < Hud.RATES.length; i++) if (Hud.RATES[i] == store.hudRate()) rate = i;
        choice("Mise à jour", Hud.RATE_LABELS, rate, new Choice() {
            public void set(int i) {
                store.setHudRate(Hud.RATES[i]);
            }
        });
        toggle("Bandeau sombre", "Sinon le texte seul, ombré, directement sur le jeu", store.hudBackground(),
                new Toggle() {
                    public void set(boolean on) {
                        store.setHudBackground(on);
                    }
                });
        header("Mesures affichées");
        java.util.Set<String> items = store.hudItems();
        for (final String[] it : Hud.ITEMS) {
            toggle(it[1], it[2], items.contains(it[0]), new Toggle() {
                public void set(boolean on) {
                    store.setHudItem(it[0], on);
                }
            });
        }
    }

    /** Son (0.62.1) : sa propre catégorie, demandée par BOSS. */
    /**
     * Accessibilité (0.62.5) : tenue à une main (droitier ou gaucher), textes, contraste,
     * animations, et aides de jeu pour qui a du mal à maintenir un appui ou à faire de grands gestes.
     */
    private void accessibility() {
        header("Tenue à une main");
        choice("Main qui tient le téléphone", new String[] {"Droitier", "Gaucher"}, store.rightHanded() ? 0 : 1,
                new Choice() {
                    public void set(int i) {
                        store.setRightHanded(i == 0);
                        a11yChanged();
                    }
                });
        note("La flèche retour et la liste des réglages passent du côté du pouce.");
        header("Lecture");
        choice("Taille des textes", new String[] {"Normale", "Grande", "Très grande"}, store.a11yText(), new Choice() {
            public void set(int i) {
                store.setA11yText(i);
                a11yChanged();
            }
        });
        toggle("Contraste élevé", "Textes secondaires plus clairs, plus faciles à lire", store.highContrast(), new Toggle() {
            public void set(boolean on) {
                store.setHighContrast(on);
                a11yChanged();
            }
        });
        toggle("Réduire les animations", "Fonds fixes, liseré de la manette immobile", store.reduceMotion(), new Toggle() {
            public void set(boolean on) {
                store.setReduceMotion(on);
                a11yChanged();
            }
        });
        header("En jeu");
        toggle("Gâchettes à bascule", "L2 et R2 : un appui les enfonce, le suivant les relâche, sans garder le doigt dessus",
                store.latchTriggers(), new Toggle() {
                    public void set(boolean on) {
                        store.setLatchTriggers(on);
                        Theme.sync(SettingsActivity.this);
                    }
                });
        choice("Course des joysticks", new String[] {"Normale", "Courte", "Très courte"}, store.stickReach(), new Choice() {
            public void set(int i) {
                store.setStickReach(i);
                Theme.sync(SettingsActivity.this);
            }
        });
        note("Une course courte pousse le joystick à fond avec un petit geste du pouce.");
        toggle("Vibrations renforcées", "Retour plus fort et plus long à chaque appui sur la manette", store.strongHaptics(),
                new Toggle() {
                    public void set(boolean on) {
                        store.setStrongHaptics(on);
                        Theme.sync(SettingsActivity.this);
                    }
                });
    }

    private void a11yChanged() {
        Theme.sync(this);
        Theme.textScale(this);
        render();
    }

    /** Mises à jour (0.62.5) : version installée, recherche, téléchargement et installation. */
    private void updates() {
        header("Version installée");
        note("Origami " + Updater.installed(this));
        toggle("Rechercher automatiquement", "Au démarrage, deux fois par jour au plus, avec une notification et un résumé des nouveautés",
                store.autoUpdates(), new Toggle() {
                    public void set(boolean on) {
                        store.setAutoUpdates(on);
                        if (on && android.os.Build.VERSION.SDK_INT >= 33) {
                            requestPermissions(new String[] {"android.permission.POST_NOTIFICATIONS"}, 62);
                        }
                    }
                });
        final LinearLayout result = vertical();
        action("Rechercher une mise à jour", "Compare avec la dernière version publiée", new Runnable() {
            public void run() {
                result.removeAllViews();
                result.addView(text(I18n.tr("Recherche…"), 14, Theme.MUTED));
                Updater.check(SettingsActivity.this, new Updater.Done() {
                    public void done(final Updater.Release r) {
                        result.removeAllViews();
                        if (r.error != null) {
                            result.addView(text(I18n.tr(r.error), 14, Theme.MUTED));
                        } else if (!r.newer) {
                            result.addView(text(I18n.tr("Origami est à jour."), 14, Theme.TEXT));
                        } else {
                            result.addView(text("Origami " + r.version, 18, Theme.CODEC));
                            if (r.summary != null && !r.summary.isEmpty()) result.addView(text(r.summary, 14, Theme.TEXT));
                            TextView go = text(I18n.tr("Télécharger et installer"), 16, Theme.BG);
                            go.setBackground(Theme.pill(Theme.CODEC, dp(20)));
                            go.setPadding(dp(18), dp(10), dp(18), dp(10));
                            go.setOnClickListener(new View.OnClickListener() {
                                public void onClick(View v) {
                                    Updater.install(SettingsActivity.this, r);
                                    toast(I18n.tr("Téléchargement lancé : l'installation s'ouvrira à la fin."));
                                }
                            });
                            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                            lp.topMargin = dp(12);
                            result.addView(go, lp);
                        }
                    }
                });
            }
        });
        body.addView(result);
    }

    private void sound() {
        header("Interface");
        toggle("Bruits de l'interface", "Déplacement, validation, retour, ouverture d'un menu et lancement d'un jeu, "
                + "propres à chaque thème", store.menuSound(), new Toggle() {
            public void set(boolean on) {
                store.setMenuSound(on);
                UiSound.play(SettingsActivity.this, UiSound.SELECT);
            }
        });
        slider("Volume des bruits", store.uiVolume(), new Choice() {
            public void set(int v) {
                store.setUiVolume(v);
                UiSound.play(SettingsActivity.this, UiSound.SELECT);
            }
        });
        header("Musique");
        toggle("Musique d'accueil", "Une boucle par thème, coupée pendant les parties", store.musicOn(), new Toggle() {
            public void set(boolean on) {
                store.setMusicOn(on);
                if (on) UiSound.musicStart(SettingsActivity.this);
                else UiSound.musicPause();
            }
        });
        slider("Volume de la musique", store.musicVolume(), new Choice() {
            public void set(int v) {
                store.setMusicVolume(v);
                UiSound.musicStart(SettingsActivity.this);
            }
        });
        note("Musique et bruits ne jouent qu'en mode sonnerie : rien en vibreur ou en silencieux.");
    }

    private void about() {
        action("Relancer l'assistant de démarrage", "Dossier, moteurs intégrés, émulateurs, manette", new Runnable() {
            public void run() {
                startActivity(new Intent(SettingsActivity.this, SetupActivity.class));
            }
        });
        String version = "?";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            // Version inconnue.
        }
        header("Débogage");
        toggle("Mode débogage", "Journal détaillé (messages des cœurs compris) et rapport de plantage, "
                + "à partager pour un retour de bug", store.debug(), new Toggle() {
            public void set(boolean on) {
                store.setDebug(on);
                DebugLog.sync(SettingsActivity.this);
            }
        });
        long logSize = DebugLog.size(this);
        if (logSize > 0) {
            action("Partager le journal", String.format(java.util.Locale.ROOT, "%.1f Mo", logSize / 1048576f)
                    + " · fiche de l'appareil comprise", new Runnable() {
                public void run() {
                    DebugLog.share(SettingsActivity.this);
                }
            });
            action("Effacer le journal", "Supprime les journaux et rapports enregistrés", new Runnable() {
                public void run() {
                    boolean on = store.debug();
                    DebugLog.stop();
                    DebugLog.clear(SettingsActivity.this);
                    if (on) DebugLog.sync(SettingsActivity.this);
                    I18n.toast(SettingsActivity.this, "Journal effacé",
                            android.widget.Toast.LENGTH_SHORT).show();
                }
            });
        }
        android.widget.ImageView mark = new android.widget.ImageView(this);
        mark.setImageDrawable(new Crane.Logo());
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(120), dp(120));
        mlp.gravity = Gravity.CENTER_HORIZONTAL;
        mlp.topMargin = dp(16);
        body.addView(mark, mlp);
        header("ORIGAMI " + version);
        note("Déplie. Joue. Une production KAMI-WORKS.");
        note("Origami n'est affilié à aucun fabricant de consoles ni éditeur de jeux. Les noms de "
                + "consoles cités servent uniquement à indiquer la compatibilité et appartiennent à leurs "
                + "propriétaires respectifs.");
        note("Origami ne fournit aucun jeu, BIOS ni clé. Utilise uniquement des copies de jeux que tu possèdes.");
        note("Polices : Black Ops One et Michroma, sous licence SIL Open Font License 1.1.");
        note("Moteur PS2 : ARMSX2 (dérivé de PCSX2), cœur libretro sous licence GNU GPL 3.0, livré avec l'app "
                + "et compilé depuis le code source publié ci-dessous, avec un petit correctif Origami pour "
                + "l'accéléré (native/armsx2-patches, fourni sur demande avec le code de l'app).");
        link("Code source d'ARMSX2", "Version " + ARMSX2_COMMIT.substring(0, 7),
                "https://github.com/ARMSX2/ARMSX2/tree/" + ARMSX2_COMMIT);
        note("Moteur GameCube et Wii : Dolphin, cœur libretro sous licence GNU GPL 2.0 ou ultérieure, livré avec "
                + "l'app et compilé sans modification depuis le code source publié ci-dessous.");
        link("Code source de Dolphin (libretro)", "Version " + DOLPHIN_COMMIT.substring(0, 7),
                "https://github.com/libretro/dolphin/tree/" + DOLPHIN_COMMIT);
    }

    /** Version de Dolphin (libretro) compilée dans l'APK (native/build-dolphin.sh). */
    static final String DOLPHIN_COMMIT = "5466da350f2fd143ee3e7b921e8c33aee64f7065";

    /** Version d'ARMSX2 compilée dans l'APK (native/build-armsx2.sh). */
    static final String ARMSX2_COMMIT = "25e7084fdefcf1e9069481a583c38c103b03e8f1";

    // ------------------------------------------------------------------ résultats

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (SaveTransfer.onResult(this, request, result, data)) return;
        if (request == REQ_SAVES && result == RESULT_OK && data != null && saveSystem != null) {
            importSaves(data, saveSystem);
            return;
        }
        if (request == REQ_BIOS && result == RESULT_OK && data != null) {
            importBios(data);
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
        render();
    }

    private void pickSaveSystem() {
        final String[] ids = SaveImport.SYSTEMS;
        CodecMenu m = new CodecMenu(this).title("Importer", "SAUVEGARDES · QUELLE CONSOLE ?");
        for (final String id : ids) {
            SystemInfo si = SystemInfo.byId(id);
            m.item(si != null ? si.name : id, SaveImport.formats(id), new Runnable() {
                public void run() { pickSaves(id); }
            });
        }
        m.show(CodecMenu.hostFor(this));
    }

    private void importSaves(Intent data, String system) {
        java.util.List<Uri> uris = new java.util.ArrayList<Uri>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        StringBuilder msg = new StringBuilder();
        SaveTransfer.install(this, system, SaveTransfer.read(this, uris, msg), msg, null);
    }

    private void pickExportSystem() {
        CodecMenu m = new CodecMenu(this).title("Exporter", "SAUVEGARDES · QUELLE CONSOLE ?");
        for (final String id : SaveImport.SYSTEMS) {
            SystemInfo si = SystemInfo.byId(id);
            m.sub(si != null ? si.name : id, null, new Runnable() {
                public void run() {
                    SaveTransfer.export(SettingsActivity.this, id, null, new Runnable() {
                        public void run() { pickExportSystem(); }
                    });
                }
            });
        }
        m.show(CodecMenu.hostFor(this));
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

    /** Range chaque BIOS choisi (ou contenu dans un .zip) là où son émulateur le cherche (BiosImport). */
    private void importBios(Intent data) {
        BiosImport.Report report = BiosImport.fromIntent(this, data);
        toast(report.text());
        render();
    }

    // ------------------------------------------------------------------ briques

    private interface Toggle {
        void set(boolean on);
    }

    private interface Choice {
        void set(int index);
    }

    /** Réglage à plusieurs valeurs : chaque appui passe à la suivante. */
    private void choice(String title, final String[] labels, int value, final Choice c) {
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
    private void slider(String title, int value, final Choice c) {
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

    private void action(String title, String subtitle, final Runnable run) {
        LinearLayout row = row(title, subtitle);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                run.run();
            }
        });
    }

    private void toggle(String title, String subtitle, boolean value, final Toggle t) {
        LinearLayout row = row(title, subtitle);
        // Case façon codec plutôt qu'un interrupteur Android.
        final TextView box = text(value ? "■" : "□", 22, value ? Theme.CODEC : Theme.MUTED);
        box.setPadding(dp(12), 0, dp(4), 0);
        row.addView(box);
        final boolean[] on = {value};
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                on[0] = !on[0];
                box.setText(on[0] ? "■" : "□");
                box.setTextColor(on[0] ? Theme.CODEC : Theme.MUTED);
                t.set(on[0]);
            }
        });
    }

    private void link(String title, String subtitle, final String url) {
        action(title, subtitle + " · site officiel", new Runnable() {
            public void run() {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (ActivityNotFoundException e) {
                    toast(url);
                }
            }
        });
    }

    private void note(String s) {
        TextView t = text(s, 13, Theme.MUTED);
        t.setPadding(dp(4), dp(10), dp(4), dp(4));
        body.addView(t);
    }

    private static String readable(String treeUri) {
        String id = Uri.decode(treeUri);
        int i = id.lastIndexOf("/tree/");
        if (i >= 0) id = id.substring(i + 6);
        return id.replace("primary:", "Stockage interne/");
    }

    private static void deleteRecursive(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRecursive(k);
        f.delete();
    }

    private void screenScraperDialog() {
        String[] cur = store.screenScraper();
        LinearLayout box = vertical();
        box.setPadding(dp(20), dp(8), dp(20), 0);
        final android.widget.EditText[] f = new android.widget.EditText[4];
        String[] hints = {"Identifiant développeur (devid)", "Mot de passe développeur",
            "Ton identifiant ScreenScraper (facultatif)", "Ton mot de passe ScreenScraper"};
        for (int i = 0; i < 4; i++) {
            f[i] = new android.widget.EditText(this);
            f[i].setHint(hints[i]);
            f[i].setText(cur[i]);
            f[i].setTextColor(Theme.TEXT);
            f[i].setSingleLine(true);
            if (i % 2 == 1) {
                f[i].setInputType(android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
            box.addView(f[i]);
        }
        Theme.show(new android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Compte ScreenScraper")
                .setView(box)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Enregistrer", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        store.setScreenScraper(f[0].getText().toString().trim(), f[1].getText().toString().trim(),
                                f[2].getText().toString().trim(), f[3].getText().toString());
                        toast("Les fiches seront recherchées sur ScreenScraper.");
                        render();
                    }
                })
                );
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

    /** Choix du cœur à retélécharger : « Tous » puis chaque cœur, installé ou non. */
    private void pickRedownload(final CoreManager cores) {
        final List<String> all = CoreManager.allCores();
        final String[] items = new String[all.size() + 1];
        items[0] = "Tous les cœurs";
        for (int i = 0; i < all.size(); i++) {
            String c = all.get(i);
            items[i + 1] = CoreManager.systemsOf(c) + " · " + c + (cores.installed(c) ? "  ✓" : "");
        }
        CodecMenu m = new CodecMenu(this).title("Retélécharger", "MOTEUR INTÉGRÉ");
        m.item(items[0], null, new Runnable() {
            public void run() { redownload(cores, all); }
        });
        for (int i = 0; i < all.size(); i++) {
            final String c = all.get(i);
            m.item(items[i + 1], null, new Runnable() {
                public void run() { redownload(cores, java.util.Collections.singletonList(c)); }
            });
        }
        m.show(CodecMenu.hostFor(this));
    }

    private void redownload(final CoreManager cores, final List<String> list) {
        // Menu codec sans entrée : il affiche la progression, B ou le voile ne l'interrompent pas.
        final CodecMenu dialog = new CodecMenu(this).title("Téléchargement des cœurs", "MOTEUR INTÉGRÉ")
                .message("Préparation…").onBack(new Runnable() { public void run() { } });
        dialog.show(CodecMenu.hostFor(this));
        new Thread(new Runnable() {
            public void run() {
                final StringBuilder failed = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    final String core = list.get(i);
                    final String step = list.size() > 1 ? (i + 1) + "/" + list.size() + " · " : "";
                    try {
                        cores.redownload(core, new CoreManager.Progress() {
                            long last;

                            public void onProgress(final long done, final long total) {
                                if (done - last < 128 * 1024 && done != total) return;
                                last = done;
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        dialog.updateMessage(step + core + " · " + (total > 0 ? (done * 100 / total) + " %"
                                                : (done / 1024) + " Ko"));
                                    }
                                });
                            }
                        });
                        // Script d'installation du cœur, étape par étape dans la fenêtre.
                        CoreInstaller.run(SettingsActivity.this, core, new CoreInstaller.Log() {
                            public void step(final String line) {
                                runOnUiThread(new Runnable() {
                                    public void run() { dialog.updateMessage(step + CoreCatalog.name(core) + " · " + line); }
                                });
                            }
                        });
                    } catch (Exception e) {
                        if (failed.length() > 0) failed.append(", ");
                        failed.append(core);
                    }
                }
                runOnUiThread(new Runnable() {
                    public void run() {
                        dialog.dismiss();
                        toast(failed.length() == 0 ? (list.size() > 1 ? "Cœurs retéléchargés." : "Cœur retéléchargé.")
                                : "Échec pour : " + failed + ". Vérifie la connexion et réessaie.");
                        render();
                    }
                });
            }
        }, "foldplay-redownload").start();
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
        // Rotation, pli ou écran externe : les volets se recalculent pour la nouvelle largeur.
        if (c.screenWidthDp != lastWidthDp && wizard == null) render();
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

    /** Le firmware s'installe dans le processus du moteur PS3 (Ps3Activity), qui ouvre le sélecteur. */
    private void pickPs3Firmware() {
        startActivity(new Intent(this, Ps3Activity.class).putExtra(Ps3Activity.EXTRA_FIRMWARE, true)
                .putExtra(Ps3Activity.EXTRA_TITLE, "Firmware PS3"));
    }
}
