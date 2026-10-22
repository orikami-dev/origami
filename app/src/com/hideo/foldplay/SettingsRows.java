package com.hideo.foldplay;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Le contenu des réglages, décrit une seule fois en lignes (type, titre, description, valeur, rappel).
 * Deux écrans le dessinent chacun à leur manière : SettingsActivity (menu classique à deux volets, Washi)
 * et le niveau « Paramètres » du XMB (MainActivity, Tsuru, à la façon d'une PS3). Une ligne ne sait rien
 * de la vue qui l'affiche ; les rappels passent par l'hôte quand il faut redessiner ou démarrer un écran.
 */
final class SettingsRows {
    static final String[] X360_TESTS = {"0 · Réglages actuels", "1 · Sans journal détaillé du moteur", "2 · Sans mode headless",
            "3 · Shaders synchrones", "4 · Réglages du moteur par défaut"};

    private SettingsRows() {
    }

    interface Toggle {
        void set(boolean on);
    }

    /** Choix à plusieurs valeurs (index) ou curseur (pourcentage, par paliers de 20). */
    interface Choice {
        void set(int value);
    }

    /** L'écran qui montre les lignes : activité, rafraîchissement et ce qui dépend de lui. */
    interface Host {
        Activity activity();

        Store store();

        SettingsMenus menus();

        /** Les valeurs ont changé : relire les lignes et les redessiner (position conservée). */
        void refresh();

        /** Thème, texte, contraste, animations : tout l'écran se redessine. */
        void restyle();

        /** Plein écran, pastilles : la mise en page de l'écran hôte se refait (rien dans le menu classique). */
        void relayout();

        void toast(String msg);

        void pickFolder();

        void rescan();

        void forgetFolder();

        /** La langue vient de changer : l'app repart dans la nouvelle langue, sur cette section. */
        void languageChanged(String section);

        /** « Rechercher une mise à jour » : le résultat s'affiche à la façon de l'écran hôte. */
        void checkUpdate();
    }

    static final class Row {
        static final int HEADER = 0, TOGGLE = 1, CHOICE = 2, SLIDER = 3, NOTE = 4, ACTION = 5, SLOT = 6, LOGO = 7;

        final int type;
        final String title;
        String desc;
        /** Interrupteur. */
        boolean on;
        /** Choix : libellés et index courant. */
        String[] labels;
        int index;
        /** Curseur : pourcentage courant. */
        int percent;
        Toggle toggle;
        Choice choice;
        Runnable action;
        /** Case de grille dans le menu classique ; le XMB la montre comme une ligne ordinaire. */
        boolean tile;
        /** Texte court à droite d'une action (XMB). */
        String valueText;

        Row(int type, String title) {
            this.type = type;
            this.title = title;
        }

        /** Peut recevoir la sélection à la manette. */
        boolean selectable() {
            return type == TOGGLE || type == CHOICE || type == SLIDER || type == ACTION;
        }

        /** Texte de la valeur à droite de la ligne : libellé du choix, pourcentage, valeur de l'action. */
        String valueLabel() {
            if (type == CHOICE) return labels[Math.max(0, Math.min(labels.length - 1, index))];
            if (type == SLIDER) return percent + " %";
            return valueText;
        }
    }

    // ------------------------------------------------------------------ briques

    static Row header(List<Row> l, String label) {
        Row r = new Row(Row.HEADER, label);
        l.add(r);
        return r;
    }

    static Row note(List<Row> l, String text) {
        Row r = new Row(Row.NOTE, text);
        l.add(r);
        return r;
    }

    static Row action(List<Row> l, String title, String desc, Runnable run) {
        Row r = new Row(Row.ACTION, title);
        r.desc = desc;
        r.action = run;
        l.add(r);
        return r;
    }

    static Row toggle(List<Row> l, String title, String desc, boolean value, Toggle t) {
        Row r = new Row(Row.TOGGLE, title);
        r.desc = desc;
        r.on = value;
        r.toggle = t;
        l.add(r);
        return r;
    }

    static Row choice(List<Row> l, String title, String[] labels, int value, Choice c) {
        Row r = new Row(Row.CHOICE, title);
        r.labels = labels;
        r.index = Math.max(0, Math.min(labels.length - 1, value));
        r.choice = c;
        l.add(r);
        return r;
    }

    static Row slider(List<Row> l, String title, int value, Choice c) {
        Row r = new Row(Row.SLIDER, title);
        r.percent = Math.max(0, Math.min(100, Math.round(value / 20f) * 20));
        r.choice = c;
        l.add(r);
        return r;
    }

    private static Row link(List<Row> l, final Host h, String title, String subtitle, final String url) {
        return action(l, title, subtitle + " · site officiel", new Runnable() {
            public void run() {
                try {
                    h.activity().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (ActivityNotFoundException e) {
                    h.toast(url);
                }
            }
        });
    }

    // ------------------------------------------------------------------ sections

    /** Les lignes d'une section (library, emulators, display, sound, a11y, monitoring, updates). */
    static List<Row> build(Host h, String section) {
        List<Row> l = new ArrayList<Row>();
        if (section.equals("library")) library(h, l);
        else if (section.equals("emulators")) emulators(h, l);
        else if (section.equals("display")) display(h, l);
        else if (section.equals("monitoring")) monitoring(h, l);
        else if (section.equals("sound")) sound(h, l);
        else if (section.equals("a11y")) accessibility(h, l);
        else {
            updates(h, l);
            about(h, l);
        }
        return l;
    }

    private static String readable(String treeUri) {
        String id = Uri.decode(treeUri);
        int i = id.lastIndexOf("/tree/");
        if (i >= 0) id = id.substring(i + 6);
        return id.replace("primary:", "Stockage interne/");
    }

    private static void library(final Host h, List<Row> l) {
        String tree = h.store().treeUri();
        action(l, "Dossier de jeux", tree == null ? "Aucun dossier choisi" : readable(tree), new Runnable() {
            public void run() { h.pickFolder(); }
        });
        if (tree != null) {
            action(l, "Rescanner la bibliothèque", "Cherche les nouveaux jeux dans ton dossier", new Runnable() {
                public void run() { h.rescan(); }
            });
            action(l, "Oublier ce dossier", "Origami n'aura plus accès à ce dossier", new Runnable() {
                public void run() { h.forgetFolder(); }
            });
        }
    }

    /** Résumé d'une console pour sa ligne, ou null si Origami n'a rien à proposer pour elle. */
    private static String consoleSummary(SystemInfo s, CoreManager cores, Launcher launcher) {
        String core = Libretro.available() ? CoreManager.coreFor(s) : null;
        String ext = launcher.emulatorLabel(s);
        StringBuilder b = new StringBuilder();
        if (core != null) {
            b.append("Intégré · ").append(core);
            if (CoreManager.bundled(core)) b.append(" · livré");
            else b.append(cores.installed(core) ? " ✓" : " · au premier lancement");
            if ("ps2".equals(s.id)) b.append(cores.hasPs2Bios() ? " · BIOS ✓" : " · BIOS manquant");
        }
        if (ext != null) b.append(b.length() > 0 ? " · " : "").append("Choix : ").append(ext);
        if (b.length() == 0) return null;
        return b.toString();
    }

    private static void emulators(final Host h, List<Row> l) {
        final Activity act = h.activity();
        final Store store = h.store();
        final SettingsMenus menus = h.menus();
        if (!Libretro.available()) note(l, "Demande un appareil 64 bits (arm64).");
        final CoreManager cores = new CoreManager(act);
        final Launcher launcher = new Launcher(act, store);

        // 1. Une ligne par console : tout ce qui la concerne s'ouvre dans son menu.
        // Une case par console (un cœur = une console) : nom, cœur installé ou aucun, taille.
        header(l, "Consoles");
        for (final SystemInfo s : SystemInfo.ALL) {
            if (consoleSummary(s, cores, launcher) == null) continue;
            String core = Libretro.available() ? CoreManager.coreFor(s) : null;
            String line2 = "aucun", line3 = "";
            if (core != null && (CoreManager.bundled(core) || cores.installed(core))) {
                line2 = CoreCatalog.name(core);
                File f = cores.file(core);
                line3 = f != null && f.exists() ? String.format(java.util.Locale.ROOT, "%.1f Mo", f.length() / 1048576f)
                        : "livré";
            }
            Row r = action(l, s.name, line2 + (line3.length() > 0 ? "\n" + line3 : ""), new Runnable() {
                public void run() { menus.consoleMenu(s, cores, launcher); }
            });
            r.tile = true;
        }
        if (Libretro.available()) note(l, "Switch : Eden, intégré comme les autres cœurs, bientôt.");

        // 3. Fichiers communs à toutes les consoles.
        header(l, "BIOS et sauvegardes");
        final boolean ps2 = Libretro.available() && cores.hasPs2Bios();
        action(l, "Importer un BIOS", (ps2 ? "BIOS PS2 présent ✓ · " : "") + "PS1, PS2, Saturn, Dreamcast, GBA, DS, Mega-CD, PC Engine CD, Neo Geo… fichiers ou .zip tirés de ta console",
                new Runnable() {
                    public void run() { menus.pickBios(); }
                });
        if (Ps3Core.present(act)) {
            action(l, "Importer le firmware PS3", (Ps3Core.firmwareInstalled(act) ? "Installé ✓ · " : "")
                    + "PS3UPDAT.PUP, téléchargé sur le site de PlayStation", new Runnable() {
                        public void run() { menus.pickPs3Firmware(); }
                    });
        }
        action(l, "Importer des sauvegardes", "Depuis un autre émulateur", new Runnable() {
            public void run() { menus.pickSaveSystem(); }
        });
        action(l, "Exporter des sauvegardes", "Vers une autre app, un PC ou un dossier", new Runnable() {
            public void run() { menus.pickExportSystem(); }
        });
        note(l, "Origami est aussi dans le menu Partager d'Android : envoie-lui des sauvegardes depuis une autre app "
                + "pour les importer. Le fichier porte le nom du jeu (Zelda.sav pour Zelda.z64).");

        // 4. Entretien.
        header(l, "Entretien");
        if (Libretro.available()) {
            action(l, "Retélécharger les cœurs", "Réinstalle un émulateur ou tous, en cas de souci", new Runnable() {
                public void run() { menus.pickRedownload(cores); }
            });
            action(l, "Supprimer les cœurs téléchargés", (cores.installedSize() / (1024 * 1024)) + " Mo utilisés",
                    new Runnable() {
                        public void run() {
                            SettingsMenus.deleteRecursive(new File(act.getFilesDir(), "cores"));
                            h.toast("Cœurs supprimés, retéléchargés au prochain lancement.");
                            h.refresh();
                        }
                    });
        }
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            boolean all = android.os.Environment.isExternalStorageManager();
            action(l, "Accès à tous les fichiers", all ? "Accordé : .cue/.bin lus directement"
                    : "Pour les jeux multi-fichiers (.cue + .bin)", new Runnable() {
                public void run() {
                    try {
                        act.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:" + act.getPackageName())));
                    } catch (ActivityNotFoundException e) {
                        h.toast("Réglage introuvable sur cet appareil.");
                    }
                }
            });
        }
        note(l, "Sauvegardes et BIOS : Android/data/com.hideo.foldplay/files (saves/ et system/).");
    }

    /** Jaquettes et fiches (sous Affichage). */
    private static void covers(final Host h, List<Row> l) {
        final Activity act = h.activity();
        final Store store = h.store();
        toggle(l, "Jaquettes automatiques", "Jaquettes manquantes après chaque scan",
                store.autoCovers(), new Toggle() {
                    public void set(boolean on) {
                        store.setAutoCovers(on);
                    }
                });
        toggle(l, "Fiches de jeux", "Résumé et année de sortie de chaque jeu, sur l'accueil",
                store.autoInfo(), new Toggle() {
                    public void set(boolean on) {
                        store.setAutoInfo(on);
                    }
                });
        toggle(l, "Illustrations de fond", "L'accueil affiche une image du jeu sélectionné "
                + "derrière : ScreenScraper (compte requis), sinon sa jaquette floutée", store.backdrops(), new Toggle() {
            public void set(boolean on) {
                store.setBackdrops(on);
            }
        });
        String[] ss = store.screenScraper();
        action(l, "Compte ScreenScraper", ss[0].isEmpty()
                ? "Synopsis officiels : identifiants développeur et compte"
                : "Saisi · synopsis officiels activés", new Runnable() {
            public void run() {
                screenScraperDialog(h);
            }
        });
        action(l, "Retélécharger les fiches", "Efface les résumés enregistrés et les récupère à nouveau", new Runnable() {
            public void run() {
                new GameInfo(act).clear();
                h.toast("Fiches effacées.");
            }
        });
        action(l, "Retélécharger toutes les jaquettes", "Efface le cache puis relance la récupération", new Runnable() {
            public void run() {
                SettingsMenus.deleteRecursive(new File(act.getFilesDir(), "covers"));
                SettingsMenus.deleteRecursive(new File(act.getFilesDir(), "backdrops"));
                store.requestRescan();
                h.toast("Cache vidé. Les jaquettes reviendront à l'accueil.");
            }
        });
        note(l, "Source : libretro-thumbnails. Les jaquettes appartiennent à leurs éditeurs "
                + "et restent sur ton appareil.");
    }

    /** Saisie des identifiants ScreenScraper : seule boîte de dialogue Android du lot (champs de texte). */
    private static void screenScraperDialog(final Host h) {
        final Activity act = h.activity();
        final Store store = h.store();
        String[] cur = store.screenScraper();
        float d = act.getResources().getDisplayMetrics().density;
        android.widget.LinearLayout box = new android.widget.LinearLayout(act);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(Math.round(20 * d), Math.round(8 * d), Math.round(20 * d), 0);
        final android.widget.EditText[] f = new android.widget.EditText[4];
        String[] hints = {"Identifiant développeur (devid)", "Mot de passe développeur",
            "Ton identifiant ScreenScraper (facultatif)", "Ton mot de passe ScreenScraper"};
        for (int i = 0; i < 4; i++) {
            f[i] = new android.widget.EditText(act);
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
        Theme.show(new android.app.AlertDialog.Builder(act, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Compte ScreenScraper")
                .setView(box)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Enregistrer", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dlg, int w) {
                        store.setScreenScraper(f[0].getText().toString().trim(), f[1].getText().toString().trim(),
                                f[2].getText().toString().trim(), f[3].getText().toString());
                        h.toast("Les fiches seront recherchées sur ScreenScraper.");
                        h.refresh();
                    }
                })
                );
    }

    private static void display(final Host h, List<Row> l) {
        final Activity act = h.activity();
        final Store store = h.store();
        header(l, "Langue");
        action(l, "Langue · Language", I18n.choiceName(act), new Runnable() {
            public void run() {
                I18n.pick(act, new Runnable() {
                    public void run() {
                        // Toute l'app repart dans la nouvelle langue, sur cette même page.
                        h.languageChanged("display");
                    }
                });
            }
        });
        header(l, "Interface");
        toggle(l, "Plein écran hors jeu", "Masque les barres système dans les menus",
                store.appFullscreen(), new Toggle() {
                    public void set(boolean on) {
                        store.setAppFullscreen(on);
                        Theme.fullscreen(act);
                        h.relayout();
                    }
                });
        toggle(l, "Pastilles", "Coins arrondis autour de l'écran du jeu, de la manette et de la barre du haut.",
                store.pills(), new Toggle() {
                    public void set(boolean on) {
                        store.setPills(on);
                        h.relayout();
                    }
                });
        // 0.66.4 (BOSS) : l'accueil part en Washi par défaut, l'option se lit donc dans ce sens.
        toggle(l, "Mode Washi par défaut", "Écran interne ; sinon Tsuru. "
                + "L'écran externe ouvre toujours Washi", !store.flexView(), new Toggle() {
            public void set(boolean on) {
                store.setFlexView(!on);
            }
        });
        toggle(l, "Mode Tsuru à l'ouverture", "Déplier le pliant dans Origami passe en mode jeu",
                store.tsuruOnOpen(), new Toggle() {
            public void set(boolean on) {
                store.setTsuruOnOpen(on);
            }
        });
        action(l, "Origami comme lanceur par défaut", HomeApps.isDefault(act)
                ? "Déjà le lanceur du téléphone. Touche pour changer"
                : "Le bouton Accueil du téléphone ouvre Origami ; tes applis restent dans le menu Applications",
                new Runnable() {
            public void run() {
                HomeApps.askDefault(act);
            }
        });
        toggle(l, "Écran titre au démarrage", "L'animation de la grue au lancement", store.intro(), new Toggle() {
            public void set(boolean on) {
                store.setIntro(on);
            }
        });
        toggle(l, "Fonds animés", "Le décor de chaque thème bouge doucement, en boucle", store.animatedBackdrop(), new Toggle() {
            public void set(boolean on) {
                store.setAnimatedBackdrop(on);
                Theme.ANIMATE = on;
                h.restyle();
            }
        });
        toggle(l, "Assombrir le fond", "Voile sombre sur le fond d'écran pour mieux lire les textes ; sinon le fond se voit en pleine lumière",
                store.dimBackdrop(), new Toggle() {
            public void set(boolean on) {
                store.setDimBackdrop(on);
                h.relayout();
            }
        });
        note(l, "Manettes et vibrations : icône manette de l'accueil.");
        header(l, "Jaquettes et fiches");
        covers(h, l);
    }

    /** Monitoring : l'ATH en jeu façon RTSS, son style et les mesures affichées. */
    private static void monitoring(Host h, List<Row> l) {
        final Store store = h.store();
        header(l, "Cœurs du processeur");
        int cm = 0;
        for (int i = 0; i < CoreAffinity.MODES.length; i++) if (CoreAffinity.MODES[i].equals(store.coresMode())) cm = i;
        choice(l, "Répartition des cœurs", CoreAffinity.LABELS, cm, new Choice() {
            public void set(int i) {
                store.setCoresMode(CoreAffinity.MODES[i]);
            }
        });
        note(l, "PS3 et Xbox 360. Performance : le son, le rendu et le jeu passent sur les gros cœurs, les shaders se "
                + "compilent sur les moyens. Économie : tout sur les moyens, le téléphone chauffe moins. Auto : Android décide.");
        header(l, "Pilote graphique (PS3)");
        int gd = "turnip".equals(store.gpuDriver()) ? 1 : 0;
        choice(l, "Pilote Vulkan", GpuDriver.LABELS, gd, new Choice() {
            public void set(int i) {
                store.setGpuDriver(GpuDriver.MODES[i]);
            }
        });
        final Activity gact = h.activity();
        final Host gh = h;
        action(l, "Télécharger Turnip", GpuDriver.installed(gact) ? "Un pilote est installé · remplacer par le dernier" : "Pilote libre pour Adreno, utilisé à la place de celui du téléphone",
                new Runnable() {
            public void run() {
                gh.toast("Téléchargement du pilote…");
                new Thread(new Runnable() {
                    public void run() {
                        String r;
                        try {
                            r = "Pilote installé : " + GpuDriver.download(gact);
                        } catch (Throwable t) {
                            r = "Échec du téléchargement : " + t.getMessage();
                        }
                        final String msg = r;
                        gact.runOnUiThread(new Runnable() {
                            public void run() {
                                gh.toast(msg);
                                gh.refresh();
                            }
                        });
                    }
                }).start();
            }
        });
        note(l, "Essaie Turnip si Demon's Souls ou un autre jeu PS3 plante dans le pilote graphique. Appliqué au prochain lancement d'un jeu PS3.");
        header(l, "Son (PS3)");
        final String[] msL = {"60 ms", "120 ms", "180 ms", "250 ms"};
        final int[] msV = {60, 120, 180, 250};
        int mi = 3;
        for (int i = 0; i < msV.length; i++) if (msV[i] == store.ps3AudioMs()) mi = i;
        choice(l, "Tampon du son", msL, mi, new Choice() {
            public void set(int i) {
                store.setPs3AudioMs(msV[i]);
            }
        });
        toggle(l, "Étirement du temps", "Le son ralentit avec le jeu au lieu de couper", store.ps3Stretch(), new Toggle() {
            public void set(boolean on) {
                store.setPs3Stretch(on);
            }
        });
        note(l, "Un tampon court réduit le décalage avec l'image mais coupe plus vite si le jeu ralentit. Appliqué au prochain lancement d'un jeu PS3.");
        header(l, "ATH en jeu");
        toggle(l, "ATH de monitoring en jeu", "Mesures en direct, façon RTSS",
                store.hud(), new Toggle() {
                    public void set(boolean on) {
                        store.setHud(on);
                    }
                });
        choice(l, "Style", new String[] {"RTSS", "Bloc sur plusieurs lignes"},
                "block".equals(store.hudStyle()) ? 1 : 0, new Choice() {
                    public void set(int i) {
                        store.setHudStyle(i == 1 ? "block" : "line");
                    }
                });
        choice(l, "Position", Hud.POS_LABELS, Hud.index(Hud.POS_KEYS, store.hudPos()), new Choice() {
            public void set(int i) {
                store.setHudPos(Hud.POS_KEYS[i]);
            }
        });
        choice(l, "Taille du texte", Hud.SIZE_LABELS, store.hudSize(), new Choice() {
            public void set(int i) {
                store.setHudSize(i);
            }
        });
        choice(l, "Couleur des libellés", Hud.COLOR_LABELS, Hud.index(Hud.COLOR_KEYS, store.hudColor()), new Choice() {
            public void set(int i) {
                store.setHudColor(Hud.COLOR_KEYS[i]);
            }
        });
        choice(l, "Unité de température", Hud.UNIT_LABELS, Hud.index(Hud.UNIT_KEYS, store.tempUnit()), new Choice() {
            public void set(int i) {
                store.setTempUnit(Hud.UNIT_KEYS[i]);
            }
        });
        int rate = 1;
        for (int i = 0; i < Hud.RATES.length; i++) if (Hud.RATES[i] == store.hudRate()) rate = i;
        choice(l, "Mise à jour", Hud.RATE_LABELS, rate, new Choice() {
            public void set(int i) {
                store.setHudRate(Hud.RATES[i]);
            }
        });
        toggle(l, "Bandeau sombre", "Fond sombre derrière l'ATH ; sinon texte ombré sur l'image", store.hudBackground(),
                new Toggle() {
                    public void set(boolean on) {
                        store.setHudBackground(on);
                    }
                });
        header(l, "Mesures affichées");
        java.util.Set<String> items = store.hudItems();
        for (final String[] it : Hud.ITEMS) {
            toggle(l, it[1], it[2], items.contains(it[0]), new Toggle() {
                public void set(boolean on) {
                    store.setHudItem(it[0], on);
                }
            });
        }
        final Activity act = h.activity();
        if (!store.devUnlocked()) return; // 0.67 : débogage et contenu expérimental cachés (10 appuis sur le logo)
        header(l, "Débogage");
        toggle(l, "Mode débogage", "Journal détaillé et rapport de plantage, "
                + "à partager en cas de bug", store.debug(), new Toggle() {
            public void set(boolean on) {
                store.setDebug(on);
                DebugLog.sync(act);
            }
        });
        if (store.debug()) {
            toggle(l, "Profil de test KAMI-WORKS", "Applique d'un coup toutes les pistes en essai (pilote Turnip, son 150 ms avec "
                    + "étirement précoce, 6 fils de compilation libres, minuteries précises, son et rendu sur les gros cœurs). "
                    + "Pour nos essais seulement.",
                    store.lab(), new Toggle() {
                public void set(boolean on) {
                    store.setLab(on);
                }
            });
            toggle(l, "Précompilation PS3 (écran éteint)", "Le jeu continue de compiler quand l'écran s'éteint (service de premier plan) ; quand la "
                    + "compilation est finie (90 s sans activité), le jeu se met en pause et le chrono s'arrête. Expérimental : "
                    + "laisse le téléphone branché.",
                    store.precompile(), new Toggle() {
                public void set(boolean on) {
                    store.setPrecompile(on);
                }
            });
            choice(l, "Essai écran noir Xbox 360", X360_TESTS, store.x360Test(), new Choice() {
                public void set(int i) {
                    store.setX360Test(i);
                }
            });
            note(l, "Une piste retirée à la fois pour trouver ce qui bloque l'image (le choix est écrit au journal, avec le nombre d'images à 10, 30, 60 et 120 s). "
                    + "Relance le jeu après chaque changement.");
            action(l, "Vider le cache de shaders PS3", "Pour mesurer à froid. Ne supprime que le cache, il se reconstruit au prochain lancement.",
                    new Runnable() {
                public void run() {
                    java.io.File base = act.getExternalFilesDir(null);
                    int n = ShaderMeter.clear(new java.io.File(Ps3Core.root(act)));
                    I18n.toast(act, n + " fichiers de cache supprimés", android.widget.Toast.LENGTH_LONG).show();
                }
            });
        }
        long logSize = DebugLog.size(act);
        if (logSize > 0) {
            action(l, "Partager le journal", String.format(java.util.Locale.ROOT, "%.1f Mo", logSize / 1048576f)
                    + " · fiche de l'appareil comprise", new Runnable() {
                public void run() {
                    DebugLog.share(act);
                }
            });
            action(l, "Copier le journal", "Un clic : la fin du journal part dans le presse-papiers, à coller dans le chat",
                    new Runnable() {
                public void run() {
                    DebugLog.copy(act);
                }
            });
            action(l, "Effacer le journal", "Supprime les journaux et rapports enregistrés", new Runnable() {
                public void run() {
                    boolean on = store.debug();
                    DebugLog.stop();
                    DebugLog.clear(act);
                    if (on) DebugLog.sync(act);
                    I18n.toast(act, "Journal effacé", android.widget.Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * Accessibilité (0.62.5) : tenue à une main (droitier ou gaucher), textes, contraste,
     * animations, et aides de jeu pour qui a du mal à maintenir un appui ou à faire de grands gestes.
     */
    private static void accessibility(final Host h, List<Row> l) {
        final Activity act = h.activity();
        final Store store = h.store();
        header(l, "Lecture");
        choice(l, "Taille des textes", new String[] {"Normale", "Grande", "Très grande"}, store.a11yText(), new Choice() {
            public void set(int i) {
                store.setA11yText(i);
                a11yChanged(h);
            }
        });
        toggle(l, "Contraste élevé", "Textes secondaires plus clairs, plus faciles à lire", store.highContrast(), new Toggle() {
            public void set(boolean on) {
                store.setHighContrast(on);
                a11yChanged(h);
            }
        });
        toggle(l, "Réduire les animations", "Fonds et contour de la manette immobiles", store.reduceMotion(), new Toggle() {
            public void set(boolean on) {
                store.setReduceMotion(on);
                a11yChanged(h);
            }
        });
        header(l, "En jeu");
        toggle(l, "Gâchettes à bascule", "L, R, L2 et R2 : un appui les enfonce, le suivant les relâche, sans garder le doigt dessus",
                store.latchTriggers(), new Toggle() {
                    public void set(boolean on) {
                        store.setLatchTriggers(on);
                        Theme.sync(act);
                    }
                });
        choice(l, "Course des joysticks", new String[] {"Normale", "Courte", "Très courte"}, store.stickReach(), new Choice() {
            public void set(int i) {
                store.setStickReach(i);
                Theme.sync(act);
            }
        });
        note(l, "Une course courte pousse le joystick à fond avec un petit geste du pouce.");
        toggle(l, "Vibrations renforcées", "Retour plus fort et plus long à chaque appui sur la manette", store.strongHaptics(),
                new Toggle() {
                    public void set(boolean on) {
                        store.setStrongHaptics(on);
                        Theme.sync(act);
                    }
                });
    }

    private static void a11yChanged(Host h) {
        Theme.sync(h.activity());
        Theme.textScale(h.activity());
        h.restyle();
    }

    /** Mises à jour (0.62.5) : version installée, recherche, téléchargement et installation. */
    private static void updates(final Host h, List<Row> l) {
        final Activity act = h.activity();
        final Store store = h.store();
        header(l, "Version installée");
        note(l, "Origami " + Updater.installed(act));
        toggle(l, "Vérifier automatiquement", "Vérifie toutes les 6 h à l'ouverture de l'accueil, puis prévient par une notification et une fenêtre",
                store.autoUpdates(), new Toggle() {
                    public void set(boolean on) {
                        store.setAutoUpdates(on);
                        if (on && android.os.Build.VERSION.SDK_INT >= 33) {
                            act.requestPermissions(new String[] {"android.permission.POST_NOTIFICATIONS"}, 62);
                        }
                    }
                });
        if (store.devUnlocked()) toggle(l, "Préversions", "Reçois aussi les builds de test et les corrections rapides avant la version stable",
                store.previewUpdates(), new Toggle() {
                    public void set(boolean on) {
                        store.setPreviewUpdates(on);
                    }
                });
        action(l, "Rechercher une mise à jour", "Compare avec la dernière version publiée", new Runnable() {
            public void run() { h.checkUpdate(); }
        });
        // Emplacement du résultat de la recherche (menu classique seulement).
        l.add(new Row(Row.SLOT, "update"));
    }

    private static int logoTaps;
    private static long logoLast;

    /** 10 appuis rapprochés sur le logo : ouvre ou rappelle le mode développeur (débogage, préversions, expérimental). */
    static void tapLogo(Activity act) {
        Store st = new Store(act);
        long now = android.os.SystemClock.elapsedRealtime();
        logoTaps = now - logoLast > 2500 ? 1 : logoTaps + 1;
        logoLast = now;
        if (st.devUnlocked()) {
            if (logoTaps == 3) I18n.toast(act, "Le mode développeur est déjà activé", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        if (logoTaps >= 10) {
            st.setDevUnlocked(true);
            logoTaps = 0;
            I18n.toast(act, "Mode développeur activé : rouvre les Réglages pour voir le débogage et les préversions",
                    android.widget.Toast.LENGTH_LONG).show();
        } else if (logoTaps >= 5) {
            I18n.toast(act, "Encore " + (10 - logoTaps) + " appuis pour activer le mode développeur", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private static void about(final Host h, List<Row> l) {
        final Activity act = h.activity();
        final Store store = h.store();
        action(l, "Relancer l'assistant de démarrage", "Dossier, moteurs intégrés, émulateurs, manette", new Runnable() {
            public void run() {
                act.startActivity(new Intent(act, SetupActivity.class));
            }
        });
        String version = "?";
        try {
            version = act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            // Version inconnue.
        }
        l.add(new Row(Row.LOGO, "origami"));
        header(l, "ORIGAMI " + version);
        note(l, "Déplie. Joue. Une production KAMI-WORKS.");
        note(l, "Origami n'est affilié à aucun fabricant de consoles ni éditeur de jeux. Les noms de "
                + "consoles cités servent uniquement à indiquer la compatibilité et appartiennent à leurs "
                + "propriétaires respectifs.");
        note(l, "Origami ne fournit ni jeu, ni BIOS, ni clé. Utilise tes propres copies.");
        note(l, "Polices : Black Ops One et Michroma, sous licence SIL Open Font License 1.1.");
        note(l, "Moteur PS2 : ARMSX2 (dérivé de PCSX2), cœur libretro sous licence GNU GPL 3.0, livré avec l'app "
                + "et compilé depuis le code source publié ci-dessous, avec un petit correctif Origami pour "
                + "l'accéléré (native/armsx2-patches, fourni sur demande avec le code de l'app).");
        link(l, h, "Code source d'ARMSX2", "Version " + SettingsActivity.ARMSX2_COMMIT.substring(0, 7),
                "https://github.com/ARMSX2/ARMSX2/tree/" + SettingsActivity.ARMSX2_COMMIT);
        note(l, "Moteur GameCube et Wii : Dolphin, cœur libretro sous licence GNU GPL 2.0 ou ultérieure, livré avec "
                + "l'app et compilé sans modification depuis le code source publié ci-dessous.");
        link(l, h, "Code source de Dolphin (libretro)", "Version " + SettingsActivity.DOLPHIN_COMMIT.substring(0, 7),
                "https://github.com/libretro/dolphin/tree/" + SettingsActivity.DOLPHIN_COMMIT);
    }

    /** Son (0.62.1) : sa propre catégorie, demandée par BOSS. */
    private static void sound(Host h, List<Row> l) {
        final Activity act = h.activity();
        final Store store = h.store();
        header(l, "Interface");
        toggle(l, "Bruits de l'interface", "Déplacement, validation, retour, lancement d'un jeu, "
                + "propres à chaque thème", store.menuSound(), new Toggle() {
            public void set(boolean on) {
                store.setMenuSound(on);
                UiSound.play(act, UiSound.SELECT);
            }
        });
        slider(l, "Volume des bruits", store.uiVolume(), new Choice() {
            public void set(int v) {
                store.setUiVolume(v);
                UiSound.play(act, UiSound.SELECT);
            }
        });
        header(l, "Musique");
        toggle(l, "Musique d'accueil", "Une boucle par thème, coupée pendant les parties", store.musicOn(), new Toggle() {
            public void set(boolean on) {
                store.setMusicOn(on);
                if (on) UiSound.musicStart(act);
                else UiSound.musicPause();
            }
        });
        slider(l, "Volume de la musique", store.musicVolume(), new Choice() {
            public void set(int v) {
                store.setMusicVolume(v);
                UiSound.musicStart(act);
            }
        });
        note(l, "Musique et bruits ne jouent qu'en mode sonnerie : rien en vibreur ou en silencieux.");
    }
}
