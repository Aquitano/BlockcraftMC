package me.blockcraft.lobby.activity;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.lobby.config.HerobrineConfig;
import me.blockcraft.lobby.cosmetic.Secret;
import me.blockcraft.lobby.cosmetic.Secrets;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Now and then at night, Herobrine stands far off ahead of one lobby player, seen by nobody else, and stares at
 * them. He is gone after a while or as soon as they come close; whoever walks up to him finds a bonus secret.
 */
public final class Herobrine implements AutoCloseable {

    private static final long WATCH_TICKS = 5L;
    private static final double VIEW_CONE = Math.toRadians(50);
    private static final int SPOT_ATTEMPTS = 12;
    private static final int MAX_HEIGHT_DIFFERENCE = 12;

    private final Plugin plugin;
    private final HerobrineConfig config;
    private final Secrets secrets;
    private final BukkitTask check;
    private BukkitTask watch;
    private Mannequin figure;
    private long next = System.nanoTime();

    public Herobrine(Plugin plugin, HerobrineConfig config, Secrets secrets) {
        this.plugin = plugin;
        this.config = config;
        this.secrets = secrets;
        long interval = config.checkInterval().toSeconds() * 20;
        this.check = config.enabled() ? Bukkit.getScheduler().runTaskTimer(plugin, this::check, interval, interval) : null;
    }

    private void check() {
        var api = BlockcraftApi.get();
        var random = ThreadLocalRandom.current();
        if (figure != null || System.nanoTime() < next || !config.night().includes(api.calendar().now().getHour())
                || random.nextInt(config.odds()) != 0) {
            return;
        }
        var witnesses = Bukkit.getOnlinePlayers().stream().filter(player -> !api.isVanished(player) && !api.isAfk(player)).toList();
        if (witnesses.isEmpty()) {
            return;
        }
        var witness = witnesses.get(random.nextInt(witnesses.size()));
        spot(witness).ifPresent(spot -> appear(witness, spot));
    }

    /** Solid ground somewhere ahead of the witness, about as high as they stand, with room for a player. */
    private Optional<Location> spot(Player witness) {
        var at = witness.getLocation();
        var random = ThreadLocalRandom.current();
        double facing = Math.toRadians(at.getYaw());
        for (int attempt = 0; attempt < SPOT_ATTEMPTS; attempt++) {
            double angle = facing + random.nextDouble(-VIEW_CONE, VIEW_CONE);
            int x = (int) Math.floor(at.getX() - Math.sin(angle) * config.distance());
            int z = (int) Math.floor(at.getZ() + Math.cos(angle) * config.distance());
            var ground = at.getWorld().getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (ground.getType().isSolid() && Math.abs(ground.getY() - at.getY()) <= MAX_HEIGHT_DIFFERENCE
                    && ground.getRelative(0, 1, 0).isEmpty() && ground.getRelative(0, 2, 0).isEmpty()) {
                return Optional.of(ground.getLocation().add(0.5, 1, 0.5));
            }
        }
        return Optional.empty();
    }

    private void appear(Player witness, Location spot) {
        next = System.nanoTime() + config.cooldown().toNanos();
        spot.setDirection(witness.getLocation().toVector().subtract(spot.toVector()));
        var herobrine = Figures.spawn(spot, Skins.HEROBRINE, hidden -> hidden.setVisibleByDefault(false));
        figure = herobrine;
        witness.showEntity(plugin, herobrine);
        long until = System.nanoTime() + config.lifetime().toNanos();
        double vanishSquared = (double) config.vanishDistance() * config.vanishDistance();
        watch = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!witness.isOnline() || !witness.getWorld().equals(herobrine.getWorld()) || System.nanoTime() > until) {
                vanish();
                return;
            }
            var stare = herobrine.getLocation().setDirection(witness.getEyeLocation().toVector().subtract(herobrine.getEyeLocation().toVector()));
            herobrine.setRotation(stare.getYaw(), stare.getPitch());
            if (witness.getLocation().distanceSquared(herobrine.getLocation()) <= vanishSquared) {
                vanish();
                Sounds.HEROBRINE.play(witness);
                secrets.find(witness, Secret.HEROBRINE);
            }
        }, WATCH_TICKS, WATCH_TICKS);
    }

    private void vanish() {
        if (watch != null) {
            watch.cancel();
            watch = null;
        }
        if (figure != null) {
            figure.remove();
            figure = null;
        }
    }

    @Override
    public void close() {
        if (check != null) {
            check.cancel();
        }
        vanish();
    }
}
