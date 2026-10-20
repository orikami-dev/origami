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
        SaveTransfer.share(a, outs, "Envoyer le journal", "text/plain");
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
