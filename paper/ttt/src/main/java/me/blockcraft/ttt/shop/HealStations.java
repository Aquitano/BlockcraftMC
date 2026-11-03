package me.blockcraft.ttt.shop;

import me.blockcraft.api.game.BlockChanges;
import me.blockcraft.common.message.Messages;
import me.blockcraft.ttt.config.TttConfig;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A detective's heal station heals everyone near it for a while. A traitor's poison turns it: it goes on looking the
 * same and hurts instead, and whoever dies of it is the poisoner's kill.
 */
final class HealStations {

    private static final class Station {
        private int left;
        private Optional<UUID> poisoner = Optional.empty();

        private Station(int left) {
            this.left = left;
        }
    }

    private final TttConfig.HealStation config;
    private final Messages messages;
    private final Culprits culprits;
    private final Supplier<List<Player>> alive;
    private final Map<Block, Station> stations = new HashMap<>();

    HealStations(TttConfig.HealStation config, Messages messages, Culprits culprits, Supplier<List<Player>> alive) {
        this.config = config;
        this.messages = messages;
        this.culprits = culprits;
        this.alive = alive;
    }

    void place(Block block) {
        stations.put(block, new Station(config.seconds()));
    }

    /** False if {@code block} is no heal station, or one already poisoned. */
    boolean poison(Player poisoner, Block block) {
        var station = stations.get(block);
        if (station == null || station.poisoner.isPresent()) {
            poisoner.sendMessage(messages.get("shop.poison.nothing"));
            return false;
        }
        station.poisoner = Optional.of(poisoner.getUniqueId());
        poisoner.sendMessage(messages.get("shop.poison.done"));
        poisoner.playSound(block.getLocation(), Sound.ITEM_BOTTLE_EMPTY, 0.6f, 0.8f);
        return true;
    }

    void tick() {
        stations.entrySet().removeIf(entry -> {
            var block = entry.getKey();
            var station = entry.getValue();
            station.left--;
            var center = block.getLocation().add(0.5, 1.0, 0.5);
            double radius = config.radius() * config.radius();
            for (var player : alive.get()) {
                if (!player.getWorld().equals(center.getWorld()) || player.getLocation().distanceSquared(center) > radius) {
                    continue;
                }
                station.poisoner.ifPresentOrElse(poisoner -> culprits.hurt(player, config.perSecond(), poisoner), () -> {
                    double max = player.getAttribute(Attribute.MAX_HEALTH).getValue();
                    player.setHealth(Math.min(max, player.getHealth() + config.perSecond()));
                });
            }
            center.getWorld().spawnParticle(Particle.HEART, center, 2, 0.6, 0.3, 0.6);
            if (station.left <= 0) {
                BlockChanges.set(block, Material.AIR);
                return true;
            }
            return false;
        });
    }

    void clear() {
        stations.clear();
    }
}
