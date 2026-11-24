package me.blockcraft.zombies.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.presentation.PresentationScope;
import me.blockcraft.api.presentation.ScreenEffects;
import me.blockcraft.zombies.config.ZombiesConfig;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class ZombiesPresentation {

    private record Listener(Player player, PresentationScope scope) { }
    private record Recovery(Player player, Location location, int started, PresentationScope scope) { }

    private static final Particle.DustOptions WAITING = new Particle.DustOptions(Color.fromRGB(85, 100, 105), 0.65f);
    private static final Particle.DustOptions HEALING = new Particle.DustOptions(Color.fromRGB(105, 245, 170), 0.85f);
    private final ZombiesGame game;
    private final Plugin plugin;
    private final ZombiesConfig.Presentation config;
    private final Map<UUID, Listener> listeners = new HashMap<>();
    private final Map<UUID, Recovery> recoveries = new HashMap<>();
    private PresentationScope outage;
    private Location generator;
    private int nextAmbient;

    ZombiesPresentation(ZombiesGame game, Plugin plugin, ZombiesConfig.Presentation config) {
        this.game = game;
        this.plugin = plugin;
        this.config = config;
    }

    void outage(Location source) {
        restorePower();
        if (!config.enabled()) return;
        generator = source.clone().add(0.5, 1, 0.5);
        outage = new PresentationScope(plugin);
        outage.own(() -> {
            listeners.values().forEach(listener -> listener.scope().close());
            listeners.clear();
        });
        nextAmbient = game.tickNumber();
        for (var player : game.fighters()) {
            var scope = listen(player);
            BlockcraftApi.get().visuals().accent(scope, player, ScreenEffects.Atmosphere.STORM, config.outageAccentTicks());
        }
    }

    private PresentationScope listen(Player player) {
        var scope = new PresentationScope(plugin);
        scope.own(() -> player.stopSound(Sound.BLOCK_CONDUIT_AMBIENT_SHORT, SoundCategory.AMBIENT));
        listeners.put(player.getUniqueId(), new Listener(player, scope));
        return scope;
    }

    void restorePower() {
        if (outage != null) {
            outage.close();
            outage = null;
        }
        generator = null;
    }

    void progress(Location location, double progress) {
        if (!config.enabled()) return;
        int completed = (int) Math.floor(Math.clamp(progress, 0, 1) * config.ringPoints());
        for (int point = 0; point < config.ringPoints(); point++) {
            double angle = 2 * Math.PI * point / config.ringPoints();
            location.getWorld().spawnParticle(Particle.DUST,
                    location.clone().add(Math.cos(angle) * config.reviveRadius(), 0.12, Math.sin(angle) * config.reviveRadius()),
                    1, 0, 0, 0, 0, point < completed ? HEALING : WAITING);
        }
    }

    void recovered(Player player) {
        endRecovery(player.getUniqueId());
        if (!config.enabled() || config.recoveryTicks() == 0) return;
        var scope = new PresentationScope(plugin);
        BlockcraftApi.get().visuals().accent(scope, player, ScreenEffects.Atmosphere.FOCUS, config.recoveryTicks());
        recoveries.put(player.getUniqueId(), new Recovery(player, player.getLocation().clone(), game.tickNumber(), scope));
    }

    void tick(int tick) {
        if (outage != null) {
            listeners.values().removeIf(listener -> {
                if (listener.player().isOnline() && game.fighting(listener.player())) return false;
                listener.scope().close();
                return true;
            });
            for (var player : game.fighters()) {
                if (!listeners.containsKey(player.getUniqueId())) listen(player);
            }
            if (tick >= nextAmbient) {
                nextAmbient = tick + config.ambientTicks();
                if (config.ambientVolume() > 0) {
                    listeners.values().forEach(listener -> listener.player().playSound(listener.player().getLocation(),
                            Sound.BLOCK_CONDUIT_AMBIENT_SHORT, SoundCategory.AMBIENT, config.ambientVolume(), 0.6f));
                }
            }
            if (tick % 20 == 0) {
                generator.getWorld().spawnParticle(Particle.SMOKE, generator, 3, 0.2, 0.2, 0.2, 0.01);
            }
        }
        recoveries.values().removeIf(recovery -> {
            int age = tick - recovery.started();
            if (age >= config.recoveryTicks() || !recovery.player().isOnline() || !game.fighting(recovery.player())) {
                recovery.scope().close();
                return true;
            }
            if (tick % 2 == 0) {
                double radius = config.reviveRadius() * (1 + 1.5 * age / config.recoveryTicks());
                for (int point = 0; point < config.ringPoints(); point++) {
                    double angle = 2 * Math.PI * point / config.ringPoints();
                    recovery.location().getWorld().spawnParticle(Particle.DUST,
                            recovery.location().clone().add(Math.cos(angle) * radius, 0.15, Math.sin(angle) * radius),
                            1, 0, 0, 0, 0, HEALING);
                }
            }
            return false;
        });
    }

    void forget(Player player) {
        var listener = listeners.remove(player.getUniqueId());
        if (listener != null) listener.scope().close();
        endRecovery(player.getUniqueId());
    }

    private void endRecovery(UUID player) {
        var recovery = recoveries.remove(player);
        if (recovery != null) recovery.scope().close();
    }

    void clear() {
        restorePower();
        recoveries.values().forEach(recovery -> recovery.scope().close());
        recoveries.clear();
    }
}
