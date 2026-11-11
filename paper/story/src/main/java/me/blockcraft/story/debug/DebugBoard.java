package me.blockcraft.story.debug;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import me.blockcraft.story.game.StoryGame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /story hud}: the story's state in the sidebar, kept current twice a second, for whoever plays and debugs at
 * once. Turning it off gives the player their old scoreboard back.
 */
public final class DebugBoard implements AutoCloseable {

    private static final int MOST_LINES = 15;
    private static final int WIDEST = 40;

    private record Shown(Scoreboard board, Objective objective, Scoreboard before) { }

    private final StoryGame game;
    private final Map<UUID, Shown> shown = new HashMap<>();
    private final BukkitTask task;

    public DebugBoard(Plugin plugin, StoryGame game) {
        this.game = game;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 10, 10);
    }

    public boolean toggle(Player player) {
        var was = shown.remove(player.getUniqueId());
        if (was != null) {
            player.setScoreboard(was.before());
            return false;
        }
        var board = Bukkit.getScoreboardManager().getNewScoreboard();
        var objective = board.registerNewObjective("story", Criteria.DUMMY, Component.text("Story", NamedTextColor.GOLD));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());
        shown.put(player.getUniqueId(), new Shown(board, objective, player.getScoreboard()));
        player.setScoreboard(board);
        refresh();
        return true;
    }

    private void refresh() {
        shown.entrySet().removeIf(entry -> Bukkit.getPlayer(entry.getKey()) == null);
        if (shown.isEmpty()) return;
        var lines = lines(StoryState.of(game));
        for (var view : shown.values()) {
            var objective = view.objective();
            for (int i = 0; i < MOST_LINES; i++) {
                var entry = "line" + i;
                if (i >= lines.size()) {
                    view.board().resetScores(entry);
                    continue;
                }
                var score = objective.getScore(entry);
                score.setScore(MOST_LINES - i);
                score.customName(lines.get(i));
            }
        }
    }

    private static List<Component> lines(StoryState state) {
        var lines = new ArrayList<Component>();
        lines.add(pair("stage", state.stage().isEmpty() ? "-" : state.stage() + " " + state.stageNumber() + "/" + state.stages().size()));
        state.scene().ifPresent(at -> lines.add(pair("scene", at.scene() + " " + at.tick() + "/" + at.duration())));
        state.upload().ifPresent(done -> lines.add(pair("upload", Math.round(done * 100) + "%")));
        state.timers().forEach(timer -> lines.add(pair(timer.inTicks() / 20 + "s", timer.label())));
        state.music().ifPresent(track -> lines.add(pair("music", track)));
        state.ambience().ifPresent(track -> lines.add(pair("ambience", track)));
        state.votes().forEach((topic, cast) -> lines.add(pair("vote " + topic, cast.toString())));
        state.facts().forEach((name, value) -> {
            if (!name.equals("stage")) lines.add(pair(name, value));
        });
        return lines.subList(0, Math.min(MOST_LINES, lines.size()));
    }

    private static Component pair(String key, String value) {
        var shown = value.length() > WIDEST ? value.substring(0, WIDEST - 1) + "…" : value;
        return Component.text(key + " ", NamedTextColor.GRAY).append(Component.text(shown, NamedTextColor.WHITE));
    }

    @Override
    public void close() {
        task.cancel();
        shown.forEach((id, view) -> {
            var player = Bukkit.getPlayer(id);
            if (player != null) player.setScoreboard(view.before());
        });
        shown.clear();
    }
}
