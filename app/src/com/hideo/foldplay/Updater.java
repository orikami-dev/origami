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
    private static final long AUTO_EVERY_MS = 12L * 3600 * 1000;

    /** Résultat d'une recherche. error non nul : rien n'a pu être lu (dépôt privé, hors ligne…). */
    static final class Release {
        String version, summary, apkUrl, pageUrl, error;
        boolean newer;
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
        if (!new Store(ctx).previewUpdates()) return stable;
        Release pre = fetchPreview(ctx);
        if (pre.error != null) return stable;
        if (stable.error != null || !stable.newer || compare(pre.version, stable.version) > 0) return pre;
        return stable;
    }

    private static Release fetchPreview(Context ctx) {
        Release r = new Release();
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(PREVIEW_RAW + "preview.json?t=" + System.currentTimeMillis()).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setUseCaches(false);
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
                    if (a.optString("name", "").endsWith(".apk")) r.apkUrl = a.optString("browser_download_url", null);
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

    /** Les trois premières puces des notes de version, sans la mise en forme. */
    static String summary(String body) {
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (String line : body.split("\n")) {
            String t = line.trim();
            if (!t.startsWith("- ") && !t.startsWith("* ")) continue;
            t = t.substring(2).replaceAll("\\*\\*|__|`", "").replaceAll("\\[([^]]*)]\\([^)]*\\)", "$1");
            int cut = t.indexOf(" : ");
            if (t.length() > 90) t = (cut > 0 && cut < 70 ? t.substring(0, cut) : t.substring(0, 87).trim() + "…");
            if (b.length() > 0) b.append('\n');
            b.append("• ").append(t);
            if (++n == 3) break;
        }
        return b.toString();
    }

    /** Au démarrage : une recherche toutes les 12 h au plus, et une notification par nouvelle version. */
    static void autoCheck(final Context ctx) {
        final Store store = new Store(ctx);
        if (!store.autoUpdates() || System.currentTimeMillis() - store.lastUpdateCheck() < (store.previewUpdates() ? 15L * 60 * 1000 : AUTO_EVERY_MS)) return;
        check(ctx, new Done() {
            public void done(Release r) {
                if (r.error == null && r.newer && !r.version.equals(store.notifiedUpdate())) {
                    store.setNotifiedUpdate(r.version);
                    Updater.notify(ctx, r);
                }
            }
        });
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

    /** Télécharge l'APK (gestionnaire de téléchargements d'Android) puis ouvre l'installation. */
    static void install(final Context ctx, Release r) {
        if (r.apkUrl == null) {
            if (r.pageUrl != null) ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(r.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        final android.app.DownloadManager dm = (android.app.DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
        android.app.DownloadManager.Request req = new android.app.DownloadManager.Request(Uri.parse(r.apkUrl))
                .setTitle("Origami " + r.version)
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(ctx, android.os.Environment.DIRECTORY_DOWNLOADS, "origami-" + r.version + ".apk");
        final long id = dm.enqueue(req);
        final Context app = ctx.getApplicationContext();
        BroadcastReceiver done = new BroadcastReceiver() {
            public void onReceive(Context c, Intent i) {
                if (i.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return;
                app.unregisterReceiver(this);
                Uri uri = dm.getUriForDownloadedFile(id);
                if (uri == null) return;
                Intent inst = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    app.startActivity(inst);
                } catch (Exception ignored) {
                    // Pas d'installateur : l'APK reste dans les téléchargements.
                }
            }
        };
        IntentFilter f = new IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (android.os.Build.VERSION.SDK_INT >= 33) app.registerReceiver(done, f, Context.RECEIVER_EXPORTED);
        else app.registerReceiver(done, f);
    }
}
