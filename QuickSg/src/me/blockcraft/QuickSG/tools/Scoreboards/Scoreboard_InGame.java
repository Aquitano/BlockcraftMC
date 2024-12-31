package me.blockcraft.QuickSG.tools.Scoreboards;

import me.blockcraft.QuickSG.Main;
import me.eventseen.Data.Data;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.ArrayList;

public class Scoreboard_InGame
        implements Listener {
    public static ArrayList<String> A = new ArrayList<>();

    public static void add(Player p) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective("Scoreboard", "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.setDisplayName("\u00a7» \u00a79" + Data.BLOCKCRAFT_LABEL + " \u00a7«");
        objective.getScore("\u00a77ONLINE:").setScore(9);
        objective.getScore("\u00a78» \u00a7f" + Main.online.size() + " \u00a78/\u00a7f 8").setScore(8);
        objective.getScore("\u00a77").setScore(7);
        objective.getScore("\u00a77SPECTATOR:").setScore(6);
        objective.getScore("\u00a78» \u00a7f" + Main.spectator.size()).setScore(5);
        objective.getScore("\u00a71").setScore(4);
        objective.getScore("\u00a77MAP:").setScore(3);
        String map = Main.getInstance().getConfig().getString("MapName");
        objective.getScore("\u00a78» \u00a7f" + map).setScore(2);
        objective.getScore("\u00a7b").setScore(1);
        p.setScoreboard(board);
    }
}

