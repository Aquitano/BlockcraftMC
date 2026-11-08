package me.blockcraft.sternenfront.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.ShotEvent;
import me.blockcraft.sternenfront.bot.Bots;
import me.blockcraft.sternenfront.combat.CombatHud;
import me.blockcraft.sternenfront.game.HudPanel.Corner;
import me.blockcraft.sternenfront.game.HudPanel.Glyph;
import me.blockcraft.sternenfront.game.HudPanel.Tint;
import me.blockcraft.sternenfront.game.MinimapLayout.Mark;
import me.blockcraft.sternenfront.game.MinimapLayout.Member;
import me.blockcraft.sternenfront.game.MinimapLayout.Role;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The map and the squad in the screen's top-left corner while a player with the pack is deployed, and their
 * {@link KillFeed} in the top-right while it has lines, on a boss bar line of their own that the pack's text shader
 * moves into both corners. It redraws every few ticks and sends nothing while nothing on it moved. An enemy shows only
 * for a while after it fired, and only if the player's client draws it at all.
 */
final class Minimap implements Listener {

    record Settings(double radius, int updateTicks, int enemyTicks) {

        static Settings from(ConfigurationSection config) {
            return new Settings(Math.clamp(config.getDouble("minimap.radius", 48), 8, 256),
                    Math.clamp(config.getInt("minimap.update-ticks", 4), 1, 20),
                    Math.clamp(config.getInt("minimap.enemy-ticks", 60), 0, 600));
        }
    }

    private static final class Panel {
        private final BossBar bar = BossBar.bossBar(Component.empty(), 0, BossBar.Color.PINK, BossBar.Overlay.NOTCHED_20);
        private List<Glyph> drawnMap = List.of();
        private List<Glyph> drawnFeed = List.of();
        private boolean shown;
    }

    private final SternenfrontGame game;
    private final Settings settings;
    private final Map<UUID, Panel> panels = new HashMap<>();
    private final Map<UUID, Long> shots = new HashMap<>();
    private final Map<Integer, String> botNames = new HashMap<>();

    Minimap(SternenfrontGame game, Settings settings) {
        this.game = game;
        this.settings = settings;
        Bukkit.getPluginManager().registerEvents(this, game.plugin());
    }

    @EventHandler
    public void onShot(ShotEvent event) {
        shots.put(event.shooter().getUniqueId(), game.tick());
    }

    void tick(long tick) {
        if (tick % settings.updateTicks() != 0) {
            return;
        }
        var field = game.combatants();
        for (var player : Bukkit.getOnlinePlayers()) {
            boolean seen = game.isPlaying(player) && BlockcraftApi.get().resourcePack().applied(player) && !CombatHud.covered(player);
            var map = seen && game.onField(player) ? map(player, field, tick) : List.<Glyph>of();
            var feed = seen ? KillFeed.layout(game.killFeed().live(player.getUniqueId(), tick), player.getName(), tick) : List.<Glyph>of();
            if (map.isEmpty() && feed.isEmpty()) {
                hide(player);
            } else {
                draw(player, map, feed);
            }
        }
    }

    /**
     * The client stacks boss bars in the order they arrive, and this line takes a slot like any other; it goes after a
     * line shown later, so the lines above it keep their places.
     */
    void restack(Player viewer) {
        var panel = panels.get(viewer.getUniqueId());
        if (panel != null && panel.shown) {
            viewer.hideBossBar(panel.bar);
            viewer.showBossBar(panel.bar);
        }
    }

    private List<Glyph> map(Player viewer, List<LivingEntity> field, long tick) {
        var side = game.factionOf(viewer).orElseThrow();
        var here = viewer.getLocation();
        var view = new MinimapLayout.View(here.getX(), here.getZ(), settings.radius());
        var glyphs = new ArrayList<>(MinimapLayout.disc());
        game.map().headquarters().forEach((faction, headquarters) -> {
            var centre = headquarters.zone().getCenter();
            view.inside(centre.getX(), centre.getZ()).ifPresent(pixel -> glyphs.add(MinimapLayout.mark(Mark.HEADQUARTERS, pixel, Tint.of(faction))));
        });
        long second = tick / 20;
        // Posts on one bearing share the rim with nearer ones, and the nearer badge goes on top.
        var farthestFirst = game.posts().indices().stream()
                .sorted(Comparator.comparingDouble((Integer index) -> -across(game.map().posts().get(index).center(), here))).toList();
        for (int index : farthestFirst) {
            var centre = game.map().posts().get(index).center();
            glyphs.add(MinimapLayout.badge(index, game.posts().contested(index) && second % 2 == 0,
                    view.clamped(centre.getX(), centre.getZ(), MinimapLayout.BADGE_REACH),
                    game.posts().owner(index).map(Tint::of).orElse(Tint.WHITE)));
        }
        game.deployment().postToHeadFor(viewer).ifPresent(target -> {
            var centre = game.map().posts().get(target).center();
            glyphs.add(MinimapLayout.target(view.clamped(centre.getX(), centre.getZ(), MinimapLayout.BADGE_REACH)));
        });
        var squadmates = game.bots().squadmates(viewer);
        var squadPlayers = game.bots().squadPlayers(viewer);
        var squad = new HashSet<>(squadPlayers);
        squadmates.forEach(mate -> mate.body().ifPresent(body -> squad.add(body.getUniqueId())));
        var enemies = new ArrayList<Glyph>();
        var squadDots = new ArrayList<Glyph>();
        for (var actor : field) {
            if (actor.equals(viewer) || !actor.getWorld().equals(here.getWorld()) || !visible(viewer, actor)) {
                continue;
            }
            var faction = game.factionOf(actor);
            if (faction.isEmpty()) {
                continue;
            }
            boolean ally = faction.get() == side;
            var shot = shots.get(actor.getUniqueId());
            if (!ally && (shot == null || tick - shot > settings.enemyTicks())) {
                continue;
            }
            var at = actor.getLocation();
            view.inside(at.getX(), at.getZ()).ifPresent(pixel -> {
                if (!ally) {
                    enemies.add(MinimapLayout.mark(Mark.ENEMY, pixel, Tint.of(faction.get())));
                } else if (squad.contains(actor.getUniqueId())) {
                    squadDots.add(MinimapLayout.mark(Mark.SQUADMATE, pixel, Tint.SQUAD));
                } else {
                    glyphs.add(MinimapLayout.mark(Mark.ALLY, pixel, Tint.of(side)));
                }
            });
        }
        glyphs.addAll(squadDots);
        glyphs.addAll(enemies);
        glyphs.add(MinimapLayout.arrow(here.getYaw()));
        glyphs.addAll(MinimapLayout.list(members(viewer, side, squadPlayers, squadmates)));
        return glyphs;
    }

    private void draw(Player viewer, List<Glyph> map, List<Glyph> feed) {
        var panel = panels.computeIfAbsent(viewer.getUniqueId(), ignored -> new Panel());
        if (!map.equals(panel.drawnMap) || !feed.equals(panel.drawnFeed)) {
            panel.drawnMap = List.copyOf(map);
            panel.drawnFeed = List.copyOf(feed);
            panel.bar.name(MinimapLayout.render(map).append(HudPanel.render(Corner.TOP_RIGHT, MinimapLayout.FONT, feed)));
        }
        if (!panel.shown) {
            viewer.showBossBar(panel.bar);
            panel.shown = true;
        }
    }

    private static double across(Location at, Location here) {
        return Math.hypot(at.getX() - here.getX(), at.getZ() - here.getZ());
    }

    /** Never more than the player's client draws: no vanished staff, and nobody it was told to hide. */
    private boolean visible(Player viewer, LivingEntity actor) {
        return !(actor instanceof Player player && BlockcraftApi.get().isVanished(player)) && viewer.canSee(game.drawnAs(viewer, actor));
    }

    private List<Member> members(Player viewer, Faction side, List<UUID> squadPlayers, List<Bots.Squadmate> squadmates) {
        var members = new ArrayList<Member>();
        members.add(new Member(viewer.getName(), role(viewer), Tint.WHITE, true));
        squadPlayers.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).sorted(Comparator.comparing(Player::getName))
                .forEach(player -> members.add(new Member(player.getName(), role(player), Tint.SQUAD, game.onField(player))));
        squadmates.forEach(mate -> members.add(new Member(botName(side, mate), Role.of(mate.role()), Tint.SQUAD, mate.body().isPresent())));
        return members;
    }

    private Role role(Player player) {
        return game.heroOf(player).isPresent() ? Role.HERO : Role.of(game.fighter(player).combatClass());
    }

    String botName(Faction side, Bots.Squadmate mate) {
        return botNames.computeIfAbsent(mate.number(), number -> PlainTextComponentSerializer.plainText().serialize(
                game.messages().get("minimap.bot." + side.key() + "." + mate.role().key(), Formatter.number("number", number))));
    }

    private void hide(Player player) {
        var panel = panels.get(player.getUniqueId());
        if (panel != null && panel.shown) {
            player.hideBossBar(panel.bar);
            panel.shown = false;
        }
    }

    void forget(Player player) {
        hide(player);
        panels.remove(player.getUniqueId());
    }

    void clear() {
        Bukkit.getOnlinePlayers().forEach(this::hide);
        panels.clear();
        shots.clear();
        botNames.clear();
    }
}
