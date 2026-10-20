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
        m.check("Gâchettes en arc", "Autour des joysticks, façon GameCube. Décoché : place d'origine, en haut",
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
        m.check("Plein écran avec une manette", "Masquée quand une manette est branchée",
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
