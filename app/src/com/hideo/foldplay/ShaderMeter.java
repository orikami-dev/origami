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

    /** Charge des fils de compilation depuis le dernier appel, en % d'un cœur ; -1 au premier appel. */
    synchronized int compilePct() {
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
                if (open < 0 || close < open || !stat.substring(open + 1, close).startsWith("RSX.W")) continue;
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
}
