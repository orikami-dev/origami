package com.hideo.foldplay;

import android.content.Context;

import org.json.JSONObject;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Trois profils d'émulation par cœur, choisis par jeu ou par défaut : Performance (définition
 * native, effets coupés), Équilibré et Qualité (rendu poussé, sans aller jusqu'à faire chauffer la
 * puce). Les valeurs viennent de assets/cores/install.json, section « profiles ».
 */
final class CoreProfiles {
    static final String[] IDS = {"performance", "balanced", "quality"};
    static final String[] NAMES = {"Performance", "Équilibré", "Qualité"};
    static final String[] HELP = {
        "Définition native et effets coûteux coupés : la fluidité et l'autonomie d'abord.",
        "Le bon compromis : définition relevée et filtrage léger, le jeu reste fluide.",
        "Rendu poussé (définition, filtrage, couleurs) mais plafonné pour ne pas faire chauffer "
                + "le téléphone sur les jeux 3D gourmands.",
    };

    private static JSONObject all;

    private CoreProfiles() {
    }

    static int index(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return 1;
    }

    static String name(String id) {
        return NAMES[index(id)];
    }

    private static synchronized JSONObject load(Context ctx) {
        if (all != null) return all;
        try {
            InputStream in = ctx.getAssets().open("cores/install.json");
            byte[] buf = new byte[in.available()];
            int n = 0;
            while (n < buf.length) {
                int r = in.read(buf, n, buf.length - n);
                if (r < 0) break;
                n += r;
            }
            in.close();
            all = new JSONObject(new String(buf, 0, n, "UTF-8")).optJSONObject("profiles");
        } catch (Exception e) {
            all = null;
        }
        if (all == null) all = new JSONObject();
        return all;
    }

    /** Vrai si les profils changent quelque chose pour ce cœur (les cœurs 2D tournent déjà au mieux). */
    static boolean matters(Context ctx, String core) {
        return !keys(ctx, core).isEmpty();
    }

    static Map<String, String> values(Context ctx, String core, String id) {
        Map<String, String> out = new HashMap<String, String>();
        JSONObject c = load(ctx).optJSONObject(core);
        JSONObject p = c == null ? null : c.optJSONObject(id);
        if (p == null) return out;
        Iterator<String> it = p.keys();
        while (it.hasNext()) {
            String k = it.next();
            out.put(k, p.optString(k));
        }
        return out;
    }

    /** Toutes les clés touchées par l'un des profils de ce cœur. */
    static Set<String> keys(Context ctx, String core) {
        Set<String> out = new LinkedHashSet<String>();
        for (String id : IDS) out.addAll(values(ctx, core, id).keySet());
        return out;
    }

    /**
     * Ce que le profil met dans chaque clé, en valeurs proposées par le cœur : la valeur telle
     * quelle, sinon la première qui commence par elle ; en Performance, la valeur d'origine.
     * vars = triplets clé, « Description; v1|v2|… », valeur (Libretro.nativeVariables).
     */
    static Map<String, String> resolve(Context ctx, String core, String id, String[] vars) {
        Map<String, String> want = values(ctx, core, id);
        Map<String, String> out = new HashMap<String, String>();
        for (String key : keys(ctx, core)) {
            String[] choices = choices(vars, key);
            if (choices == null || choices.length == 0) continue;
            String w = want.get(key);
            if (w == null) {
                // Performance : retour à la valeur d'origine ; ailleurs, la clé n'est pas touchée.
                if (id.equals("performance")) out.put(key, choices[0]);
                continue;
            }
            String hit = null;
            for (String c : choices) if (c.equalsIgnoreCase(w)) hit = c;
            if (hit == null) for (String c : choices) if (c.toLowerCase().startsWith(w.toLowerCase())) { hit = c; break; }
            if (hit != null) out.put(key, hit);
        }
        return out;
    }

    private static String[] choices(String[] vars, String key) {
        if (vars == null) return null;
        for (int i = 0; i + 2 < vars.length; i += 3) {
            if (!vars[i].equals(key)) continue;
            String spec = vars[i + 1];
            int semi = spec.indexOf("; ");
            return (semi >= 0 ? spec.substring(semi + 2) : spec).split("\\|");
        }
        return null;
    }
}
