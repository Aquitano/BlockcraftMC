package me.blockcraft.sternenfront.game;

import me.blockcraft.api.game.TimerBar;
import me.blockcraft.api.pack.UiFont;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.StreamSupport;

/**
 * The round at the top of the screen: each player's own reinforcements on the left, a badge per command post in its
 * holder's colour, the enemy's reinforcements on the right and the time left. It takes a boss bar's text line because
 * that is the one place text stays on top at every GUI scale; the pack draws this colour and overlay of bar as nothing.
 */
final class ObjectiveHud {

    private static final char FIRST_BADGE = '\uE230';

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
        for (var viewer : Bukkit.getOnlinePlayers()) {
            var side = game.factionOf(viewer).orElse(Faction.KOLONIEN);
            bars.forEach((faction, bar) -> {
                if (faction == side) {
                    boolean added = StreamSupport.stream(bar.viewers().spliterator(), false).noneMatch(viewer::equals);
                    viewer.showBossBar(bar);
                    if (added) {
                        game.minimap().restack(viewer);
                    }
                } else {
                    viewer.hideBossBar(bar);
                }
            });
        }
    }

    private Component badge(Posts posts, int index, long tick) {
        var colour = posts.owner(index).map(Faction::text).orElse(NamedTextColor.WHITE);
        var badge = UiFont.glyph(PostBadge.of(posts, index, tick).glyph(FIRST_BADGE)).color(colour);
        return posts.priority(index) ? messages.get("hud.priority").append(badge) : badge;
    }

    void clear() {
        for (var viewer : Bukkit.getOnlinePlayers()) {
            bars.values().forEach(viewer::hideBossBar);
        }
    }
}
