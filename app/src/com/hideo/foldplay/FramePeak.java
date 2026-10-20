package com.hideo.foldplay;

/**
 * Pic du temps d'image pour l'ATH des moteurs à activité propre (0.62.3) : un petit fil relève
 * souvent le compteur d'images du moteur (ou la durée de sa dernière image) et garde le plus long
 * écart vu depuis la dernière lecture, comme la ligne « ↑ » de RTSS.
 */
final class FramePeak {
    interface Probe {
        /** Compteur d'images (counter) ou durée de la dernière image en ns, -1 si inconnu. */
        long read();
    }

    private final Probe probe;
    private final boolean counter;
    private volatile boolean running;
    private long maxNs;
    private double rateSum;
    private int rateN;

    FramePeak(Probe probe, boolean counter) {
        this.probe = probe;
        this.counter = counter;
    }

    synchronized void start() {
        if (running) return;
        running = true;
        Thread t = new Thread(new Runnable() {
            public void run() {
                long last = -1, lastAt = System.nanoTime();
                while (running) {
                    long v;
                    try {
                        v = probe.read();
                    } catch (Throwable e) {
                        v = -1;
                    }
                    long now = System.nanoTime();
                    if (counter) {
                        if (v >= 0 && v != last) {
                            if (last >= 0) note(now - lastAt);
                            last = v;
                            lastAt = now;
                        }
                    } else if (v > 0) {
                        note(v);
                        rate(v);
                    }
                    try {
                        Thread.sleep(counter ? 2 : 8);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        }, "origami-ath");
        t.setDaemon(true);
        t.start();
    }

    void stop() {
        running = false;
    }

    private synchronized void note(long ns) {
        if (ns > maxNs) maxNs = ns;
    }

    private synchronized void rate(long periodNs) {
        rateSum += 1e9 / periodNs;
        rateN++;
    }

    /**
     * Images/s moyennes depuis le dernier appel (durée de la dernière image relevée régulièrement),
     * ou -1 : une seule lecture faisait sauter l'ATH entre 30, 40 et 60.
     */
    synchronized float takeFps() {
        float f = rateN > 0 ? (float) (rateSum / rateN) : -1;
        rateSum = 0;
        rateN = 0;
        return f;
    }

    /** Pic en ms depuis le dernier appel, ou -1 sans mesure. */
    synchronized float takeMs() {
        float ms = maxNs > 0 ? maxNs / 1e6f : -1;
        maxNs = 0;
        return ms;
    }
}
