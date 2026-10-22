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
        if (!"turnip".equals(store.gpuDriver())) return;
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

    /** Télécharge le dernier Turnip publié (réseau, hors thread principal). */
    static String download(Context c) throws Exception {
        JSONObject rel = new JSONObject(new String(read(open(RELEASES))));
        JSONArray assets = rel.getJSONArray("assets");
        String url = null;
        for (int i = 0; i < assets.length() && url == null; i++) {
            String n = assets.getJSONObject(i).getString("name").toLowerCase();
            if (n.endsWith(".zip") && n.contains("turnip")) url = assets.getJSONObject(i).getString("browser_download_url");
        }
        if (url == null) throw new Exception("aucun pilote Turnip dans la dernière version");
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
