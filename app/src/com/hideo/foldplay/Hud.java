package com.hideo.foldplay;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * ATH de monitoring façon RTSS (l'incrustation de MSI Afterburner) : une ligne en haut de l'écran,
 * libellés en couleur, valeurs en blanc, unités plus petites. Le style « bloc » garde une mesure par ligne.
 */
final class Hud {
    private Hud() {
    }

    /** Mesures disponibles : clé, nom dans les réglages, détail. */
    static final String[][] ITEMS = {
            {"fps", "Images/s", "Images affichées par seconde"},
            {"ft", "Temps d'image", "Moyenne et pic en millisecondes"},
            {"speed", "Vitesse", "Vitesse d'émulation, 100 % = temps réel"},
            {"cpu", "Processeur", "Charge de l'app, 100 % = un cœur plein"},
            {"mhz", "Fréquence processeur", "Cœur le plus rapide en MHz, si Android la donne"},
            {"ram", "Mémoire", "Mémoire utilisée par l'app"},
            {"bat", "Batterie", "Niveau et charge"},
            {"temp", "Température", "Température de la batterie"},
            {"heat", "Température CPU et GPU", "Degrés du processeur et de la carte graphique"},
            {"res", "Moteur et résolution", "Cœur utilisé et taille de l'image"},
            {"clock", "Heure", "Heure actuelle"},
    };

    static Set<String> defaults() {
        return new HashSet<String>(Arrays.asList("fps", "ft", "speed", "cpu", "ram", "bat", "temp"));
    }

    static final String[] POS_KEYS = {"left", "center", "right"};
    static final String[] POS_LABELS = {"En haut à gauche", "En haut au centre", "En haut à droite"};
    static final String[] COLOR_KEYS = {"phone", "theme", "rtss", "codec", "white"};
    static final String[] COLOR_LABELS = {"Couleur du téléphone", "Thème d'Origami", "Orange RTSS", "Vert Codec", "Blanc"};
    static final String[] UNIT_KEYS = {"c", "f"};
    static final String[] UNIT_LABELS = {"Degrés Celsius (°C)", "Degrés Fahrenheit (°F)"};
    static final String[] SIZE_LABELS = {"Petite", "Moyenne", "Grande"};
    static final float[] SIZES = {10, 12, 14};
    static final int[] RATES = {500, 1000, 2000};
    static final String[] RATE_LABELS = {"0,5 s", "1 s", "2 s"};

    /**
     * Couleur des libellés. « phone » suit la couleur d'accent du téléphone (Android 12+, Material
     * You), « theme » celle du thème d'Origami.
     */
    static int labelColor(android.content.Context ctx, String key) {
        if ("phone".equals(key) && android.os.Build.VERSION.SDK_INT >= 31) {
            try {
                return ctx.getColor(android.R.color.system_accent1_200);
            } catch (Exception ignored) {
                // Couleur absente sur ce téléphone : orange RTSS.
            }
        }
        if ("theme".equals(key)) return Theme.CODEC;
        return labelColor(key);
    }

    static int labelColor(String key) {
        if ("codec".equals(key)) return 0xFF7CFF8A;
        if ("white".equals(key)) return 0xFFB8C0CC;
        return 0xFFFF9A1F;
    }

    static int index(String[] keys, String key) {
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(key)) return i;
        return 0;
    }

    /** Une mesure prise par GameActivity ; une valeur négative veut dire « inconnue ». */
    static final class Sample {
        float fps, frameMs, frameMaxMs, speed, cpu;
        int mhz = -1, ramMb, battery = -1, heat = -1;
        boolean charging;
        float tempC = Float.NaN, cpuC = Float.NaN, gpuC = Float.NaN;
        boolean fahrenheit;
        String core;
        int width, height;
        boolean paused;
        /** Résolution interne du jeu (ex. « 1280×720 »), ou null : on affiche alors la taille de la surface. */
        String renderRes;
    }

    private static final String[] HEAT = {"normale", "légère", "modérée", "forte", "critique", "urgence", "arrêt"};

    static CharSequence compose(Sample s, Set<String> items, boolean block, int label) {
        SpannableStringBuilder b = new SpannableStringBuilder();
        Builder w = new Builder(b, block, label);
        if (items.contains("res") && s.core != null)
            w.item(s.core.toUpperCase(Locale.ROOT), s.renderRes != null ? s.renderRes : s.width + "×" + s.height, null);
        if (items.contains("fps")) w.item("FPS", s.paused ? "‖" : s.fps < 0 ? "—" : fmt0(s.fps), null);
        if (items.contains("ft") && s.frameMs > 0) {
            w.item(items.contains("fps") && !block ? null : "FT", fmt1(s.frameMs), "ms");
            if (s.frameMaxMs > s.frameMs * 1.25f) w.extra("↑" + fmt0(s.frameMaxMs), "ms");
        }
        if (items.contains("speed")) w.item("VIT", s.paused ? "0" : s.speed < 0 ? "—" : fmt0(s.speed), "%");
        if (items.contains("cpu")) {
            w.item("CPU", fmt0(s.cpu), "%");
            if (items.contains("mhz") && s.mhz > 0) w.extra(String.valueOf(s.mhz), "MHz");
        } else if (items.contains("mhz") && s.mhz > 0) {
            w.item("CPU", String.valueOf(s.mhz), "MHz");
        }
        if (items.contains("ram")) w.item("RAM", String.valueOf(s.ramMb), "Mo");
        if (items.contains("bat") && s.battery >= 0) {
            w.item("BAT", String.valueOf(s.battery), "%");
            if (s.charging) w.extra("⚡", null);
        }
        String unit = s.fahrenheit ? "°F" : "°C";
        if (items.contains("temp") && !Float.isNaN(s.tempC)) {
            if (items.contains("bat") && !block) w.extra(fmt1(deg(s.tempC, s.fahrenheit)), unit);
            else w.item("TEMP", fmt1(deg(s.tempC, s.fahrenheit)), unit);
        }
        // Températures du processeur et de la carte graphique, en degrés seulement (0.65).
        if (items.contains("heat")) {
            if (!Float.isNaN(s.cpuC)) w.item("CPU", fmt0(deg(s.cpuC, s.fahrenheit)), unit);
            if (!Float.isNaN(s.gpuC)) w.item("GPU", fmt0(deg(s.gpuC, s.fahrenheit)), unit);
        }
        if (items.contains("clock")) {
            java.util.Calendar c = java.util.Calendar.getInstance();
            w.item(null, String.format(Locale.ROOT, "%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY),
                    c.get(java.util.Calendar.MINUTE)), null);
        }
        if (b.length() == 0) w.item("ATH", "aucune mesure choisie", null);
        return b;
    }

    private static float deg(float c, boolean f) {
        return f ? c * 9f / 5f + 32f : c;
    }

    /** Température du processeur en °C (moyenne des zones lisibles, retenues au premier appel), ou NaN. */
    static float cpuC() {
        return zoneMean(true);
    }

    static float gpuC() {
        return zoneMean(false);
    }

    /** Moyenne des zones thermiques lisibles du processeur (cpu) ou de la carte graphique (gpu), ou NaN. */
    private static float zoneMean(boolean cpu) {
        if (cpuZones == null) {
            java.util.List<File> c = new java.util.ArrayList<File>(), g = new java.util.ArrayList<File>();
            File[] all = new File("/sys/class/thermal").listFiles();
            if (all != null) {
                for (File z : all) {
                    String type = readLine(new File(z, "type"));
                    if (type == null || readLine(new File(z, "temp")) == null) continue;
                    type = type.toLowerCase(Locale.ROOT);
                    if (type.contains("gpu")) g.add(new File(z, "temp"));
                    else if (type.contains("cpu")) c.add(new File(z, "temp"));
                }
            }
            cpuZones = c.toArray(new File[0]);
            gpuZones = g.toArray(new File[0]);
        }
        float sum = 0;
        int n = 0;
        for (File f : cpu ? cpuZones : gpuZones) {
            String v = readLine(f);
            if (v == null) continue;
            try {
                float t = Long.parseLong(v.trim());
                if (t > 1000) t /= 1000f;
                if (t > 5 && t < 125) {
                    sum += t;
                    n++;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return n == 0 ? Float.NaN : sum / n;
    }

    private static File[] cpuZones, gpuZones;

    private static String readLine(File f) {
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(f));
            return r.readLine();
        } catch (Exception e) {
            return null;
        } finally {
            if (r != null) try { r.close(); } catch (Exception ignored) { }
        }
    }

    /**
     * L'ATH passe sous la caméra frontale (encoche ou poinçon) : on le décale juste en dessous
     * quand il la recouvre, selon la position réelle de la caméra.
     */
    static void avoidCutout(final android.widget.TextView hud, final int margin) {
        hud.post(new Runnable() {
            public void run() {
                if (android.os.Build.VERSION.SDK_INT < 28) return;
                android.view.WindowInsets wi = hud.getRootWindowInsets();
                android.view.DisplayCutout dc = wi != null ? wi.getDisplayCutout() : null;
                if (dc == null) return;
                int[] at = new int[2];
                hud.getLocationInWindow(at);
                // Place d'origine, sans le décalage déjà appliqué.
                int top = at[1] - Math.round(hud.getTranslationY());
                android.graphics.Rect me = new android.graphics.Rect(at[0], top, at[0] + hud.getWidth(), top + hud.getHeight());
                float shift = 0;
                for (android.graphics.Rect r : dc.getBoundingRects()) {
                    if (android.graphics.Rect.intersects(me, r)) shift = Math.max(shift, r.bottom + margin - me.top);
                }
                if (hud.getTranslationY() != shift) hud.setTranslationY(shift);
            }
        });
    }

    /** Mémoire résidente du processus en Mo (/proc/self/statm), ou -1. */
    static int residentMb() {
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader("/proc/self/statm"));
            String[] f = r.readLine().trim().split("\\s+");
            return (int) (Long.parseLong(f[1]) * android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE) >> 20);
        } catch (Exception e) {
            return -1;
        } finally {
            if (r != null) try { r.close(); } catch (Exception ignored) { }
        }
    }

    /** Fréquence actuelle du cœur le plus rapide, en MHz, ou -1 si Android la cache. */
    static int cpuMhz() {
        int best = -1;
        for (int i = 0; i < 16; i++) {
            File f = new File("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq");
            if (!f.exists()) {
                if (i > 0) break;
                continue;
            }
            BufferedReader r = null;
            try {
                r = new BufferedReader(new FileReader(f));
                String line = r.readLine();
                if (line != null) best = Math.max(best, (int) (Long.parseLong(line.trim()) / 1000));
            } catch (Exception e) {
                // Lecture refusée (SELinux) sur beaucoup d'appareils : la mesure est simplement absente.
            } finally {
                if (r != null) try { r.close(); } catch (Exception ignored) { }
            }
        }
        return best;
    }

    private static String fmt0(float v) {
        return String.format(Locale.ROOT, "%.0f", v);
    }

    private static String fmt1(float v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /** Assemble les segments « LIBELLÉ valeur unité » avec leurs couleurs. */
    private static final class Builder {
        final SpannableStringBuilder b;
        final boolean block;
        final int label;

        Builder(SpannableStringBuilder b, boolean block, int label) {
            this.b = b;
            this.block = block;
            this.label = label;
        }

        void item(String name, String value, String unit) {
            if (b.length() > 0) b.append(block ? "\n" : "   ");
            if (name != null) {
                span(name, new ForegroundColorSpan(label), new RelativeSizeSpan(0.85f));
                b.append(' ');
            }
            value(value, unit);
        }

        void extra(String value, String unit) {
            b.append(' ');
            value(value, unit);
        }

        private void value(String value, String unit) {
            span(value, new ForegroundColorSpan(0xFFFFFFFF));
            if (unit != null) span(unit, new ForegroundColorSpan(label), new RelativeSizeSpan(0.75f));
        }

        private void span(String text, Object... spans) {
            int start = b.length();
            b.append(text);
            for (Object o : spans) b.setSpan(o, start, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
}
