package me.blockcraft.duell.methods;

import me.blockcraft.duell.Duell;
import me.eventseen.Data.Data;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

public class BoardManager {
    public static String Mapname;

    static {
        BoardManager.Mapname = null;
    }

    public static void sendLobbyBoard(final Player p) {
        final ScoreboardManager manager = Bukkit.getScoreboardManager();
        final Scoreboard board = manager.getNewScoreboard();
        final Objective objective = board.registerNewObjective("test", "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.setDisplayName("\u00a78● \u00a79" + Data.BLOCKCRAFT_LABEL + " \u00a78●");
        final Score Score7 = objective.getScore("\u00a77\u00a73 ");
        Score7.setScore(9);
        final Score Score8 = objective.getScore("\u00a77MODUS");
        Score8.setScore(8);
        final Score Score9 = objective.getScore("\u00a78» \u00a7r-/-  ");
        Score9.setScore(7);
        final Score Score10 = objective.getScore("\u00a77\u00a74 ");
        Score10.setScore(6);
        final Score Score11 = objective.getScore("\u00a77MAP");
        Score11.setScore(5);
        final Score Score12 = objective.getScore("\u00a78» \u00a7rRandom");
        Score12.setScore(4);
        final Score Score1b = objective.getScore("\u00a74\u00a7l");
        Score1b.setScore(3);
        final Score Score13 = objective.getScore("\u00a77GEGNER");
        Score13.setScore(2);
        if (Bukkit.getOnlinePlayers().size() == 1) {
            final Score Score14 = objective.getScore("\u00a78» \u00a7r-/-");
            Score14.setScore(1);
        } else {
            for (final Player all : Duell.online) {
                if (all != p) {
                    final Score Score15 = objective.getScore("\u00a78» \u00a7r" + all.getName());
                    Score15.setScore(1);
                }
            }
        }
        final Score Score16 = objective.getScore("\u00a74\u00a75\u00a76   ");
        Score16.setScore(0);
        p.setScoreboard(board);
    }

    @SuppressWarnings("unused")
    public static void sendInGameBoard(final Player p) {
        final String map = p.getLocation().getWorld().getName();
        final ScoreboardManager manager = Bukkit.getScoreboardManager();
        final Scoreboard board = manager.getNewScoreboard();
        final Objective objective = board.registerNewObjective("test", "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.setDisplayName("\u00a78● \u00a79" + Data.BLOCKCRAFT_LABEL + " \u00a78●");
        final Score Score7 = objective.getScore("\u00a77\u00a73 ");
        Score7.setScore(9);
        final Score Score8 = objective.getScore("\u00a77MODUS");
        Score8.setScore(8);
        final Score Score9 = objective.getScore("\u00a78» \u00a7r" + Duell.currentMode);
        Score9.setScore(7);
        final Score Score10 = objective.getScore("\u00a77\u00a74 ");
        Score10.setScore(6);
        final Score Score11 = objective.getScore("\u00a77MAP");
        Score11.setScore(5);
        final Score Score12 = objective.getScore("\u00a78» \u00a7r" + BoardManager.Mapname);
        Score12.setScore(4);
        final Score Score1b = objective.getScore("\u00a74\u00a7l");
        Score1b.setScore(3);
        final Score Score13 = objective.getScore("\u00a77GEGNER");
        Score13.setScore(2);
        if (Bukkit.getOnlinePlayers().size() == 1) {
            final Score Score14 = objective.getScore("\u00a78» \u00a7r-/-");
            Score14.setScore(1);
        } else {
            for (final Player all : Duell.online) {
                if (all != p) {
                    final Score Score15 = objective.getScore("\u00a78» \u00a7r" + all.getName());
                    Score15.setScore(1);
                }
            }
        }
        final Score Score16 = objective.getScore("\u00a74\u00a75\u00a76   ");
        Score16.setScore(0);
        p.setScoreboard(board);
    }

    public static void sendVotedBoard(final Player p) {
        final ScoreboardManager manager = Bukkit.getScoreboardManager();
        final Scoreboard board = manager.getNewScoreboard();
        final Objective objective = board.registerNewObjective("test", "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.setDisplayName("\u00a78● \u00a79" + Data.BLOCKCRAFT_LABEL + " \u00a78●");
        final Score Score7 = objective.getScore("\u00a77\u00a73 ");
        Score7.setScore(9);
        final Score Score8 = objective.getScore("\u00a77MODUS");
        Score8.setScore(8);
        final Score Score9 = objective.getScore("\u00a78» \u00a7r" + Duell.currentMode);
        Score9.setScore(7);
        final Score Score10 = objective.getScore("\u00a77\u00a74 ");
        Score10.setScore(6);
        final Score Score11 = objective.getScore("\u00a77MAP");
        Score11.setScore(5);
        final Score Score12 = objective.getScore("\u00a78» \u00a7rRandom");
        Score12.setScore(4);
        final Score Score1b = objective.getScore("\u00a74\u00a7l");
        Score1b.setScore(3);
        final Score Score13 = objective.getScore("\u00a77GEGNER");
        Score13.setScore(2);
        if (Bukkit.getOnlinePlayers().size() == 1) {
            final Score Score14 = objective.getScore("\u00a78» \u00a7r-/-");
            Score14.setScore(1);
        } else {
            for (final Player all : Duell.online) {
                if (all != p) {
                    final Score Score15 = objective.getScore("\u00a78» \u00a7r" + all.getName());
                    Score15.setScore(1);
                }
            }
        }
        final Score Score16 = objective.getScore("\u00a74\u00a75\u00a76   ");
        Score16.setScore(0);
        p.setScoreboard(board);
    }
}
