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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The round at the top of the screen: each player's own reinforcements on the left, a tile per command post in its
 * holder's colour, the enemy's reinforcements on the right and the time left. It takes a boss bar's text line because
 * that is the one place text stays on top at every GUI scale; the pack draws this colour and overlay of bar as nothing.
 */
final class ObjectiveHud {

    private static final char FIRST_PIP = '\uE230';

    private final SternenfrontGame game;
    private final Messages messages;
    private final Map<Faction, BossBar> bars = new EnumMap<>(Faction.class);
    private final Map<UUID, BossBar> orderBars = new HashMap<>();

    ObjectiveHud(SternenfrontGame game) {
        this.game = game;
        messages = game.messages();
        for (var faction : Faction.values()) {
            bars.put(faction, BossBar.bossBar(Component.empty(), 0, BossBar.Color.PINK, BossBar.Overlay.NOTCHED_20));
        }
    }

    void update(Reinforcements reinforcements, int secondsLeft, long second) {
        var tiles = Component.text();
        for (int index : game.posts().indices()) {
            tiles.append(tile(game.posts(), index, second)).append(Component.space());
        }
        bars.forEach((faction, bar) -> bar.name(messages.get("hud.objective",
                Placeholder.component("own", Component.text(reinforcements.left(faction), faction.text())),
                Placeholder.component("posts", tiles.build()),
                Placeholder.component("enemy", Component.text(reinforcements.left(faction.enemy()), faction.enemy().text())),
                Placeholder.unparsed("time", TimerBar.clock(secondsLeft)))));
        for (var viewer : Bukkit.getOnlinePlayers()) {
            var side = game.factionOf(viewer).orElse(Faction.KOLONIEN);
            bars.forEach((faction, bar) -> {
                if (faction == side) {
                    viewer.showBossBar(bar);
                } else {
                    viewer.hideBossBar(bar);
                }
            });
            order(viewer);
        }
    }

    /** A second line under the first, added after it, with the fighter's current order. */
    void order(Player viewer) {
        var order = game.orderLine(viewer);
        game.deployment().guidance(viewer).map(navigation -> order
                .map(task -> navigation.append(messages.get("hud.separator")).append(task)).orElse(navigation))
                .or(() -> order).ifPresentOrElse(line -> {
            var bar = orderBars.computeIfAbsent(viewer.getUniqueId(),
                    ignored -> BossBar.bossBar(Component.empty(), 0, BossBar.Color.PINK, BossBar.Overlay.NOTCHED_20));
            bar.name(line);
            viewer.showBossBar(bar);
        }, () -> {
            var bar = orderBars.remove(viewer.getUniqueId());
            if (bar != null) {
                viewer.hideBossBar(bar);
            }
        });
    }

    private Component tile(Posts posts, int index, long second) {
        boolean hollow = posts.contested(index) && second % 2 == 0;
        var colour = posts.owner(index).map(Faction::text).orElse(NamedTextColor.WHITE);
        var pip = UiFont.glyph((char) (FIRST_PIP + index * 2 + (hollow ? 1 : 0))).color(colour);
        return posts.priority(index) ? messages.get("hud.priority").append(pip) : pip;
    }

    void clear() {
        for (var viewer : Bukkit.getOnlinePlayers()) {
            bars.values().forEach(viewer::hideBossBar);
            orderBars.values().forEach(viewer::hideBossBar);
        }
        orderBars.clear();
    }
}
