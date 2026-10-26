package me.blockcraft.ttt.shop;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.ttt.config.TttConfig;
import me.blockcraft.ttt.game.Role;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * What the shop's goods do once bought: the radar points at the nearest target for a while, mines and fake chests
 * blow up under anyone but a traitor, a heal station heals whoever stands near it, creeper arrows leave a lit creeper
 * where they land, and a spoofer gets its traitor through the next test. Explosions break no blocks and count as the
 * owner's kill.
 */
public final class Equipment {

    private static final long TICK = 20L;
    private static final int CREEPER_FUSE_TICKS = 25;
    private static final NamespacedKey CREEPER_ARROW = new NamespacedKey("blockcraft", "ttt_creeper_arrow");

    private final Plugin plugin;
    private final TttConfig.Shop config;
    private final Messages messages;
    private final Function<Player, Optional<Role>> roles;
    private final Supplier<List<Player>> alive;
    private final Map<UUID, Integer> radars = new HashMap<>();
    private final Map<Block, UUID> mines = new HashMap<>();
    private final Map<Block, UUID> fakeChests = new HashMap<>();
    private final Map<Block, Integer> healStations = new HashMap<>();
    /** Creepers and primed explosives by the player they belong to, who gets the kills they make. */
    private final Map<UUID, UUID> explosives = new HashMap<>();
    private final Set<UUID> spoofers = new HashSet<>();
    private BukkitTask ticker;

    public Equipment(Plugin plugin, TttConfig.Shop config, Messages messages, Function<Player, Optional<Role>> roles,
                     Supplier<List<Player>> alive) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.roles = roles;
        this.alive = alive;
    }

    public void start() {
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK, TICK);
    }

    public void stop() {
        if (ticker != null) {
            ticker.cancel();
        }
        radars.clear();
    }

    public ItemStack item(ShopItem item, int amount) {
        return item.tag(Items.of(item.material(), amount)
                .name(messages.get("shop.items." + item.key() + ".name"))
                .lore(messages.getLines("shop.items." + item.key() + ".lore"))
                .build());
    }

    public void deliver(Player buyer, ShopItem item) {
        switch (item) {
            case SPOOFER -> spoofers.add(buyer.getUniqueId());
            case RADAR -> {
                radars.put(buyer.getUniqueId(), config.radarSeconds());
                give(buyer, item(item, 1));
            }
            case CREEPER_ARROWS -> {
                if (!buyer.getInventory().contains(Material.BOW)) {
                    give(buyer, ItemStack.of(Material.BOW));
                }
                give(buyer, item(item, config.creeperArrows()));
            }
            default -> give(buyer, item(item, 1));
        }
    }

    public boolean hasSpoofer(Player player) {
        return spoofers.contains(player.getUniqueId());
    }

    /** Whether {@code player} shows as a traitor in a test; a spoofer hides them once and is used up. */
    public boolean showsAsTraitor(Player player) {
        return roles.apply(player).filter(role -> role == Role.TRAITOR).isPresent() && !spoofers.remove(player.getUniqueId());
    }

    public boolean heal(Player player, ItemStack hand) {
        if (HotbarItem.of(hand, ShopItem.class).filter(item -> item == ShopItem.MEDIPACK).isEmpty()) {
            return false;
        }
        double max = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        if (player.getHealth() >= max) {
            return true;
        }
        player.setHealth(Math.min(max, player.getHealth() + config.medipack()));
        hand.subtract();
        Sounds.HEAL.play(player);
        return true;
    }

    /** Lets a bought landmine, fake chest or heal station be placed; nothing else goes into the town. */
    public boolean place(Player player, ItemStack hand, Block block) {
        var item = HotbarItem.of(hand, ShopItem.class);
        if (item.isEmpty()) {
            return false;
        }
        var owner = player.getUniqueId();
        switch (item.get()) {
            case LANDMINE -> mines.put(block, owner);
            case FAKE_CHEST -> fakeChests.put(block, owner);
            case HEAL_STATION -> healStations.put(block, config.healSeconds());
            default -> {
                return false;
            }
        }
        return true;
    }

    public void step(Player player, Block block) {
        var owner = mines.get(block);
        if (owner == null || isTraitor(player)) {
            return;
        }
        mines.remove(block);
        block.setType(Material.AIR, false);
        explode(block.getLocation().add(0.5, 0.2, 0.5), owner);
    }

    /** True if {@code block} is a fake chest, which then blows up under anyone but a traitor. */
    public boolean openFake(Player player, Block block) {
        var owner = fakeChests.get(block);
        if (owner == null) {
            return false;
        }
        if (isTraitor(player)) {
            player.sendMessage(messages.get("shop.fake-chest-own"));
            return true;
        }
        fakeChests.remove(block);
        block.setType(Material.AIR, false);
        explode(block.getLocation().add(0.5, 0.5, 0.5), owner);
        return true;
    }

    public void shoot(Player shooter, ItemStack consumed, Entity projectile) {
        if (HotbarItem.of(consumed, ShopItem.class).filter(item -> item == ShopItem.CREEPER_ARROWS).isPresent()) {
            projectile.getPersistentDataContainer().set(CREEPER_ARROW, PersistentDataType.STRING, shooter.getUniqueId().toString());
        }
    }

    public void land(Projectile projectile) {
        var owner = projectile.getPersistentDataContainer().get(CREEPER_ARROW, PersistentDataType.STRING);
        if (owner == null) {
            return;
        }
        projectile.remove();
        var creeper = projectile.getWorld().spawn(projectile.getLocation(), Creeper.class, spawned -> {
            spawned.setMaxFuseTicks(CREEPER_FUSE_TICKS);
            spawned.setExplosionRadius(Math.round(config.explosionPower()));
            spawned.setPersistent(false);
        });
        creeper.ignite();
        explosives.put(creeper.getUniqueId(), UUID.fromString(owner));
    }

    /** The player a creeper arrow or explosive belonged to, for the kill it made. */
    public Optional<Player> ownerOf(Entity cause) {
        return Optional.ofNullable(cause).map(entity -> explosives.get(entity.getUniqueId()))
                .map(plugin.getServer()::getPlayer);
    }

    private void tick() {
        radars.entrySet().removeIf(entry -> {
            var player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !alive.get().contains(player)) {
                return true;
            }
            int left = entry.getValue() - 1;
            entry.setValue(left);
            if (left <= 0) {
                player.getInventory().all(Material.COMPASS).forEach((slot, stack) -> {
                    if (HotbarItem.of(stack, ShopItem.class).isPresent()) {
                        player.getInventory().clear(slot);
                    }
                });
                player.sendMessage(messages.get("shop.radar-expired"));
                return true;
            }
            point(player);
            return false;
        });
        healStations.entrySet().removeIf(entry -> {
            var block = entry.getKey();
            int left = entry.getValue() - 1;
            entry.setValue(left);
            var center = block.getLocation().add(0.5, 1.0, 0.5);
            double radius = config.healRadius() * config.healRadius();
            for (var player : alive.get()) {
                if (player.getWorld().equals(center.getWorld()) && player.getLocation().distanceSquared(center) <= radius) {
                    double max = player.getAttribute(Attribute.MAX_HEALTH).getValue();
                    player.setHealth(Math.min(max, player.getHealth() + config.healPerSecond()));
                }
            }
            center.getWorld().spawnParticle(Particle.HEART, center, 2, 0.6, 0.3, 0.6);
            if (left <= 0) {
                block.setType(Material.AIR, false);
                return true;
            }
            return false;
        });
    }

    private void point(Player player) {
        boolean traitor = isTraitor(player);
        var origin = player.getLocation();
        alive.get().stream()
                .filter(other -> !other.equals(player) && other.getWorld().equals(origin.getWorld()))
                .filter(other -> !traitor || !isTraitor(other))
                .min(Comparator.comparingDouble(other -> other.getLocation().distanceSquared(origin)))
                .ifPresent(target -> {
                    player.setCompassTarget(target.getLocation());
                    BlockcraftApi.get().notifications().status(player, messages.get("shop.radar-distance",
                            Formatter.number("distance", (int) origin.distance(target.getLocation()))));
                });
    }

    private boolean isTraitor(Player player) {
        return roles.apply(player).filter(role -> role == Role.TRAITOR).isPresent();
    }

    /**
     * Through primed TNT rather than with the owner as the source: a dead owner is a spectator, whose damage
     * SpectatorGuard cancels, and the mine must still go off.
     */
    private void explode(Location location, UUID owner) {
        var tnt = location.getWorld().spawn(location, TNTPrimed.class, primed -> {
            primed.setFuseTicks(0);
            primed.setYield(config.explosionPower());
            primed.setSource(plugin.getServer().getPlayer(owner));
        });
        explosives.put(tnt.getUniqueId(), owner);
    }

    private static void give(Player player, ItemStack item) {
        player.getInventory().addItem(item).values()
                .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
    }
}
