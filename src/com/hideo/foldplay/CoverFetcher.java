package com.hideo.foldplay;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Récupère les jaquettes manquantes depuis la base libre libretro-thumbnails, sur l'appareil,
 * au moment où l'utilisateur scanne sa bibliothèque. Rien n'est embarqué dans l'app.
 *
 * Correspondance prudente : on compare des noms normalisés (sans accents, ponctuation ni
 * mentions entre parenthèses) et on n'accepte qu'une égalité stricte, pour ne jamais afficher
 * la jaquette d'un autre jeu. Les mentions de région du fichier départagent les versions.
 */
final class CoverFetcher {
    interface Listener {
        void onProgress(int done, int total);

        void onFinished(int found);
    }

    private static final String SERVER = "https://thumbnails.libretro.com/";
    private static final String MIRROR = "https://raw.githubusercontent.com/libretro-thumbnails/";
    private static final long INDEX_MAX_AGE_MS = 7L * 24 * 3600 * 1000;
    private static final Pattern HREF = Pattern.compile("href=\"([^\"]+\\.png)\"");
    private static final Pattern TAG = Pattern.compile("[\\(\\[]([^\\)\\]]*)[\\)\\]]");

    private static final Map<String, String> PLAYLISTS = new HashMap<String, String>();

    static {
        PLAYLISTS.put("nes", "Nintendo - Nintendo Entertainment System");
        PLAYLISTS.put("snes", "Nintendo - Super Nintendo Entertainment System");
        PLAYLISTS.put("gb", "Nintendo - Game Boy");
        PLAYLISTS.put("gbc", "Nintendo - Game Boy Color");
        PLAYLISTS.put("gba", "Nintendo - Game Boy Advance");
        PLAYLISTS.put("nds", "Nintendo - Nintendo DS");
        PLAYLISTS.put("n64", "Nintendo - Nintendo 64");
        PLAYLISTS.put("md", "Sega - Mega Drive - Genesis");
        PLAYLISTS.put("sms", "Sega - Master System - Mark III");
        PLAYLISTS.put("gg", "Sega - Game Gear");
        PLAYLISTS.put("pce", "NEC - PC Engine - TurboGrafx 16");
        PLAYLISTS.put("psx", "Sony - PlayStation");
        PLAYLISTS.put("psp", "Sony - PlayStation Portable");
        PLAYLISTS.put("ps2", "Sony - PlayStation 2");
        PLAYLISTS.put("vita", "Sony - PlayStation Vita");
        PLAYLISTS.put("gc", "Nintendo - GameCube");
        PLAYLISTS.put("wii", "Nintendo - Wii");
        PLAYLISTS.put("3ds", "Nintendo - Nintendo 3DS");
        PLAYLISTS.put("dc", "Sega - Dreamcast");
        PLAYLISTS.put("saturn", "Sega - Saturn");
        PLAYLISTS.put("arcade", "FBNeo - Arcade Games");
        // 0.60.6 : PS3 et Xbox 360 manquaient, leurs jaquettes n'étaient jamais demandées.
        // La Switch n'a pas de base libretro : ScreenScraper seulement.
        PLAYLISTS.put("ps3", "Sony - PlayStation 3");
        PLAYLISTS.put("x360", "Microsoft - Xbox 360");
        // Bases de secours, tentées si la première ne trouve rien (CD rangés avec leur console,
        // arcade hors FBNeo).
        PLAYLISTS.put("md+cd", "Sega - Mega-CD - Sega CD");
        PLAYLISTS.put("md+32x", "Sega - 32X");
        PLAYLISTS.put("pce+cd", "NEC - PC Engine CD - TurboGrafx-CD");
        PLAYLISTS.put("arcade+mame", "MAME");
        PLAYLISTS.put("arcade+naomi", "Sega - Naomi");
        PLAYLISTS.put("arcade+aw", "Atomiswave");
    }

    private final File dir;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private final Store store;

    private final android.content.ContentResolver cr;

    CoverFetcher(Context ctx) {
        cr = ctx.getContentResolver();
        dir = new File(ctx.getFilesDir(), "covers");
        store = new Store(ctx);
    }

    /** Vrai si un compte ScreenScraper est renseigné (Réglages > Jaquettes). */
    private boolean screenScraperReady() {
        String[] id = store.screenScraper();
        return !id[0].isEmpty() && !id[1].isEmpty();
    }

    /** Fichier local de la jaquette d'un jeu (qu'il existe déjà ou non). */
    File coverFile(Game g) {
        return new File(new File(dir, g.system.id), Integer.toHexString(g.uri.hashCode()) + ".png");
    }

    /** Branche les jaquettes déjà téléchargées lors d'une session précédente. */
    void attachCached(List<Game> games) {
        for (Game g : games) {
            if (g.coverUri != null) continue;
            File f = coverFile(g);
            if (f.exists()) g.coverUri = android.net.Uri.fromFile(f).toString();
        }
    }

    void fetchMissing(List<Game> games, final Listener listener) {
        final List<Game> todo = new ArrayList<Game>();
        for (Game g : games) {
            // libretro-thumbnails d'abord ; ScreenScraper pour les consoles qu'il n'a pas (Switch…)
            // et les jeux qu'il ne trouve pas, si un compte est renseigné.
            if (g.coverUri == null && (PLAYLISTS.containsKey(g.system.id) || screenScraperReady())) todo.add(g);
            else if (g.coverUri == null && g.system.id.equals("ps3")) todo.add(g);
        }
        if (todo.isEmpty()) return;
        worker.execute(new Runnable() {
            @Override
            public void run() {
                Map<String, Map<String, List<String>>> indexes = new HashMap<String, Map<String, List<String>>>();
                int found = 0;
                for (int i = 0; i < todo.size(); i++) {
                    Game g = todo.get(i);
                    boolean got = false;
                    // PS3 : l'icône du jeu lui-même (PS3_GAME/ICON0.PNG), sans réseau.
                    if (g.system.id.equals("ps3")) {
                        byte[] png = Ps3Icon.read(cr, g);
                        try {
                            if (png != null) got = save(g, png);
                        } catch (java.io.IOException ignored) {
                            // Stockage plein : on tente le réseau.
                        }
                    }
                    for (String key : playlists(g.system.id)) {
                        if (got) break;
                        try {
                            Map<String, List<String>> index = indexes.get(key);
                            if (index == null) {
                                try {
                                    index = loadIndex(key);
                                } catch (Exception e) {
                                    index = new HashMap<String, List<String>>();
                                }
                                indexes.put(key, index);
                            }
                            String name = pick(index, g);
                            // Sans index (serveur injoignable), un fichier déjà nommé à la No-Intro
                            // « Titre (Région) » peut être demandé tel quel au miroir.
                            if (name == null && index.isEmpty() && g.fileBase.contains("(")) name = g.fileBase;
                            if (name != null && download(g, key, name)) got = true;
                        } catch (Exception ignored) {
                            // Pas de réseau ou serveur indisponible : base suivante, puis ScreenScraper.
                        }
                    }
                    if (!got && screenScraperReady()) {
                        try {
                            got = screenScraper(g);
                        } catch (Exception ignored) {
                            // Jeu inconnu de ScreenScraper ou quota atteint : le jeu garde son emblème.
                        }
                    }
                    if (got) {
                        g.coverUri = android.net.Uri.fromFile(coverFile(g)).toString();
                        found++;
                    }
                    final int done = i + 1;
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onProgress(done, todo.size());
                        }
                    });
                }
                final int total = found;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onFinished(total);
                    }
                });
            }
        });
    }

    /** Index « nom normalisé → noms de fichiers » d'une console, gardé en cache une semaine. */
    private Map<String, List<String>> loadIndex(String system) throws Exception {
        File cache = new File(dir, system + ".index");
        String html;
        if (cache.exists() && System.currentTimeMillis() - cache.lastModified() < INDEX_MAX_AGE_MS) {
            html = new String(readAll(new FileInputStream(cache)), "UTF-8");
        } else {
            byte[] raw = get(SERVER + encodePath(PLAYLISTS.get(system)) + "/Named_Boxarts/");
            cache.getParentFile().mkdirs();
            FileOutputStream out = new FileOutputStream(cache);
            out.write(raw);
            out.close();
            html = new String(raw, "UTF-8");
        }
        Map<String, List<String>> index = new HashMap<String, List<String>>();
        Matcher m = HREF.matcher(html);
        while (m.find()) {
            String file = URLDecoder.decode(m.group(1).replace("+", "%2B"), "UTF-8");
            if (file.contains("/")) continue;
            String name = file.substring(0, file.length() - 4);
            String key = normalize(name);
            List<String> list = index.get(key);
            if (list == null) {
                list = new ArrayList<String>();
                index.put(key, list);
            }
            list.add(name);
        }
        return index;
    }

    /** Choisit la bonne version : celle dont les mentions (région, langue) collent le mieux au fichier. */
    static String pick(Map<String, List<String>> index, Game g) {
        List<String> candidates = index.get(normalize(g.baseName));
        if (candidates == null || candidates.isEmpty()) return null;
        List<String> wanted = tags(g.baseName);
        String best = null;
        int bestScore = Integer.MIN_VALUE;
        for (String c : candidates) {
            int score = 0;
            List<String> have = tags(c);
            for (String t : wanted) if (have.contains(t)) score += 10;
            // Sans indication dans le fichier : version française, puis européenne, puis mondiale, puis US.
            if (have.contains("france")) score += 6;
            if (have.contains("europe")) score += 5;
            if (have.contains("world")) score += 4;
            if (have.contains("usa")) score += 3;
            if (have.contains("fr")) score += 2;
            // Les versions annexes (démo, beta, rev…) passent après ; les codes de langue ne pénalisent pas.
            for (String t : have) if (t.length() > 2) score -= 1;
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return best;
    }

    /** Bases libretro d'une console : la sienne, puis ses bases de secours. */
    private static List<String> playlists(String system) {
        List<String> out = new ArrayList<String>();
        if (PLAYLISTS.containsKey(system)) out.add(system);
        for (String k : PLAYLISTS.keySet()) if (k.startsWith(system + "+")) out.add(k);
        return out;
    }

    private boolean download(Game g, String key, String name) throws Exception {
        // libretro remplace ces caractères par « _ » dans les noms de fichiers.
        String safe = name.replaceAll("[&*/:`<>?\\\\|]", "_");
        byte[] png;
        try {
            png = get(SERVER + encodePath(PLAYLISTS.get(key)) + "/Named_Boxarts/" + encodePath(safe) + ".png");
        } catch (Exception e) {
            String repo = PLAYLISTS.get(key).replace(' ', '_');
            png = get(MIRROR + encodePath(repo) + "/master/Named_Boxarts/" + encodePath(safe) + ".png");
        }
        if (png.length < 8 || png[1] != 'P' || png[2] != 'N' || png[3] != 'G') return false;
        return save(g, png);
    }

    private boolean save(Game g, byte[] png) throws java.io.IOException {
        File f = coverFile(g);
        f.getParentFile().mkdirs();
        FileOutputStream out = new FileOutputStream(f);
        out.write(png);
        out.close();
        return true;
    }

    private static final String[] SS_REGIONS = {"fr", "eu", "wor", "us", "ss", "jp"};

    /**
     * Jaquette ScreenScraper (« box-2D ») : le jeu est cherché par son nom de fichier, puis par son
     * titre nettoyé (les .nsp/.xci de Switch portent souvent un identifiant entre crochets).
     */
    /** Fiche ScreenScraper du jeu (par nom de fichier, puis par titre), ou null. */
    private org.json.JSONObject ssGame(Game g) throws Exception {
        String[] id = store.screenScraper();
        String sys = GameInfo.ssSystem(g.system.id);
        StringBuilder base = new StringBuilder("https://api.screenscraper.fr/api2/%s?output=json&softname=FOLDPLAY")
                .append("&devid=").append(URLEncoder.encode(id[0], "UTF-8"))
                .append("&devpassword=").append(URLEncoder.encode(id[1], "UTF-8"));
        if (!id[2].isEmpty()) {
            base.append("&ssid=").append(URLEncoder.encode(id[2], "UTF-8"))
                    .append("&sspassword=").append(URLEncoder.encode(id[3], "UTF-8"));
        }
        if (sys != null) base.append("&systemeid=").append(sys);
        String api = base.toString();
        org.json.JSONObject jeu = null;
        try {
            String url = String.format(api, "jeuInfos.php") + "&romtype=rom&romnom="
                    + URLEncoder.encode(g.fileName, "UTF-8") + (g.size > 0 ? "&romtaille=" + g.size : "");
            jeu = new org.json.JSONObject(new String(get(url), "UTF-8")).getJSONObject("response").getJSONObject("jeu");
        } catch (Exception e) {
            String title = TAG.matcher(g.fileBase).replaceAll(" ").replace('_', ' ').replaceAll("\\s+", " ").trim();
            String url = String.format(api, "jeuRecherche.php") + "&recherche=" + URLEncoder.encode(title, "UTF-8");
            org.json.JSONArray jeux = new org.json.JSONObject(new String(get(url), "UTF-8"))
                    .getJSONObject("response").optJSONArray("jeux");
            if (jeux != null && jeux.length() > 0 && jeux.getJSONObject(0).has("medias")) jeu = jeux.getJSONObject(0);
        }
        return jeu;
    }

    private boolean screenScraper(Game g) throws Exception {
        org.json.JSONObject jeu = ssGame(g);
        if (jeu == null) return false;
        org.json.JSONArray medias = jeu.optJSONArray("medias");
        String best = null;
        int bestRank = Integer.MAX_VALUE;
        for (int i = 0; medias != null && i < medias.length(); i++) {
            org.json.JSONObject m = medias.getJSONObject(i);
            if (!"box-2D".equals(m.optString("type"))) continue;
            int rank = SS_REGIONS.length;
            for (int r = 0; r < SS_REGIONS.length; r++) if (SS_REGIONS[r].equals(m.optString("region"))) rank = r;
            if (rank < bestRank) {
                bestRank = rank;
                best = m.optString("url");
            }
        }
        if (best == null || best.isEmpty()) return false;
        byte[] img = get(best + (best.contains("?") ? "&" : "?") + "maxwidth=640");
        boolean png = img.length > 8 && img[1] == 'P' && img[2] == 'N' && img[3] == 'G';
        boolean jpg = img.length > 3 && (img[0] & 0xFF) == 0xFF && (img[1] & 0xFF) == 0xD8;
        if (!png && !jpg) return false;
        File f = coverFile(g);
        f.getParentFile().mkdirs();
        FileOutputStream out = new FileOutputStream(f);
        out.write(img);
        out.close();
        return true;
    }


    // ------------------------------------------------------------------ illustrations de fond

    /**
     * Illustration de fond d'un jeu, à la façon de l'accueil PS5 (hors du fil principal) : le fanart
     * ScreenScraper, si un compte est renseigné. Rend null si rien n'est trouvé.
     */
    byte[] artwork(Game g) {
        // Seulement de vraies illustrations (fanart) : ni écrans-titres ni captures en 4:3, qui gênent la
        // lecture. Les images trop étroites sont encore écartées par Backdrop.
        if (!screenScraperReady()) return null;
        try {
            return ssMedia(g, new String[] {"fanart"}, 1600);
        } catch (Exception e) {
            return null;
        }
    }

    /** Premier média ScreenScraper trouvé parmi les types donnés, dans l'ordre, meilleure région d'abord. */
    private byte[] ssMedia(Game g, String[] types, int maxWidth) throws Exception {
        org.json.JSONObject jeu = ssGame(g);
        if (jeu == null) return null;
        org.json.JSONArray medias = jeu.optJSONArray("medias");
        for (String type : types) {
            String best = null;
            int bestRank = Integer.MAX_VALUE;
            for (int i = 0; medias != null && i < medias.length(); i++) {
                org.json.JSONObject m = medias.getJSONObject(i);
                if (!type.equals(m.optString("type"))) continue;
                int rank = SS_REGIONS.length;
                for (int r = 0; r < SS_REGIONS.length; r++) if (SS_REGIONS[r].equals(m.optString("region"))) rank = r;
                if (rank < bestRank) {
                    bestRank = rank;
                    best = m.optString("url");
                }
            }
            if (best == null || best.isEmpty()) continue;
            byte[] img = get(best + (best.contains("?") ? "&" : "?") + "maxwidth=" + maxWidth);
            boolean png = img.length > 8 && img[1] == 'P' && img[2] == 'N' && img[3] == 'G';
            boolean jpg = img.length > 3 && (img[0] & 0xFF) == 0xFF && (img[1] & 0xFF) == 0xD8;
            if (png || jpg) return img;
        }
        return null;
    }

    static String normalize(String s) {
        String t = TAG.matcher(s).replaceAll(" ");
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        t = t.toLowerCase(Locale.ROOT).replace("&", " and ").replace('_', ' ');
        return t.replaceAll("[^a-z0-9]+", "");
    }

    private static List<String> tags(String s) {
        List<String> out = new ArrayList<String>();
        Matcher m = TAG.matcher(s);
        while (m.find()) {
            for (String part : m.group(1).split(",")) {
                out.add(part.trim().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    private static String encodePath(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
    }

    private static byte[] get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "Origami/1.0 (Android)");
        try {
            if (c.getResponseCode() != 200) throw new java.io.IOException("HTTP " + c.getResponseCode());
            return readAll(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toByteArray();
    }
}
