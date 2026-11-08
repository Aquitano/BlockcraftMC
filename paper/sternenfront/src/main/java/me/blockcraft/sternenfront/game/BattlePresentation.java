package me.blockcraft.sternenfront.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.presentation.PresentationScope;
import me.blockcraft.api.presentation.ScreenEffects;
import me.blockcraft.api.scene.Scenes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleConsumer;

final class BattlePresentation implements AutoCloseable {
    private final SternenfrontGame game;
    private final Map<UUID, PresentationScope> deployments = new HashMap<>();
    private final Map<Integer, PresentationScope> captures = new HashMap<>();

    BattlePresentation(SternenfrontGame game) {
        this.game = game;
    }

    void arrive(Player player, Runnable deploy) {
        transition(player, deploy, true);
    }

    void deploy(Player player, Runnable deploy) {
        transition(player, deploy, false);
    }

    private void transition(Player player, Runnable deploy, boolean arrival) {
        if (deployments.containsKey(player.getUniqueId())) return;
        int fadeTicks = Math.clamp(game.plugin().getConfig().getInt("presentation.deployment.fade-ticks"), 0, 40);
        if (fadeTicks == 0 || !game.plugin().getConfig().getBoolean("presentation.effects")
                || !BlockcraftApi.get().resourcePack().applied(player) || busy(player)) {
            deploy.run();
            return;
        }
        var scope = new PresentationScope(game.plugin());
        deployments.put(player.getUniqueId(), scope);
        scope.own(() -> deployments.remove(player.getUniqueId(), scope));
        var world = player.getWorld();
        DoubleConsumer frame = fade -> ScreenEffects.frame(scope, player, fade, false);
        var transition = arrival ? DeploymentTransition.arrival(fadeTicks, deploy, frame)
                : new DeploymentTransition(fadeTicks, deploy, frame);
        scope.own(transition::close);
        try {
            if (transition.tick() || scope.closed()) {
                scope.close();
                return;
            }
        } catch (RuntimeException failure) {
            scope.close();
            game.plugin().getSLF4JLogger().warn("Could not start deployment transition", failure);
            return;
        }
        var task = new BukkitRunnable() {
            @Override
            public void run() {
                if (scope.closed()) return;
                if (!player.isOnline() || player.isDead() || player.getWorld() != world
                        || game.phase() != GamePhase.INGAME || !game.isPlaying(player) || busy(player)) {
                    scope.close();
                    return;
                }
                try {
                    if (transition.tick()) scope.close();
                } catch (RuntimeException failure) {
                    scope.close();
                    game.plugin().getSLF4JLogger().warn("Deployment transition failed", failure);
                }
            }
        }.runTaskTimer(game.plugin(), 1, 1);
        scope.own(task::cancel);
    }

    void deployed(Player player, Spawn spawn) {
        if (!game.plugin().getConfig().getBoolean("presentation.deployment.briefing")) return;
        var location = switch (spawn) {
            case Spawn.Headquarters ignored -> game.messages().get("deploy.headquarters");
            case Spawn.Post post -> game.messages().get("deploy.post", game.posts().name(post.index()));
            case Spawn.Dropship ignored -> game.messages().get("deploy.dropship-plain");
        };
        int ticks = Math.clamp(game.plugin().getConfig().getInt("presentation.deployment.banner-ticks"), 1, 100);
        BlockcraftApi.get().notifications().important(player, Title.title(Component.empty(),
                game.messages().get("deploy.location", Placeholder.component("location", location)),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(ticks * 50L), Duration.ofMillis(300))));
    }

    void captured(int index, Faction faction, List<Player> present) {
        if (!game.plugin().getConfig().getBoolean("presentation.effects")) return;
        int duration = Math.clamp(game.plugin().getConfig().getInt("presentation.capture.ticks"), 0, 100);
        if (duration == 0) return;
        var previous = captures.remove(index);
        if (previous != null) previous.close();
        var scope = new PresentationScope(game.plugin());
        captures.put(index, scope);
        scope.own(() -> captures.remove(index, scope));
        var center = game.map().posts().get(index).center().clone().add(0, 0.2, 0);
        double configuredRadius = game.plugin().getConfig().getDouble("presentation.capture.radius");
        double radius = Double.isFinite(configuredRadius) ? Math.clamp(configuredRadius, 1, 16) : 6;
        int points = Math.clamp(game.plugin().getConfig().getInt("presentation.capture.points"), 8, 64);
        var dust = new Particle.DustOptions(faction.bolt(), 1.2f);
        var task = new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (scope.closed()) return;
                if (game.phase() != GamePhase.INGAME || tick >= duration) {
                    scope.close();
                    return;
                }
                double size = radius * (tick + 1.0) / duration;
                for (int point = 0; point < points; point++) {
                    double angle = point * Math.TAU / points;
                    center.getWorld().spawnParticle(Particle.DUST, center.getX() + Math.cos(angle) * size,
                            center.getY(), center.getZ() + Math.sin(angle) * size, 1, 0, 0, 0, 0, dust);
                }
                tick += 2;
            }
        }.runTaskTimer(game.plugin(), 0, 2);
        scope.own(task::cancel);
        var title = Title.title(Component.empty(), game.messages().get("post.capture-banner", game.posts().name(index)),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(duration * 50L), Duration.ofMillis(400)));
        present.stream().filter(player -> !busy(player)).forEach(player -> BlockcraftApi.get().notifications().important(player, title));
    }

    private static boolean busy(Player player) {
        var api = BlockcraftApi.get();
        return Scenes.isViewing(player) || api.dialogues().active(player) || api.visuals().active(player);
    }

    void cancel(Player player) {
        var scope = deployments.remove(player.getUniqueId());
        if (scope != null) scope.close();
    }

    @Override
    public void close() {
        for (var scope : deployments.values().toArray(PresentationScope[]::new)) scope.close();
        for (var scope : captures.values().toArray(PresentationScope[]::new)) scope.close();
    }
}
