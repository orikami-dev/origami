package com.hideo.foldplay;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Répartition des fils du moteur sur les cœurs (0.66.24), réglée dans Réglages > Performances > Cœurs.
 * Auto : Android décide seul. Performance : les fils critiques (PS3 : SPU et rendu RSX ; 360 : fils du jeu et
 * GPU) sont épinglés sur les gros cœurs et passés en priorité haute, les autres restent libres. Économie : tout le
 * moteur reste sur les cœurs moyens, le téléphone chauffe moins. Un relevé des fils part dans le journal à chaque
 * lancement pour affiner les noms reconnus.
 */
final class CoreAffinity {
    private CoreAffinity() {
    }

    static final String[] MODES = {"auto", "perf", "eco"};
    static final String[] LABELS = {"Auto", "Performance", "Économie"};

    /** Précompilation en cours (0.67) : les fils de compilation (LLVM, shaders) vont sur tous les cœurs, le temps de la compilation. */
    static volatile boolean boost;

    private static boolean loaded;
    private static Thread watcher;

    private static native int nativePin(int tid, long mask);

    private static boolean load() {
        if (loaded) return true;
        try {
            System.loadLibrary("foldplay_cores");
            loaded = true;
        } catch (Throwable t) {
            DebugLog.event("[cœurs] bibliothèque absente : " + t);
        }
        return loaded;
    }

    /** Fréquences actuelles des cœurs en MHz, pour le journal (« 1324 1324 … »). */
    static String freqs() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            String f = read("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq");
            if (f == null) break;
            try {
                b.append(Long.parseLong(f.trim()) / 1000).append(' ');
            } catch (NumberFormatException e) {
                b.append("? ");
            }
        }
        return b.toString().trim();
    }

    /** Masque des gros cœurs : ceux dont la fréquence maximale est la plus haute. */
    static long bigMask() {
        long[] max = new long[16];
        long top = 0;
        int n = 0;
        for (int i = 0; i < 16; i++) {
            String f = read("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq");
            if (f == null) break;
            try {
                max[i] = Long.parseLong(f.trim());
            } catch (NumberFormatException e) {
                max[i] = 0;
            }
            top = Math.max(top, max[i]);
            n = i + 1;
        }
        long big = 0;
        for (int i = 0; i < n; i++) if (max[i] >= top && top > 0) big |= 1L << i;
        // Tous pareils (ou illisible) : pas de gros cœurs à distinguer.
        return Long.bitCount(big) == n ? 0 : big;
    }

    static long allMask() {
        int n = Runtime.getRuntime().availableProcessors();
        return n >= 63 ? -1L : (1L << n) - 1;
    }

    /** Fil critique pour cette console, d'après son nom (tronqué à 15 caractères par Linux). */
    static boolean critical(String system, String name) {
        String s = name.toLowerCase(java.util.Locale.ROOT);
        if ("ps3".equals(system)) return s.startsWith("spu") || s.contains("rsx") || s.contains("cell");
        if ("x360".equals(system)) return s.contains("gpu") || s.contains("xthread") || s.contains("main xthread")
                || s.contains("command processor") || s.contains("xma");
        return false;
    }

    /** Fils de compilation des shaders (« Vulkan Pipeline »…) : sur les cœurs moyens, les gros restent au jeu et au son. */
    static boolean compiler(String name) {
        String s = name.toLowerCase(java.util.Locale.ROOT);
        return s.contains("shader") || s.contains("pipeline") || s.contains("compil");
    }

    /** Lance la surveillance des fils du processus du moteur ; à appeler une fois le jeu démarré. */
    static void start(final android.content.Context ctx, final String system) {
        final Store st = new Store(ctx);
        final String mode = st.lab() && "ps3".equals(system) ? "lab" : st.coresMode();
        DebugLog.event("[cœurs] mode " + mode + " pour " + system + ", gros cœurs " + Long.toBinaryString(bigMask()));
        if (watcher != null) return;
        watcher = new Thread(new Runnable() {
            public void run() {
                Map<Integer, String> done = new HashMap<Integer, String>();
                boolean listed = false;
                long big = bigMask(), all = allMask(), little = all & ~big;
                boolean act = !mode.equals("auto") && big != 0 && load();
                boolean wasBoost = false;
                int self = android.os.Process.myTid();
                for (int round = 0; !Thread.interrupted(); round++) {
                    boolean boosting = boost && big != 0 && load();
                    if (boosting != wasBoost) {
                        // Entrée ou sortie du plein régime : les fils sont repris un à un avec la règle du moment.
                        done.clear();
                        wasBoost = boosting;
                        DebugLog.event("[cœurs] plein régime de précompilation " + (boosting ? "activé" : "terminé, répartition rétablie"));
                    }
                    File[] tasks = new File("/proc/self/task").listFiles();
                    if (tasks != null) {
                        StringBuilder names = new StringBuilder();
                        for (File t : tasks) {
                            int tid;
                            try {
                                tid = Integer.parseInt(t.getName());
                            } catch (NumberFormatException e) {
                                continue;
                            }
                            if (tid == self) continue;
                            String name = read(t.getPath() + "/comm");
                            if (name == null) continue;
                            name = name.trim();
                            if (!listed) names.append(name).append(", ");
                            if (!(act || boosting) || name.equals(done.get(tid))) continue;
                            done.put(tid, name);
                            if (boosting && (compiler(name) || name.toLowerCase(java.util.Locale.ROOT).contains("llvm"))) {
                                nativePin(tid, all);
                                DebugLog.event("[cœurs] " + name + " (" + tid + ") → tous les cœurs (précompilation)");
                                continue;
                            }
                            if (!act) continue;
                            boolean crit = critical(system, name);
                            if (mode.equals("lab")) {
                                // Profil de test : son, rendu et compilation sur les gros cœurs, SPU/PPU sur les moyens.
                                boolean prime = Lab.onPrime(name);
                                nativePin(tid, prime ? big : (little == 0 ? all : little));
                                if (prime) DebugLog.event("[cœurs] " + name + " (" + tid + ") → gros cœurs (profil de test)");
                                continue;
                            }
                            if (mode.equals("perf")) {
                                if (compiler(name) && little != 0) {
                                    nativePin(tid, little);
                                    DebugLog.event("[cœurs] " + name + " (" + tid + ") → cœurs moyens");
                                    continue;
                                }
                                if (!crit) continue;
                                int r = nativePin(tid, big);
                                try {
                                    android.os.Process.setThreadPriority(tid, android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                                } catch (Exception e) {
                                    // Priorité refusée : l'épinglage seul reste utile.
                                }
                                DebugLog.event("[cœurs] " + name + " (" + tid + ") → gros cœurs" + (r == 0 ? "" : " refusé " + r));
                            } else {
                                nativePin(tid, little == 0 ? all : little);
                            }
                        }
                        // Relevé des noms une fois le moteur lancé (après 20 s), pour le diagnostic.
                        if (!listed && round >= 4) {
                            DebugLog.event("[cœurs] fils " + system + " : " + names);
                            listed = true;
                        }
                    }
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        }, "origami-cores");
        watcher.setDaemon(true);
        watcher.start();
        thermal(ctx);
    }

    /** Surchauffe : prévient le joueur et le note dans le journal (statut Android 3 = sévère et plus). */
    private static void thermal(final android.content.Context ctx) {
        if (android.os.Build.VERSION.SDK_INT < 29) return;
        android.os.PowerManager pm = (android.os.PowerManager) ctx.getSystemService(android.content.Context.POWER_SERVICE);
        pm.addThermalStatusListener(ctx.getMainExecutor(), new android.os.PowerManager.OnThermalStatusChangedListener() {
            int last = 0;

            public void onThermalStatusChanged(int status) {
                DebugLog.event("[cœurs] état thermique " + status);
                if (status >= android.os.PowerManager.THERMAL_STATUS_SEVERE && last < android.os.PowerManager.THERMAL_STATUS_SEVERE) {
                    I18n.toast(ctx, "Le téléphone chauffe : Android ralentit les cœurs. Retire la coque ou débranche le chargeur.",
                            android.widget.Toast.LENGTH_LONG).show();
                }
                last = status;
            }
        });
    }

    private static String read(String path) {
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(path));
            try {
                return r.readLine();
            } finally {
                r.close();
            }
        } catch (Exception e) {
            return null;
        }
    }
}
