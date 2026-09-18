package me.blockcraft.lobby.npc;

import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.game.TopEntry;
import me.blockcraft.common.db.Database;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.config.Leaderboard;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The top ten of a stats table as the lines of a hologram, read from the database on a timer. A board that cannot
 * be read keeps the places it last showed.
 */
public final class Leaderboards {

    private static final int PLACES = 10;

    private final Plugin plugin;
    private final Messages messages;
    private final Messages shared;
    private final Map<String, Leaderboard> boards;
    private final Map<String, StatsRepository> tables;
    private volatile Map<String, List<TopEntry>> places = Map.of();

    public Leaderboards(Plugin plugin, Database database, Messages messages, Messages shared, Map<String, Leaderboard> boards) {
        this.plugin = plugin;
        this.messages = messages;
        this.shared = shared;
        this.boards = boards;
        this.tables = boards.values().stream().map(Leaderboard::table).distinct()
                .collect(Collectors.toUnmodifiableMap(table -> table, table -> new StatsRepository(database, table)));
    }

    public Set<String> names() {
        return boards.keySet();
    }

    /** The game a board ranks, whose icon hovers above it. */
    public Optional<String> game(String name) {
        return Optional.ofNullable(boards.get(name)).map(Leaderboard::game);
    }

    /** A title and ten places, so the icon above a board keeps its height however full the table is today. */
    public OptionalInt rows(String name) {
        return boards.containsKey(name) ? OptionalInt.of(PLACES + 1) : OptionalInt.empty();
    }

    public Optional<List<Component>> lines(String name) {
        return Optional.ofNullable(boards.get(name)).map(board -> render(name, board));
    }

    /** Blocking; the caller runs it off the main thread. */
    void read() {
        var loaded = new HashMap<>(places);
        boards.forEach((name, board) -> {
            try {
                loaded.put(name, tables.get(board.table()).top(board.stat(), PLACES));
            } catch (DatabaseException e) {
                plugin.getSLF4JLogger().warn("Could not read the {} leaderboard", name, e);
            }
        });
        places = Map.copyOf(loaded);
    }

    private List<Component> render(String name, Leaderboard board) {
        var lines = new ArrayList<Component>();
        lines.add(messages.get("holo.leaderboard.title",
                Placeholder.component("game", gameName(board.game())),
                Placeholder.component("stat", messages.get("holo.leaderboard.stat." + board.stat().key()))));
        var top = places.getOrDefault(name, List.of());
        if (top.isEmpty()) {
            lines.add(messages.get("holo.leaderboard.empty"));
        }
        int rank = 0;
        for (var entry : top) {
            rank++;
            lines.add(messages.get(rank == 1 ? "holo.leaderboard.first" : "holo.leaderboard.entry",
                    Formatter.number("rank", rank),
                    Placeholder.unparsed("name", String.valueOf(entry.name())),
                    Formatter.number("value", entry.value())));
        }
        return List.copyOf(lines);
    }

    /** Games are named network-wide, the way the status signs and the profile card name them. */
    private Component gameName(String game) {
        String key = "games." + game + ".name";
        return shared.has(key) ? shared.get(key) : Component.text(game);
    }
}
