package com.hideo.foldplay;

import android.app.Activity;
import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Mode débogage : copie le logcat de l'app dans files/logs (deux fichiers tournants de 2 Mo),
 * écrit chaque plantage dans un rapport, et permet de tout partager d'un geste.
 * Désactivé, rien ne tourne et rien n'est écrit.
 */
final class DebugLog {
    private DebugLog() {
    }

    private static Process logcat;
    private static boolean handlerSet;

    static File dir(Context ctx) {
        return new File(ctx.getFilesDir(), "logs");
    }

    /** À appeler à l'ouverture de chaque écran : démarre ou arrête la capture selon le réglage. */
    static synchronized void sync(Context ctx) {
        boolean on = new Store(ctx).debug();
        if (on) start(ctx.getApplicationContext());
        else stop();
    }

    private static void start(final Context ctx) {
        File d = dir(ctx);
        d.mkdirs();
        if (!handlerSet) {
            handlerSet = true;
            final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                public void uncaughtException(Thread t, Throwable e) {
                    if (new Store(ctx).debug()) crash(ctx, t, e);
                    if (prev != null) prev.uncaughtException(t, e);
                }
            });
        }
        if (logcat != null) return;
        try {
            logcat = Runtime.getRuntime().exec(new String[] {"logcat", "-v", "threadtime",
                    "--pid=" + android.os.Process.myPid(), "-f", new File(d, logName(ctx)).getPath(),
                    "-r", "2048", "-n", "2"});
        } catch (IOException e) {
            logcat = null;
        }
    }

    /** Un fichier par processus : la PS3 tourne dans « :ps3 », à côté de l'app. */
    private static String logName(Context ctx) {
        String p = Build.VERSION.SDK_INT >= 28 ? android.app.Application.getProcessName() : null;
        if (p != null && p.endsWith(":ps3")) return "logcat-ps3.txt";
        if (p != null && p.endsWith(":x360")) return "logcat-x360.txt";
        return "logcat.txt";
    }

    /**
     * Plantages des moteurs à part (PS3, Xbox 360) depuis la dernière visite, d'après Android
     * (0.60.7) : enregistrés même sans mode débogage, avec le rapport natif quand il existe.
     * Renvoie le nom de la console qui a planté, ou null.
     */
    static String collectExits(Context ctx) {
        if (Build.VERSION.SDK_INT < 30) return null;
        String crashed = null;
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            android.content.SharedPreferences prefs = ctx.getSharedPreferences("exits", Context.MODE_PRIVATE);
            long seen = prefs.getLong("seen", 0), newest = seen;
            for (android.app.ApplicationExitInfo e : am.getHistoricalProcessExitReasons(ctx.getPackageName(), 0, 16)) {
                if (e.getTimestamp() <= seen) continue;
                newest = Math.max(newest, e.getTimestamp());
                String p = e.getProcessName();
                boolean ps3 = p.endsWith(":ps3"), x360 = p.endsWith(":x360");
                if (!ps3 && !x360) continue;
                int r = e.getReason();
                File clean = new File(dir(ctx), "clean-" + e.getPid());
                if (clean.exists()) {
                    // Arrêt voulu par l'app (Quitter, relance) : rien à signaler.
                    clean.delete();
                    continue;
                }
                if (r != android.app.ApplicationExitInfo.REASON_CRASH && r != android.app.ApplicationExitInfo.REASON_CRASH_NATIVE
                        && r != android.app.ApplicationExitInfo.REASON_ANR && r != android.app.ApplicationExitInfo.REASON_LOW_MEMORY
                        && r != android.app.ApplicationExitInfo.REASON_SIGNALED) continue;
                File d = dir(ctx);
                d.mkdirs();
                String base = "sortie-" + (ps3 ? "ps3" : "x360") + "-"
                        + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date(e.getTimestamp()));
                FileOutputStream out = new FileOutputStream(new File(d, base + ".txt"));
                out.write((info(ctx) + "\n" + e.toString() + "\n").getBytes("UTF-8"));
                out.close();
                InputStream trace = e.getTraceInputStream();
                if (trace != null) {
                    // Natif : rapport « tombstone » (protobuf) ; Java/ANR : texte.
                    FileOutputStream t = new FileOutputStream(new File(d, base
                            + (r == android.app.ApplicationExitInfo.REASON_CRASH_NATIVE ? ".tombstone.pb" : ".trace.txt")));
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = trace.read(buf)) > 0) t.write(buf, 0, n);
                    t.close();
                    trace.close();
                }
                if (crashed == null) crashed = ps3 ? "PS3" : "Xbox 360";
            }
            prefs.edit().putLong("seen", newest).apply();
        } catch (Exception ignored) {
            // Pas d'accès à l'historique : rien à signaler.
        }
        return crashed;
    }

    /** Note au journal (étiquette Origami) : jalon d'un écran ou d'un moteur. */
    static void event(String msg) {
        android.util.Log.i("Origami", msg);
        engineWrite(msg);
    }

    static void event(String msg, Throwable where) {
        String full = msg + "\n" + android.util.Log.getStackTraceString(where);
        android.util.Log.i("Origami", full);
        engineWrite(full);
    }

    /**
     * 0.66.29 : le processus moteur (PS3, Xbox 360) double ses événements dans un fichier à lui, écrit tout de
     * suite : logcat -f perdait la fin de session quand le moteur est tué, et « Copier le journal » ne la voyait pas.
     */
    private static java.io.FileOutputStream engineOut;
    private static final java.text.SimpleDateFormat ENGINE_TIME = new java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US);

    private static synchronized void engineOpen(Context ctx, String tag) {
        if (engineOut != null) return;
        try {
            File d = dir(ctx);
            d.mkdirs();
            engineOut = new FileOutputStream(new File(d, "moteur-" + tag.toLowerCase(java.util.Locale.US) + ".txt"), false);
        } catch (IOException e) {
            engineOut = null;
        }
    }

    private static synchronized void engineWrite(String msg) {
        if (engineOut == null) return;
        try {
            engineOut.write((ENGINE_TIME.format(new java.util.Date()) + " " + msg + "\n").getBytes("UTF-8"));
            engineOut.flush();
        } catch (IOException e) {
            engineOut = null;
        }
    }

    /**
     * État de l'appareil à un instant donné : mémoire, batterie, chaleur, écran, stockage, droits.
     * Une ligne par thème, pour qu'un journal dise dans quelles conditions un jeu a tourné ou échoué.
     */
    static void snapshot(Context ctx, String why) {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            Runtime rt = Runtime.getRuntime();
            event("[état " + why + "] mémoire libre " + (mi.availMem >> 20) + " Mo sur " + (mi.totalMem >> 20)
                    + " Mo, seuil bas " + (mi.threshold >> 20) + " Mo, manque=" + mi.lowMemory
                    + ", tas Java " + ((rt.totalMemory() - rt.freeMemory()) >> 20) + "/" + (rt.maxMemory() >> 20) + " Mo");
            android.content.Intent b = ctx.registerReceiver(null, new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
            if (b != null) {
                event("[état " + why + "] batterie " + b.getIntExtra("level", -1) + " %, "
                        + (b.getIntExtra("temperature", 0) / 10f) + " °C, branchée=" + b.getIntExtra("plugged", 0)
                        + ", tension " + b.getIntExtra("voltage", 0) + " mV");
            }
            if (Build.VERSION.SDK_INT >= 29) {
                android.os.PowerManager pm = (android.os.PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
                event("[état " + why + "] chaleur Android " + pm.getCurrentThermalStatus() + " (0 aucune … 6 arrêt), économie d'énergie=" + pm.isPowerSaveMode());
            }
            android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
            android.content.res.Configuration c = ctx.getResources().getConfiguration();
            event("[état " + why + "] écran " + dm.widthPixels + "×" + dm.heightPixels + " px, " + dm.densityDpi + " dpi, "
                    + c.screenWidthDp + "×" + c.screenHeightDp + " dp, orientation " + c.orientation);
            File f = ctx.getExternalFilesDir(null);
            if (f != null) event("[état " + why + "] stockage libre " + (f.getUsableSpace() >> 20) + " Mo");
            event("[état " + why + "] tous les fichiers=" + (Build.VERSION.SDK_INT >= 30 && android.os.Environment.isExternalStorageManager())
                    + ", processus " + (Build.VERSION.SDK_INT >= 28 ? android.app.Application.getProcessName() : "?")
                    + " pid " + android.os.Process.myPid() + ", fils " + Thread.getAllStackTraces().size());
        } catch (Throwable t) {
            event("[état " + why + "] illisible : " + t);
        }
    }

    private static android.os.Handler perfHandler;
    private static Runnable perfTick;
    private static long perfLastCpu, perfLastAt;

    /**
     * Mesures de performance toutes les 2 s dans le journal (débogage seulement) : charge du processus,
     * mémoire, fréquence de chaque cœur et du GPU, températures, chaleur Android. Sert à comprendre
     * pourquoi un jeu rame (cœur saturé, GPU au plafond, chaleur qui bride…).
     */
    /** Mesures propres à un moteur (images/s, son…) ajoutées à chaque relevé. */
    interface Extra {
        String get();
    }

    static volatile Extra perfExtra;
    private static long perfStartAt;
    private static volatile long sessionEnd;

    /** Durée de session : s'arrête quand la compilation est finie (stopSession), sinon court jusqu'à maintenant. */
    static long sessionMs() {
        long end = sessionEnd > 0 ? sessionEnd : android.os.SystemClock.elapsedRealtime();
        return perfStartAt > 0 ? end - perfStartAt : -1;
    }

    static void stopSession() {
        sessionEnd = android.os.SystemClock.elapsedRealtime();
    }

    /** Durée au format « mm:ss » (ou « h:mm:ss »). */
    static String clock(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format(java.util.Locale.ROOT, "%02d:%02d", s / 60, s % 60);
    }

    static void perfStart(final Context ctx, final String tag) {
        if (!new Store(ctx).debug() || perfTick != null) return;
        engineOpen(ctx, tag);
        perfStartAt = android.os.SystemClock.elapsedRealtime();
        sessionEnd = 0;
        perfHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        perfLastCpu = android.os.Process.getElapsedCpuTime();
        perfLastAt = android.os.SystemClock.elapsedRealtime();
        perfTick = new Runnable() {
            public void run() {
                try {
                    long cpu = android.os.Process.getElapsedCpuTime(), now = android.os.SystemClock.elapsedRealtime();
                    float load = (cpu - perfLastCpu) * 100f / Math.max(1, now - perfLastAt);
                    perfLastCpu = cpu;
                    perfLastAt = now;
                    StringBuilder f = new StringBuilder();
                    for (int i = 0; i < 12; i++) {
                        String v = Hud.readLine("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq");
                        if (v == null) break;
                        try {
                            f.append(i == 0 ? "" : " ").append(Integer.parseInt(v.trim()) / 1000);
                        } catch (NumberFormatException e) {
                            f.append(" ?");
                        }
                    }
                    String gpu = Hud.readLine("/sys/class/kgsl/kgsl-3d0/gpuclk");
                    String busy = Hud.readLine("/sys/class/kgsl/kgsl-3d0/gpubusy");
                    event("[perf " + tag + "] session " + clock(sessionMs()) + ", charge processus " + Math.round(load) + " % (100 = un cœur), mémoire "
                            + Hud.residentMb() + " Mo, cœurs MHz [" + f + "], GPU " + (gpu == null ? "?" : Long.parseLong(gpu.trim()) / 1000000L)
                            + " MHz, occupation GPU " + (busy == null ? "?" : busy.trim()) + ", CPU " + Math.round(Hud.cpuC())
                            + " °C, GPU " + Math.round(Hud.gpuC()) + " °C, fils " + Thread.getAllStackTraces().size());
                    Extra ex = perfExtra;
                    if (ex != null) {
                        String x = ex.get();
                        if (x != null) event("[perf " + tag + "] " + x);
                    }
                    // 0.66 : qui consomme, fil par fil (SPU, PPU, compilateurs, rendu, son).
                    String th = ThreadLoad.top(6);
                    if (th != null) event("[perf " + tag + "] fils : " + th);
                    if (Build.VERSION.SDK_INT >= 29) {
                        android.os.PowerManager pm = (android.os.PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
                        if (pm.getCurrentThermalStatus() > 0) event("[perf " + tag + "] chaleur Android " + pm.getCurrentThermalStatus());
                    }
                } catch (Throwable t) {
                    event("[perf " + tag + "] mesure impossible : " + t);
                }
                perfHandler.postDelayed(this, 2000);
            }
        };
        ThreadLoad.top(0);
        perfHandler.postDelayed(perfTick, 2000);
    }

    static void perfStop() {
        if (perfHandler != null && perfTick != null) perfHandler.removeCallbacks(perfTick);
        perfTick = null;
    }

    /** À appeler juste avant de tuer volontairement un processus moteur : sa sortie n'est pas un plantage. */
    static void markClean(Context ctx) {
        try {
            File d = dir(ctx);
            d.mkdirs();
            new FileOutputStream(new File(d, "clean-" + android.os.Process.myPid())).close();
        } catch (Throwable ignored) {
            // Pire cas : la sortie sera signalée comme un plantage.
        }
    }

    static synchronized void stop() {
        if (logcat != null) logcat.destroy();
        logcat = null;
    }

    private static void crash(Context ctx, Thread t, Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("Plantage dans le fil « " + t.getName() + " »");
        pw.println(info(ctx));
        e.printStackTrace(pw);
        pw.flush();
        try {
            FileOutputStream out = new FileOutputStream(new File(dir(ctx), "plantage-" + stamp() + ".txt"));
            out.write(sw.toString().getBytes("UTF-8"));
            out.close();
        } catch (IOException ignored) {
            // Rien à faire de plus pendant un plantage.
        }
    }

    private static String stamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
    }

    static String info(Context ctx) {
        String version = "?";
        try {
            android.content.pm.PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            version = pi.versionName + " (" + pi.versionCode + ")";
        } catch (Exception ignored) {
            // Version inconnue.
        }
        return "Origami " + version + "\nAppareil : " + Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE
                + ")\nAndroid " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\nPuce : "
                + (Build.VERSION.SDK_INT >= 31 ? Build.SOC_MANUFACTURER + " " + Build.SOC_MODEL : Build.HARDWARE)
                + "\nDate : " + new Date() + "\n";
    }

    /** Taille totale des journaux, en octets. */
    static long size(Context ctx) {
        long n = 0;
        File[] fs = dir(ctx).listFiles();
        if (fs != null) for (File f : fs) n += f.length();
        return n;
    }

    static void clear(Context ctx) {
        File[] fs = dir(ctx).listFiles();
        if (fs != null) for (File f : fs) f.delete();
    }

    /** Partage les journaux et les rapports de plantage, précédés d'une fiche de l'appareil. */
    /** Part la plus récente d'un journal gardée dans l'envoi (assez pour un retour de bug). */
    private static final int KEEP = 1536 * 1024;

    /**
     * Un seul fichier texte (text/plain) : fiche de l'appareil, puis chaque journal et rapport à la
     * suite. En texte, l'envoi est accepté par toutes les apps, Claude compris (0.62) ; les
     * rapports binaires (.pb) restent sur le téléphone.
     */
    /** Boîte qui reçoit les journaux envoyés depuis Réglages ; vide = partage libre. */
    static final String SUPPORT_MAIL = "orikami.logs@gmail.com";

    static void share(Activity a) {
        List<SaveImport.Out> outs = new ArrayList<SaveImport.Out>();
        try {
            StringBuilder all = new StringBuilder(info(a)).append('\n');
            File[] fs = dir(a).listFiles();
            if (fs != null) {
                java.util.Arrays.sort(fs);
                for (File f : fs) {
                    // Rapports de plantage natifs au format binaire : illisibles dans un envoi texte.
                    if (f.getName().endsWith(".pb")) continue;
                    byte[] b = read(f);
                    int from = Math.max(0, b.length - KEEP);
                    all.append("\n===== ").append(f.getName());
                    if (from > 0) all.append(" (fin du fichier, ").append(KEEP / 1024).append(" Ko)");
                    all.append(" =====\n").append(new String(b, from, b.length - from, "UTF-8")).append('\n');
                }
            }
            outs.add(0, new SaveImport.Out("origami-journal-" + stamp() + ".txt", all.toString().getBytes("UTF-8")));
        } catch (IOException e) {
            I18n.toast(a, "Lecture du journal impossible : " + e.getMessage(),
                    android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        String ver = "";
        try {
            ver = a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        SaveTransfer.share(a, outs, "Envoyer le journal", "text/plain", SUPPORT_MAIL, "Journal Origami " + ver,
                "Journal joint (fiche de l'appareil et journaux). Appuyez sur Envoyer pour le transmettre à l'équipe ORIKAMI.");
    }

    /** Part du journal mise dans le presse-papiers : assez pour un retour de bug, collable dans un message. */
    private static final int CLIP = 4 * 1024 * 1024;
    private static final java.util.regex.Pattern NOISE = java.util.regex.Pattern.compile(
            "AudioTrack: setVolume|VRI\\[|InsetsController|InsetsSource|ImeTracker|InputTransport|ScrollView|perf_hint"
            + "|BLASTBufferQueue|SurfaceComposerClient|HWUI|InputMethodManager|ImeFocusController|ViewRootImpl"
            + "|WindowOnBackDispatcher|DecorView|NativeCustomFrequencyManager|BufferQueueProducer| Surface :");

    /**
     * Un clic : la fiche de l'appareil et la fin du journal le plus récent partent dans le presse-papiers,
     * prêtes à coller dans le chat (Claude ne reçoit pas les envois d'Android).
     */
    static void copy(Activity a) {
        try {
            StringBuilder all = new StringBuilder(info(a)).append('\n');
            File[] fs = dir(a).listFiles();
            File last = null;
            if (fs != null) {
                for (File f : fs) {
                    if (f.getName().endsWith(".pb")) continue;
                    if (last == null || f.lastModified() > last.lastModified()) last = f;
                }
            }
            if (last != null) {
                // Bruit d'Android (fenêtres, pistes audio, insets…) retiré : il noie les lignes utiles.
                StringBuilder kept = new StringBuilder();
                for (String line : new String(read(last), "UTF-8").split("\n")) {
                    if (!NOISE.matcher(line).find()) kept.append(line).append('\n');
                }
                int from = Math.max(0, kept.length() - CLIP);
                all.append("\n===== ").append(last.getName()).append(" (fin, ").append(CLIP / 1024)
                        .append(" Ko, sans le bruit système) =====\n").append(kept.substring(from));
            }
            // 0.66 : mesures des moteurs (charge par fil, fréquences, chaleur) de toutes les sessions de la
            // dernière heure, y compris celles du processus PS3/360 que la fin du journal ne montre pas.
            if (fs != null) {
                StringBuilder perf = new StringBuilder();
                long since = System.currentTimeMillis() - 3600 * 1000L;
                for (File f : fs) {
                    if (f.getName().endsWith(".pb") || f.lastModified() < since || f == last) continue;
                    for (String line : new String(read(f), "UTF-8").split("\n")) {
                        if (line.contains("[perf ") || line.contains("PS3 refuse")) perf.append(line).append('\n');
                    }
                }
                if (perf.length() > 0) {
                    int from = Math.max(0, perf.length() - 24 * 1024);
                    all.append("\n===== mesures des moteurs (dernière heure) =====\n").append(perf.substring(from));
                }
            }
            File[] eng = dir(a).listFiles();
            if (eng != null) {
                for (File f : eng) {
                    if (!f.getName().startsWith("moteur-")) continue;
                    byte[] b = read(f);
                    int from = Math.max(0, b.length - 1024 * 1024);
                    all.append("\n===== ").append(f.getName()).append(" (session moteur complète) =====\n")
                            .append(new String(b, from, b.length - from, "UTF-8"));
                }
            }
            // 0.66.26 : le journal complet part dans un fichier .txt déposé dans le presse-papier (adresse de
            // contenu) : un collage dans le chat le joint tel quel, sans limite de longueur.
            byte[] data = all.toString().getBytes("UTF-8");
            File out = new File(SaveProvider.dir(a), "journal-origami.txt");
            out.getParentFile().mkdirs();
            FileOutputStream fo = new FileOutputStream(out);
            fo.write(data);
            fo.close();
            try {
                File logs = new File(OrigamiFolder.root(), "LOGS");
                logs.mkdirs();
                FileOutputStream lo = new FileOutputStream(new File(logs, "dernier-journal.txt"));
                lo.write(data);
                lo.close();
            } catch (Exception ignore) {
                // dossier Origami non accessible : le presse-papier suffit
            }
            android.content.ClipboardManager cm = (android.content.ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newUri(a.getContentResolver(), "journal Origami", SaveProvider.uri("journal-origami.txt")));
            I18n.toast(a, "Journal copié en fichier .txt : colle-le dans le chat", android.widget.Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            I18n.toast(a, "Copie du journal impossible : " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private static byte[] read(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            byte[] b = new byte[(int) f.length()];
            int n = 0;
            while (n < b.length) {
                int r = in.read(b, n, b.length - n);
                if (r < 0) break;
                n += r;
            }
            return n == b.length ? b : java.util.Arrays.copyOf(b, n);
        } finally {
            in.close();
        }
    }
}
