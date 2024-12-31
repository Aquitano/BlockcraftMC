package me.blockcraft.rush.Scoreboards;

import me.blockcraft.rush.Main;
import me.eventseen.Data.Data;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

public class Scoreboard_Ending implements Listener {
    public static void add(final Player p) {
        final ScoreboardManager manager = Bukkit.getScoreboardManager();
        final Scoreboard board = manager.getNewScoreboard();
        final Objective objective = board.registerNewObjective("Scoreboard", "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.setDisplayName("\u00a78» \u00a79" + Data.BLOCKCRAFT_LABEL + " \u00a78«");
        objective.getScore("\u00a77WINNER:").setScore(2);
        for (final Player all : Main.online) {
            objective.getScore("\u00a78» \u00a7f" + all.getPlayerListName()).setScore(1);
        }
        p.setScoreboard(board);
    }
}
