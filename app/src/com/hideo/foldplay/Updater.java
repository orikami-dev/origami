package com.hideo.foldplay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Mises à jour d'Origami (0.62.5) : lit la dernière release GitHub, compare sa version à celle
 * installée, prévient par une notification avec un court résumé des nouveautés, puis télécharge
 * l'APK et lance son installation. Réglages > Mises à jour.
 */
final class Updater {
    private Updater() {
    }

    static final String API = "https://api.github.com/repos/orikami-dev/origami/releases/latest";
    /** Préversions : la branche « preview » du dépôt contient preview.json et l'APK de test le plus récent (poussés d'ici, sans release). */
    static final String PREVIEW_RAW = "https://raw.githubusercontent.com/orikami-dev/origami/preview/";
    private static final String CHANNEL = "updates";
    private static final long AUTO_EVERY_MS = 6L * 3600 * 1000;
    private static final long POSTPONE_MS = 24L * 3600 * 1000;
    /** Une seule fenêtre « mise à jour disponible » par session, et jamais deux recherches à la fois. */
    static boolean popupShown;
    private static boolean checking;

    /** Résultat d'une recherche. error non nul : rien n'a pu être lu (dépôt privé, hors ligne…). */
    static final class Release {
        String version, summary, apkUrl, pageUrl, error;
        /** Poids de l'APK en octets, -1 si inconnu (préversion : connu seulement au début du téléchargement). */
        long apkSize = -1;
        boolean newer;
        String info;
    }

    interface Done {
        void done(Release r);
    }

    static String installed(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "0";
        }
    }

    /** Compare deux versions « 0.62.5 » : positif si a est plus récente que b. */
    static int compare(String a, String b) {
        String[] x = a.replaceAll("[^0-9.]", "").split("\\."), y = b.replaceAll("[^0-9.]", "").split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int u = i < x.length && !x[i].isEmpty() ? Integer.parseInt(x[i]) : 0;
            int v = i < y.length && !y[i].isEmpty() ? Integer.parseInt(y[i]) : 0;
            if (u != v) return u - v;
        }
        return 0;
    }

    /** Recherche en arrière-plan ; done est rappelé sur le fil principal. */
    static void check(final Context ctx, final Done done) {
        final Context app = ctx.getApplicationContext();
        final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        new Thread(new Runnable() {
            public void run() {
                final Release r = fetch(app);
                new Store(app).setLastUpdateCheck(System.currentTimeMillis());
                main.post(new Runnable() {
                    public void run() {
                        done.done(r);
                    }
                });
            }
        }, "origami-maj").start();
    }

    private static Release fetch(Context ctx) {
        Release stable = fetchStable(ctx);
        boolean wantPre = new Store(ctx).previewUpdates();
        Release pre = wantPre ? fetchPreview(ctx) : null;
        // Détail affiché sous « Origami est à jour » (0.66.20) : installée, stable, préversion, erreurs.
        String info = "Installée " + installed(ctx) + " · stable " + (stable.error != null ? "erreur (" + stable.error + ")" : stable.version)
                + " · préversion " + (pre == null ? "désactivée" : pre.error != null ? "erreur (" + pre.error + ")" : pre.version);
        DebugLog.event("Mise à jour : " + info);
        Release out;
        if (pre == null || pre.error != null) out = stable;
        else if (stable.error != null || !stable.newer || compare(pre.version, stable.version) > 0) out = pre;
        else out = stable;
        out.info = info;
        return out;
    }

    private static Release fetchPreview(Context ctx) {
        Release r = new Release();
        HttpURLConnection c = null;
        try {
            // 0.66.33 : adresse unique à chaque vérification et en-têtes « sans cache » : un relais réseau ne peut plus
            // renvoyer l'ancien fichier de préversion.
            c = (HttpURLConnection) new URL(PREVIEW_RAW + "preview.json?t=" + System.currentTimeMillis()
                    + "&r=" + Long.toHexString(new java.util.Random().nextLong())).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setUseCaches(false);
            c.setRequestProperty("Cache-Control", "no-cache, no-store");
            c.setRequestProperty("Pragma", "no-cache");
            if (c.getResponseCode() != 200) {
                r.error = "Pas de préversion publiée.";
                return r;
            }
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            in.close();
            JSONObject j = new JSONObject(out.toString("UTF-8"));
            r.version = j.optString("version", "");
            r.summary = j.optString("notes", "");
            r.apkUrl = PREVIEW_RAW + j.optString("apk", "origami-preview.apk");
            r.pageUrl = "https://github.com/orikami-dev/origami/tree/preview";
            r.newer = !r.version.isEmpty() && compare(r.version, installed(ctx)) > 0;
        } catch (Exception e) {
            r.error = "Pas de connexion au serveur des mises à jour.";
        } finally {
            if (c != null) c.disconnect();
        }
        return r;
    }

    private static Release fetchStable(Context ctx) {
        Release r = new Release();
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(API).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("Accept", "application/vnd.github+json");
            int code = c.getResponseCode();
            if (code == 404) {
                r.error = "Le dépôt d'Origami est encore privé : les mises à jour automatiques arriveront avec la version publique.";
                return r;
            }
            if (code != 200) {
                r.error = "Serveur des mises à jour indisponible (" + code + ").";
                return r;
            }
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            in.close();
            JSONObject j = new JSONObject(out.toString("UTF-8"));
            r.version = j.optString("tag_name", "").replaceFirst("^v", "");
            r.pageUrl = j.optString("html_url", null);
            r.summary = summary(j.optString("body", ""));
            JSONArray assets = j.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    if (a.optString("name", "").endsWith(".apk")) {
                        r.apkUrl = a.optString("browser_download_url", null);
                        r.apkSize = a.optLong("size", -1);
                    }
                }
            }
            r.newer = !r.version.isEmpty() && compare(r.version, installed(ctx)) > 0;
        } catch (Exception e) {
            r.error = "Pas de connexion au serveur des mises à jour.";
        } finally {
            if (c != null) c.disconnect();
        }
        return r;
    }

    /** Toutes les puces des notes de version (jusqu'à 12), sans la mise en forme ; chacune garde sa phrase entière (0.67). */
    static String summary(String body) {
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (String line : body.split("\n")) {
            String t = line.trim();
            if (!t.startsWith("- ") && !t.startsWith("* ")) continue;
            t = t.substring(2).replaceAll("\\*\\*|__|`", "").replaceAll("\\[([^]]*)]\\([^)]*\\)", "$1");
            if (t.length() > 260) t = t.substring(0, 257).trim() + "…";
            if (b.length() > 0) b.append('\n');
            b.append("• ").append(t);
            if (++n == 12) break;
        }
        return b.toString();
    }

    /**
     * À l'ouverture de l'accueil : une recherche toutes les 6 h au plus (canal choisi dans les réglages),
     * une notification par nouvelle version et, si onNewer est fourni, la fenêtre « Mise à jour disponible »
     * (sauf si cette version a été repoussée il y a moins de 24 h ou si la fenêtre a déjà paru).
     */
    static void autoCheck(final Context ctx, final Done onNewer) {
        final Store store = new Store(ctx);
        if (!store.autoUpdates()) return;
        // Mise à jour déjà trouvée par une recherche récente : la fenêtre peut reparaître sans réseau.
        String[] f = store.foundUpdate();
        if (f != null) {
            if (compare(f[0], installed(ctx)) <= 0) {
                store.setFoundUpdate(null, null, null, null);
            } else if (onNewer != null && !popupShown && !isPostponed(store, f[0])) {
                Release r = new Release();
                r.version = f[0];
                r.apkUrl = f[1].isEmpty() ? null : f[1];
                r.pageUrl = f[2].isEmpty() ? null : f[2];
                r.summary = f[3];
                r.newer = true;
                onNewer.done(r);
            }
        }
        if (checking || System.currentTimeMillis() - store.lastUpdateCheck() < AUTO_EVERY_MS) return;
        checking = true;
        check(ctx, new Done() {
            public void done(Release r) {
                checking = false;
                if (r.error != null) return;
                if (!r.newer) {
                    store.setFoundUpdate(null, null, null, null);
                    return;
                }
                store.setFoundUpdate(r.version, r.apkUrl, r.pageUrl, r.summary);
                if (!r.version.equals(store.notifiedUpdate())) {
                    store.setNotifiedUpdate(r.version);
                    Updater.notify(ctx, r);
                }
                if (onNewer != null && !popupShown && !isPostponed(store, r.version)) onNewer.done(r);
            }
        });
    }

    private static boolean isPostponed(Store store, String version) {
        return version.equals(store.postponedUpdate()) && System.currentTimeMillis() - store.postponedUpdateAt() < POSTPONE_MS;
    }

    static void notify(Context ctx, Release r) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (android.os.Build.VERSION.SDK_INT >= 33
                && ctx.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, I18n.tr("Mises à jour"), NotificationManager.IMPORTANCE_DEFAULT));
        Intent open = new Intent(ctx, SettingsActivity.class).putExtra(SettingsActivity.EXTRA_SECTION, "updates")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(ctx, 7, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String text = r.summary == null || r.summary.isEmpty() ? I18n.tr("Touchez pour mettre à jour.") : r.summary;
        Notification n = new Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_foreground)
                .setContentTitle("Origami " + r.version + " " + I18n.tr("est disponible"))
                .setContentText(text.split("\n")[0])
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        nm.notify(62, n);
    }

    static String mo(double bytes) {
        return String.format(java.util.Locale.FRANCE, "%.1f", bytes / 1048576.0);
    }

    static String detail(long got, long total, double bytesPerSec) {
        StringBuilder b = new StringBuilder(mo(got));
        if (total > 0) b.append(" / ").append(mo(total));
        b.append(" Mo");
        if (bytesPerSec > 0) b.append(" · ").append(mo(bytesPerSec)).append(" Mo/s");
        return b.toString();
    }

    /** Suivi du téléchargement, rappelé sur le fil principal : pct de 0 à 100, puis done ou erreur. */
    interface Progress {
        /** pct de 0 à 100 ; detail : « 12,3 / 77,8 Mo · 4,5 Mo/s ». */
        void progress(int pct, String detail);

        void done(boolean ok);
    }

    /**
     * Télécharge l'APK dans le stockage de l'app (sans notification Android, la barre est dans
     * l'interface), l'installe par session, puis supprime le fichier téléchargé.
     */
    static void install(final Context ctx, final Release r, final Progress ui) {
        if (r.apkUrl == null) {
            if (r.pageUrl != null) ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(r.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        final Context app = ctx.getApplicationContext();
        final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        new Thread(new Runnable() {
            public void run() {
                java.io.File dir = new java.io.File(app.getCacheDir(), "maj");
                dir.mkdirs();
                final java.io.File f = new java.io.File(dir, "origami-" + r.version + ".apk");
                HttpURLConnection c = null;
                boolean ok = false;
                try {
                    c = (HttpURLConnection) new URL(r.apkUrl).openConnection();
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(15000);
                    long total = c.getContentLengthLong();
                    InputStream in = c.getInputStream();
                    java.io.FileOutputStream out = new java.io.FileOutputStream(f);
                    byte[] buf = new byte[1 << 16];
                    long got = 0;
                    int last = -1;
                    long t0 = System.nanoTime(), tPrev = t0, gotPrev = 0;
                    double rate = 0;
                    for (int n; (n = in.read(buf)) > 0; ) {
                        out.write(buf, 0, n);
                        got += n;
                        long now = System.nanoTime();
                        if (now - tPrev >= 700000000L) {
                            double inst = (got - gotPrev) / ((now - tPrev) / 1e9);
                            rate = rate == 0 ? inst : rate * 0.6 + inst * 0.4;
                            tPrev = now;
                            gotPrev = got;
                        }
                        final int pct = total > 0 ? (int) (got * 100 / total) : 0;
                        final String det = detail(got, total, rate);
                        if (pct != last || now == tPrev) {
                            last = pct;
                            main.post(new Runnable() {
                                public void run() { if (ui != null) ui.progress(pct, det); }
                            });
                        }
                    }
                    out.close();
                    in.close();
                    ok = installSession(app, f);
                } catch (Exception e) {
                    DebugLog.event("[maj] téléchargement impossible", e);
                } finally {
                    if (c != null) c.disconnect();
                    f.delete();
                }
                final boolean res = ok;
                main.post(new Runnable() {
                    public void run() { if (ui != null) ui.done(res); }
                });
            }
        }, "origami-install").start();
    }

    /** Supprime les APK téléchargés restants (anciennes mises à jour, installation interrompue). */
    static void cleanup(Context ctx) {
        try {
            java.io.File[] fs = new java.io.File(ctx.getCacheDir(), "maj").listFiles();
            if (fs != null) for (java.io.File f : fs) f.delete();
            java.io.File[] old = ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS).listFiles();
            if (old != null) for (java.io.File f : old) if (f.getName().startsWith("origami-") && f.getName().endsWith(".apk")) f.delete();
        } catch (Exception ignored) {
            // Rien à nettoyer.
        }
    }

    static final String ACTION_INSTALL_STATUS = "com.hideo.foldplay.INSTALL_STATUS";

    /** Copie l'APK dans une session PackageInstaller et la valide ; faux si quelque chose échoue. */
    static boolean installSession(Context ctx, java.io.File apk) {
        android.content.pm.PackageInstaller pi = ctx.getPackageManager().getPackageInstaller();
        android.content.pm.PackageInstaller.Session session = null;
        try {
            android.content.pm.PackageInstaller.SessionParams sp = new android.content.pm.PackageInstaller.SessionParams(
                    android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            sp.setAppPackageName(ctx.getPackageName());
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                sp.setRequireUserAction(android.content.pm.PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
            }
            int sid = pi.createSession(sp);
            session = pi.openSession(sid);
            InputStream in = new java.io.FileInputStream(apk);
            java.io.OutputStream out = session.openWrite("origami.apk", 0, -1);
            byte[] buf = new byte[1 << 16];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            session.fsync(out);
            out.close();
            in.close();
            Intent status = new Intent(ctx, InstallStatusReceiver.class).setAction(ACTION_INSTALL_STATUS);
            PendingIntent cb = PendingIntent.getBroadcast(ctx, sid, status,
                    PendingIntent.FLAG_UPDATE_CURRENT | (android.os.Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0));
            session.commit(cb.getIntentSender());
            session.close();
            DebugLog.event("[maj] session d'installation " + sid + " validée");
            return true;
        } catch (Exception e) {
            DebugLog.event("[maj] session d'installation impossible", e);
            if (session != null) session.abandon();
            return false;
        }
    }
}
