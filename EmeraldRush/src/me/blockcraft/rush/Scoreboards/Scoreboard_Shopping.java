package me.blockcraft.rush.Scoreboards;

import me.blockcraft.rush.Main;
import me.blockcraft.rush.RushManager;
import me.eventseen.Data.Data;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

public class Scoreboard_Shopping implements Listener {
    public static void add(final Player p) {
        final ScoreboardManager manager = Bukkit.getScoreboardManager();
        final Scoreboard board = manager.getNewScoreboard();
        final Objective objective = board.registerNewObjective("Scoreboard", "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.setDisplayName("\u00a78» \u00a79" + Data.BLOCKCRAFT_LABEL + " \u00a78«");
        objective.getScore("\u00a77ONLINE:").setScore(6);
        objective.getScore("\u00a78» \u00a7f" + Main.online.size() + " \u00a78/\u00a7f " + Bukkit.getServer().getMaxPlayers()).setScore(5);
        objective.getScore("\u00a71").setScore(4);
        objective.getScore("\u00a77EMERALDS:").setScore(3);
        objective.getScore("\u00a78» \u00a7f" + RushManager.getCoins(p)).setScore(2);
        objective.getScore("\u00a7a").setScore(1);
        p.setScoreboard(board);
    }
}
