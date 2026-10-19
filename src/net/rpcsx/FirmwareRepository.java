package net.rpcsx;

/** Appelé par le cœur PS3 quand le firmware est installé, puis quand ses modules sont compilés. */
public final class FirmwareRepository {
    public static volatile String installed;
    public static volatile String compiled;

    private FirmwareRepository() {
    }

    public static void onFirmwareInstalled(String version) {
        installed = version;
    }

    public static void onFirmwareCompiled(String version) {
        compiled = version;
    }
}
