package com.hideo.foldplay;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;

import java.io.File;
import java.util.List;

/**
 * Menus codec des réglages lourds (émulateurs, BIOS, sauvegardes, manettes physiques), communs aux deux
 * endroits qui les montrent : SettingsActivity (menu classique, Washi) et le niveau « Paramètres » du XMB
 * (MainActivity, Tsuru). Rien ici ne dépend d'une activité en particulier : l'hôte (SettingsRows.Host)
 * fournit l'activité, le rafraîchissement des lignes et les messages. Les sélecteurs de fichiers rendent
 * leur résultat à l'activité, qui le passe à onResult.
 */
final class SettingsMenus {
    static final int REQ_BIOS = 12;
    static final int REQ_SAVES = 13;

    private final SettingsRows.Host host;
    private final Activity act;
    private String saveSystem;
    /** Jeu concerné par les réglages avancés d'un cœur : toute modification fait passer son profil en Personnalisé. */
    private String optionsGame;
    private Pads pads;

    SettingsMenus(SettingsRows.Host host) {
        this.host = host;
        this.act = host.activity();
    }

    private Store store() {
        return host.store();
    }

    private Pads pads() {
        if (pads == null) pads = new Pads(act);
        return pads;
    }

    // ------------------------------------------------------------------ résultats

    /** Résultat d'un sélecteur de fichiers lancé d'ici ; vrai s'il était à nous. */
    boolean onResult(int request, int result, Intent data) {
        if (SaveTransfer.onResult(act, request, result, data)) return true;
        if (request == REQ_SAVES) {
            if (result == Activity.RESULT_OK && data != null && saveSystem != null) importSaves(data, saveSystem);
            return true;
        }
        if (request == REQ_BIOS) {
            if (result == Activity.RESULT_OK && data != null) importBios(data);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ console

    void consoleMenu(final SystemInfo s, final CoreManager cores, final Launcher launcher) {
        final Runnable back = new Runnable() {
            public void run() { consoleMenu(s, cores, launcher); }
        };
        CodecMenu m = new CodecMenu(act).title(s.name, "CONSOLE · " + s.shortName);
        final String core = Libretro.available() ? CoreManager.coreFor(s) : null;
        if (core != null) {
            boolean b = CoreManager.bundled(core);
            m.item("Moteur intégré : " + CoreCatalog.name(core), b ? "Livré avec l'app" : cores.installed(core) ? "Installé ✓"
                    : "Se télécharge au premier lancement d'un jeu", new Runnable() {
                        public void run() { corePage(core, back); }
                    });
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
        if ("ps3".equals(s.id) && Ps3Core.present(act)) {
            m.item("Importer le firmware PS3", Ps3Core.firmwareInstalled(act) ? "Installé ✓" : "Requis · PS3UPDAT.PUP",
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
                public void run() { SaveTransfer.export(act, s.id, null, back); }
            });
        }
        String ext = launcher.emulatorLabel(s);
        if (ext != null) m.item("Oublier le choix : " + ext, "Le prochain lancement te redemandera l'émulateur",
                new Runnable() {
                    public void run() {
                        store().setEmulator(s.id, (String) null);
                        host.toast("Le prochain lancement " + s.shortName + " te redemandera l'émulateur.");
                        host.refresh();
                    }
                });
        if (core != null) m.sub("Page du cœur " + CoreCatalog.name(core), "Réglages, licence, code source", new Runnable() {
            public void run() { corePage(core, back); }
        });
        m.show(CodecMenu.hostFor(act));
    }

    void pickBios() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            act.startActivityForResult(i, REQ_BIOS);
        } catch (ActivityNotFoundException e) {
            host.toast("Sélecteur de fichiers introuvable.");
        }
    }

    void pickSaves(String id) {
        saveSystem = id;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            act.startActivityForResult(i, REQ_SAVES);
        } catch (ActivityNotFoundException e) {
            host.toast("Sélecteur de fichiers introuvable.");
        }
    }

    /** Le firmware s'installe dans le processus du moteur PS3 (Ps3Activity), qui ouvre le sélecteur. */
    void pickPs3Firmware() {
        act.startActivity(new Intent(act, Ps3Activity.class).putExtra(Ps3Activity.EXTRA_FIRMWARE, true)
                .putExtra(Ps3Activity.EXTRA_TITLE, "Firmware PS3"));
    }

    // ------------------------------------------------------------------ cœurs

    static String coreState(CoreManager cores, String core) {
        if (CoreManager.bundled(core)) return "livré avec l'app";
        if (!cores.installed(core)) return "à télécharger";
        File f = cores.file(core);
        return String.format(java.util.Locale.ROOT, "installé · %.1f Mo", f.length() / 1048576f);
    }

    /** Page d'un cœur : présentation, état, téléchargement, réglages hors jeu, licence et source. */
    void corePage(final String core, final Runnable back) {
        final CoreManager cores = new CoreManager(act);
        final CoreCatalog.Info info = CoreCatalog.get(core);
        final Runnable self = new Runnable() {
            public void run() { corePage(core, back); }
        };
        CodecMenu m = new CodecMenu(act).title(CoreCatalog.name(core), "CŒUR · " + CoreManager.systemsOf(core))
                .message((info != null ? info.about + "\n\n" : "") + "État : " + coreState(cores, core));
        if (back != null) m.onBack(back);
        if (!CoreManager.bundled(core)) {
            m.item(cores.installed(core) ? "Mettre à jour" : "Télécharger", "Dernière version depuis libretro",
                    new Runnable() {
                        public void run() { redownload(cores, java.util.Collections.singletonList(core)); }
                    });
            if (cores.installed(core)) m.item("Supprimer", "Retire l'émulateur ; il se réinstalle au prochain jeu", new Runnable() {
                public void run() {
                    cores.delete(core);
                    host.toast(CoreCatalog.name(core) + " supprimé.");
                    host.refresh();
                }
            });
        }
        String inote = store().coreInstallNote(core);
        if (inote != null) m.item("Installation", inote, null);
        final java.util.List<String[]> variants = CoreCatalog.variants(core);
        if (variants.size() > 1) {
            m.sub("Version : " + CoreCatalog.variantLabel(core, store().coreVariant(core)),
                    variants.size() + " versions disponibles · choisis ta préférée", new Runnable() {
                public void run() { pickVariant(core, variants, self); }
            });
        }
        if (cores.hasPrevious(core)) m.item("Revenir à la version précédente", "Celle d'avant la dernière mise à jour",
                new Runnable() {
                    public void run() {
                        try {
                            cores.rollback(core);
                            store().setCoreInstallNote(core, "Version précédente rétablie");
                            host.toast("Version précédente rétablie.");
                        } catch (java.io.IOException e) {
                            host.toast("Retour impossible : " + e.getMessage());
                        }
                        self.run();
                    }
                });
        if (cores.installed(core)) m.item("Relancer l'installation", "Vérifie et réoptimise pour cet appareil",
                new Runnable() {
                    public void run() { runInstaller(core, self); }
                });
        final String[] specs = store().coreSpecs(core);
        m.sub("Réglages du cœur", specs.length == 0 ? "Lance un jeu pour les voir"
                : (specs.length / 3) + " réglages · appliqués au prochain lancement", new Runnable() {
            public void run() {
                if (specs.length == 0) {
                    host.toast("Lance un jeu de cette console pour voir ses réglages.");
                    self.run();
                } else {
                    coreOptions(core, specs, self, null);
                }
            }
        });
        if (info != null) {
            m.item("Licence : " + info.licence, info.nonCommercial
                    ? "Usage non commercial uniquement" : "Logiciel libre", null);
            m.item("Code source", info.source.replace("https://", ""), new Runnable() {
                public void run() {
                    try {
                        act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(info.source)));
                    } catch (ActivityNotFoundException e) {
                        host.toast("Aucun navigateur.");
                    }
                }
            });
        }
        m.show(CodecMenu.hostFor(act));
    }

    private void pickVariant(final String core, java.util.List<String[]> variants, final Runnable back) {
        String current = store().coreVariant(core);
        CodecMenu m = new CodecMenu(act).title("Version", CoreCatalog.name(core).toUpperCase()).onBack(back);
        for (final String[] v : variants) {
            m.radio(v[2], v[3] + " · " + v[4], v[1].equals(current), new Runnable() {
                public void run() {
                    if (v[1].equals(store().coreVariant(core))) {
                        back.run();
                        return;
                    }
                    // Autre émulateur : ses réglages ne sont pas ceux du précédent.
                    store().setCoreVariant(core, v[1]);
                    store().clearCoreOptions(core);
                    store().setCoreSpecs(core, new String[0]);
                    store().setCoreInstallNote(core, null);
                    redownload(new CoreManager(act), java.util.Collections.singletonList(core));
                }
            });
        }
        m.show(CodecMenu.hostFor(act));
    }

    private void runInstaller(final String core, final Runnable done) {
        final CodecMenu dialog = new CodecMenu(act).title("Installation", CoreCatalog.name(core).toUpperCase())
                .message("Préparation…").onBack(new Runnable() { public void run() { } });
        dialog.show(CodecMenu.hostFor(act));
        new Thread(new Runnable() {
            public void run() {
                String result;
                try {
                    result = CoreInstaller.run(act, core, new CoreInstaller.Log() {
                        public void step(final String line) {
                            act.runOnUiThread(new Runnable() {
                                public void run() { dialog.updateMessage(line); }
                            });
                        }
                    });
                } catch (Exception e) {
                    result = "Échec : " + e.getMessage();
                }
                final String r = result;
                act.runOnUiThread(new Runnable() {
                    public void run() {
                        dialog.dismiss();
                        host.toast(r);
                        done.run();
                    }
                });
            }
        }).start();
    }

    /**
     * Options d'un cœur hors jeu, rangées par thème : la liste vient du dernier lancement.
     * game : jeu dont le profil passe en Personnalisé à chaque modification (null : réglages du cœur seuls).
     */
    void coreOptions(final String core, final String[] vars, final Runnable back, String game) {
        optionsGame = game;
        CodecMenu m = new CodecMenu(act).title("Réglages", CoreCatalog.name(core).toUpperCase()).onBack(back);
        final Runnable self = new Runnable() {
            public void run() { coreOptions(core, vars, back, optionsGame); }
        };
        for (final java.util.Map.Entry<String, java.util.List<Integer>> g : CoreOptionGroups.split(vars).entrySet()) {
            m.sub(g.getKey() + " (" + g.getValue().size() + ")", CoreOptionGroups.detail(g.getKey()), new Runnable() {
                public void run() { coreOptionGroup(core, vars, g.getKey(), g.getValue(), self); }
            });
        }
        m.item("Tout remettre par défaut", null, new Runnable() {
            public void run() {
                store().clearCoreOptions(core);
                host.toast("Réglages par défaut au prochain lancement.");
                self.run();
            }
        });
        m.show(CodecMenu.hostFor(act));
    }

    private void coreOptionGroup(final String core, final String[] vars, final String group,
            final java.util.List<Integer> idx, final Runnable back) {
        java.util.Map<String, String> chosen = store().coreOptions(core);
        CodecMenu m = new CodecMenu(act).title(group, CoreCatalog.name(core).toUpperCase()).onBack(back);
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
                    CodecMenu v = new CodecMenu(act).title(label, group.toUpperCase()).onBack(self);
                    for (final String val : values) {
                        v.radio(val, null, val.equals(current), new Runnable() {
                            public void run() {
                                store().setCoreOption(core, key, val);
                                if (optionsGame != null) store().setGameProfile(optionsGame, CoreProfiles.CUSTOM);
                                self.run();
                            }
                        });
                    }
                    v.show(CodecMenu.hostFor(act));
                }
            });
        }
        m.show(CodecMenu.hostFor(act));
    }

    // ------------------------------------------------------------------ sauvegardes et BIOS

    void pickSaveSystem() {
        final String[] ids = SaveImport.SYSTEMS;
        CodecMenu m = new CodecMenu(act).title("Importer", "SAUVEGARDES · QUELLE CONSOLE ?");
        for (final String id : ids) {
            SystemInfo si = SystemInfo.byId(id);
            m.item(si != null ? si.name : id, SaveImport.formats(id), new Runnable() {
                public void run() { pickSaves(id); }
            });
        }
        m.show(CodecMenu.hostFor(act));
    }

    private void importSaves(Intent data, String system) {
        java.util.List<Uri> uris = new java.util.ArrayList<Uri>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        StringBuilder msg = new StringBuilder();
        SaveTransfer.install(act, system, SaveTransfer.read(act, uris, msg), msg, null);
    }

    void pickExportSystem() {
        CodecMenu m = new CodecMenu(act).title("Exporter", "SAUVEGARDES · QUELLE CONSOLE ?");
        for (final String id : SaveImport.SYSTEMS) {
            SystemInfo si = SystemInfo.byId(id);
            m.sub(si != null ? si.name : id, null, new Runnable() {
                public void run() {
                    SaveTransfer.export(act, id, null, new Runnable() {
                        public void run() { pickExportSystem(); }
                    });
                }
            });
        }
        m.show(CodecMenu.hostFor(act));
    }

    /** Range chaque BIOS choisi (ou contenu dans un .zip) là où son émulateur le cherche (BiosImport). */
    private void importBios(Intent data) {
        BiosImport.Report report = BiosImport.fromIntent(act, data);
        host.toast(report.text());
        host.refresh();
    }

    /** Choix du cœur à retélécharger : « Tous » puis chaque cœur, installé ou non. */
    void pickRedownload(final CoreManager cores) {
        final List<String> all = CoreManager.allCores();
        final String[] items = new String[all.size() + 1];
        items[0] = "Tous les cœurs";
        for (int i = 0; i < all.size(); i++) {
            String c = all.get(i);
            items[i + 1] = CoreManager.systemsOf(c) + " · " + c + (cores.installed(c) ? "  ✓" : "");
        }
        CodecMenu m = new CodecMenu(act).title("Retélécharger", "MOTEUR INTÉGRÉ");
        m.item(items[0], null, new Runnable() {
            public void run() { redownload(cores, all); }
        });
        for (int i = 0; i < all.size(); i++) {
            final String c = all.get(i);
            m.item(items[i + 1], null, new Runnable() {
                public void run() { redownload(cores, java.util.Collections.singletonList(c)); }
            });
        }
        m.show(CodecMenu.hostFor(act));
    }

    void redownload(final CoreManager cores, final List<String> list) {
        // Menu codec sans entrée : il affiche la progression, B ou le voile ne l'interrompent pas.
        final CodecMenu dialog = new CodecMenu(act).title("Téléchargement des cœurs", "MOTEUR INTÉGRÉ")
                .message("Préparation…").onBack(new Runnable() { public void run() { } });
        dialog.show(CodecMenu.hostFor(act));
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
                                act.runOnUiThread(new Runnable() {
                                    public void run() {
                                        dialog.updateMessage(step + core + " · " + (total > 0 ? (done * 100 / total) + " %"
                                                : (done / 1024) + " Ko"));
                                    }
                                });
                            }
                        });
                        // Script d'installation du cœur, étape par étape dans la fenêtre.
                        CoreInstaller.run(act, core, new CoreInstaller.Log() {
                            public void step(final String line) {
                                act.runOnUiThread(new Runnable() {
                                    public void run() { dialog.updateMessage(step + CoreCatalog.name(core) + " · " + line); }
                                });
                            }
                        });
                    } catch (Exception e) {
                        if (failed.length() > 0) failed.append(", ");
                        failed.append(core);
                    }
                }
                act.runOnUiThread(new Runnable() {
                    public void run() {
                        dialog.dismiss();
                        UiSound.play(act, failed.length() == 0 ? UiSound.DONE : UiSound.ERROR);
                        host.toast(failed.length() == 0 ? (list.size() > 1 ? "Cœurs retéléchargés." : "Cœur retéléchargé.")
                                : "Échec pour : " + failed + ". Vérifie la connexion et réessaie.");
                        host.refresh();
                    }
                });
            }
        }, "foldplay-redownload").start();
    }

    static void deleteRecursive(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRecursive(k);
        f.delete();
    }

    // ------------------------------------------------------------------ manettes physiques

    /** Réglages d'une manette branchée : joueur, disposition Nintendo, zone morte, réattribution des touches. */
    void padMenu(final android.view.InputDevice d, final Pads.Profile p) {
        CodecMenu m = new CodecMenu(act).title(p.name, "MANETTE · JOUEUR " + p.player);
        m.sub("Joueur", "Joueur " + p.player, new Runnable() {
            public void run() {
                CodecMenu r = new CodecMenu(act).title("Joueur", p.name);
                for (int i = 1; i <= 4; i++) {
                    final int n = i;
                    r.radio("Joueur " + i, i == 1 ? "Partage la manette à l'écran et pilote les menus" : null,
                            p.player == i, new Runnable() {
                                public void run() {
                                    p.player = n;
                                    pads().save(p);
                                    host.refresh();
                                }
                            });
                }
                r.onBack(new Runnable() { public void run() { padMenu(d, p); } });
                r.show(CodecMenu.hostFor(act));
            }
        });
        m.check("Disposition Nintendo", "A à droite, B en bas (Switch Pro, Joy-Con…)", p.nintendo, new Runnable() {
            public void run() {
                p.nintendo = !p.nintendo;
                pads().save(p);
                padMenu(d, p);
            }
        });
        m.sub("Zone morte des sticks", p.deadzone + " %", new Runnable() {
            public void run() {
                CodecMenu r = new CodecMenu(act).title("Zone morte", p.name);
                int[] values = {5, 10, 15, 20, 30};
                for (final int v : values) {
                    r.radio(v + " %", v == 15 ? "Par défaut" : null, p.deadzone == v, new Runnable() {
                        public void run() {
                            p.deadzone = v;
                            pads().save(p);
                            host.refresh();
                        }
                    });
                }
                r.onBack(new Runnable() { public void run() { padMenu(d, p); } });
                r.show(CodecMenu.hostFor(act));
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
                    pads().save(p);
                    host.refresh();
                }
            });
        }
        m.show(CodecMenu.hostFor(act));
    }

    private Pads.Profile wizard;
    private int wizardDevice;
    private int wizardStep;

    private void wizardPrompt() {
        if (wizard == null) return;
        if (wizardStep >= Pads.FUNCTIONS.length) {
            pads().save(wizard);
            host.toast("Touches enregistrées pour " + wizard.name + ".");
            wizard = null;
            host.refresh();
            return;
        }
        String f = Pads.FUNCTIONS[wizardStep];
        new CodecMenu(act).title("Touche " + ControlLayout.label(f), wizard.name + " · " + (wizardStep + 1) + "/"
                + Pads.FUNCTIONS.length)
                .message("Appuie sur la touche de la manette qui doit servir de « " + ControlLayout.label(f) + " ».")
                .sub("Passer", "Garde l'attribution d'origine pour cette touche", new Runnable() {
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
                        host.refresh();
                    }
                })
                .show(CodecMenu.hostFor(act));
    }

    /** Assistant de réattribution en cours : vrai si l'activité ne doit pas traiter la touche. */
    boolean wizardActive() {
        return wizard != null;
    }

    /** Touche reçue pendant la réattribution : retenue si elle vient de la manette réglée. Vrai si prise. */
    boolean wizardKey(android.view.KeyEvent e) {
        android.view.InputDevice d = e.getDevice();
        if (wizard == null || d == null || d.getId() != wizardDevice) return false;
        if (e.getAction() == android.view.KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            wizard.remap.put(e.getKeyCode(), Pads.FUNCTIONS[wizardStep]);
            wizardStep++;
            wizardPrompt();
        }
        return true;
    }
}
