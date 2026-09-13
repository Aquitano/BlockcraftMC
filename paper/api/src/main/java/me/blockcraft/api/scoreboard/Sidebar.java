package me.blockcraft.api.scoreboard;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** One sidebar objective on the player's {@link Boards} scoreboard, updated in place so refreshes do not flicker. */
public final class Sidebar {

    private static final String OBJECTIVE = "blockcraft";

    private final Component title;
    /** Only the entries this sidebar wrote are reset again; the board also carries the tab list teams. */
    private final Map<Player, Set<String>> written = new WeakHashMap<>();

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
        var shown = new LinkedHashSet<String>();
        for (var row : rows) {
            var score = objective.getScore(row.entry());
            score.customName(row.text());
            score.setScore(row.score());
            shown.add(row.entry());
        }
        var board = objective.getScoreboard();
        for (var entry : written.getOrDefault(player, Set.of())) {
            if (!shown.contains(entry) && board != null) {
                board.resetScores(entry);
            }
        }
        written.put(player, shown);
    }

    public record Row(String entry, Component text, int score) {
    }

    private Objective objective(Player player) {
        var board = Boards.of(player);
        var existing = board.getObjective(OBJECTIVE);
        if (existing != null) {
            return existing;
        }
        var objective = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY, title);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        return objective;
    }
}
