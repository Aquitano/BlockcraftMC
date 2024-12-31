package me.blockcraft.QuickSG.tools.Scoreboards;

import me.blockcraft.QuickSG.Main;
import me.blockcraft.QuickSG.tools.items.KitSelector;
import me.eventseen.Data.Data;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.ArrayList;

public class Scoreboard_Lobby
        implements Listener {
    public static ArrayList<String> A = new ArrayList<>();
    public static String KitName = "Starter";

    public static void add(Player p) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective("Scoreboard", "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.setDisplayName("\u00a7» \u00a79" + Data.BLOCKCRAFT_LABEL + " \u00a7«");
        objective.getScore("\u00a77ONLINE:").setScore(9);
        objective.getScore("\u00a78» \u00a7f" + Main.online.size() + " \u00a78/\u00a7f 8").setScore(8);
        objective.getScore("\u00a71").setScore(7);
        objective.getScore("\u00a77MAP:").setScore(6);
        String map = Main.getInstance().getConfig().getString("MapName");
        objective.getScore("\u00a78» \u00a7f" + map).setScore(5);
        objective.getScore("\u00a7a").setScore(4);
        objective.getScore("\u00a77KIT:").setScore(3);
        KitName = KitSelector.Kit1.contains(p) ? "Starter" : (KitSelector.Kit2.contains(p) ? "Bogen" : (KitSelector.Kit3.contains(p) ? "Herzen" : (KitSelector.Kit4.contains(p) ? "Chain" : (KitSelector.Kit5.contains(p) ? "Diamanten" : (KitSelector.Kit6.contains(p) ? "" : (KitSelector.Kit7.contains(p) ? "" : (KitSelector.Kit8.contains(p) ? "" : "\u00a7cError")))))));
        objective.getScore("\u00a78» \u00a7f" + KitName).setScore(2);
        objective.getScore("\u00a79").setScore(1);
        p.setScoreboard(board);
    }
}

