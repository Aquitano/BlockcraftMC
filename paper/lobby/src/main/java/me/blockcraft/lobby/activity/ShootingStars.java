package me.blockcraft.lobby.activity;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.lobby.config.ShootingStarConfig;
import me.blockcraft.lobby.cosmetic.Secret;
import me.blockcraft.lobby.cosmetic.Secrets;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class ShootingStars implements AutoCloseable {

    private final Plugin plugin;
    private final ShootingStarConfig config;
    private final Messages messages;
    private final StarWishes wishes;
    private final BukkitTask check;
    private BukkitTask animation;
    private long next = System.nanoTime();

    public ShootingStars(Plugin plugin, ShootingStarConfig config, Messages messages, Secrets secrets) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.wishes = new StarWishes(messages, config.wishPrefixes(), player -> Tasks.sync(plugin, () -> {
            if (player.isOnline()) {
                secrets.find(player, Secret.SHOOTING_STAR);
            }
        }), System::nanoTime);
        long interval = config.checkInterval().toSeconds() * 20;
        this.check = config.enabled() ? Bukkit.getScheduler().runTaskTimer(plugin, this::check, interval, interval) : null;
    }

    public StarWishes wishes() {
        return wishes;
    }

    private void check() {
        long now = System.nanoTime();
        var api = BlockcraftApi.get();
        var viewers = Bukkit.getOnlinePlayers().stream().filter(player -> !api.isVanished(player)).toList();
        var observer = viewers.stream().filter(player -> !api.isAfk(player)).findFirst();
        if (now < next || !config.nightAt(Instant.now()) || observer.isEmpty()
                || ThreadLocalRandom.current().nextInt(config.odds()) != 0) {
            return;
        }
        next = now + config.cooldown().toNanos();
        wishes.open(config.wishWindow());
        viewers.forEach(player -> player.sendMessage(messages.get("shooting-star.announce",
                Formatter.number("seconds", config.wishWindow().toSeconds()), Placeholder.unparsed("phrase", config.wishPrefixes().getFirst()))));
        animate(observer.orElseThrow().getLocation(), viewers);
    }

    private void animate(Location center, List<? extends Player> viewers) {
        if (animation != null) {
            animation.cancel();
        }
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        double dx = Math.cos(angle) * config.length();
        double dz = Math.sin(angle) * config.length();
        var start = center.clone().add(-dx / 2, config.height(), -dz / 2);
        var delta = new org.bukkit.util.Vector(dx, -config.height() / 2.0, dz);
        animation = new BukkitRunnable() {
            private int frame;

            @Override
            public void run() {
                if (frame > config.animationTicks()) {
                    cancel();
                    return;
                }
                for (int tail = 0; tail < 6 && frame - tail >= 0; tail++) {
                    var position = start.clone().add(delta.clone().multiply((double) (frame - tail) / config.animationTicks()));
                    for (var viewer : viewers) {
                        if (viewer.isOnline() && viewer.getWorld().equals(center.getWorld())
                                && BlockcraftApi.get().settings().enabled(viewer, PlayerSetting.EFFECTS)) {
                            viewer.spawnParticle(tail == 0 ? Particle.FIREWORK : Particle.END_ROD, position, 1, 0, 0, 0, 0);
                        }
                    }
                }
                frame++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void close() {
        wishes.close();
        if (check != null) {
            check.cancel();
        }
        if (animation != null) {
            animation.cancel();
        }
    }
}
