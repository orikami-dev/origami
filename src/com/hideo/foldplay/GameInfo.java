package com.hideo.foldplay;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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
 * Fiche d'un jeu (résumé, année de sortie) tirée de Wikipédia, en français d'abord, puis en anglais.
 * L'année vient de Wikidata (date de publication). Les fiches sont gardées sur l'appareil ;
 * une recherche sans résultat n'est retentée qu'au bout d'une semaine.
 */
final class GameInfo {
    static final class Sheet {
        String title;
        String summary;
        int year;
        String source;
        boolean found;
        /** Vrai si le texte vient de la section synopsis, faux si c'est l'introduction de l'article. */
        boolean synopsis;
    }

    interface Callback {
        void onSheet(Game g, Sheet s);
    }

    private static final long RETRY_MS = 7L * 24 * 3600 * 1000;
    private static final String UA = "Origami/1.0 (lanceur retro Android personnel)";
    private static final Pattern YEAR = Pattern.compile("\\b(19[7-9]\\d|20[0-3]\\d)\\b");

    private final File dir;
    private final Store store;
    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Sheet> memory = new HashMap<String, Sheet>();

    GameInfo(Context ctx) {
        dir = new File(ctx.getFilesDir(), "info3");
        store = new Store(ctx);
    }

    /** La fiche déjà connue (mémoire ou disque), ou null. Ne touche pas au réseau. */
    Sheet cached(Game g) {
        String k = key(g);
        Sheet s = memory.get(k);
        if (s != null) return s;
        s = readDisk(k, false);
        if (s != null) memory.put(k, s);
        return s;
    }

    /** Cherche la fiche en arrière-plan si besoin ; le rappel arrive sur le fil principal. */
    void fetch(final Game g, final boolean network, final Callback cb) {
        final String k = key(g);
        Sheet hit = memory.get(k);
        if (hit != null) {
            cb.onSheet(g, hit);
            return;
        }
        pool.execute(new Runnable() {
            public void run() {
                Sheet s = readDisk(k, true);
                if (s == null && network) {
                    s = lookup(g);
                    writeDisk(k, s);
                }
                if (s == null) return;
                final Sheet done = s;
                main.post(new Runnable() {
                    public void run() {
                        memory.put(k, done);
                        cb.onSheet(g, done);
                    }
                });
            }
        });
    }

    // ------------------------------------------------------------------ recherche

    private Sheet lookup(Game g) {
        Sheet s = new Sheet();
        // Synopsis officiel (texte de jaquette) chez ScreenScraper quand un compte est saisi.
        try {
            if (screenScraper(g, s)) {
                s.found = true;
                return s;
            }
        } catch (Exception ignored) {
            // On se rabat sur Wikipédia.
        }
        String[][] langs = {{"fr", "jeu vidéo"}, {"en", "video game"}};
        for (String[] lang : langs) {
            try {
                if (lookup(g, lang[0], lang[1], s)) {
                    s.found = true;
                    return s;
                }
            } catch (Exception ignored) {
                // Réseau ou réponse inattendue : on tente la langue suivante.
            }
        }
        return s;
    }

    private boolean lookup(Game g, String lang, String hint, Sheet s) throws Exception {
        String q = g.title + " " + hint + " " + g.system.name;
        JSONObject res = json("https://" + lang + ".wikipedia.org/w/api.php?action=query&list=search&format=json"
                + "&srlimit=6&srprop=&srsearch=" + enc(q));
        JSONArray hits = res.getJSONObject("query").getJSONArray("search");
        // Le titre exact passe devant (« Final Fantasy X » avant « Final Fantasy X-2 »).
        List<String> pages = new ArrayList<String>();
        for (int i = 0; i < hits.length(); i++) {
            String page = hits.getJSONObject(i).getString("title");
            if (!matches(g.title, page)) continue;
            if (norm(page).equals(norm(g.title))) pages.add(0, page);
            else pages.add(page);
        }
        for (String page : pages) {
            JSONObject sum = json("https://" + lang + ".wikipedia.org/api/rest_v1/page/summary/"
                    + enc(page.replace(' ', '_')));
            String desc = sum.optString("description", "");
            String extract = sum.optString("extract", "");
            String about = (desc + " " + extract).toLowerCase(Locale.ROOT);
            // On ne garde qu'une page qui parle d'un jeu, pas d'un film ou d'une série homonyme.
            if (!about.contains(lang.equals("fr") ? "jeu" : "game")) continue;
            s.title = sum.optString("title", page);
            // Le synopsis (section « Synopsis », « Scénario », « Plot »…) passe avant l'introduction.
            String plot = synopsis(lang, page);
            s.synopsis = plot != null;
            s.summary = lead(plot != null ? plot : extract, 2, 320);
            s.source = "Wikipédia" + (lang.equals("fr") ? "" : " (en anglais)");
            String qid = sum.optString("wikibase_item", null);
            if (qid != null) s.year = wikidataYear(qid);
            if (s.year == 0) {
                Matcher m = YEAR.matcher(desc + " " + extract);
                if (m.find()) s.year = Integer.parseInt(m.group(1));
            }
            return true;
        }
        return false;
    }

    /** Identifiants ScreenScraper des consoles (systemeid). */
    /** Identifiant ScreenScraper de la console, ou null. */
    static String ssSystem(String id) {
        for (String[] e : SS_SYSTEMS) if (e[0].equals(id)) return e[1];
        return null;
    }

    private static final String[][] SS_SYSTEMS = {
        {"nes", "3"}, {"snes", "4"}, {"n64", "14"}, {"gc", "13"}, {"wii", "16"}, {"switch", "225"},
        {"gb", "9"}, {"gbc", "10"}, {"gba", "12"}, {"nds", "15"}, {"3ds", "17"}, {"psx", "57"},
        {"ps2", "58"}, {"psp", "61"}, {"vita", "62"}, {"md", "1"}, {"sms", "2"}, {"gg", "21"},
        {"saturn", "22"}, {"dc", "23"}, {"pce", "31"}, {"arcade", "75"},
        {"ps3", "59"}, {"x360", "33"},
    };

    private boolean screenScraper(Game g, Sheet s) throws Exception {
        String[] id = store.screenScraper();
        if (id[0].isEmpty() || id[1].isEmpty()) return false;
        String sys = null;
        for (String[] e : SS_SYSTEMS) if (e[0].equals(g.system.id)) sys = e[1];
        StringBuilder url = new StringBuilder("https://api.screenscraper.fr/api2/jeuInfos.php?output=json&softname=FOLDPLAY")
                .append("&devid=").append(enc(id[0])).append("&devpassword=").append(enc(id[1]))
                .append("&romtype=rom&romnom=").append(enc(g.fileName));
        if (!id[2].isEmpty()) url.append("&ssid=").append(enc(id[2])).append("&sspassword=").append(enc(id[3]));
        if (sys != null) url.append("&systemeid=").append(sys);
        if (g.size > 0) url.append("&romtaille=").append(g.size);
        JSONObject jeu = json(url.toString()).getJSONObject("response").getJSONObject("jeu");
        String fr = null, en = null;
        JSONArray syn = jeu.optJSONArray("synopsis");
        for (int i = 0; syn != null && i < syn.length(); i++) {
            JSONObject o = syn.getJSONObject(i);
            if ("fr".equals(o.optString("langue"))) fr = o.optString("text");
            else if ("en".equals(o.optString("langue"))) en = o.optString("text");
        }
        String text = fr != null ? fr : en;
        if (text == null || text.trim().isEmpty()) return false;
        s.summary = shorten(text.replaceAll("\\s+", " "), 600);
        s.synopsis = true;
        s.source = "ScreenScraper" + (fr != null ? "" : " (en anglais)");
        JSONArray names = jeu.optJSONArray("noms");
        for (int i = 0; names != null && i < names.length(); i++) {
            JSONObject o = names.getJSONObject(i);
            if (s.title == null || "eu".equals(o.optString("region")) || "fr".equals(o.optString("region"))) {
                s.title = o.optString("text", s.title);
            }
        }
        JSONArray dates = jeu.optJSONArray("dates");
        for (int i = 0; dates != null && i < dates.length(); i++) {
            Matcher m = YEAR.matcher(dates.getJSONObject(i).optString("text"));
            if (m.find()) {
                int y = Integer.parseInt(m.group(1));
                if (s.year == 0 || y < s.year) s.year = y;
            }
        }
        return true;
    }

    private static final String[] PLOT_FR = {"synopsis", "scenario", "trame", "intrigue", "histoire", "resume"};
    private static final String[] PLOT_EN = {"plot", "synopsis", "story", "premise", "storyline"};

    /** Texte de la section synopsis de l'article, ou null s'il n'en a pas. */
    private static String synopsis(String lang, String page) {
        try {
            JSONObject pages = json("https://" + lang + ".wikipedia.org/w/api.php?action=query&prop=extracts"
                    + "&explaintext=1&exsectionformat=wiki&redirects=1&format=json&titles=" + enc(page))
                    .getJSONObject("query").getJSONObject("pages");
            java.util.Iterator<String> it = pages.keys();
            if (!it.hasNext()) return null;
            return section(pages.getJSONObject(it.next()).optString("extract", ""),
                    lang.equals("fr") ? PLOT_FR : PLOT_EN);
        } catch (Exception e) {
            return null;
        }
    }

    /** Extrait la première section de niveau 2 dont le titre commence par l'un des mots donnés. */
    static String section(String text, String[] names) {
        String[] lines = text.split("\n");
        StringBuilder out = null;
        for (String line : lines) {
            String t = line.trim();
            boolean h2 = t.startsWith("== ") && !t.startsWith("=== ") && t.endsWith(" ==");
            if (h2) {
                if (out != null) break;
                String title = norm(t.replace("=", ""));
                for (String n : names) {
                    if (title.startsWith(n)) {
                        out = new StringBuilder();
                        break;
                    }
                }
                continue;
            }
            if (out == null || t.isEmpty() || t.startsWith("=")) continue;
            if (out.length() > 0) out.append('\n');
            out.append(t);
        }
        if (out == null) return null;
        String r = out.toString().trim();
        return r.length() < 60 ? null : r;
    }

    /** Année de sortie la plus ancienne (propriété P577, date de publication). */
    private static int wikidataYear(String qid) {
        try {
            JSONObject e = json("https://www.wikidata.org/wiki/Special:EntityData/" + qid + ".json")
                    .getJSONObject("entities").getJSONObject(qid);
            JSONArray dates = e.getJSONObject("claims").getJSONArray("P577");
            int best = 0;
            for (int i = 0; i < dates.length(); i++) {
                String t = dates.getJSONObject(i).getJSONObject("mainsnak").getJSONObject("datavalue")
                        .getJSONObject("value").getString("time");
                int y = Integer.parseInt(t.substring(1, 5));
                if (best == 0 || y < best) best = y;
            }
            return best;
        } catch (Exception e) {
            return 0;
        }
    }

    /** La page doit reprendre l'essentiel des mots du titre (au moins 60 %). */
    static boolean matches(String title, String page) {
        String p = " " + norm(page) + " ";
        List<String> words = new ArrayList<String>();
        for (String w : norm(title).split(" ")) {
            if (w.length() < 2 || STOP.contains(" " + w + " ")) continue;
            words.add(w);
        }
        if (words.isEmpty()) return false;
        int hit = 0;
        for (String w : words) if (p.contains(" " + w + " ")) hit++;
        return hit * 10 >= words.size() * 6 && p.contains(" " + words.get(0) + " ");
    }

    private static final String STOP = " the of and de la le les du des et version edition a an ";

    static String norm(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    /** Les premières phrases seulement : une accroche, pas tout le scénario. */
    static String lead(String s, int sentences, int max) {
        s = s.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        int end = 0;
        for (int n = 0; n < sentences; n++) {
            int dot = s.indexOf(". ", end);
            if (dot < 0 || dot + 1 > max) break;
            end = dot + 1;
        }
        if (end == 0) return shorten(s, max);
        return s.substring(0, end);
    }

    /** Coupe au dernier point avant la limite, pour ne pas finir au milieu d'une phrase. */
    static String shorten(String s, int max) {
        s = s.trim();
        if (s.length() <= max) return s;
        int cut = s.lastIndexOf(". ", max);
        return cut > max / 3 ? s.substring(0, cut + 1) : s.substring(0, max).trim() + "…";
    }

    private static JSONObject json(String url) throws IOException, org.json.JSONException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept", "application/json");
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            return new JSONObject(new String(readAll(c.getInputStream()), "UTF-8"));
        } finally {
            c.disconnect();
        }
    }

    private static String enc(String s) throws IOException {
        return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
    }

    // ------------------------------------------------------------------ cache disque

    private String key(Game g) {
        // Une fiche Wikipédia est recherchée à nouveau dès qu'un compte ScreenScraper est saisi.
        String src = store.screenScraper()[0].isEmpty() ? "w" : "ss";
        return src + "-" + g.system.id + "-" + Integer.toHexString(g.baseName.hashCode());
    }

    private Sheet readDisk(String k, boolean allowStale) {
        File f = new File(dir, k + ".json");
        if (!f.isFile()) return null;
        try {
            InputStream in = new FileInputStream(f);
            JSONObject o;
            try {
                o = new JSONObject(new String(readAll(in), "UTF-8"));
            } finally {
                in.close();
            }
            Sheet s = new Sheet();
            s.found = o.optBoolean("found");
            // Pas trouvé il y a plus d'une semaine : on retentera.
            if (!s.found && System.currentTimeMillis() - f.lastModified() > RETRY_MS) return null;
            s.title = o.optString("title", null);
            s.summary = o.optString("summary", null);
            s.year = o.optInt("year");
            s.source = o.optString("source", null);
            s.synopsis = o.optBoolean("synopsis");
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    private void writeDisk(String k, Sheet s) {
        if (s == null) return;
        dir.mkdirs();
        try {
            JSONObject o = new JSONObject();
            o.put("found", s.found);
            if (s.title != null) o.put("title", s.title);
            if (s.summary != null) o.put("summary", s.summary);
            o.put("year", s.year);
            if (s.source != null) o.put("source", s.source);
            o.put("synopsis", s.synopsis);
            FileOutputStream out = new FileOutputStream(new File(dir, k + ".json"));
            try {
                out.write(o.toString().getBytes("UTF-8"));
            } finally {
                out.close();
            }
        } catch (Exception ignored) {
            // Cache facultatif.
        }
    }

    void clear() {
        memory.clear();
        File[] all = dir.listFiles();
        if (all != null) for (File f : all) f.delete();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
