package me.blockcraft.api.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.scoreboard.Sidebar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The sidebar every game shows, from BlockcraftAPI's {@code sidebar} texts: in the lobby who is there and the map;
 * during the round who is still in and how many watch, the time left when no boss bar tells it, and the map; at the
 * end the winners. The game's own sections follow the frame, each a heading over its values.
 */
public final class GameSidebar {

    /** {@code heading} carries its icon already, resolved for the viewer with {@link #icons}. */
    public record Section(Component heading, List<Component> values) {

        public Section(Component heading, Component value) {
            this(heading, List.of(value));
        }
    }

    /** The client shows no more; during the round the map makes room for the game's lines when they need it. */
    private static final int MAX_ROWS = 15;

    private final Sidebar sidebar;
    private final Round round;

    public GameSidebar(Component title, Round round) {
        this.sidebar = new Sidebar(title);
        this.round = round;
    }

    /** The headings carry icons, which only the viewer's client knows how to draw. */
    public static TagResolver icons(Player viewer) {
        return BlockcraftApi.get().resourcePack().icons(viewer);
    }

    /** A value line the way the frame writes its own. */
    public static Component value(Component value) {
        return BlockcraftApi.get().messages().get("sidebar.value", Placeholder.component("value", value));
    }

    public void lobby(Player viewer, int max, Component map, List<Section> game) {
        var frame = new ArrayList<Section>();
        frame.add(players(viewer, max));
        frame.add(new Section(heading(viewer, "sidebar.map"), value(map)));
        show(viewer, frame, game);
    }

    public void round(Player viewer, int max, int secondsLeft, Component map, List<Section> game) {
        var frame = new ArrayList<Section>();
        var messages = BlockcraftApi.get().messages();
        var players = new ArrayList<>(players(viewer, max).values());
        if (round.spectatorCount() > 0) {
            players.add(messages.get("sidebar.spectators-value", Formatter.number("spectators", round.spectatorCount())));
        }
        frame.add(new Section(heading(viewer, "sidebar.players"), players));
        if (round.clockOnSidebar()) {
            frame.add(new Section(heading(viewer, "sidebar.time"), value(Component.text(TimerBar.clock(secondsLeft)))));
        }
        var withMap = new ArrayList<>(frame);
        withMap.add(new Section(heading(viewer, "sidebar.map"), value(map)));
        show(viewer, rows(withMap, game) <= MAX_ROWS ? withMap : frame, game);
    }

    /** No winners is a draw. */
    public void end(Player viewer, List<Component> winners) {
        var values = winners.isEmpty()
                ? List.of(BlockcraftApi.get().messages().get("sidebar.draw"))
                : winners.stream().map(GameSidebar::value).toList();
        show(viewer, List.of(new Section(heading(viewer, "sidebar.winner"), values)), List.of());
    }

    private Section players(Player viewer, int max) {
        return new Section(heading(viewer, "sidebar.players"), BlockcraftApi.get().messages().get("sidebar.players-value",
                Formatter.number("players", round.playerCount()), Formatter.number("max", max)));
    }

    private static Component heading(Player viewer, String key) {
        return BlockcraftApi.get().messages().get(key, icons(viewer));
    }

    private static int rows(List<Section> frame, List<Section> game) {
        var sections = Stream.concat(frame.stream(), game.stream()).toList();
        return sections.stream().mapToInt(section -> 1 + section.values().size()).sum() + Math.max(0, sections.size() - 1);
    }

    static List<Component> layout(List<Section> sections) {
        int contentRows = sections.stream().mapToInt(section -> 1 + section.values().size()).sum();
        int gaps = Math.min(Math.max(0, MAX_ROWS - contentRows), Math.max(0, sections.size() - 1));
        var lines = new ArrayList<Component>();
        for (var section : sections) {
            if (!lines.isEmpty() && gaps > 0) {
                lines.add(Component.empty());
                gaps--;
            }
            lines.add(section.heading());
            lines.addAll(section.values());
        }
        return List.copyOf(lines);
    }

    private void show(Player viewer, List<Section> frame, List<Section> game) {
        sidebar.show(viewer, layout(Stream.concat(frame.stream(), game.stream()).toList()));
    }
}
