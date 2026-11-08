package me.blockcraft.sternenfront.game;

import me.blockcraft.sternenfront.game.HudPanel.Glyph;
import me.blockcraft.sternenfront.game.HudPanel.Tint;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Each player's own kills, assists and death for the top-right corner of their screen, newest on top, in the minimap's
 * font. A line reads killer, mark, victim; it greys out in its last second and is gone after {@code kill-feed.seconds}.
 */
final class KillFeed {

    enum Kind { KILL, HEADSHOT, ASSIST, DEATH }

    /** A fighter as the feed names it, in their side's colour. */
    record Named(String name, Tint tint) { }

    /** {@code other} is the victim, or for a death the killer; empty for a death nobody else caused. */
    record Entry(Kind kind, Optional<Named> other, long until) { }

    static final int ROWS = 4;
    static final int ROW = 9;
    private static final int FADE_TICKS = 20;
    private static final int GAP = 3;
    private static final int LETTER_ADVANCE = 4;
    static final char SKULL = '\uE050';
    private static final char HOLLOW_SKULL = SKULL + 1;
    /** The skull with the crosshair's headshot diamond at its corner. */
    private static final char HEADSHOT_SKULL = SKULL + 2;
    private static final int SKULL_ADVANCE = 8;
    private static final int HEADSHOT_ADVANCE = 11;

    private final int ticks;
    private final Map<UUID, Deque<Entry>> feeds = new HashMap<>();

    KillFeed(int ticks) {
        this.ticks = ticks;
    }

    static KillFeed from(ConfigurationSection config) {
        return new KillFeed(Math.clamp(config.getInt("kill-feed.seconds", 5), 1, 30) * 20);
    }

    void add(UUID player, Kind kind, Optional<Named> other, long tick) {
        var feed = feeds.computeIfAbsent(player, ignored -> new ArrayDeque<>());
        feed.addFirst(new Entry(kind, other, tick + ticks));
        while (feed.size() > ROWS) {
            feed.removeLast();
        }
    }

    /** The player's lines still showing at {@code tick}, newest first. */
    List<Entry> live(UUID player, long tick) {
        var feed = feeds.get(player);
        if (feed == null) {
            return List.of();
        }
        feed.removeIf(entry -> entry.until() <= tick);
        return List.copyOf(feed);
    }

    void forget(UUID player) {
        feeds.remove(player);
    }

    void clear() {
        feeds.clear();
    }

    /** The lines right-aligned on the panel's origin, which is its top-right corner, so every glyph lies left of it. */
    static List<Glyph> layout(List<Entry> entries, String own, long tick) {
        var glyphs = new ArrayList<Glyph>();
        for (int row = 0; row < Math.min(ROWS, entries.size()); row++) {
            glyphs.addAll(line(entries.get(row), own, row * ROW, tick));
        }
        return glyphs;
    }

    private static List<Glyph> line(Entry entry, String own, int y, long tick) {
        boolean fading = entry.until() - tick <= FADE_TICKS;
        var self = new Named(own, Tint.WHITE);
        var left = entry.kind() == Kind.DEATH ? entry.other() : Optional.of(self);
        var right = entry.kind() == Kind.DEATH ? Optional.of(self) : entry.other();
        var parts = new ArrayList<Glyph>();
        left.ifPresent(named -> parts.add(name(named, y)));
        parts.add(switch (entry.kind()) {
            case HEADSHOT -> new Glyph(String.valueOf(HEADSHOT_SKULL), 0, y, Tint.WHITE, true, HEADSHOT_ADVANCE);
            case ASSIST -> new Glyph(String.valueOf(HOLLOW_SKULL), 0, y, Tint.WHITE, true, SKULL_ADVANCE);
            case KILL, DEATH -> new Glyph(String.valueOf(SKULL), 0, y, Tint.WHITE, true, SKULL_ADVANCE);
        });
        right.ifPresent(named -> parts.add(name(named, y)));
        int width = parts.stream().mapToInt(Glyph::advance).sum() + GAP * (parts.size() - 1);
        var placed = new ArrayList<Glyph>();
        int x = -width;
        for (var part : parts) {
            placed.add(new Glyph(part.text(), x, y, fading ? Tint.DIMMED : part.tint(), true, part.advance()));
            x += part.advance() + GAP;
        }
        return placed;
    }

    private static Glyph name(Named named, int y) {
        var label = MinimapLayout.label(named.name());
        return new Glyph(label, 0, y, named.tint(), true, label.length() * LETTER_ADVANCE);
    }
}
