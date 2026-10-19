package net.rpcsx;

import java.util.ArrayList;
import java.util.List;

/** Jeux trouvés par le cœur PS3 (jeux installés depuis un PKG dans dev_hdd0). */
public final class GameRepository {
    public static final List<GameInfo> GAMES = new ArrayList<GameInfo>();

    private GameRepository() {
    }

    public static synchronized void add(GameInfo[] games, long progressId) {
        for (GameInfo g : games) GAMES.add(g);
    }
}
