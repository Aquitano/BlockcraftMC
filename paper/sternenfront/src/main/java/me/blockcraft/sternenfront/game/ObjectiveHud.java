package me.blockcraft.sternenfront.game;

import me.blockcraft.api.game.TimerBar;
import me.blockcraft.api.pack.UiFont;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * The round at the top of the screen for the players in it: their own side's reinforcements on the left, a badge per command post in its
 * holder's colour, the assault's priority framed in the minimap's yellow corners, the enemy's reinforcements on the right
 * and the time left. It takes a boss bar's text line because that is the one place text stays on top at every GUI scale;
 * the pack draws this colour and overlay of bar as nothing.
 */
final class ObjectiveHud {

    private static final char FIRST_BADGE = '\uE230';
    /** The minimap's corners round the post to make for, a pixel clear of the badge on every side. */
    private static final char TARGET = '\uE225';
    private static final int TARGET_ADVANCE = 14;
    private static final int TARGET_MARGIN = 1;

    private final SternenfrontGame game;
    private final Messages messages;
    private final Map<Faction, BossBar> bars = new EnumMap<>(Faction.class);

    ObjectiveHud(SternenfrontGame game) {
        this.game = game;
        messages = game.messages();
        for (var faction : Faction.values()) {
            bars.put(faction, BossBar.bossBar(Component.empty(), 0, BossBar.Color.PINK, BossBar.Overlay.NOTCHED_20));
        }
    }

    void update(Reinforcements reinforcements, int secondsLeft, long tick) {
        var badges = Component.text();
        for (int index : game.posts().indices()) {
            badges.append(badge(game.posts(), index, tick)).append(Component.space());
        }
        bars.forEach((faction, bar) -> bar.name(messages.get("hud.objective",
                Placeholder.component("own", Component.text(reinforcements.left(faction), faction.text())),
                Placeholder.component("posts", badges.build()),
                Placeholder.component("enemy", Component.text(reinforcements.left(faction.enemy()), faction.enemy().text())),
                Placeholder.unparsed("time", TimerBar.clock(secondsLeft)))));
        Bukkit.getOnlinePlayers().forEach(this::show);
    }

    /**
     * The viewer's side's line, first of the round's boss bars, so every bar shown after it keeps its slot. A spectator has
     * no side whose reinforcements are "ours", so they see none.
     */
    void show(Player viewer) {
        var side = game.isPlaying(viewer) ? game.factionOf(viewer) : Optional.<Faction>empty();
        bars.forEach((faction, bar) -> {
            if (side.filter(faction::equals).isEmpty()) {
                viewer.hideBossBar(bar);
            } else if (StreamSupport.stream(bar.viewers().spliterator(), false).noneMatch(viewer::equals)) {
                viewer.showBossBar(bar);
                game.minimap().restack(viewer);
            }
        });
    }

    /** The post's badge in its holder's colour, framed in yellow corners that take no room while it is the priority. */
    private static Component badge(Posts posts, int index, long tick) {
        var colour = posts.owner(index).map(Faction::text).orElse(NamedTextColor.WHITE);
        var badge = UiFont.glyph(PostBadge.of(posts, index, tick).glyph(FIRST_BADGE)).color(colour);
        if (!posts.priority(index)) {
            return badge;
        }
        return Component.empty().append(UiFont.space(-TARGET_MARGIN)).append(UiFont.glyph(TARGET).color(NamedTextColor.YELLOW))
                .append(UiFont.space(TARGET_MARGIN - TARGET_ADVANCE)).append(badge);
    }

    void clear() {
        for (var viewer : Bukkit.getOnlinePlayers()) {
            bars.values().forEach(viewer::hideBossBar);
        }
    }
}
