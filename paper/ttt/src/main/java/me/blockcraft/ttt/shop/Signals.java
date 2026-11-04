package me.blockcraft.ttt.shop;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.ttt.config.TttConfig;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * What finds people and what keeps them from being found. The radar points at the nearest target for a while; a
 * motion sensor notes everyone who passes it and tells whoever reads it; the mine detector shows its holder the traps
 * nearby. A traitor's jammer silences all of these, and the tester, around its holder for a while.
 */
final class Signals {

    private static final int SIGHTING_COOLDOWN = 200;
    private static final int LOG_LENGTH = 12;
    private static final Particle.DustOptions TRAP_MARK = new Particle.DustOptions(Color.RED, 1.4f);

    private record Seen(String name, int tick) {
    }

    private record Sensor(Block space, Deque<Seen> log, Map<UUID, Integer> lastSeen) {

        private Sensor(Block space) {
            this(space, new ArrayDeque<>(), new HashMap<>());
        }
    }

    private final TttConfig.Shop config;
    private final Messages messages;
    private final Gadgets gadgets;
    private final Predicate<Player> traitor;
    private final Supplier<List<Player>> alive;
    private final Supplier<List<Location>> traps;
    private final Function<ShopItem, ItemStack> look;
    private final Map<UUID, Integer> radars = new HashMap<>();
    private final Map<UUID, Integer> jammers = new HashMap<>();
    private final Map<UUID, Sensor> sensors = new HashMap<>();

    Signals(TttConfig.Shop config, Messages messages, Gadgets gadgets, Predicate<Player> traitor,
            Supplier<List<Player>> alive, Supplier<List<Location>> traps, Function<ShopItem, ItemStack> look) {
        this.config = config;
        this.messages = messages;
        this.gadgets = gadgets;
        this.traitor = traitor;
        this.alive = alive;
        this.traps = traps;
        this.look = look;
    }

    void radar(Player player) {
        radars.put(player.getUniqueId(), config.radarSeconds());
    }

    void jam(Player player) {
        jammers.put(player.getUniqueId(), config.jammer().seconds());
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 0.5f);
        BlockcraftApi.get().notifications().feedback(player, messages.get("shop.jammer.started", Formatter.number("seconds", config.jammer().seconds())));
    }

    /** Whether a living jammer's holder stands within its radius of {@code location}. */
    boolean jammed(Location location) {
        double radius = config.jammer().radius() * config.jammer().radius();
        return jammers.keySet().stream().map(Bukkit::getPlayer)
                .anyMatch(holder -> holder != null && holder.getWorld().equals(location.getWorld())
                        && holder.getLocation().distanceSquared(location) <= radius);
    }

    boolean placeSensor(Player owner, Block space) {
        if (!Gadgets.fits(space)) {
            BlockcraftApi.get().notifications().feedback(owner, messages.get("shop.no-room"));
            Sounds.MENU_DENY.play(owner);
            return false;
        }
        gadgets.show(space, owner.getLocation().getYaw(), look.apply(ShopItem.MOTION_SENSOR));
        var hitbox = gadgets.hitbox(space, 0.6f, 0.6f);
        sensors.put(hitbox.getUniqueId(), new Sensor(space));
        BlockcraftApi.get().notifications().feedback(owner, messages.get("shop.motion-sensor.set"));
        return true;
    }

    /** True if {@code clicked} is a motion sensor; the reader then sees its log. */
    boolean read(Player reader, Entity clicked) {
        var sensor = sensors.get(clicked.getUniqueId());
        if (sensor == null) {
            return false;
        }
        if (jammed(sensor.space().getLocation())) {
            BlockcraftApi.get().notifications().feedback(reader, messages.get("shop.jammer.blocked"));
            Sounds.MENU_DENY.play(reader);
            return true;
        }
        int now = Bukkit.getCurrentTick();
        var sightings = sensor.log().stream()
                .map(seen -> new SensorLogDialog.Sighting(seen.name(), (now - seen.tick()) / 20)).toList();
        SensorLogDialog.show(reader, messages, sightings, () -> sensors.containsKey(clicked.getUniqueId()));
        return true;
    }

    /** Twice a second: every sensor notes who is within its radius, each passer-by once in a while. */
    void watch() {
        int now = Bukkit.getCurrentTick();
        double radius = config.motionSensorRadius() * config.motionSensorRadius();
        var living = alive.get();
        for (var sensor : sensors.values()) {
            var at = sensor.space().getLocation().add(0.5, 0.5, 0.5);
            if (jammed(at)) {
                continue;
            }
            for (var player : living) {
                if (!player.getWorld().equals(at.getWorld()) || player.getLocation().distanceSquared(at) > radius) {
                    continue;
                }
                var last = sensor.lastSeen().get(player.getUniqueId());
                if (last != null && now - last < SIGHTING_COOLDOWN) {
                    continue;
                }
                sensor.lastSeen().put(player.getUniqueId(), now);
                sensor.log().addFirst(new Seen(player.getName(), now));
                if (sensor.log().size() > LOG_LENGTH) {
                    sensor.log().removeLast();
                }
            }
        }
    }

    /** Once a second: radars point and run out, jammers run out, mine detectors mark the traps nearby. */
    void tick() {
        var living = alive.get();
        jammers.entrySet().removeIf(entry -> {
            var holder = Bukkit.getPlayer(entry.getKey());
            entry.setValue(entry.getValue() - 1);
            if (holder == null || !living.contains(holder) || entry.getValue() <= 0) {
                if (holder != null) {
                    BlockcraftApi.get().notifications().feedback(holder, messages.get("shop.jammer.stopped"));
                }
                return true;
            }
            return false;
        });
        radars.entrySet().removeIf(entry -> {
            var player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !living.contains(player)) {
                return true;
            }
            entry.setValue(entry.getValue() - 1);
            if (entry.getValue() <= 0) {
                player.getInventory().all(Material.COMPASS).forEach((slot, stack) -> {
                    if (HotbarItem.of(stack, ShopItem.class).isPresent()) {
                        player.getInventory().clear(slot);
                    }
                });
                BlockcraftApi.get().notifications().feedback(player, messages.get("shop.radar-expired"));
                return true;
            }
            point(player, living);
            return false;
        });
        for (var player : living) {
            if (HotbarItem.of(player.getInventory().getItemInMainHand(), ShopItem.class)
                    .filter(item -> item == ShopItem.MINE_DETECTOR).isPresent()) {
                detect(player);
            }
        }
    }

    void forget(Player player) {
        radars.remove(player.getUniqueId());
        jammers.remove(player.getUniqueId());
    }

    void clear() {
        radars.clear();
        jammers.clear();
        sensors.clear();
    }

    private void point(Player player, List<Player> living) {
        if (jammed(player.getLocation())) {
            BlockcraftApi.get().notifications().status(player, messages.get("shop.jammer.static"));
            return;
        }
        boolean hunter = traitor.test(player);
        var origin = player.getLocation();
        living.stream()
                .filter(other -> !other.equals(player) && other.getWorld().equals(origin.getWorld()))
                .filter(other -> !hunter || !traitor.test(other))
                .min(Comparator.comparingDouble(other -> other.getLocation().distanceSquared(origin)))
                .ifPresent(target -> {
                    player.setCompassTarget(target.getLocation());
                    BlockcraftApi.get().notifications().status(player, messages.get("shop.radar-distance",
                            Formatter.number("distance", (int) origin.distance(target.getLocation()))));
                });
    }

    /** Marks only the holder can see, so the detector gives nobody else away. */
    private void detect(Player holder) {
        if (jammed(holder.getLocation())) {
            BlockcraftApi.get().notifications().status(holder, messages.get("shop.jammer.static"));
            return;
        }
        double radius = config.mineDetectorRadius() * config.mineDetectorRadius();
        var near = traps.get().stream().filter(spot -> spot.getWorld().equals(holder.getWorld())
                && spot.distanceSquared(holder.getLocation()) <= radius).toList();
        near.forEach(spot -> holder.spawnParticle(Particle.DUST, spot, 12, 0.25, 0.15, 0.25, 0, TRAP_MARK));
        BlockcraftApi.get().notifications().status(holder, messages.get("shop.mine-detector.found", Formatter.number("traps", near.size())));
    }
}
