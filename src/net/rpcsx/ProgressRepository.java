package net.rpcsx;

/**
 * Appelé par le cœur PS3 (ARMSX3) pendant une installation : il cherche cette classe sous ce nom
 * exact. Origami ne garde que la dernière progression, que Ps3Activity affiche.
 */
public final class ProgressRepository {
    public interface Listener {
        void onProgress(long id, long value, long max, String message);
    }

    public static volatile Listener listener;

    private ProgressRepository() {
    }

    /** Vrai pour continuer, faux pour annuler. value = -1 : échec. */
    public static boolean onProgressEvent(long id, long value, long max, String message) {
        Listener l = listener;
        if (l != null) l.onProgress(id, value, max, message);
        return true;
    }
}
