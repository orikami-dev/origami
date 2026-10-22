package com.hideo.foldplay;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Préférences : dossier de jeux, favoris, récents, émulateur choisi par console. */
final class Store {
    private static final int MAX_RECENTS = 20;

    private final SharedPreferences prefs;

    Store(Context ctx) {
        prefs = ctx.getSharedPreferences("foldplay", Context.MODE_PRIVATE);
    }

    /** Assistant de démarrage terminé (ou ancienne installation qui avait déjà un dossier). */
    boolean setupDone() {
        return prefs.getBoolean("setup_done", treeUri() != null);
    }

    void setSetupDone(boolean on) {
        prefs.edit().putBoolean("setup_done", on).apply();
    }

    /** Étape de l'assistant où l'on s'est arrêté : il y reprend après un passage dans une autre app. */
    int setupStep() {
        return prefs.getInt("setup_step", 0);
    }

    void setSetupStep(int step) {
        prefs.edit().putInt("setup_step", step).apply();
    }

    String treeUri() {
        return prefs.getString("tree", null);
    }

    void setTreeUri(String uri) {
        prefs.edit().putString("tree", uri).apply();
    }

    /** Vue Flex (Tsuru) partout, ou Washi (par défaut depuis la 0.66.4) selon l'écran. */
    boolean flexView() {
        return prefs.getBoolean("flex_view", false);
    }

    void setFlexView(boolean on) {
        prefs.edit().putBoolean("flex_view", on).apply();
    }

    /** Dispositions de manette enregistrées (vide = seulement la disposition classique). */
    java.util.List<ControlLayout> layouts() {
        java.util.List<ControlLayout> out = new ArrayList<ControlLayout>();
        try {
            org.json.JSONArray arr = new org.json.JSONArray(prefs.getString("layouts", "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(ControlLayout.fromJson(arr.getJSONObject(i)));
        } catch (org.json.JSONException ignored) {
            // Données abîmées : on repart des dispositions par défaut.
        }
        // 0.42.5 : manettes redessinées (PadDesign), la 2D perd L1 et R1 ; on refait la migration
        // pour que les dispositions d'origine jamais retouchées prennent le nouveau dessin.
        if (!prefs.getBoolean("layouts_v0425", false)) {
            migrateLayouts(out);
            saveLayouts(out);
            prefs.edit().putBoolean("layouts_v042", true).putBoolean("layouts_v0425", true).apply();
        }
        if (out.isEmpty()) out.addAll(ControlLayout.defaults());
        if (!prefs.getBoolean("layouts_v0603", false)) {
            migrateFlex(out);
            saveLayouts(out);
            prefs.edit().putBoolean("layouts_v0603", true).apply();
        }
        if (!prefs.getBoolean("layouts_v0610", false)) {
            // 0.61 : touche FORMAT ajoutée à toutes les manettes.
            for (ControlLayout l : out) l.ensureAspect();
            saveLayouts(out);
            prefs.edit().putBoolean("layouts_v0610", true).apply();
        }
        if (!prefs.getBoolean("layouts_v06621", false)) {
            // 0.66.21 : touches CAPTURE et VIDÉO ajoutées à toutes les manettes.
            for (ControlLayout l : out) l.ensureCaptures();
            saveLayouts(out);
            prefs.edit().putBoolean("layouts_v06621", true).apply();
        }
        if (!prefs.getBoolean("layouts_homebig", false)) {
            // Maison agrandie de 35 % sur les manettes retouchées (les fournies sont placées par PadDesign).
            for (ControlLayout l : out) {
                if (l.stock) continue;
                for (ControlLayout.Control c : l.controls) {
                    if (!c.type.equals("HOME")) continue;
                    int nw = Math.round(c.w * 1.35f), nh = Math.round(c.h * 1.35f);
                    if (l.refCols > 0) c.x = Math.max(0f, c.x - (nw - c.w) / 2f / l.refCols);
                    if (l.refRows > 0) c.y = Math.max(0f, c.y - (nh - c.h) / 2f / l.refRows);
                    c.w = nw;
                    c.h = nh;
                }
            }
            saveLayouts(out);
            prefs.edit().putBoolean("layouts_homebig", true).apply();
        }
        return out;
    }

    /**
     * 0.60.3 : la Flex divisée laisse la place à la Flex par console (ConsolePad), en tête de liste ;
     * ordre Flex, 2D, 3D, les tiennes, puis Mini (en construction). La Flex devient la manette active,
     * Mini ne l'est plus. Les retouches d'écran faites sur l'ancienne Flex sont oubliées.
     */
    private void migrateFlex(java.util.List<ControlLayout> list) {
        ControlLayout flex = null, d2 = null, d3 = null, mini = null;
        java.util.List<ControlLayout> own = new ArrayList<ControlLayout>();
        for (ControlLayout l : list) {
            if (l.stock && l.kind.equals("flex") && flex == null) flex = l.forSystem(null);
            else if (l.stock && l.kind.equals("2d") && d2 == null) d2 = l;
            else if (l.stock && l.kind.equals("3d") && d3 == null) d3 = l;
            else if (l.stock && l.kind.equals("mini") && mini == null) mini = l;
            else own.add(l);
        }
        list.clear();
        list.add(flex != null ? flex : ControlLayout.flex());
        list.add(d2 != null ? d2 : ControlLayout.arcade());
        list.add(d3 != null ? d3 : ControlLayout.xbox());
        list.addAll(own);
        list.add(mini != null ? mini : ControlLayout.mini());
        for (ControlLayout l : list) if (l.stock) l.name = l.kind.equals("flex") ? "Flex" : l.name;
        setActiveLayout("Flex");
        android.content.SharedPreferences.Editor e = prefs.edit();
        for (String k : prefs.getAll().keySet()) {
            if (!k.startsWith("slayout_")) continue;
            String raw = prefs.getString(k, "");
            if (raw.contains("\"kind\":\"flex\"")) e.remove(k);
        }
        e.apply();
    }

    /**
     * 0.42 : quatre profils fournis, 2D (arcade), 3D (façon Xbox), Flex et Mini (flottante). Les dispositions d'origine
     * jamais retouchées sont redessinées (la « Classique » devient « 3D », mêmes couleurs) ; celles
     * que BOSS a modifiées restent telles quelles, et les profils manquants sont ajoutés.
     */
    private void migrateLayouts(java.util.List<ControlLayout> list) {
        String active = activeLayout();
        boolean has2d = false, has3d = false, hasFlex = false, hasMini = false;
        for (int i = 0; i < list.size(); i++) {
            ControlLayout l = list.get(i);
            if (!l.stock) continue;
            ControlLayout fresh = l.origin(0, 0);
            if (l.name.equals("Classique")) {
                if (active.equals("Classique")) active = fresh.name;
            } else {
                fresh.name = l.name;
            }
            fresh.deckColor = l.deckColor;
            fresh.buttonColor = l.buttonColor;
            fresh.accentColor = l.accentColor;
            fresh.deckAlpha = l.deckAlpha;
            fresh.buttonAlpha = l.buttonAlpha;
            fresh.background = l.background;
            fresh.bgX = l.bgX;
            fresh.bgY = l.bgY;
            fresh.bgScale = l.bgScale;
            fresh.skin = l.skin;
            list.set(i, fresh);
        }
        for (ControlLayout l : list) {
            has2d |= l.name.equals("2D");
            has3d |= l.name.equals("3D");
            hasFlex |= l.name.equals("Flex");
            hasMini |= l.name.equals("Mini");
        }
        if (!has2d) list.add(0, ControlLayout.arcade());
        if (!has3d) list.add(Math.min(1, list.size()), ControlLayout.xbox());
        if (!hasFlex) list.add(ControlLayout.flex());
        if (!hasMini) list.add(ControlLayout.mini());
        boolean found = false;
        for (ControlLayout l : list) found |= l.name.equals(active);
        setActiveLayout(found ? active : "3D");
    }

    void saveLayouts(java.util.List<ControlLayout> layouts) {
        org.json.JSONArray arr = new org.json.JSONArray();
        try {
            for (ControlLayout l : layouts) arr.put(l.toJson());
        } catch (org.json.JSONException ignored) {
            return;
        }
        prefs.edit().putString("layouts", arr.toString()).apply();
    }

    /** Manette Flex en surimpression : décalage vertical choisi en jeu (fraction de la hauteur). */
    float flexOffset() {
        return prefs.getFloat("flex_dy", 0f);
    }

    void setFlexOffset(float f) {
        prefs.edit().putFloat("flex_dy", f).apply();
    }

    /** Manette Mini : position (fractions de l'écran, coin haut-gauche) et côté en dp. */
    float[] miniBox() {
        return new float[] {prefs.getFloat("mini_x", -1f), prefs.getFloat("mini_y", -1f), prefs.getInt("mini_dp", 220)};
    }

    void setMiniBox(float x, float y, int sizeDp) {
        prefs.edit().putFloat("mini_x", x).putFloat("mini_y", y).putInt("mini_dp", sizeDp).apply();
    }

    String activeLayout() {
        String a = prefs.getString("layout_active", "Flex");
        // Mini est en construction (0.60.3) : on retombe sur la Flex.
        return a.equals("Mini") ? "Flex" : a;
    }

    void setActiveLayout(String name) {
        prefs.edit().putString("layout_active", name).apply();
    }

    /**
     * Disposition retouchée sur le jeu pour une zone précise (écran, orientation et mode donnent
     * chacun leur grille) : elle s'affiche telle quelle, sans réarrangement.
     */
    ControlLayout screenLayout(String name, int cols, int rows) {
        String raw = prefs.getString("slayout_" + cols + "x" + rows, null);
        if (raw == null) return null;
        try {
            ControlLayout l = ControlLayout.fromJson(new org.json.JSONObject(raw));
            return name.equals(l.name) && l.refCols == cols && l.refRows == rows ? l : null;
        } catch (org.json.JSONException e) {
            return null;
        }
    }

    void setScreenLayout(int cols, int rows, ControlLayout l) {
        String key = "slayout_" + cols + "x" + rows;
        if (l == null) {
            prefs.edit().remove(key).apply();
            return;
        }
        try {
            prefs.edit().putString(key, l.toJson().toString()).apply();
        } catch (org.json.JSONException ignored) {
            // Rien d'enregistré : la disposition commune reste affichée.
        }
    }

    /** La disposition active, ou la classique calée sur la zone donnée. */
    ControlLayout currentLayout(int cols, int rows) {
        String name = activeLayout();
        ControlLayout own = screenLayout(name, cols, rows);
        if (own != null) return own;
        for (ControlLayout l : layouts()) if (l.name.equals(name)) return l;
        return ControlLayout.classic(cols, rows);
    }

    // ------------------------------------------------------------------ moteur intégré

    /** Options avancées choisies pour un cœur libretro (clé → valeur). */
    java.util.Map<String, String> coreOptions(String core) {
        java.util.Map<String, String> out = new java.util.HashMap<String, String>();
        try {
            org.json.JSONObject o = new org.json.JSONObject(prefs.getString("opts_" + core, "{}"));
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                out.put(k, o.getString(k));
            }
        } catch (org.json.JSONException ignored) {
            // Options abîmées : valeurs par défaut du cœur.
        }
        return out;
    }

    void setCoreOption(String core, String key, String value) {
        java.util.Map<String, String> all = coreOptions(core);
        all.put(key, value);
        prefs.edit().putString("opts_" + core, new org.json.JSONObject(all).toString()).apply();
    }

    /** Oublie les choix de l'utilisateur sur ces clés (un profil vient de les reprendre). */
    void clearCoreOptions(String core, java.util.Set<String> keys) {
        java.util.Map<String, String> all = coreOptions(core);
        if (!all.keySet().removeAll(keys)) return;
        prefs.edit().putString("opts_" + core, new org.json.JSONObject(all).toString()).apply();
    }

    /** Identifiant stable d'un jeu pour ses préférences : son adresse, sinon son chemin. */
    static String gameId(String uri, String path) {
        return uri != null ? uri : String.valueOf(path);
    }

    /**
     * Profil d'émulation par défaut : performance pour les consoles récentes (SystemInfo.isModern),
     * balanced pour les anciennes. Ce réglage global ne s'applique qu'aux anciennes ; chaque
     * console récente démarre en performance (voir defaultProfile(system)).
     */
    String defaultProfile() {
        return CoreProfiles.normalize(prefs.getString("profile_default", "balanced"));
    }

    /** Profil par défaut d'une console : Performance dès la PS3 (décision de BOSS), sinon le réglage global. */
    String defaultProfile(String system) {
        return SystemInfo.isModern(system) ? "performance" : defaultProfile();
    }

    void setDefaultProfile(String id) {
        prefs.edit().putString("profile_default", id).apply();
    }

    /** Profil choisi pour un jeu (sinon celui par défaut) ; un ancien « quality » devient « balanced ». */
    String gameProfile(String game) {
        return CoreProfiles.normalize(prefs.getString("gprof_" + game, defaultProfile()));
    }

    /** Idem avec la console du jeu : sans choix propre, une console récente démarre en Performance. */
    String gameProfile(String game, String system) {
        return CoreProfiles.normalize(prefs.getString("gprof_" + game, defaultProfile(system)));
    }

    void setGameProfile(String game, String id) {
        prefs.edit().putString("gprof_" + game, id).apply();
    }

    /** Langue de l'app : « system » (celle du téléphone si l'app la connaît) ou un code (fr, en…). */
    String language() {
        return prefs.getString("lang", "system");
    }

    void setLanguage(String code) {
        prefs.edit().putString("lang", code).apply();
    }

    boolean languageChosen() {
        return prefs.contains("lang");
    }

    /** Rail des réglages et de l'éditeur déplié (noms visibles) ou replié (icônes seules). */
    boolean railOpen() {
        return prefs.getBoolean("rail_open", false);
    }

    void setRailOpen(boolean on) {
        prefs.edit().putBoolean("rail_open", on).apply();
    }

    String coreVariant(String core) {
        return prefs.getString("variant_" + core, core);
    }

    void setCoreVariant(String core, String variant) {
        prefs.edit().putString("variant_" + core, variant).apply();
    }

    /** Réglages posés par le script d'installation (appliqués avant ceux de l'utilisateur). */
    java.util.Map<String, String> coreProfile(String core) {
        java.util.Map<String, String> out = new java.util.HashMap<String, String>();
        try {
            org.json.JSONObject o = new org.json.JSONObject(prefs.getString("prof_" + core, "{}"));
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                out.put(k, o.getString(k));
            }
        } catch (org.json.JSONException ignored) {
            // Profil abîmé : aucun.
        }
        return out;
    }

    void setCoreProfile(String core, java.util.Map<String, String> profile) {
        prefs.edit().putString("prof_" + core, new org.json.JSONObject(profile).toString()).apply();
    }

    String coreInstallNote(String core) {
        return prefs.getString("inote_" + core, null);
    }

    void setCoreInstallNote(String core, String note) {
        prefs.edit().putString("inote_" + core, note).apply();
    }

    /** Liste des options d'un cœur (clé, spec, valeur) gardée au dernier lancement, pour les régler hors jeu. */
    String[] coreSpecs(String core) {
        try {
            org.json.JSONArray a = new org.json.JSONArray(prefs.getString("specs_" + core, "[]"));
            String[] out = new String[a.length()];
            for (int i = 0; i < out.length; i++) out[i] = a.getString(i);
            return out;
        } catch (org.json.JSONException e) {
            return new String[0];
        }
    }

    void setCoreSpecs(String core, String[] vars) {
        org.json.JSONArray a = new org.json.JSONArray();
        for (String v : vars) a.put(v == null ? "" : v);
        prefs.edit().putString("specs_" + core, a.toString()).apply();
    }

    void clearCoreOptions(String core) {
        prefs.edit().remove("opts_" + core).apply();
    }

    /** Format d'affichage par console : core, 4:3, 16:9, stretch, integer. */
    String displayMode(String system) {
        return prefs.getString("display_" + system, "core");
    }

    void setDisplayMode(String system, String mode) {
        prefs.edit().putString("display_" + system, mode).apply();
    }

    /** Lissage de l'image (sinon pixels nets). */
    boolean smooth(String system) {
        return prefs.getBoolean("smooth_" + system, true);
    }

    void setSmooth(String system, boolean on) {
        prefs.edit().putBoolean("smooth_" + system, on).apply();
    }

    /** Pack d'icônes : « auto » suit le thème, sinon trait, givre, braise, petale, phosphore, neon, bambou, lune. */
    String iconPack() {
        // Les icônes suivent le thème tant que le store de thèmes communautaires n'est pas ouvert.
        return "auto";
    }

    void setIconPack(String id) {
        prefs.edit().putString("icon_pack", id).apply();
    }

    /** À l'ouverture du pliant (écran externe vers écran interne), passer en mode Tsuru. */
    boolean tsuruOnOpen() {
        return prefs.getBoolean("tsuru_on_open", true);
    }

    void setTsuruOnOpen(boolean on) {
        prefs.edit().putBoolean("tsuru_on_open", on).apply();
    }

    boolean intro() {
        return prefs.getBoolean("intro", true);
    }

    void setIntro(boolean on) {
        prefs.edit().putBoolean("intro", on).apply();
    }

    /** Pastilles : coins arrondis autour du jeu, de la manette et de la barre du haut (BOSS, 20/10). */
    boolean pills() {
        return prefs.getBoolean("pills", true);
    }

    void setPills(boolean on) {
        prefs.edit().putBoolean("pills", on).apply();
    }

    /** Voile sombre sur le fond d'écran de l'accueil ; éteint par défaut. */
    boolean dimBackdrop() {
        return prefs.getBoolean("dim_backdrop", false);
    }

    void setDimBackdrop(boolean on) {
        prefs.edit().putBoolean("dim_backdrop", on).apply();
    }

    /** Plein écran (manette à l'écran masquée) dès qu'une manette physique est branchée. */
    boolean padFullscreen() {
        return prefs.getBoolean("pad_fullscreen", true);
    }

    void setPadFullscreen(boolean on) {
        prefs.edit().putBoolean("pad_fullscreen", on).apply();
    }

    /** Thème de l'interface (Theme.THEMES), « codec » par défaut. */
    String theme() {
        return prefs.getString("ui_theme", "codec");
    }

    /** Icône du lanceur à poser quand l'app passera en arrière-plan (null : rien en attente). */
    String pendingIcon() {
        return prefs.getString("pending_icon", null);
    }

    void setPendingIcon(String id) {
        prefs.edit().putString("pending_icon", id).apply();
    }

    void setTheme(String id) {
        prefs.edit().putString("ui_theme", id).apply();
    }

    /** Couleur du logo (la grue) : 0 = celle du thème. */
    int logoColor() {
        return prefs.getInt("logo_color", 0);
    }

    void setLogoColor(int c) {
        prefs.edit().putInt("logo_color", c).apply();
    }

    /**
     * Les retouches faites sur un écran précis gardent leurs places mais prennent le style
     * (couleurs, opacités, habillage) de la disposition commune du même nom.
     */
    void restyleScreenLayouts(ControlLayout src) {
        android.content.SharedPreferences.Editor e = prefs.edit();
        for (java.util.Map.Entry<String, ?> en : prefs.getAll().entrySet()) {
            if (!en.getKey().startsWith("slayout_") || !(en.getValue() instanceof String)) continue;
            try {
                ControlLayout l = ControlLayout.fromJson(new org.json.JSONObject((String) en.getValue()));
                if (!l.name.equals(src.name)) continue;
                l.copyStyle(src);
                e.putString(en.getKey(), l.toJson().toString());
            } catch (org.json.JSONException ignored) {
                // Retouche abîmée : laissée telle quelle.
            }
        }
        e.apply();
    }

    /** Plein écran (barres d'état et de navigation masquées) hors jeu : désactivé par défaut. */
    boolean appFullscreen() {
        return prefs.getBoolean("app_fullscreen", false);
    }

    void setAppFullscreen(boolean on) {
        prefs.edit().putBoolean("app_fullscreen", on).apply();
    }

    /** L'interface reprend les couleurs de la manette à l'écran. */
    boolean syncUi() {
        return prefs.getBoolean("sync_ui", false);
    }

    void setSyncUi(boolean on) {
        prefs.edit().putBoolean("sync_ui", on).apply();
    }

    boolean menuSound() {
        return prefs.getBoolean("menu_sound", true);
    }

    void setMenuSound(boolean on) {
        prefs.edit().putBoolean("menu_sound", on).apply();
    }

    /** Volume des bruits de l'interface, 0 à 100. */
    int uiVolume() {
        return prefs.getInt("ui_volume", 60);
    }

    void setUiVolume(int v) {
        prefs.edit().putInt("ui_volume", Math.max(0, Math.min(100, v))).apply();
    }

    /** Musique d'accueil en boucle (par thème). */
    boolean animatedBackdrop() {
        return prefs.getBoolean("animated_backdrop", true);
    }

    void setAnimatedBackdrop(boolean on) {
        prefs.edit().putBoolean("animated_backdrop", on).apply();
    }

    // ------------------------------------------------------------ Accessibilité (0.62.5)

    /** Main qui tient le téléphone : droitier par défaut (flèche retour et listes à droite). */
    boolean rightHanded() {
        return prefs.getBoolean("a11y_right", true);
    }

    void setRightHanded(boolean on) {
        prefs.edit().putBoolean("a11y_right", on).apply();
    }

    /** Taille des textes : 0 normale, 1 grande, 2 très grande. */
    int a11yText() {
        return prefs.getInt("a11y_text", 0);
    }

    void setA11yText(int v) {
        prefs.edit().putInt("a11y_text", v).apply();
    }

    boolean highContrast() {
        return prefs.getBoolean("a11y_contrast", false);
    }

    void setHighContrast(boolean on) {
        prefs.edit().putBoolean("a11y_contrast", on).apply();
    }

    boolean reduceMotion() {
        return prefs.getBoolean("a11y_motion", false);
    }

    void setReduceMotion(boolean on) {
        prefs.edit().putBoolean("a11y_motion", on).apply();
    }

    /** Gâchettes à bascule : un appui les enfonce, le suivant les relâche. */
    boolean latchTriggers() {
        return prefs.getBoolean("a11y_latch", false);
    }

    void setLatchTriggers(boolean on) {
        prefs.edit().putBoolean("a11y_latch", on).apply();
    }

    /** Joysticks : course réduite (0 normale, 1 courte, 2 très courte) pour les petits gestes. */
    int stickReach() {
        return prefs.getInt("a11y_reach", 0);
    }

    void setStickReach(int v) {
        prefs.edit().putInt("a11y_reach", v).apply();
    }

    /** Vibration plus longue et plus forte à chaque appui. */
    boolean strongHaptics() {
        return prefs.getBoolean("a11y_haptics", false);
    }

    void setStrongHaptics(boolean on) {
        prefs.edit().putBoolean("a11y_haptics", on).apply();
    }

    // ------------------------------------------------------------ Mises à jour (0.62.5)

    boolean autoUpdates() {
        return prefs.getBoolean("upd_auto", true);
    }

    void setAutoUpdates(boolean on) {
        prefs.edit().putBoolean("upd_auto", on).apply();
    }

    /** Préversions : builds de test (corrections rapides) en plus des versions stables. */
    boolean previewUpdates() {
        return prefs.getBoolean("upd_beta", true);
    }

    void setPreviewUpdates(boolean on) {
        prefs.edit().putBoolean("upd_beta", on).apply();
    }

    long lastUpdateCheck() {
        return prefs.getLong("upd_last", 0);
    }

    void setLastUpdateCheck(long t) {
        prefs.edit().putLong("upd_last", t).apply();
    }

    String notifiedUpdate() {
        return prefs.getString("upd_notified", "");
    }

    void setNotifiedUpdate(String v) {
        prefs.edit().putString("upd_notified", v).apply();
    }

    /** Version dont la fenêtre « Installer ultérieurement » a été choisie, et quand (redemande après 24 h). */
    String postponedUpdate() {
        return prefs.getString("upd_postponed", "");
    }

    long postponedUpdateAt() {
        return prefs.getLong("upd_postponed_at", 0);
    }

    void postponeUpdate(String version) {
        prefs.edit().putString("upd_postponed", version).putLong("upd_postponed_at", System.currentTimeMillis()).apply();
    }

    /** Dernière mise à jour trouvée {version, apk, page, notes}, gardée pour la fenêtre des reprises suivantes ; null si aucune. */
    String[] foundUpdate() {
        String v = prefs.getString("upd_found", "");
        if (v.isEmpty()) return null;
        return new String[] {v, prefs.getString("upd_found_apk", ""), prefs.getString("upd_found_page", ""),
                prefs.getString("upd_found_notes", "")};
    }

    void setFoundUpdate(String v, String apk, String page, String notes) {
        prefs.edit().putString("upd_found", v == null ? "" : v).putString("upd_found_apk", apk == null ? "" : apk)
                .putString("upd_found_page", page == null ? "" : page).putString("upd_found_notes", notes == null ? "" : notes).apply();
    }

    boolean setupAdvanced() {
        return prefs.getBoolean("setup_advanced", false);
    }

    void setSetupAdvanced(boolean on) {
        prefs.edit().putBoolean("setup_advanced", on).apply();
    }

    boolean musicOn() {
        return prefs.getBoolean("music_on", true);
    }

    void setMusicOn(boolean on) {
        prefs.edit().putBoolean("music_on", on).apply();
    }

    /** Volume de la musique d'accueil, 0 à 100. */
    int musicVolume() {
        return prefs.getInt("music_volume", 40);
    }

    void setMusicVolume(int v) {
        prefs.edit().putInt("music_volume", Math.max(0, Math.min(100, v))).apply();
    }

    boolean haptics() {
        return hapticLevel() > 0;
    }

    /** 0 coupée, 1 légère, 2 moyenne, 3 forte (l'ancien réglage oui/non donne 2 ou 0). */
    int hapticLevel() {
        return prefs.getInt("haptic_level", prefs.getBoolean("haptics", true) ? 2 : 0);
    }

    void setHapticLevel(int level) {
        prefs.edit().putInt("haptic_level", level).putBoolean("haptics", level > 0).apply();
    }

    void setHaptics(boolean on) {
        setHapticLevel(on ? 2 : 0);
    }

    /** Demande de rescan posée par la page Réglages, consommée par l'écran principal. */
    boolean takeRescanRequest() {
        boolean r = prefs.getBoolean("rescan", false);
        if (r) prefs.edit().remove("rescan").apply();
        return r;
    }

    void requestRescan() {
        prefs.edit().putBoolean("rescan", true).apply();
    }

    /** Fiches de jeux (résumé, année) récupérées sur Wikipédia. */
    boolean autoInfo() {
        return prefs.getBoolean("auto_info", true);
    }

    void setAutoInfo(boolean on) {
        prefs.edit().putBoolean("auto_info", on).apply();
    }

    /** Identifiants ScreenScraper : [devid, devpassword, ssid, sspassword], vides si non saisis. */
    String[] screenScraper() {
        return new String[] {prefs.getString("ss_devid", ""), prefs.getString("ss_devpw", ""),
                prefs.getString("ss_id", ""), prefs.getString("ss_pw", "")};
    }

    void setScreenScraper(String devId, String devPw, String id, String pw) {
        prefs.edit().putString("ss_devid", devId).putString("ss_devpw", devPw)
                .putString("ss_id", id).putString("ss_pw", pw).apply();
    }

    /** Gâchettes de la Flex : en arc autour des joysticks (par défaut) ou à leur place d'origine. */
    boolean ergoTriggers() {
        return prefs.getBoolean("ergo_triggers", true);
    }

    void setErgoTriggers(boolean on) {
        prefs.edit().putBoolean("ergo_triggers", on).apply();
    }

    /** Touche FORMAT verrouillée : plus de changement par un appui involontaire. */
    /** Andon (lumière d'ambiance autour du jeu) : off, theme, image, mix, soft. */
    String glowMode() {
        return prefs.getString("andon", "image");
    }

    void setGlowMode(String m) {
        prefs.edit().putString("andon", m).apply();
    }

    boolean formatLocked() {
        return prefs.getBoolean("format_locked", false);
    }

    void setFormatLocked(boolean on) {
        prefs.edit().putBoolean("format_locked", on).apply();
    }

    /** ATH de monitoring en jeu : images/s, vitesse, processeur, mémoire, batterie, chauffe. */
    boolean hud() {
        return prefs.getBoolean("hud", false);
    }

    void setHud(boolean on) {
        prefs.edit().putBoolean("hud", on).apply();
    }

    /** Style de l'ATH : "line" (une ligne en haut, façon RTSS, par défaut) ou "block" (encadré sur plusieurs lignes). */
    /** Tampon du son PS3 en ms (4 à 250) : plus court = moins de retard sur l'image, plus long = moins de coupures. */
    int ps3AudioMs() {
        return prefs.getInt("ps3_audio_ms", 250);
    }

    void setPs3AudioMs(int ms) {
        prefs.edit().putInt("ps3_audio_ms", ms).apply();
    }

    /** Étirement du temps : le son ralentit avec le jeu au lieu de couper. */
    boolean ps3Stretch() {
        return prefs.getBoolean("ps3_stretch", false);
    }

    void setPs3Stretch(boolean on) {
        prefs.edit().putBoolean("ps3_stretch", on).apply();
    }

    /** Pilote graphique PS3 : auto ou turnip (GpuDriver). */
    String gpuDriver() {
        return prefs.getString("gpu_driver", "auto");
    }

    void setGpuDriver(String m) {
        prefs.edit().putString("gpu_driver", m).apply();
    }

    /** Répartition des fils du moteur (0.66.24) : auto, perf ou eco (CoreAffinity). Économie par défaut (0.66.27, validé sur Demon's Souls avec Turnip). */
    String coresMode() {
        return prefs.getString("cores_mode", "eco");
    }

    void setCoresMode(String m) {
        prefs.edit().putString("cores_mode", m).apply();
    }

    String hudStyle() {
        return prefs.getString("hud_style", "line");
    }

    void setHudStyle(String s) {
        prefs.edit().putString("hud_style", s).apply();
    }

    /** Position de l'ATH en haut de l'écran : "left", "center" ou "right". */
    String hudPos() {
        return prefs.getString("hud_pos", "left");
    }

    void setHudPos(String s) {
        prefs.edit().putString("hud_pos", s).apply();
    }

    /** Taille du texte de l'ATH : 0 petite, 1 moyenne, 2 grande. */
    int hudSize() {
        return prefs.getInt("hud_size", 0);
    }

    void setHudSize(int s) {
        prefs.edit().putInt("hud_size", s).apply();
    }

    /** Couleur des libellés : "phone" (accent du téléphone), "theme", "rtss" (orange), "codec" (vert) ou "white". */
    String hudColor() {
        return prefs.getString("hud_color", "phone");
    }

    /** Unité des températures de l'ATH : "c" ou "f". */
    String tempUnit() {
        return prefs.getString("temp_unit", "c");
    }

    void setTempUnit(String u) {
        prefs.edit().putString("temp_unit", u).apply();
    }

    void setHudColor(String s) {
        prefs.edit().putString("hud_color", s).apply();
    }

    /** Bandeau sombre derrière l'ATH (sinon texte seul, ombré). */
    boolean hudBackground() {
        return prefs.getBoolean("hud_bg", true);
    }

    void setHudBackground(boolean on) {
        prefs.edit().putBoolean("hud_bg", on).apply();
    }

    /** Intervalle de mise à jour de l'ATH, en millisecondes. */
    int hudRate() {
        return prefs.getInt("hud_rate", 1000);
    }

    void setHudRate(int ms) {
        prefs.edit().putInt("hud_rate", ms).apply();
    }

    /** Mesures affichées par l'ATH (clés de Hud.ITEMS). */
    Set<String> hudItems() {
        return new HashSet<String>(prefs.getStringSet("hud_items", Hud.defaults()));
    }

    void setHudItem(String key, boolean on) {
        Set<String> s = hudItems();
        if (on) s.add(key);
        else s.remove(key);
        prefs.edit().putStringSet("hud_items", s).apply();
    }

    /** Console d'une sauvegarde saves/<jeu>.srm, notée au lancement du jeu (pour l'export par console). */
    String saveSystem(String fileBase) {
        return prefs.getString("savesys:" + fileBase, null);
    }

    void setSaveSystem(String fileBase, String system) {
        if (fileBase == null || system == null || system.equals(saveSystem(fileBase))) return;
        prefs.edit().putString("savesys:" + fileBase, system).apply();
    }

    /** En paysage, la manette tactile : "overlay" (jeu en plein écran, manette par-dessus) ou "split" (séparée). */
    String landscapeDeck() {
        return prefs.getString("landscape_deck", "overlay");
    }

    void setLandscapeDeck(String s) {
        prefs.edit().putString("landscape_deck", s).apply();
    }

    /** Opacité de la manette superposée, en %. */
    int overlayOpacity() {
        return prefs.getInt("overlay_opacity", 35);
    }

    void setOverlayOpacity(int v) {
        prefs.edit().putInt("overlay_opacity", v).apply();
    }

    /** Sauvegarde, chargement rapides et ×2 gardés à l'écran quand une manette physique est branchée. */
    boolean padQuickButtons() {
        return prefs.getBoolean("pad_quick", true);
    }

    void setPadQuickButtons(boolean on) {
        prefs.edit().putBoolean("pad_quick", on).apply();
    }

    /** Mode débogage : journal détaillé des moteurs, enregistré pour être partagé. */
    /**
     * Profil de test KAMI-WORKS/BOSS (0.66.30, Réglages > Débogage) : applique toutes les pistes en cours d'essai
     * (voir Lab.java) ; seuls nous y touchons, il n'est jamais proposé hors du mode débogage.
     */
    /** Valeurs réellement appliquées au moteur : celles du profil de test s'il est actif, sinon celles des Réglages (jamais modifiées par lui). */
    int ps3AudioMsEff() {
        return lab() ? Lab.AUDIO_MS : ps3AudioMs();
    }

    boolean ps3StretchEff() {
        return lab() ? Lab.STRETCH : ps3Stretch();
    }

    String gpuDriverEff() {
        return lab() ? "turnip" : gpuDriver();
    }

    boolean lab() {
        return debug() && prefs.getBoolean("lab_profile", false);
    }

    void setLab(boolean on) {
        prefs.edit().putBoolean("lab_profile", on).apply();
    }

    boolean debug() {
        return prefs.getBoolean("debug", false);
    }

    void setDebug(boolean on) {
        prefs.edit().putBoolean("debug", on).apply();
    }

    boolean backdrops() {
        return prefs.getBoolean("backdrops", true);
    }

    void setBackdrops(boolean on) {
        prefs.edit().putBoolean("backdrops", on).apply();
    }

    boolean autoCovers() {
        return prefs.getBoolean("auto_covers", true);
    }

    void setAutoCovers(boolean on) {
        prefs.edit().putBoolean("auto_covers", on).apply();
    }

    /** Clients Remote Play ajoutés à la main (« paquet/activité »), en plus des clients connus. */
    Set<String> remoteClients() {
        return new HashSet<String>(prefs.getStringSet("remote_clients", new HashSet<String>()));
    }

    /** App de Remote Play préférée d'une plateforme (« app:paquet/activité »), ou null. */
    String remotePreferred(String group) {
        return prefs.getString("remote_pref_" + group, null);
    }

    void setRemotePreferred(String group, String uri) {
        prefs.edit().putString("remote_pref_" + group, uri).apply();
    }

    void setRemoteClient(String component, boolean on) {
        Set<String> s = remoteClients();
        if (on) s.add(component);
        else s.remove(component);
        prefs.edit().putStringSet("remote_clients", s).apply();
    }

    /** Lanceur d'apps (logo Origami) : grille par défaut, liste au choix. */
    boolean appsGrid() {
        return prefs.getBoolean("apps_grid", true);
    }

    void setAppsGrid(boolean on) {
        prefs.edit().putBoolean("apps_grid", on).apply();
    }

    /** Tri du lanceur d'apps : 0 A→Z, 1 Z→A, 2 installées récemment, 3 mises à jour récemment, 4 jeux d'abord. */
    int appsSort() {
        return prefs.getInt("apps_sort", 0);
    }

    void setAppsSort(int v) {
        prefs.edit().putInt("apps_sort", v).apply();
    }

    boolean isFavorite(Game g) {
        return prefs.getStringSet("favs", new HashSet<String>()).contains(g.uri);
    }

    boolean toggleFavorite(Game g) {
        Set<String> favs = new HashSet<String>(prefs.getStringSet("favs", new HashSet<String>()));
        boolean now = !favs.remove(g.uri);
        if (now) favs.add(g.uri);
        prefs.edit().putStringSet("favs", favs).apply();
        return now;
    }

    List<String> recents() {
        String raw = prefs.getString("recents", "");
        List<String> out = new ArrayList<String>();
        if (!raw.isEmpty()) out.addAll(Arrays.asList(raw.split("\n")));
        return out;
    }

    void addRecent(Game g) {
        List<String> r = recents();
        r.remove(g.uri);
        r.add(0, g.uri);
        while (r.size() > MAX_RECENTS) r.remove(r.size() - 1);
        StringBuilder sb = new StringBuilder();
        for (String s : r) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(s);
        }
        prefs.edit().putString("recents", sb.toString()).apply();
    }

    /** Clé de l'émulateur choisi (« pkg:… » ou « retroarch:… »), ou null. */
    String emulatorFor(SystemInfo s) {
        return prefs.getString("emu_" + s.id, null);
    }

    void setEmulator(String systemId, String value) {
        if (value == null) prefs.edit().remove("emu_" + systemId).apply();
        else prefs.edit().putString("emu_" + systemId, value).apply();
    }

    /**
     * Une fois, à l'arrivée du moteur intégré : on oublie les émulateurs externes choisis pour les
     * consoles qu'il gère, pour qu'il devienne celui par défaut (on peut rechoisir depuis Maison).
     */
    void adoptBuiltinOnce() {
        if (prefs.getBoolean("builtin_adopted", false)) return;
        SharedPreferences.Editor e = prefs.edit();
        for (SystemInfo s : SystemInfo.ALL) if (CoreManager.coreFor(s) != null) e.remove("emu_" + s.id);
        e.putBoolean("builtin_adopted", true).apply();
    }

    void clearEmulators() {
        SharedPreferences.Editor e = prefs.edit();
        for (SystemInfo s : SystemInfo.ALL) e.remove("emu_" + s.id);
        e.apply();
    }
}
