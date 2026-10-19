package com.hideo.foldplay;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.FrameLayout;

/**
 * Profils et réglages des moteurs à activité propre (PS3 : ARMSX3, Xbox 360 : Xenia), même modèle
 * que les cœurs libretro : un profil Performance / Équilibré / Qualité donne toutes les valeurs, et
 * chaque réglage peut ensuite être changé à la main dans le menu Home.
 */
final class EngineSettings {
    private EngineSettings() {
    }

    static final String PS3 = "ps3", X360 = "x360";

    /** Un réglage : clé du moteur, type (bool, int, string), valeurs et noms affichés. */
    static final class Opt {
        final String key, label, help, type;
        final String[] values, names;

        Opt(String key, String label, String help, String type, String[] values, String[] names) {
            this.key = key;
            this.label = label;
            this.help = help;
            this.type = type;
            this.values = values;
            this.names = names;
        }
    }

    private static final String[] BOOL = {"true", "false"};
    private static final String[] YESNO = {"Oui", "Non"};

    private static final Opt[] X360_OPTS = {
            new Opt("draw_resolution_scale", "Définition interne", "Multiplie la définition du jeu (720p ×1).",
                    "int", new String[]{"1", "2", "3"}, new String[]{"×1 (720p)", "×2 (1440p)", "×3 (2160p)"}),
            new Opt("render_target_path_vulkan", "Rendu des cibles",
                    "FBO : rapide. FSI : plus fidèle, si la puce le permet. Auto : le moteur choisit. À changer si des zones sont noires ou les couleurs fausses.",
                    "string", new String[]{"", "fbo", "fsi"}, new String[]{"Auto", "FBO (rapide)", "FSI (fidèle)"}),
            new Opt("readback_resolve", "Relecture des images",
                    "Rend au processeur les images calculées par la carte graphique. Corrige des effets, reflets ou vidéos manquants.",
                    "string", new String[]{"none", "fast", "full"}, new String[]{"Aucune", "Rapide", "Complète (lente)"}),
            new Opt("anisotropic_override", "Filtrage anisotrope", "Netteté des textures vues de biais.",
                    "int", new String[]{"-1", "0", "4", "8", "16"}, new String[]{"Jeu", "Désactivé", "×4", "×8", "×16"}),
            new Opt("gpu_allow_invalid_fetch_constants", "Tolérer les données graphiques invalides",
                    "Affiche des modèles que certains jeux décrivent mal (personnages ou décors manquants).",
                    "bool", BOOL, YESNO),
            new Opt("async_shader_compilation", "Shaders en arrière-plan",
                    "Le jeu continue pendant la préparation des shaders : moins de saccades, objets parfois absents un instant.",
                    "bool", BOOL, YESNO),
            new Opt("vsync", "Synchro verticale", "Cale l'image sur l'écran : pas de déchirure, cadence plafonnée.",
                    "bool", BOOL, YESNO),
    };

    private static final String[][][] X360_PROFILES = {
            // performance
            {{"draw_resolution_scale", "1"}, {"render_target_path_vulkan", "fbo"}, {"readback_resolve", "none"},
                    {"anisotropic_override", "0"}, {"gpu_allow_invalid_fetch_constants", "true"},
                    {"async_shader_compilation", "true"}, {"vsync", "true"}},
            // balanced
            {{"draw_resolution_scale", "1"}, {"render_target_path_vulkan", ""}, {"readback_resolve", "fast"},
                    {"anisotropic_override", "-1"}, {"gpu_allow_invalid_fetch_constants", "true"},
                    {"async_shader_compilation", "true"}, {"vsync", "true"}},
            // quality
            {{"draw_resolution_scale", "2"}, {"render_target_path_vulkan", ""}, {"readback_resolve", "fast"},
                    {"anisotropic_override", "16"}, {"gpu_allow_invalid_fetch_constants", "true"},
                    {"async_shader_compilation", "true"}, {"vsync", "true"}},
    };

    private static final Opt[] PS3_OPTS = {
            new Opt("Video@@Resolution Scale", "Définition interne", "En pourcentage de la définition du jeu (720p).",
                    "uint", new String[]{"100", "150", "200", "300"}, new String[]{"100 %", "150 %", "200 %", "300 %"}),
            new Opt("Video@@Shader Mode", "Préparation des shaders",
                    "En arrière-plan : recommandé, décors complets. Avec interpréteur : image plus tôt mais certains décors restent noirs (textures de sommets).",
                    "string",
                    new String[]{"Async Recompiler (multi-threaded)", "Async Recompiler with Shader Interpreter", "Shader Interpreter only"},
                    new String[]{"En arrière-plan", "En arrière-plan avec interpréteur", "Interpréteur seul"}),
            new Opt("Video@@Anisotropic Filter Override", "Filtrage anisotrope", "Netteté des textures vues de biais.",
                    "uint", new String[]{"0", "4", "8", "16"}, new String[]{"Jeu", "×4", "×8", "×16"}),
            new Opt("Video@@Output Scaling Mode", "Mise à l'échelle", "FSR : agrandissement net (AMD FidelityFX).",
                    "string", new String[]{"Bilinear", "Nearest", "FidelityFX Super Resolution 1"},
                    new String[]{"Bilinéaire", "Pixels nets", "FSR"}),
            new Opt("Video@@Frame limit", "Limite d'images", "Auto suit le jeu.",
                    "string", new String[]{"Auto", "30", "60", "Off"}, new String[]{"Auto", "30", "60", "Aucune"}),
            new Opt("Video@@Multithreaded RSX", "Rendu sur plusieurs cœurs", "Peut accélérer les jeux lourds en rendu.",
                    "bool", BOOL, YESNO),
            new Opt("Video@@Write Color Buffers", "Écrire les tampons de couleur",
                    "Corrige des écrans noirs ou des effets manquants dans certains jeux, au prix de la vitesse.",
                    "bool", BOOL, YESNO),
            new Opt("Video@@Strict Rendering Mode", "Rendu strict", "Plus fidèle, plus lent.", "bool", BOOL, YESNO),
    };

    private static final String[][][] PS3_PROFILES = {
            {{"Video@@Resolution Scale", "100"}, {"Video@@Shader Mode", "Async Recompiler (multi-threaded)"},
                    {"Video@@Anisotropic Filter Override", "0"}, {"Video@@Output Scaling Mode", "Bilinear"},
                    {"Video@@Frame limit", "Auto"}, {"Video@@Multithreaded RSX", "true"},
                    {"Video@@Write Color Buffers", "false"}, {"Video@@Strict Rendering Mode", "false"}},
            {{"Video@@Resolution Scale", "150"}, {"Video@@Shader Mode", "Async Recompiler (multi-threaded)"},
                    {"Video@@Anisotropic Filter Override", "4"}, {"Video@@Output Scaling Mode", "FidelityFX Super Resolution 1"},
                    {"Video@@Frame limit", "Auto"}, {"Video@@Multithreaded RSX", "true"},
                    {"Video@@Write Color Buffers", "false"}, {"Video@@Strict Rendering Mode", "false"}},
            {{"Video@@Resolution Scale", "200"}, {"Video@@Shader Mode", "Async Recompiler (multi-threaded)"},
                    {"Video@@Anisotropic Filter Override", "16"}, {"Video@@Output Scaling Mode", "FidelityFX Super Resolution 1"},
                    {"Video@@Frame limit", "Auto"}, {"Video@@Multithreaded RSX", "true"},
                    {"Video@@Write Color Buffers", "false"}, {"Video@@Strict Rendering Mode", "false"}},
    };

    static Opt[] opts(String engine) {
        return PS3.equals(engine) ? PS3_OPTS : X360_OPTS;
    }

    private static SharedPreferences prefs(Context ctx, String engine) {
        return ctx.getSharedPreferences("engine_" + engine, Context.MODE_PRIVATE);
    }

    static String profile(Context ctx, String engine) {
        return prefs(ctx, engine).getString("profile", new Store(ctx).defaultProfile());
    }

    static String profileName(Context ctx, String engine) {
        return CoreProfiles.name(profile(ctx, engine));
    }

    /** Un nouveau profil efface les réglages faits à la main. */
    static void setProfile(Context ctx, String engine, String id) {
        prefs(ctx, engine).edit().clear().putString("profile", id).apply();
    }

    private static String profileValue(String engine, String id, String key) {
        String[][][] all = PS3.equals(engine) ? PS3_PROFILES : X360_PROFILES;
        for (String[] kv : all[CoreProfiles.index(id)]) if (kv[0].equals(key)) return kv[1];
        return null;
    }

    static String get(Context ctx, String engine, Opt o) {
        String v = prefs(ctx, engine).getString("v_" + o.key, null);
        if (v == null) v = profileValue(engine, profile(ctx, engine), o.key);
        return v != null ? v : o.values[0];
    }

    static void set(Context ctx, String engine, Opt o, String value) {
        prefs(ctx, engine).edit().putString("v_" + o.key, value).apply();
    }

    /** Xbox 360 : les réglages partent en cvars, lues par Xenia au lancement. */
    static void putCvars(Context ctx, Bundle cvars) {
        for (Opt o : X360_OPTS) {
            String v = get(ctx, X360, o);
            if (o.key.equals("draw_resolution_scale")) {
                int s = Integer.parseInt(v);
                cvars.putInt("draw_resolution_scale_x", s);
                cvars.putInt("draw_resolution_scale_y", s);
            } else if ("bool".equals(o.type)) {
                cvars.putBoolean(o.key, Boolean.parseBoolean(v));
            } else if ("int".equals(o.type)) {
                cvars.putInt(o.key, Integer.parseInt(v));
            } else {
                cvars.putString(o.key, v);
            }
        }
    }

    /** PS3 : les réglages sont écrits dans la configuration du moteur avant le lancement du jeu. */
    static void applyPs3(Context ctx) {
        for (Opt o : PS3_OPTS) {
            try {
                Ps3Core.nativeSettingsSet(o.key, get(ctx, PS3, o));
            } catch (Throwable ignored) {
                // Réglage inconnu de cette version du moteur : on garde le sien.
            }
        }
        // Sauvegardes rapides : sans ce mode, le moteur n'arrive pas à figer les SPU.
        try {
            Ps3Core.nativeSettingsSet("Savestate@@Compatible Savestate Mode", "true");
        } catch (Throwable ignored) {
        }
    }

    /**
     * Sous-menu « Profil et réglages » du menu Home. back rouvre le menu Home.
     * Les changements s'appliquent au prochain lancement du jeu.
     */
    static void menu(final android.app.Activity a, final FrameLayout stage, final String engine,
                     final String console, final Runnable back) {
        final String prof = profile(a, engine);
        CodecMenu m = new CodecMenu(a).title("Profil et réglages", console)
                .message("Les changements s'appliquent au prochain lancement du jeu.");
        for (int i = 0; i < CoreProfiles.IDS.length; i++) {
            final String id = CoreProfiles.IDS[i];
            m.radio(CoreProfiles.NAMES[i], CoreProfiles.HELP[i], id.equals(prof), new Runnable() {
                public void run() { setProfile(a, engine, id); }
            });
        }
        for (final Opt o : opts(engine)) {
            String cur = get(a, engine, o);
            if ("bool".equals(o.type)) {
                final boolean on = Boolean.parseBoolean(cur);
                m.check(o.label, o.help, on, new Runnable() {
                    public void run() {
                        set(a, engine, o, String.valueOf(!Boolean.parseBoolean(get(a, engine, o))));
                    }
                });
            } else {
                String name = cur;
                for (int k = 0; k < o.values.length; k++) if (o.values[k].equals(cur)) name = o.names[k];
                m.sub(o.label, name, new Runnable() {
                    public void run() { pick(a, stage, engine, console, o, back); }
                });
            }
        }
        m.onBack(back).show(stage);
    }

    private static void pick(final android.app.Activity a, final FrameLayout stage, final String engine,
                             final String console, final Opt o, final Runnable back) {
        String cur = get(a, engine, o);
        CodecMenu m = new CodecMenu(a).title(o.label, console).message(o.help);
        for (int k = 0; k < o.values.length; k++) {
            final String v = o.values[k];
            m.radio(o.names[k], null, v.equals(cur), new Runnable() {
                public void run() { set(a, engine, o, v); }
            });
        }
        m.onBack(new Runnable() {
            public void run() { menu(a, stage, engine, console, back); }
        }).show(stage);
    }
}
