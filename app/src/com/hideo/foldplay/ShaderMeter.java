package com.hideo.foldplay;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Activité des shaders du moteur PS3 (0.66.30) : le moteur ne donne aucun compteur, on mesure donc
 * la charge des fils de compilation (RSX.W, en % d'un cœur) et la taille du cache de shaders sur disque,
 * qui grandit à chaque shader nouveau. Sert à l'ATH et au journal.
 */
final class ShaderMeter {
    private final File root;
    private Map<Integer, Long> last = new HashMap<Integer, Long>();
    private long lastAt, cacheAt;
    private int cacheFiles;
    private long cacheKb;

    ShaderMeter(File root) {
        this.root = root;
    }

    /** Fils de compilation : shaders (RSX.W) et code du jeu (modules PPU/LLVM, « ps3-compile »), qui pèse le plus sur MGS4. */
    private static boolean isCompiler(String name) {
        String s = name.toLowerCase(java.util.Locale.ROOT);
        return s.startsWith("rsx.w") || s.contains("compil") || s.contains("llvm");
    }

    private static boolean isShader(String name) {
        return name.toLowerCase(java.util.Locale.ROOT).startsWith("rsx.w");
    }

    private static boolean isCode(String name) {
        return isCompiler(name) && !isShader(name);
    }

    /** Charge des fils de compilation depuis le dernier appel, en % d'un cœur ; -1 au premier appel. */
    synchronized int compilePct() {
        return pct(0);
    }

    /** Charge des fils de shaders seuls (RSX.W), même mesure que compilePct (0.67). */
    synchronized int shaderPct() {
        return pct(1);
    }

    /** Charge du code PS3 seul (fils LLVM / ps3-compile), même mesure que compilePct (0.67). */
    synchronized int codePct() {
        return pct(2);
    }

    private final Map<Integer, Long>[] lastKind = newKinds();
    private final long[] lastKindAt = new long[3];

    @SuppressWarnings("unchecked")
    private static Map<Integer, Long>[] newKinds() {
        return new Map[] {null, new HashMap<Integer, Long>(), new HashMap<Integer, Long>()};
    }

    private int pct(int kind) {
        if (kind == 0) return pctAll();
        long now = android.os.SystemClock.elapsedRealtime();
        Map<Integer, Long> cur = new HashMap<Integer, Long>();
        long ticks = 0;
        File[] tasks = new File("/proc/self/task").listFiles();
        if (tasks == null) return -1;
        Map<Integer, Long> before = lastKind[kind];
        for (File t : tasks) {
            try {
                String stat = Hud.readLine(t.getPath() + "/stat");
                if (stat == null) continue;
                int open = stat.indexOf('('), close = stat.lastIndexOf(')');
                if (open < 0 || close < open) continue;
                String name = stat.substring(open + 1, close);
                if (kind == 1 ? !isShader(name) : !isCode(name)) continue;
                String[] f = stat.substring(close + 2).split(" ");
                long tk = Long.parseLong(f[11]) + Long.parseLong(f[12]);
                int tid = Integer.parseInt(t.getName());
                cur.put(tid, tk);
                Long b = before.get(tid);
                ticks += b == null ? tk : tk - b;
            } catch (Throwable ignored) {
            }
        }
        long span = now - lastKindAt[kind];
        boolean first = lastKindAt[kind] == 0;
        lastKind[kind] = cur;
        lastKindAt[kind] = now;
        if (first || span <= 0) return -1;
        return (int) (ticks * 1000L * 100L / 100L / span);
    }

    private final java.util.Set<String> seenModules = new java.util.HashSet<String>();

    /** Fichiers de code compilé (modules PPU/SPU en cache) apparus depuis le dernier appel : « nom (Ko) » ; le premier appel ne liste que le total. */
    synchronized java.util.List<String> newModules() {
        java.util.List<String> out = new java.util.ArrayList<String>();
        collect(new File(root, "cache"), 0, out);
        return out;
    }

    private void collect(File d, int depth, java.util.List<String> out) {
        File[] fs = d.listFiles();
        if (fs == null || depth > 6) return;
        for (File f : fs) {
            if (f.isDirectory()) {
                collect(f, depth + 1, out);
                continue;
            }
            String n = f.getName().toLowerCase(java.util.Locale.ROOT);
            if (!(n.endsWith(".obj") || n.endsWith(".so") || n.contains("ppu") || n.contains("spu"))) continue;
            if (seenModules.add(f.getPath())) out.add(f.getName() + " (" + f.length() / 1024 + " Ko)");
        }
    }

    private int pctAll() {
        long now = android.os.SystemClock.elapsedRealtime();
        Map<Integer, Long> cur = new HashMap<Integer, Long>();
        long ticks = 0;
        File[] tasks = new File("/proc/self/task").listFiles();
        if (tasks == null) return -1;
        for (File t : tasks) {
            try {
                String stat = Hud.readLine(t.getPath() + "/stat");
                if (stat == null) continue;
                int open = stat.indexOf('('), close = stat.lastIndexOf(')');
                if (open < 0 || close < open || !isCompiler(stat.substring(open + 1, close))) continue;
                String[] f = stat.substring(close + 2).split(" ");
                long tk = Long.parseLong(f[11]) + Long.parseLong(f[12]);
                int tid = Integer.parseInt(t.getName());
                cur.put(tid, tk);
                Long before = last.get(tid);
                ticks += before == null ? tk : tk - before;
            } catch (Throwable ignored) {
            }
        }
        long span = now - lastAt;
        boolean first = lastAt == 0;
        last = cur;
        lastAt = now;
        if (first || span <= 0) return -1;
        return (int) (ticks * 1000L * 100L / 100L / span);
    }

    /** Fichiers et Ko du cache de shaders (relu au plus toutes les 10 s). */
    synchronized String cache() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - cacheAt > 10000) {
            cacheAt = now;
            cacheFiles = 0;
            cacheKb = 0;
            walk(new File(root, "cache"), 0);
        }
        return cacheFiles + " fichiers, " + cacheKb / 1024 + " Mo";
    }

    private void walk(File d, int depth) {
        File[] fs = d.listFiles();
        if (fs == null || depth > 6) return;
        for (File f : fs) {
            if (f.isDirectory()) {
                String n = f.getName().toLowerCase();
                if (n.contains("shader") || n.contains("pipeline") || depth < 3) walkAll(f, n.contains("shader") || n.contains("pipeline"), depth + 1);
            } else if (f.getName().toLowerCase().contains("pipeline")) {
                // vk_pipeline_cache.bin : le résultat de la conversion du pilote, gardé entre deux lancements.
                cacheFiles++;
                cacheKb += f.length() / 1024;
            }
        }
    }

    private void walkAll(File d, boolean count, int depth) {
        File[] fs = d.listFiles();
        if (fs == null || depth > 8) return;
        for (File f : fs) {
            if (f.isDirectory()) {
                String n = f.getName().toLowerCase();
                walkAll(f, count || n.contains("shader") || n.contains("pipeline"), depth + 1);
            } else if (count) {
                cacheFiles++;
                cacheKb += f.length() / 1024;
            }
        }
    }

    /** Vide le cache de shaders (jamais les sauvegardes) ; renvoie le nombre de fichiers supprimés. */
    static int clear(File root) {
        return clearDir(new File(root, "cache"), false, 0);
    }

    private static int clearDir(File d, boolean inCache, int depth) {
        File[] fs = d.listFiles();
        if (fs == null || depth > 8) return 0;
        int n = 0;
        for (File f : fs) {
            String name = f.getName().toLowerCase();
            boolean hit = inCache || name.contains("shader") || name.contains("pipeline");
            if (f.isDirectory()) {
                n += clearDir(f, hit, depth + 1);
                if (hit) f.delete();
            } else if (hit && f.delete()) {
                n++;
            }
        }
        return n;
    }
}
