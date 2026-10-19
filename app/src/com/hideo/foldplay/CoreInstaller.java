package com.hideo.foldplay;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Locale;

/**
 * Script d'installation d'un cœur, lancé tout seul après son téléchargement (et à la demande depuis
 * sa page) : vérifie le fichier, installe ses fichiers d'appoint, puis pose un profil de réglages
 * adapté à l'appareil, lu dans assets/cores/install.json. Les choix de l'utilisateur passent avant.
 */
final class CoreInstaller {
    interface Log {
        void step(String line);
    }

    private CoreInstaller() {
    }

    /** Gamme de l'appareil : « haut », « milieu » ou « bas ». */
    static String tier(Context ctx) {
        String soc = soc().toLowerCase(Locale.ROOT);
        long ramGb = ramGb(ctx);
        if (soc.startsWith("sm8") || soc.startsWith("mt699") || soc.contains("tensor") || ramGb >= 12) return "haut";
        if (ramGb >= 8) return "milieu";
        return "bas";
    }

    static String soc() {
        if (Build.VERSION.SDK_INT >= 31 && Build.SOC_MODEL != null && !Build.SOC_MODEL.isEmpty()
                && !"unknown".equalsIgnoreCase(Build.SOC_MODEL)) return Build.SOC_MODEL;
        return Build.HARDWARE;
    }

    private static long ramGb(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(mi);
        return Math.round(mi.totalMem / (1024.0 * 1024 * 1024));
    }

    /** Lance le script ; renvoie le résumé gardé sur la page du cœur. Hors du fil principal. */
    static String run(Context ctx, String core, Log log) throws java.io.IOException {
        CoreManager cores = new CoreManager(ctx);
        Store store = new Store(ctx);
        String variant = store.coreVariant(core);

        log.step("Vérification du fichier");
        File f = cores.file(core);
        checkElf(f);

        log.step("Fichiers d'appoint");
        cores.systemFiles(core, null);

        String tier = tier(ctx);
        log.step("Optimisation pour " + soc() + " (gamme " + tier + ")");
        java.util.Map<String, String> profile = new java.util.HashMap<String, String>();
        try {
            org.json.JSONObject script = new org.json.JSONObject(readAsset(ctx, "cores/install.json"));
            org.json.JSONObject c = script.getJSONObject("cores").optJSONObject(variant);
            org.json.JSONObject t = c == null ? null : c.optJSONObject(tier);
            if (t != null) {
                java.util.Iterator<String> it = t.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    profile.put(k, t.getString(k));
                }
            }
        } catch (Exception e) {
            // Script illisible : le cœur garde ses valeurs par défaut.
        }
        store.setCoreProfile(core, profile);

        String summary = "Installé le " + new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
                .format(new java.util.Date()) + " · " + CoreCatalog.variantLabel(core, variant) + " · "
                + (profile.isEmpty() ? "réglages d'origine" : profile.size() + " réglage" + (profile.size() > 1 ? "s" : "")
                + " optimisé" + (profile.size() > 1 ? "s" : "") + " (gamme " + tier + ")");
        store.setCoreInstallNote(core, summary);
        log.step("Terminé");
        return summary;
    }

    /** Une bibliothèque Android 64 bits ARM : en-tête ELF, classe 64 bits, machine AArch64 (183). */
    private static void checkElf(File f) throws java.io.IOException {
        byte[] h = new byte[20];
        InputStream in = new FileInputStream(f);
        try {
            if (in.read(h) < 20) throw new java.io.IOException("fichier du cœur tronqué");
        } finally {
            in.close();
        }
        if (h[0] != 0x7F || h[1] != 'E' || h[2] != 'L' || h[3] != 'F') throw new java.io.IOException("ce n'est pas une bibliothèque");
        if (h[4] != 2) throw new java.io.IOException("cœur 32 bits");
        int machine = (h[18] & 0xFF) | ((h[19] & 0xFF) << 8);
        if (machine != 183) throw new java.io.IOException("cœur pour une autre architecture");
    }

    private static String readAsset(Context ctx, String name) throws java.io.IOException {
        InputStream in = ctx.getAssets().open(name);
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            in.close();
        }
    }
}
