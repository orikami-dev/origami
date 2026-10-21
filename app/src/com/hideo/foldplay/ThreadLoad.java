package com.hideo.foldplay;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Charge de chaque fil du processus, lue dans /proc/self/task (aucune permission requise), regroupée
 * par nom de fil (« SPU[0x2000100] » et « SPU[0x2000200] » deviennent « SPU »). Sert à voir, sans PC,
 * qui sature quand un jeu saccade : fils SPU ou PPU (calcul du jeu), compilateurs LLVM ou shaders
 * (code nouveau à traduire), rendu, son.
 */
final class ThreadLoad {
    private ThreadLoad() {
    }

    private static Map<Integer, Long> last = new HashMap<Integer, Long>();
    private static long lastAt;
    private static final long HZ = 100; // USER_HZ d'Android

    /** Les fils les plus chargés depuis l'appel précédent, en % d'un cœur ; null au premier appel. */
    static String top(int n) {
        long now = android.os.SystemClock.elapsedRealtime();
        Map<Integer, Long> cur = new HashMap<Integer, Long>();
        Map<String, Long> byName = new HashMap<String, Long>();
        Map<String, Integer> count = new HashMap<String, Integer>();
        File[] tasks = new File("/proc/self/task").listFiles();
        if (tasks == null) return null;
        for (File t : tasks) {
            try {
                int tid = Integer.parseInt(t.getName());
                String stat = Hud.readLine(t.getPath() + "/stat");
                if (stat == null) continue;
                int open = stat.indexOf('('), close = stat.lastIndexOf(')');
                if (open < 0 || close < open) continue;
                String name = group(stat.substring(open + 1, close));
                String[] f = stat.substring(close + 2).split(" ");
                long ticks = Long.parseLong(f[11]) + Long.parseLong(f[12]); // utime + stime
                cur.put(tid, ticks);
                Long before = last.get(tid);
                long d = before == null ? (lastAt == 0 ? 0 : ticks) : ticks - before;
                if (d <= 0) continue;
                Long s = byName.get(name);
                byName.put(name, (s == null ? 0 : s) + d);
                Integer c = count.get(name);
                count.put(name, (c == null ? 0 : c) + 1);
            } catch (Throwable ignored) {
            }
        }
        long span = now - lastAt;
        boolean first = lastAt == 0;
        last = cur;
        lastAt = now;
        if (first || span <= 0) return null;
        List<Map.Entry<String, Long>> list = new ArrayList<Map.Entry<String, Long>>(byName.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<String, Long>>() {
            public int compare(Map.Entry<String, Long> a, Map.Entry<String, Long> b) {
                return b.getValue().compareTo(a.getValue());
            }
        });
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(n, list.size()); i++) {
            Map.Entry<String, Long> e = list.get(i);
            long pct = e.getValue() * 1000L * 100L / HZ / span;
            if (pct < 3) break;
            int c = count.get(e.getKey());
            sb.append(i == 0 ? "" : ", ").append(e.getKey()).append(c > 1 ? " ×" + c : "").append(' ').append(pct).append(" %");
        }
        return sb.toString();
    }

    /** Retire les numéros propres à chaque fil pour regrouper les fils d'un même rôle. */
    private static String group(String name) {
        String s = name.replaceAll("\\[[^\\]]*\\]?", "").replaceAll("0x[0-9a-fA-F]+", "")
                .replaceAll("[#:_ -]*\\d+$", "").trim();
        return s.isEmpty() ? name : s;
    }
}
