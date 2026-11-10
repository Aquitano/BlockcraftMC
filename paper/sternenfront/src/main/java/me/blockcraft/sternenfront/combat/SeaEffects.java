package me.blockcraft.sternenfront.combat;

import me.blockcraft.sternenfront.arena.YardEnvironment.SeaSupport;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

final class SeaEffects {

    private static final String SURF = "blockcraft:sternenfront.sea.surf";

    private static final class Listening {
        private SeaSupport source;
        private long nextSurf;
        private long nextWind;

        private Listening(SeaSupport source) {
            this.source = source;
        }
    }

    private final SternenfrontGame game;
    private final boolean enabled;
    private final double rangeSquared;
    private final int interval;
    private final int maxSites;
    private final int crestParticles;
    private final int farEvery;
    private final double crestWidth;
    private final int sprayParticles;
    private final double spraySpeed;
    private final double windX;
    private final double windZ;
    private final double windSpeed;
    private final float windVolume;
    private final float windPitch;
    private final int windInterval;
    private final float surfVolume;
    private final int surfInterval;
    private final Particle.DustOptions foam;
    private final SeaMotion motion;
    private final Map<Player, Listening> listeners = new HashMap<>();

    SeaEffects(SternenfrontGame game) {
        this.game = game;
        var config = game.plugin().getConfig();
        String path = "presentation.sea.";
        enabled = config.getBoolean("presentation.effects") && config.getBoolean(path + "enabled");
        rangeSquared = Math.pow(Math.clamp(config.getDouble(path + "range"), 1, 96), 2);
        interval = Math.clamp(config.getInt(path + "interval-ticks"), 1, 20);
        maxSites = Math.clamp(config.getInt(path + "max-sites"), 1, 8);
        crestParticles = Math.clamp(config.getInt(path + "wave.particles"), 0, 16);
        farEvery = Math.max(1, config.getInt(path + "wave.far-every"));
        crestWidth = Math.clamp(config.getDouble(path + "wave.width"), 1, 20);
        int period = Math.max(interval * 4, config.getInt(path + "wave.period-ticks"));
        motion = new SeaMotion(period, Math.clamp(config.getInt(path + "wave.break-ticks"), interval * 2, period - interval),
                Math.clamp(config.getDouble(path + "wave.reach"), 1, 24));
        foam = new Particle.DustOptions(Color.fromRGB(Integer.parseInt(config.getString(path + "wave.color"), 16)),
                (float) Math.clamp(config.getDouble(path + "wave.foam-size"), 0.1, 2));
        sprayParticles = Math.clamp(config.getInt(path + "spray.particles"), 0, 16);
        spraySpeed = Math.clamp(config.getDouble(path + "spray.speed"), 0, 1);
        double heading = Math.toRadians(config.getDouble(path + "wind.yaw"));
        windX = -Math.sin(heading);
        windZ = Math.cos(heading);
        windSpeed = Math.clamp(config.getDouble(path + "wind.speed"), 0, 1);
        windVolume = (float) Math.clamp(config.getDouble(path + "wind.volume"), 0, 1);
        windPitch = (float) Math.clamp(config.getDouble(path + "wind.pitch"), 0.5, 2);
        windInterval = Math.max(20, config.getInt(path + "wind.interval-ticks"));
        surfVolume = (float) Math.clamp(config.getDouble(path + "surf.volume"), 0, 1);
        surfInterval = Math.max(20, config.getInt(path + "surf.interval-ticks"));
    }

    void tick(long tick) {
        if (!enabled || tick % interval != 0 || game.map().environment().sea().isEmpty()) return;
        var sites = game.map().environment().sea();
        var nearby = sites.stream().filter(SeaEffects::loaded).toList();
        var hearing = new HashSet<Player>();
        for (var viewer : game.map().world().getPlayers()) {
            var eye = viewer.getEyeLocation();
            var visible = nearby.stream().filter(site -> eye.distanceSquared(site.at()) <= rangeSquared)
                    .sorted(Comparator.comparingDouble(site -> eye.distanceSquared(site.at()))).limit(maxSites).toList();
            if (visible.isEmpty()) continue;
            hearing.add(viewer);
            for (var site : visible) {
                int phase = sites.indexOf(site) * motion.periodTicks() / sites.size();
                crest(viewer, site, tick, phase);
            }
            sound(viewer, visible.getFirst(), tick);
        }
        List.copyOf(listeners.keySet()).stream().filter(player -> !hearing.contains(player)).forEach(this::stop);
    }

    private void crest(Player viewer, SeaSupport support, long tick, int phase) {
        var at = support.at();
        double distance = support.radius() + motion.distance(tick, phase);
        double x = at.getX() - windX * distance;
        double z = at.getZ() - windZ * distance;
        for (int point = 0; point < crestParticles; point++) {
            double across = crestWidth * ((double) point / Math.max(1, crestParticles - 1) - 0.5);
            viewer.spawnParticle(Particle.DUST, x - windZ * across, at.getY(), z + windX * across,
                    1, 0.12, 0.025, 0.12, 0, foam, point % farEvery == 0);
        }
        int spray = (int) Math.round(sprayParticles * motion.impact(tick, phase));
        var random = ThreadLocalRandom.current();
        for (int drop = 0; drop < spray; drop++) {
            double across = random.nextDouble(-crestWidth, crestWidth) * 0.25;
            viewer.spawnParticle(Particle.SPLASH, x - windZ * across, at.getY(), z + windX * across,
                    0, windX * windSpeed, spraySpeed, windZ * windSpeed, 1);
        }
    }

    private void sound(Player viewer, SeaSupport closest, long tick) {
        var playing = listeners.computeIfAbsent(viewer, ignored -> new Listening(closest));
        if (!loaded(playing.source) || viewer.getLocation().distanceSquared(playing.source.at()) > rangeSquared) {
            stop(viewer);
            playing = new Listening(closest);
            listeners.put(viewer, playing);
        }
        if (surfVolume > 0 && tick >= playing.nextSurf) {
            viewer.stopSound(SURF, SoundCategory.WEATHER);
            playing.source = closest;
            viewer.playSound(closest.at(), SURF, SoundCategory.WEATHER, surfVolume, 1);
            playing.nextSurf = tick + surfInterval;
        }
        if (windVolume > 0 && tick >= playing.nextWind) {
            viewer.stopSound(Sound.ITEM_ELYTRA_FLYING, SoundCategory.WEATHER);
            viewer.playSound(viewer.getLocation(), Sound.ITEM_ELYTRA_FLYING, SoundCategory.WEATHER, windVolume, windPitch);
            playing.nextWind = tick + windInterval;
        }
    }

    private static boolean loaded(SeaSupport support) {
        Location at = support.at();
        return at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4);
    }

    private void stop(Player viewer) {
        viewer.stopSound(SURF, SoundCategory.WEATHER);
        viewer.stopSound(Sound.ITEM_ELYTRA_FLYING, SoundCategory.WEATHER);
        listeners.remove(viewer);
    }

    void clear() {
        List.copyOf(listeners.keySet()).forEach(this::stop);
    }
}
