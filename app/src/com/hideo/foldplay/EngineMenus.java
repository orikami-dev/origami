package com.hideo.foldplay;

import android.app.Activity;
import android.content.Intent;
import android.widget.FrameLayout;

/**
 * Sous-menus Home communs aux moteurs à surface propre (PS3, Xbox 360) : format d'image et
 * options contrôleur, pour retrouver ce que propose le menu des autres consoles (0.62).
 */
final class EngineMenus {
    private static final int[] OPACITIES = {10, 20, 35, 50, 70};

    private EngineMenus() {
    }

    /** Format d'image : Auto, 16:9, 4:3, Étirer, et le verrou de la touche FORMAT. */
    static void format(final Activity a, final FrameLayout stage, final Store store, final String system,
                       final Runnable back, final Runnable apply) {
        final Runnable self = new Runnable() {
            public void run() { format(a, stage, store, system, back, apply); }
        };
        CodecMenu m = new CodecMenu(a).title("Format d'image", system.toUpperCase()).onBack(back);
        String cur = store.displayMode(system);
        String[] help = {"Le rapport choisi par le jeu", "Écran large", "Format d'origine des vieux téléviseurs",
                "Remplit tout l'écran"};
        for (int i = 0; i < ScreenFormat.KEYS.length; i++) {
            final String k = ScreenFormat.KEYS[i];
            m.radio(ScreenFormat.LABELS[i], help[i], k.equals(cur), new Runnable() {
                public void run() {
                    store.setDisplayMode(system, k);
                    ScreenFormat.refreshCaption(store, system);
                    apply.run();
                }
            });
        }
        m.check("Verrouiller la touche FORMAT", "Évite de changer de format par un appui involontaire "
                + "(appui long sur la touche)", store.formatLocked(), new Runnable() {
            public void run() {
                store.setFormatLocked(!store.formatLocked());
                ScreenFormat.refreshCaption(store, system);
                apply.run();
                self.run();
            }
        });
        m.show(stage);
    }

    /**
     * Sous-menu Affichage des moteurs PS3 / 360 : ATH, format d'image et Lumen (grisé sans les pastilles).
     * Chaque sous-menu revient ici ; « back » ramène au menu Home.
     */
    static void display(final Activity a, final FrameLayout stage, final Store store, final String system,
                        final String sub, final Runnable back, final Runnable applyHud,
                        final Runnable applyFormat, final Runnable applyLumen) {
        final Runnable self = new Runnable() {
            public void run() {
                CodecMenu old = CodecMenu.find(stage);
                if (old != null) old.dismiss();
                display(a, stage, store, system, sub, back, applyHud, applyFormat, applyLumen);
            }
        };
        new CodecMenu(a).title("Affichage", sub).onBack(back)
                .check("ATH", "Images/s, processeur, batterie…", store.hud(), new Runnable() {
                    public void run() {
                        store.setHud(!store.hud());
                        applyHud.run();
                    }
                })
                .sub("Format d'image", ScreenFormat.label(store.displayMode(system))
                        + (store.formatLocked() ? " · verrouillé" : ""), new Runnable() {
                    public void run() { format(a, stage, store, system, self, applyFormat); }
                })
                .subIf(store.pills(), "Lumen", andonLabel(store) + " · lumière autour du jeu", new Runnable() {
                    public void run() { andon(a, stage, store, self, applyLumen); }
                })
                .show(stage);
    }

    /** Lumen (classe Andon) : lumière d'ambiance autour de l'écran du jeu, plusieurs modes. */
    static void andon(final Activity a, final FrameLayout stage, final Store store, final Runnable back,
                      final Runnable apply) {
        CodecMenu m = new CodecMenu(a).title("Lumen", "LUMIÈRE AUTOUR DU JEU").onBack(back);
        String cur = store.glowMode();
        for (int i = 0; i < Andon.KEYS.length; i++) {
            final String k = Andon.KEYS[i];
            m.radio(Andon.LABELS[i], Andon.HELP[i], k.equals(cur), new Runnable() {
                public void run() {
                    store.setGlowMode(k);
                    apply.run();
                }
            });
        }
        m.show(stage);
    }

    static String andonLabel(Store store) {
        String cur = store.glowMode();
        for (int i = 0; i < Andon.KEYS.length; i++) if (Andon.KEYS[i].equals(cur)) return Andon.LABELS[i];
        return Andon.LABELS[0];
    }

    /** Manette tactile : disposition, transparence, gâchettes, plein écran avec une manette, éditeur. */
    static void controller(final Activity a, final FrameLayout stage, final Store store, final ControlDeckView deck,
                           final Runnable back, final Runnable relayout) {
        final Runnable self = new Runnable() {
            public void run() { controller(a, stage, store, deck, back, relayout); }
        };
        CodecMenu m = new CodecMenu(a).title("Options contrôleur", "MANETTE TACTILE").onBack(back);
        m.sub("Manette", store.activeLayout() + " · Flex, 2D, 3D ou les tiennes", new Runnable() {
            public void run() {
                CodecMenu p = new CodecMenu(a).title("Manette", null).onBack(self);
                String cur = store.activeLayout();
                for (final ControlLayout l : store.layouts()) {
                    if (l.floating()) {
                        p.soon(null, l.name, "👷‍♂️🚧");
                        continue;
                    }
                    p.radio(l.name, l.kind.equals("flex") && !l.split ? "La manette de la console du jeu" : null,
                            l.name.equals(cur), new Runnable() {
                        public void run() {
                            store.setActiveLayout(l.name);
                            deck.reloadFromSource();
                            relayout.run();
                        }
                    });
                }
                p.show(stage);
            }
        });
        m.sub("Transparence en superposition", "Opacité " + store.overlayOpacity() + " %", new Runnable() {
            public void run() {
                CodecMenu r = new CodecMenu(a).title("Transparence", "MANETTE SUPERPOSÉE").onBack(self);
                for (final int v : OPACITIES) {
                    r.radio(v + " %", v == 10 ? "Presque invisible" : v == 35 ? "Par défaut" : v == 70 ? "Bien visible" : null,
                            store.overlayOpacity() == v, new Runnable() {
                        public void run() { store.setOverlayOpacity(v); relayout.run(); }
                    });
                }
                r.show(stage);
            }
        });
        m.check("Gâchettes en arc", "Gâchettes en haricot autour des joysticks ; décoché, en haut de la manette",
                store.ergoTriggers(), new Runnable() {
            public void run() {
                store.setErgoTriggers(!store.ergoTriggers());
                ConsolePad.ergo = store.ergoTriggers();
                deck.reloadFromSource();
                relayout.run();
                self.run();
            }
        });
        m.check("Vibrations", "Retour haptique sur les touches tactiles", store.haptics(), new Runnable() {
            public void run() {
                store.setHaptics(!store.haptics());
                self.run();
            }
        });
        m.check("Plein écran avec une manette", "La manette tactile disparaît quand une manette physique est branchée",
                store.padFullscreen(), new Runnable() {
            public void run() {
                store.setPadFullscreen(!store.padFullscreen());
                relayout.run();
                self.run();
            }
        });
        m.item("Éditeur complet", "Dispositions, couleurs, fond, vibrations", new Runnable() {
            public void run() { a.startActivity(new Intent(a, LayoutEditorActivity.class)); }
        });
        m.show(stage);
    }
}
