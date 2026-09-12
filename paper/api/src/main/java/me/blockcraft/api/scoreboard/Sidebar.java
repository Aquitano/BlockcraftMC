package me.blockcraft.api.scoreboard;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** One sidebar objective per player, updated in place so refreshes do not flicker. */
public final class Sidebar {

    private static final String OBJECTIVE = "blockcraft";

    private final Component title;

    public Sidebar(Component title) {
        this.title = title;
    }

    /** Lines from top to bottom. */
    public void show(Player player, List<Component> lines) {
        var rows = new ArrayList<Row>(lines.size());
        int score = lines.size();
        for (var line : lines) {
            rows.add(new Row("line" + score, line, score--));
        }
        showRows(player, rows);
    }

    /** Rows ordered by descending score; entries not in {@code rows} disappear. */
    public void showRows(Player player, List<Row> rows) {
        var objective = objective(player);
        var board = player.getScoreboard();
        var shown = new HashSet<String>();
        for (var row : rows) {
            var score = objective.getScore(row.entry());
            score.customName(row.text());
            score.setScore(row.score());
            shown.add(row.entry());
        }
        for (var entry : board.getEntries()) {
            if (!shown.contains(entry)) {
                board.resetScores(entry);
            }
        }
    }

    public record Row(String entry, Component text, int score) {
    }

    private Objective objective(Player player) {
        var manager = Bukkit.getScoreboardManager();
        var board = player.getScoreboard();
        var existing = board.getObjective(OBJECTIVE);
        if (existing != null && board != manager.getMainScoreboard()) {
            return existing;
        }
        board = manager.getNewScoreboard();
        var objective = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY, title);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        player.setScoreboard(board);
        return objective;
    }
}
