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
                    "FBO : rapide. FSI : plus fidèle, si la puce le permet. À changer si des zones sont noires.",
                    "string", new String[]{"", "fbo", "fsi"}, new String[]{"Auto", "FBO (rapide)", "FSI (fidèle)"}),
            new Opt("readback_resolve", "Relecture des images",
                    "Corrige effets, reflets ou vidéos manquants.",
                    "string", new String[]{"none", "fast", "full"}, new String[]{"Aucune", "Rapide", "Complète (lente)"}),
            // Valeurs gardées en « facteur » (×4, ×8, ×16) ; putCvars les traduit vers l'échelle de Xenia.
            new Opt("anisotropic_override", "Filtrage anisotrope", "Netteté des textures vues de biais.",
                    "int", new String[]{"-1", "0", "4", "8", "16"}, new String[]{"Jeu", "Désactivé", "×4", "×8", "×16"}),
            new Opt("gpu_allow_invalid_fetch_constants", "Tolérer les données graphiques invalides",
                    "Affiche des modèles mal décrits (personnages ou décors manquants).",
                    "bool", BOOL, YESNO),
            new Opt("async_shader_compilation", "Shaders en arrière-plan",
                    "Moins de saccades, objets parfois absents un instant.",
                    "bool", BOOL, YESNO),
            new Opt("vsync", "Synchro verticale", "Pas de déchirure, cadence plafonnée.",
                    "bool", BOOL, YESNO),
    };

    private static final String[][][] X360_PROFILES = {
            // performance
            // Relecture « aucune » en Performance et Équilibré (0.65) : la relecture rapide de la 0.62.5
            // faisait ramer DMC 4 dès l'écran-titre (chaque résolution d'image repasse par le processeur).
            // Elle reste en Qualité, pour les jeux qui sinon n'affichent que du noir. FBO partout :
            // le chemin FSI demande une extension absente des puces Adreno.
            {{"draw_resolution_scale", "1"}, {"render_target_path_vulkan", "fbo"}, {"readback_resolve", "none"},
                    {"anisotropic_override", "0"}, {"gpu_allow_invalid_fetch_constants", "true"},
                    {"async_shader_compilation", "true"}, {"vsync", "true"}},
            // balanced
            {{"draw_resolution_scale", "1"}, {"render_target_path_vulkan", "fbo"}, {"readback_resolve", "none"},
                    {"anisotropic_override", "-1"}, {"gpu_allow_invalid_fetch_constants", "true"},
                    {"async_shader_compilation", "true"}, {"vsync", "true"}},
            // quality
            // Qualité : ×8 (et non ×16) pour ménager la bande passante mémoire de l'Adreno.
            {{"draw_resolution_scale", "2"}, {"render_target_path_vulkan", "fbo"}, {"readback_resolve", "fast"},
                    {"anisotropic_override", "8"}, {"gpu_allow_invalid_fetch_constants", "true"},
                    {"async_shader_compilation", "true"}, {"vsync", "true"}},
    };

    private static final Opt[] PS3_OPTS = {
            new Opt("Video@@Resolution Scale", "Définition interne", "En pourcentage de la définition du jeu (720p).",
                    "uint", new String[]{"75", "100", "150", "200", "300"}, new String[]{"75 %", "100 %", "150 %", "200 %", "300 %"}),
            new Opt("Video@@Shader Mode", "Préparation des shaders",
                    "En arrière-plan : recommandé. Avec interpréteur : image plus tôt, certains décors restent noirs.",
                    "string",
                    new String[]{"Async Recompiler (multi-threaded)", "Async Recompiler with Shader Interpreter", "Shader Interpreter only"},
                    new String[]{"En arrière-plan", "En arrière-plan avec interpréteur", "Interpréteur seul"}),
            new Opt("Video@@Anisotropic Filter Override", "Filtrage anisotrope", "Netteté des textures vues de biais.",
                    "uint", new String[]{"0", "4", "8", "16"}, new String[]{"Jeu", "×4", "×8", "×16"}),
            // Valeurs exactes de l'enum output_scaling_mode du moteur (« FidelityFX Super Resolution », sans « 1 »).
            new Opt("Video@@Output Scaling Mode", "Mise à l'échelle",
                    "FSR : agrandissement net (AMD). SGSR : équivalent Snapdragon, plus léger.",
                    "string", new String[]{"Bilinear", "Nearest", "FidelityFX Super Resolution", "Snapdragon Game Super Resolution"},
                    new String[]{"Bilinéaire", "Pixels nets", "FSR", "SGSR"}),
            new Opt("Video@@Frame limit", "Limite d'images", "Auto suit le jeu.",
                    "string", new String[]{"Auto", "30", "60", "Off"}, new String[]{"Auto", "30", "60", "Aucune"}),
            new Opt("Video@@Multithreaded RSX", "Rendu sur plusieurs cœurs", "Peut accélérer les jeux lourds en rendu.",
                    "bool", BOOL, YESNO),
            new Opt("Video@@Write Color Buffers", "Écrire les tampons de couleur",
                    "Corrige écrans noirs et effets manquants, au prix de la vitesse.",
                    "bool", BOOL, YESNO),
            new Opt("Video@@Strict Rendering Mode", "Rendu strict", "Plus fidèle, plus lent.", "bool", BOOL, YESNO),
    };

    // Définition (0.65.1) : le moteur accepte 25 à 800 %. Sur le Fold 8 les jeux lourds sont limités
    // par le processeur, monter la définition chauffe sans gagner d'images : 75 % (≈ 540p, agrandi par
    // SGSR) en Performance, 100 % en Équilibré, 150 % (1080p, proche de l'écran intérieur) en Qualité.
    private static final String[][][] PS3_PROFILES = {
            {{"Video@@Resolution Scale", "75"}, {"Video@@Shader Mode", "Async Recompiler (multi-threaded)"},
                    {"Video@@Anisotropic Filter Override", "0"}, {"Video@@Output Scaling Mode", "Snapdragon Game Super Resolution"},
                    {"Video@@Frame limit", "Auto"}, {"Video@@Multithreaded RSX", "true"},
                    {"Video@@Write Color Buffers", "false"}, {"Video@@Strict Rendering Mode", "false"}},
            {{"Video@@Resolution Scale", "100"}, {"Video@@Shader Mode", "Async Recompiler (multi-threaded)"},
                    {"Video@@Anisotropic Filter Override", "4"}, {"Video@@Output Scaling Mode", "FidelityFX Super Resolution"},
                    {"Video@@Frame limit", "Auto"}, {"Video@@Multithreaded RSX", "true"},
                    {"Video@@Write Color Buffers", "false"}, {"Video@@Strict Rendering Mode", "false"}},
            {{"Video@@Resolution Scale", "150"}, {"Video@@Shader Mode", "Async Recompiler (multi-threaded)"},
                    {"Video@@Anisotropic Filter Override", "16"}, {"Video@@Output Scaling Mode", "FidelityFX Super Resolution"},
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
            } else if (o.key.equals("anisotropic_override")) {
                // Échelle de Xenia (vérifiée dans libxenia-app.so, 0.65.1) : -1 jeu, 0 coupé, 1 = ×1,
                // 2 = ×2, 3 = ×4, 4 = ×8, 5 = ×16. Les anciennes valeurs 8 et 16 étaient hors bornes.
                int f = Integer.parseInt(v);
                cvars.putInt(o.key, f <= 0 ? f : f >= 16 ? 5 : f >= 8 ? 4 : f >= 4 ? 3 : f >= 2 ? 2 : 1);
            } else if ("int".equals(o.type)) {
                cvars.putInt(o.key, Integer.parseInt(v));
            } else {
                cvars.putString(o.key, v);
            }
        }
        // Le « CONFIG DUMP » de Xenia recopie le fichier xenia-canary.config.toml au début de ReadConfig :
        // il ne montre pas ces valeurs, qui passent pourtant avant celles du fichier. On les journalise ici.
        StringBuilder log = new StringBuilder("cvars du profil :");
        for (String k : cvars.keySet()) log.append(' ').append(k).append('=').append(cvars.get(k));
        android.util.Log.i("Origami", log.toString());
    }

    /** PS3 : les réglages sont écrits dans la configuration du moteur avant le lancement du jeu. */
    static void applyPs3(Context ctx, String game) {
        String g = game == null ? "" : game.toUpperCase(java.util.Locale.ROOT);
        boolean demons = g.contains("DEMON") || g.contains("BLES00932") || g.contains("BLUS30443")
                || g.contains("BCJS30022") || g.contains("BCAS20071");
        for (Opt o : PS3_OPTS) {
            try {
                String v = get(ctx, PS3, o);
                boolean mine = prefs(ctx, PS3).getString("v_" + o.key, null) != null;
                // Correctifs par jeu, sauf si BOSS a choisi lui-même la valeur (0.62.1) :
                // Demon's Souls a besoin des tampons de couleur pour dessiner le monde après la cinématique,
                // et tourne à 30 images/s : calé dessus, il ne saute plus entre 30, 40 et 60 (0.62.3).
                if (demons && !mine && o.key.equals("Video@@Write Color Buffers")) v = "true";
                if (demons && !mine && o.key.equals("Video@@Frame limit")) v = "30";
                // Limité par le processeur (journal du 20/10 : CPU 239 %) : monter la définition ne
                // rend rien de plus fluide et chauffe pour rien, donc 100 % sauf choix de BOSS.
                if (demons && !mine && o.key.equals("Video@@Resolution Scale") && Integer.parseInt(v) > 100) v = "100";
                // Ancienne valeur de la 0.65 refusée par le moteur (« ... Resolution 1 ») : on la corrige.
                if (v.equals("FidelityFX Super Resolution 1")) v = "FidelityFX Super Resolution";
                set3(o.key, v);
            } catch (Throwable ignored) {
                // Réglage inconnu de cette version du moteur : on garde le sien.
            }
        }
        // Sauvegardes rapides : sans ce mode, le moteur n'arrive pas à figer les SPU.
        // Son (0.62.3) : un tampon plus long et l'étirement du temps évitent les craquements et
        // les coupures quand le jeu ralentit ; volume à 80 % pour ne plus saturer.
        String[][] fixed = {
                {"Savestate@@Compatible Savestate Mode", "true"},
                {"Audio@@Enable Buffering", "true"},
                {"Audio@@Desired Audio Buffer Duration", "150"},
                {"Audio@@Enable Time Stretching", "true"},
                {"Audio@@Time Stretching Threshold", "75"},
                {"Audio@@Master Volume", "80"},
        };
        // Réglages processeur par profil, calés sur la puce du Fold 8 (8 cœurs Oryon, 0.62.5) :
        // blocs SPU plus gros et calcul flottant allégé en Performance, threads de compilation
        // des shaders sur 6 cœurs, flux de textures asynchrone pour éviter les à-coups.
        // Noms et bornes relus dans libarmsx3-core.so (0.65.1) : Max SPURS Threads 1 à 6,
        // Preferred SPU Threads 0 à 6 (0 = auto), Shader Compiler Threads 0 à 16, SPU Block Size
        // Safe/Mega/Giga, SPU XFloat Accuracy Accurate/Approximate/Relaxed/Inaccurate ; le flux de
        // textures asynchrone est rangé sous Video@@Vulkan.
        int prof = CoreProfiles.index(profile(ctx, PS3));
        String[][] cpu = {
                {"Core@@SPU Block Size", new String[] {"Mega", "Mega", "Safe"}[prof]},
                {"Core@@SPU XFloat Accuracy", new String[] {"Relaxed", "Approximate", "Approximate"}[prof]},
                {"Core@@Max SPURS Threads", new String[] {"4", "6", "6"}[prof]},
                {"Core@@Preferred SPU Threads", new String[] {"0", "0", "0"}[prof]},
                {"Video@@Shader Compiler Threads", "6"},
                {"Video@@Relaxed ZCULL Sync", new String[] {"true", "true", "false"}[prof]},
                {"Video@@Vulkan@@Asynchronous Texture Streaming", "true"},
        };
        for (String[] kv : cpu) {
            try {
                set3(kv[0], kv[1]);
            } catch (Throwable ignored) {
            }
        }
        for (String[] kv : fixed) {
            try {
                set3(kv[0], kv[1]);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Écrit un réglage PS3 et signale au journal celui que le moteur refuse (nœud ou valeur inconnus). */
    private static void set3(String key, String value) {
        if (!Ps3Core.nativeSettingsSet(key, value)) android.util.Log.w("Origami", "PS3 refuse " + key + " = " + value);
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
