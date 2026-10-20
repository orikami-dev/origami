package com.hideo.foldplay;

import android.app.Activity;
import android.content.Context;
import android.text.Spanned;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Langues de l'app. Les textes sont écrits en français dans le code ; chaque langue est un
 * dictionnaire assets/i18n/<code>.json (français exact → traduction). Plutôt que de toucher chaque
 * appel, la traduction passe sur les écrans : avant chaque image, les textes encore en français
 * sont remplacés, en entier si la phrase est connue, sinon morceau par morceau (les phrases
 * collées autour d'un nom de jeu ou d'un nombre).
 */
final class I18n {
    /** Langues proposées : code, nom dans sa langue. */
    static final String[][] LANGS = {
        {"fr", "Français"}, {"en", "English"}, {"es", "Español"}, {"de", "Deutsch"}, {"it", "Italiano"},
        {"pt", "Português (Brasil)"}, {"ru", "Русский"}, {"ja", "日本語"}, {"ko", "한국어"}, {"zh", "简体中文"},
    };

    private static String lang = "fr";
    private static Map<String, String> dict = Collections.emptyMap();
    private static List<String> pieces = Collections.emptyList();
    private static final Map<String, String> memo = new LinkedHashMap<String, String>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> e) {
            return size() > 600;
        }
    };
    private static final Set<String> outputs = new HashSet<String>();

    private I18n() {
    }

    static String lang() {
        return lang;
    }

    /** La langue du téléphone si l'app la connaît, sinon l'anglais. */
    static String systemLang() {
        String l = Locale.getDefault().getLanguage();
        for (String[] s : LANGS) if (s[0].equals(l)) return l;
        return "en";
    }

    static String nativeName(String code) {
        for (String[] s : LANGS) if (s[0].equals(code)) return s[1];
        return code;
    }

    /** À l'ouverture de l'app et après un changement de langue. */
    static synchronized void init(Context ctx) {
        String choice = new Store(ctx).language();
        String l = choice.equals("system") ? systemLang() : choice;
        if (l.equals(lang) && (l.equals("fr") || !dict.isEmpty())) return;
        lang = l;
        memo.clear();
        outputs.clear();
        dict = Collections.emptyMap();
        pieces = Collections.emptyList();
        if (l.equals("fr")) return;
        try {
            InputStream in = ctx.getAssets().open("i18n/" + l + ".json");
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            JSONObject o = new JSONObject(bo.toString("UTF-8"));
            Map<String, String> d = new HashMap<String, String>();
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                String v = o.optString(k);
                if (!v.isEmpty() && !v.equals(k)) d.put(k, v);
            }
            dict = d;
            // Morceaux pour les phrases collées : les plus longs d'abord, assez longs pour ne pas
            // toucher par hasard au nom d'un jeu.
            List<String> p = new ArrayList<String>();
            for (String k : d.keySet()) if (k.trim().length() >= 4) p.add(k);
            Collections.sort(p, new Comparator<String>() {
                public int compare(String a, String b) { return b.length() - a.length(); }
            });
            pieces = p;
        } catch (Exception e) {
            dict = Collections.emptyMap();
        }
    }

    /** Un texte dans la langue choisie (tel quel en français ou s'il est inconnu). */
    static String tr(CharSequence cs) {
        if (cs == null) return null;
        String s = cs.toString();
        if (dict.isEmpty() || s.isEmpty()) return s;
        String hit = dict.get(s);
        if (hit != null) return hit;
        synchronized (memo) {
            String m = memo.get(s);
            if (m != null) return m;
        }
        String out = s;
        // Lettres françaises seulement : un nombre, une heure ou un titre déjà traduit passent vite.
        if (!outputs.contains(s)) {
            for (String k : pieces) {
                if (out.indexOf(k) >= 0) out = out.replace(k, dict.get(k));
            }
        }
        synchronized (memo) {
            memo.put(s, out);
        }
        return out;
    }

    /** Nom affiché du choix courant : « Langue système (English) » ou le nom de la langue. */
    static String choiceName(Context ctx) {
        String c = new Store(ctx).language();
        return c.equals("system") ? "Langue système (" + nativeName(systemLang()) + ")" : nativeName(c);
    }

    /** Menu codec des langues ; après un changement, init est fait et « changed » est lancé. */
    static void pick(final Activity a, final Runnable changed) {
        final Store store = new Store(a);
        final String cur = store.language();
        CodecMenu m = new CodecMenu(a).title("Langue", "Language");
        m.radio("Langue système (" + nativeName(systemLang()) + ")", null, cur.equals("system"), choose(a, store, "system", changed));
        for (String[] l : LANGS) m.radio(l[1], null, cur.equals(l[0]), choose(a, store, l[0], changed));
        m.show(CodecMenu.hostFor(a));
    }

    private static Runnable choose(final Activity a, final Store store, final String code, final Runnable changed) {
        return new Runnable() {
            public void run() {
                String before = lang;
                store.setLanguage(code);
                init(a);
                if (!lang.equals(before) && changed != null) changed.run();
            }
        };
    }

    private static android.widget.Toast lastToast;

    /** Affiche tout de suite un message court en annulant le précédent (pas de file d'attente). */
    static void say(Context c, CharSequence text) {
        if (lastToast != null) lastToast.cancel();
        lastToast = android.widget.Toast.makeText(c.getApplicationContext(), tr(text), android.widget.Toast.LENGTH_SHORT);
        lastToast.show();
    }

    static android.widget.Toast toast(Context c, CharSequence text, int duration) {
        return android.widget.Toast.makeText(c, tr(text), duration);
    }

    /** Traduit l'écran de cette activité avant chaque image (rien à faire en français). */
    static void attach(final Activity a) {
        init(a);
        if (lang.equals("fr")) return;
        final View decor = a.getWindow().getDecorView();
        decor.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            public boolean onPreDraw() {
                walk(decor);
                return true;
            }
        });
    }

    private static void walk(View v) {
        if (v instanceof TextView && !(v instanceof EditText)) {
            TextView t = (TextView) v;
            CharSequence cs = t.getText();
            if (cs.length() > 0 && !(cs instanceof Spanned && ((Spanned) cs).getSpans(0, cs.length(), Object.class).length > 0)) {
                String s = cs.toString();
                if (outputs.size() > 4000) outputs.clear();
                if (!outputs.contains(s)) {
                    String out = tr(s);
                    if (!out.equals(s)) {
                        outputs.add(out);
                        t.setText(out);
                    } else {
                        outputs.add(s);
                    }
                }
            }
            CharSequence hint = t.getHint();
            if (hint != null && hint.length() > 0) {
                String h = tr(hint);
                if (!h.contentEquals(hint)) t.setHint(h);
            }
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) walk(g.getChildAt(i));
        }
    }
}
