package me.blockcraft.api.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;

/**
 * One scoreboard per player, owned by BlockcraftAPI. The sidebar and the tab list teams both write to it, so
 * whoever goes second no longer replaces the other one's board.
 */
public final class Boards {

    private Boards() {
    }

    public static Scoreboard of(Player player) {
        var manager = Bukkit.getScoreboardManager();
        var board = player.getScoreboard();
        if (board != manager.getMainScoreboard()) {
            return board;
        }
        var own = manager.getNewScoreboard();
        player.setScoreboard(own);
        return own;
    }
}
