package com.hideo.foldplay;

import android.app.Activity;
import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Pilote Vulkan personnalisé (Turnip) pour la PS3 : téléchargé ou importé (zip adrenotools), chargé par libadrenotools. */
final class GpuDriver {
    static final String[] MODES = {"auto", "turnip"};
    static final String[] LABELS = {"Auto (pilote du téléphone)", "Turnip (Adreno)"};
    private static final String RELEASES = "https://api.github.com/repos/StevenMXZ/Adreno-Tools-Drivers/releases/latest";

    static File dir(Context c) {
        return new File(c.getFilesDir(), "gpu-driver");
    }

    static String libName(Context c) {
        try {
            return new String(read(new java.io.FileInputStream(new File(dir(c), "origami-lib.txt")))).trim();
        } catch (Throwable t) {
            return null;
        }
    }

    static boolean installed(Context c) {
        return libName(c) != null;
    }

    /** Applique le pilote choisi au moteur PS3 (avant nativeInitialize). */
    static void apply(Context c, Store store) {
        if (!"turnip".equals(store.gpuDriverEff())) return;
        String lib = libName(c);
        if (lib == null) {
            DebugLog.event("[PS3] pilote Turnip demandé mais absent : pilote du téléphone");
            return;
        }
        boolean ok = Ps3Core.nativeSetDriver(dir(c).getPath(), lib, c.getApplicationInfo().nativeLibraryDir);
        DebugLog.event("[PS3] pilote Turnip " + lib + " ok=" + ok);
    }

    /** Dézippe un pilote au format adrenotools (meta.json + .so). */
    static String installZip(Context c, InputStream in) throws Exception {
        File d = dir(c);
        File[] old = d.listFiles();
        if (old != null) for (File f : old) f.delete();
        d.mkdirs();
        String lib = null, name = null;
        ZipInputStream z = new ZipInputStream(in);
        for (ZipEntry e; (e = z.getNextEntry()) != null; ) {
            if (e.isDirectory()) continue;
            String base = new File(e.getName()).getName();
            byte[] data = read(z);
            if (base.equals("meta.json")) {
                JSONObject m = new JSONObject(new String(data));
                lib = m.optString("libraryName", null);
                name = m.optString("name", m.optString("description", "Turnip"));
            }
            OutputStream o = new FileOutputStream(new File(d, base));
            o.write(data);
            o.close();
        }
        if (lib == null || !new File(d, lib).isFile()) throw new Exception("zip sans meta.json valide");
        OutputStream o = new FileOutputStream(new File(d, "origami-lib.txt"));
        o.write(lib.getBytes());
        o.close();
        return name;
    }

    private static String sys(String path) {
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(path));
            try {
                return r.readLine();
            } finally {
                r.close();
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private static String prop(String key) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            return String.valueOf(c.getMethod("get", String.class, String.class).invoke(null, key, ""));
        } catch (Throwable t) {
            return "";
        }
    }

    /** Modèle Adreno du téléphone (ex. 740), 0 si GPU non Adreno (Mali, Xclipse…), -1 si introuvable (0.67). */
    static int adreno() {
        String g = sys("/sys/class/kgsl/kgsl-3d0/gpu_model");
        if (g != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{3})").matcher(g);
            if (g.toLowerCase(java.util.Locale.ROOT).contains("adreno") && m.find()) return Integer.parseInt(m.group(1));
        }
        // Tous les téléphones : le pilote Vulkan déclaré par le fabricant (adreno, mali, powervr, xclipse…).
        String vk = (prop("ro.hardware.vulkan") + " " + prop("ro.hardware.egl")).toLowerCase(java.util.Locale.ROOT);
        boolean viaProp = false;
        if (vk.contains("mali") || vk.contains("powervr") || vk.contains("xclipse") || vk.contains("pastel") || vk.contains("immortalis")
                || vk.contains("emulation") || vk.contains("swiftshader") || vk.contains("ranchu") || vk.contains("gfxstream")) return 0;
        if (vk.contains("adreno")) viaProp = true;
        String soc = (android.os.Build.VERSION.SDK_INT >= 31 ? android.os.Build.SOC_MODEL : "") + " " + android.os.Build.HARDWARE + " " + android.os.Build.BOARD;
        soc = soc.toUpperCase(java.util.Locale.ROOT);
        // Snapdragon, tous modèles : le lecteur kgsl et le pilote déclaré passent d abord, cette table sert de dernier recours (Fold 5 = SM8550 / 740).
        String[][] map = {{"SM8850", "840"}, {"SM8750", "830"}, {"SM8650", "750"}, {"SM8550", "740"}, {"SM8475", "730"}, {"SM8450", "730"},
                {"SM8350", "660"}, {"SM8250", "650"}, {"KALAMA", "740"}, {"PINEAPPLE", "750"}, {"SUN", "830"}, {"LAHAINA", "660"}, {"TASMAN", "840"}};
        for (String[] e : map) if (soc.contains(e[0])) return Integer.parseInt(e[1]);
        if (viaProp) return -1; // Adreno d'un modèle inconnu : pilote générique, pas de blocage.
        if (soc.contains("EXYNOS") || soc.contains("S5E") || soc.contains("MT6") || soc.contains("TENSOR") || soc.contains("GS")) return 0;
        return -1;
    }

    /** Score d'un fichier de pilote pour ce GPU : les noms qui citent la famille (a7xx, 740…) passent devant le générique. */
    private static int score(String name, int gpu) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        if (gpu <= 0) return 0;
        int fam = gpu / 100;
        int sc = 0;
        if (n.contains("gen" + fam) || n.contains("a" + fam + "xx") || n.contains("adreno" + fam) || n.contains("a" + gpu) || n.contains(String.valueOf(gpu))) sc += 10;
        // Une famille précise citée ailleurs que la nôtre : mauvais candidat.
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:a|gen)([6-9])(?:xx|[^0-9]|$)").matcher(n);
        while (m.find()) if (Integer.parseInt(m.group(1)) != fam) sc -= 20;
        if (n.contains("v8") || n.contains("v7")) sc += 0;
        return sc;
    }

    /** Télécharge le Turnip adapté au GPU du téléphone (réseau, hors thread principal). */
    static String download(Context c) throws Exception {
        int gpu = adreno();
        if (gpu == 0) throw new Exception("Turnip ne fonctionne que sur les GPU Adreno (Snapdragon) : ce téléphone garde son pilote");
        if (gpu > 0 && gpu < 600) throw new Exception("Adreno " + gpu + " trop ancien pour Turnip");
        JSONObject rel = new JSONObject(new String(read(open(RELEASES))));
        JSONArray assets = rel.getJSONArray("assets");
        String url = null;
        int best = Integer.MIN_VALUE;
        String pick = null;
        for (int i = 0; i < assets.length(); i++) {
            String n = assets.getJSONObject(i).getString("name");
            if (!n.toLowerCase(java.util.Locale.ROOT).endsWith(".zip") || !n.toLowerCase(java.util.Locale.ROOT).contains("turnip")) continue;
            int sc = score(n, gpu);
            if (gpu > 0 && sc < 0) continue; // pilote d'une autre famille Adreno : ni proposé ni téléchargé
            if (sc > best) {
                best = sc;
                pick = n;
                url = assets.getJSONObject(i).getString("browser_download_url");
            }
        }
        if (url == null) throw new Exception("aucun pilote Turnip adapté à ce GPU (Adreno " + (gpu > 0 ? gpu : "inconnu") + ") dans la dernière version");
        DebugLog.event("[PS3] GPU Adreno " + (gpu > 0 ? gpu : "inconnu") + " : pilote choisi " + pick + " (score " + best + ")");
        return installZip(c, open(url));
    }

    private static InputStream open(String u) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(u).openConnection();
        h.setConnectTimeout(15000);
        h.setReadTimeout(60000);
        h.setInstanceFollowRedirects(true);
        return h.getInputStream();
    }

    private static byte[] read(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
        return b.toByteArray();
    }
}
