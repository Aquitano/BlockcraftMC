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

public class Scoreboard_Ending
        implements Listener {
    public static ArrayList<String> A = new ArrayList<>();

    public static void add(Player p) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective("Scoreboard", "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.setDisplayName("\u00a7» \u00a79" + Data.BLOCKCRAFT_LABEL + " \u00a7«");
        objective.getScore("\u00a78»\u00a77WINNER:").setScore(2);
        for (Player all : Main.online) {
            objective.getScore("\u00a78» \u00a7f" + all.getName()).setScore(1);
        }
        p.setScoreboard(board);
    }
}

