package com.hideo.foldplay;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Cœurs libretro intégrés : lesquels pour quelle console, et leur téléchargement sur l'appareil
 * depuis le buildbot officiel de libretro. Seul ARMSX2 (PS2) est livré dans l'APK : libretro ne le
 * compile pas encore pour Android, on le construit nous-mêmes avec le NDK (native/build-armsx2.sh).
 */
final class CoreManager {
    private static final String BUILDBOT = "https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/";
    private static final String ASSETS = "https://buildbot.libretro.com/assets/system/";

    /** Console → cœur intégré. Les trois derniers dessinent en 3D avec OpenGL ES (depuis la 0.21). */
    private static final String[][] SOFTWARE = {
        {"nes", "fceumm"},
        {"snes", "snes9x"},
        {"gb", "gambatte"},
        {"gbc", "gambatte"},
        {"gba", "mgba"},
        {"md", "genesis_plus_gx"},
        {"sms", "genesis_plus_gx"},
        {"gg", "genesis_plus_gx"},
        {"pce", "mednafen_pce_fast"},
        {"psx", "pcsx_rearmed"},
        {"nds", "melonds"},
        {"saturn", "yabause"},
        {"n64", "mupen64plus_next_gles3"},
        {"psp", "ppsspp"},
        {"dc", "flycast"},
        {"arcade", "fbneo"},
        {"ps2", "armsx2"},
        {"gc", "dolphin"},
        {"wii", "dolphin"},
    };

    /** Cœurs livrés dans l'APK (lib/arm64-v8a/lib<cœur>_libretro.so), jamais téléchargés. */
    static final String[] BUNDLED = {"armsx2", "dolphin"};

    /**
     * Fichiers d'un cœur livré, zippés dans les assets et recopiés dans le dossier système à chaque
     * nouvelle version : ARMSX2 (shaders, GameIndex, polices), Dolphin (Sys : réglages par jeu, polices).
     */
    private static final String[][] BUNDLED_FILES = {
        {"armsx2", "pcsx2-resources.zip", "pcsx2/resources"},
        {"dolphin", "dolphin-sys.zip", "dolphin-emu/Sys"},
    };

    private static String[] bundledFiles(String core) {
        for (String[] e : BUNDLED_FILES) if (e[0].equals(core)) return e;
        return null;
    }

    /**
     * Cœur choisi à la place du premier quand ses fichiers sont là : SwanStation (PS1 en HD, rendu
     * OpenGL) demande un vrai BIOS PS1, pcsx_rearmed s'en passe.
     */
    private static final String[][] UPGRADES = {
        {"psx", "swanstation"},
    };

    /** BIOS PS1 cherchés dans le dossier système (ceux qu'accepte SwanStation). */
    private static final String[] PS1_BIOS = {"scph5500.bin", "scph5501.bin", "scph5502.bin", "scph1001.bin",
        "scph7001.bin", "scph7502.bin", "scph101.bin", "scph1000.bin", "ps1_rom.bin", "psxonpsp660.bin"};

    /** Options par défaut d'un cœur (l'utilisateur peut les changer dans Options avancées). */
    static final String[][] DEFAULT_OPTIONS = {
        {"swanstation", "swanstation_GPU_Renderer", "Auto"},
        {"swanstation", "swanstation_GPU_ResolutionScale", "3"},
    };

    /** Fichiers d'appoint d'un cœur, à décompresser dans le dossier système (polices de la PSP…). */
    private static final String[][] SYSTEM_FILES = {
        {"ppsspp", "PPSSPP.zip", "PPSSPP"},
    };

    interface Progress {
        void onProgress(long done, long total);
    }

    private final Context ctx;
    private final File dir;
    private final File system;

    CoreManager(Context ctx) {
        this.ctx = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
        dir = new File(ctx.getFilesDir(), "cores");
        // Même dossier système que celui donné au cœur par GameActivity.
        File base = ctx.getExternalFilesDir(null);
        system = new File(base != null ? base : ctx.getFilesDir(), "system");
    }

    /** Tous les cœurs intégrés, sans doublon (pour les installer d'un coup). */
    static java.util.List<String> allCores() {
        java.util.List<String> out = new java.util.ArrayList<String>();
        for (String[] e : SOFTWARE) if (!out.contains(e[1])) out.add(e[1]);
        for (String[] e : UPGRADES) if (!out.contains(e[1])) out.add(e[1]);
        return out;
    }

    /** Le cœur à lancer sur cet appareil : la version améliorée quand ses fichiers sont présents. */
    String coreToRun(SystemInfo s) {
        String core = coreFor(s);
        for (String[] e : UPGRADES) {
            if (e[0].equals(s.id) && e[1].equals("swanstation") && hasPs1Bios()) return e[1];
        }
        return core;
    }

    boolean hasPs1Bios() {
        String[] names = system.list();
        if (names == null) return false;
        for (String n : names) for (String b : PS1_BIOS) if (n.equalsIgnoreCase(b)) return true;
        return false;
    }

    /** Consoles d'un cœur, pour l'afficher (« GB, GBC »). */
    static String systemsOf(String core) {
        StringBuilder sb = new StringBuilder();
        for (String[] e : UPGRADES) if (e[1].equals(core)) return SystemInfo.byId(e[0]).shortName + " HD";
        for (String[] e : SOFTWARE) {
            if (!e[1].equals(core)) continue;
            SystemInfo s = SystemInfo.byId(e[0]);
            if (sb.length() > 0) sb.append(", ");
            sb.append(s != null ? s.shortName : e[0]);
        }
        return sb.toString();
    }

    /** Le cœur intégré pour cette console, ou null (console laissée aux émulateurs externes). */
    static String coreFor(SystemInfo s) {
        for (String[] e : SOFTWARE) if (e[0].equals(s.id)) return e[1];
        return null;
    }

    static boolean hasBuiltin(SystemInfo s) {
        return coreFor(s) != null && Libretro.available();
    }

    static boolean bundled(String core) {
        for (String b : BUNDLED) if (b.equals(core)) return true;
        return false;
    }

    File file(String core) {
        if (bundled(core)) return new File(ctx.getApplicationInfo().nativeLibraryDir, "lib" + core + "_libretro.so");
        return new File(dir, core + "_libretro_android.so");
    }

    boolean installed(String core) {
        return file(core).length() > 0;
    }

    void delete(String core) {
        if (!bundled(core)) {
            file(core).delete();
            previous(core).delete();
        }
    }

    /** Version précédente gardée après une mise à jour. */
    File previous(String core) {
        return new File(dir, core + "_libretro_android.so.prev");
    }

    boolean hasPrevious(String core) {
        return !bundled(core) && previous(core).length() > 0;
    }

    /** Échange la version installée et la précédente. */
    void rollback(String core) throws IOException {
        File cur = file(core), prev = previous(core), tmp = new File(dir, core + ".swap");
        tmp.delete();
        if (!cur.renameTo(tmp) || !prev.renameTo(cur) || !tmp.renameTo(prev)) {
            throw new IOException("échange impossible");
        }
    }

    // ------------------------------------------------------------------ PS2 (ARMSX2)

    /** Dossier des BIOS PS2 lu par ARMSX2. */
    File ps2BiosDir() {
        return new File(system, "pcsx2/bios");
    }

    /**
     * Vrai si un BIOS PS2 est prêt. Un BIOS posé directement dans system/ (comme ceux de la PS1)
     * est rangé au passage dans system/pcsx2/bios, où ARMSX2 le cherche.
     */
    boolean hasPs2Bios() {
        File bios = ps2BiosDir();
        File[] loose = system.listFiles();
        if (loose != null) {
            for (File f : loose) {
                if (looksLikePs2Bios(f)) {
                    bios.mkdirs();
                    f.renameTo(new File(bios, f.getName()));
                }
            }
        }
        String[] names = bios.list();
        if (names == null) return false;
        for (String n : names) if (n.toLowerCase(java.util.Locale.ROOT).endsWith(".bin")) return true;
        return false;
    }

    /** Un BIOS PS2 fait 4 Mo (parfois 8 avec la ROM DVD) ; ceux de la PS1 font 512 Ko. */
    static boolean looksLikePs2Bios(File f) {
        String n = f.getName().toLowerCase(java.util.Locale.ROOT);
        long len = f.length();
        return f.isFile() && n.endsWith(".bin") && (len == 4L << 20 || len == 8L << 20);
    }


    private String appVersion() {
        try {
            return String.valueOf(ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionCode);
        } catch (Exception e) {
            return "0";
        }
    }

    private boolean bundledFilesReady(String[] e) {
        File f = new File(system, e[2] + "/.foldplay-version");
        if (!f.isFile()) return false;
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f));
            try {
                return appVersion().equals(r.readLine());
            } finally {
                r.close();
            }
        } catch (IOException ex) {
            return false;
        }
    }

    /** Recopie les fichiers d'un cœur livré depuis l'APK (à chaque nouvelle version de Origami). */
    private void installBundledFiles(String[] e, Progress progress) throws IOException {
        File res = new File(system, e[2]);
        File tmp = new File(system, e[2] + ".part");
        deleteTree(tmp);
        tmp.mkdirs();
        String root = tmp.getCanonicalPath() + File.separator;
        InputStream in = ctx.getAssets().open(e[1]);
        ZipInputStream zip = new ZipInputStream(new CountingStream(in, -1, progress));
        try {
            ZipEntry z;
            byte[] buf = new byte[256 * 1024];
            while ((z = zip.getNextEntry()) != null) {
                File out = new File(tmp, z.getName());
                if (!out.getCanonicalPath().startsWith(root)) continue;
                if (z.isDirectory()) {
                    out.mkdirs();
                    continue;
                }
                out.getParentFile().mkdirs();
                OutputStream o = new FileOutputStream(out);
                try {
                    int n;
                    while ((n = zip.read(buf)) > 0) o.write(buf, 0, n);
                } finally {
                    o.close();
                }
            }
        } finally {
            zip.close();
        }
        OutputStream o = new FileOutputStream(new File(tmp, ".foldplay-version"));
        try {
            o.write(appVersion().getBytes("UTF-8"));
        } finally {
            o.close();
        }
        deleteTree(res);
        if (!tmp.renameTo(res)) throw new IOException("impossible d'installer les fichiers de " + e[0]);
    }

    long installedSize() {
        long n = 0;
        File[] all = dir.listFiles();
        if (all != null) for (File f : all) n += f.length();
        return n;
    }

    /** Télécharge et décompresse le cœur (à appeler hors du fil principal). */
    void download(String core, Progress progress) throws IOException {
        if (bundled(core)) {
            if (!installed(core)) throw new IOException("le cœur " + core + " manque dans cette version de Origami");
            systemFiles(core, progress);
            return;
        }
        dir.mkdirs();
        // La version choisie sur la page du cœur (Snes9x 2010, Nestopia…) prend sa place sous le même nom.
        String name = new Store(ctx).coreVariant(core) + "_libretro_android.so";
        HttpURLConnection c = (HttpURLConnection) new URL(BUILDBOT + name + ".zip").openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "Origami");
        if (c.getResponseCode() != 200) {
            throw new IOException("buildbot libretro : réponse " + c.getResponseCode() + " pour " + core);
        }
        long total = c.getContentLengthLong();
        File tmp = new File(dir, name + ".part");
        InputStream raw = c.getInputStream();
        CountingStream counted = new CountingStream(raw, total, progress);
        ZipInputStream zip = new ZipInputStream(counted);
        try {
            ZipEntry e;
            boolean found = false;
            while ((e = zip.getNextEntry()) != null) {
                if (!e.getName().endsWith(".so")) continue;
                OutputStream out = new FileOutputStream(tmp);
                try {
                    byte[] buf = new byte[256 * 1024];
                    int n;
                    while ((n = zip.read(buf)) > 0) out.write(buf, 0, n);
                } finally {
                    out.close();
                }
                found = true;
                break;
            }
            if (!found) throw new IOException("archive du cœur " + core + " vide");
        } finally {
            zip.close();
            c.disconnect();
        }
        File target = file(core);
        // La version remplacée reste à côté, pour revenir en arrière d'un geste.
        File prev = previous(core);
        prev.delete();
        if (target.exists() && !target.renameTo(prev)) target.delete();
        // Par prudence (règles d'Android 14 sur le code chargé à la volée), le cœur est en lecture seule.
        tmp.setReadOnly();
        if (!tmp.renameTo(target)) throw new IOException("impossible d'installer " + core);
        try {
            systemFiles(core, progress);
        } catch (IOException e) {
            // Pas bloquant : le cœur tourne sans, et le lancement suivant réessaie.
        }
    }

    /** Efface le cœur et ses fichiers d'appoint, puis les retélécharge (version du jour de libretro). */
    void redownload(String core, Progress progress) throws IOException {
        if (bundled(core)) {
            String[] bf = bundledFiles(core);
            if (bf != null) deleteTree(new File(system, bf[2]));
            systemFiles(core, progress);
            return;
        }
        for (String[] e : SYSTEM_FILES) if (e[0].equals(core)) deleteTree(new File(system, e[2]));
        download(core, progress);
    }

    /** Vrai s'il manque au cœur ses fichiers d'appoint (installés avec lui, ou à rattraper). */
    boolean needsSystemFiles(String core) {
        String[] bf = bundledFiles(core);
        if (bf != null) return !bundledFilesReady(bf);
        for (String[] e : SYSTEM_FILES) if (e[0].equals(core) && !new File(system, e[2]).isDirectory()) return true;
        return false;
    }

    /** Télécharge et décompresse dans le dossier système les fichiers d'appoint du cœur. */
    void systemFiles(String core, Progress progress) throws IOException {
        String[] bf = bundledFiles(core);
        if (bf != null) {
            if (!bundledFilesReady(bf)) installBundledFiles(bf, progress);
            return;
        }
        for (String[] e : SYSTEM_FILES) {
            if (!e[0].equals(core) || new File(system, e[2]).isDirectory()) continue;
            HttpURLConnection c = (HttpURLConnection) new URL(ASSETS + e[1]).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", "Origami");
            if (c.getResponseCode() != 200) {
                throw new IOException("buildbot libretro : réponse " + c.getResponseCode() + " pour " + e[1]);
            }
            File tmp = new File(system, e[2] + ".part");
            deleteTree(tmp);
            String root = tmp.getCanonicalPath() + File.separator;
            ZipInputStream zip = new ZipInputStream(new CountingStream(c.getInputStream(), c.getContentLengthLong(), progress));
            try {
                ZipEntry z;
                byte[] buf = new byte[256 * 1024];
                while ((z = zip.getNextEntry()) != null) {
                    // Le zip contient « PPSSPP/… » : on retire ce premier dossier, qui devient e[2].
                    String name = z.getName();
                    if (name.startsWith(e[2] + "/")) name = name.substring(e[2].length() + 1);
                    if (name.isEmpty()) continue;
                    File out = new File(tmp, name);
                    if (!out.getCanonicalPath().startsWith(root)) continue;
                    if (z.isDirectory()) {
                        out.mkdirs();
                        continue;
                    }
                    out.getParentFile().mkdirs();
                    OutputStream o = new FileOutputStream(out);
                    try {
                        int n;
                        while ((n = zip.read(buf)) > 0) o.write(buf, 0, n);
                    } finally {
                        o.close();
                    }
                }
            } finally {
                zip.close();
                c.disconnect();
            }
            if (!tmp.renameTo(new File(system, e[2]))) throw new IOException("impossible d'installer " + e[1]);
        }
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }

    private static final class CountingStream extends java.io.FilterInputStream {
        private final long total;
        private final Progress progress;
        private long done;

        CountingStream(InputStream in, long total, Progress progress) {
            super(in);
            this.total = total;
            this.progress = progress;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) tick(1);
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) tick(n);
            return n;
        }

        private void tick(int n) {
            done += n;
            if (progress != null) progress.onProgress(done, total);
        }
    }
}
