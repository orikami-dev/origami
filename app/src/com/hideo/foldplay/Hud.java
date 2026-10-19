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
            {"heat", "Chauffe", "État thermique annoncé par Android"},
            {"res", "Moteur et résolution", "Cœur utilisé et taille de l'image"},
            {"clock", "Heure", "Heure actuelle"},
    };

    static Set<String> defaults() {
        return new HashSet<String>(Arrays.asList("fps", "ft", "speed", "cpu", "ram", "bat", "temp"));
    }

    static final String[] POS_KEYS = {"left", "center", "right"};
    static final String[] POS_LABELS = {"En haut à gauche", "En haut au centre", "En haut à droite"};
    static final String[] COLOR_KEYS = {"rtss", "codec", "white"};
    static final String[] COLOR_LABELS = {"Orange RTSS", "Vert Codec", "Blanc"};
    static final String[] SIZE_LABELS = {"Petite", "Moyenne", "Grande"};
    static final float[] SIZES = {10, 12, 14};
    static final int[] RATES = {500, 1000, 2000};
    static final String[] RATE_LABELS = {"0,5 s", "1 s", "2 s"};

    static int labelColor(String key) {
        if ("codec".equals(key)) return Theme.CODEC;
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
        float tempC = Float.NaN;
        String core;
        int width, height;
        boolean paused;
    }

    private static final String[] HEAT = {"normale", "légère", "modérée", "forte", "critique", "urgence", "arrêt"};

    static CharSequence compose(Sample s, Set<String> items, boolean block, int label) {
        SpannableStringBuilder b = new SpannableStringBuilder();
        Builder w = new Builder(b, block, label);
        if (items.contains("res") && s.core != null)
            w.item(s.core.toUpperCase(Locale.ROOT), s.width + "×" + s.height, null);
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
        if (items.contains("temp") && !Float.isNaN(s.tempC)) {
            if (items.contains("bat") && !block) w.extra(fmt1(s.tempC), "°C");
            else w.item("TEMP", fmt1(s.tempC), "°C");
        }
        if (items.contains("heat") && s.heat >= 0)
            w.item("CHAUFFE", s.heat < HEAT.length ? HEAT[s.heat] : "?", null);
        if (items.contains("clock")) {
            java.util.Calendar c = java.util.Calendar.getInstance();
            w.item(null, String.format(Locale.ROOT, "%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY),
                    c.get(java.util.Calendar.MINUTE)), null);
        }
        if (b.length() == 0) w.item("ATH", "aucune mesure choisie", null);
        return b;
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
