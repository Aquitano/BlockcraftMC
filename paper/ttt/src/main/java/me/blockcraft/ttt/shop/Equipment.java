package me.blockcraft.ttt.shop;

import com.destroystokyo.paper.profile.PlayerProfile;
import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.ttt.config.TttConfig;
import me.blockcraft.ttt.game.Role;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * What the shop's goods do once bought. Each kind of good lives with its kin: {@link Traps} for what lies in wait,
 * {@link Guns} for what shoots, {@link Signals} for what finds people or hides them, {@link HealStations} and
 * {@link Masks}. This class hands each click, step and hit to the good it concerns; what the goods do to a corpse
 * is the round's, which knows the dead.
 */
public final class Equipment {

    private static final long SECOND = 20L;
    private static final long HALF_SECOND = 10L;
    private static final int CREEPER_FUSE_TICKS = 25;
    private static final double BACKSTAB_DOT = -0.2;
    private static final NamespacedKey CREEPER_ARROW = new NamespacedKey("blockcraft", "ttt_creeper_arrow");

    private final Plugin plugin;
    private final TttConfig.Shop config;
    private final Messages messages;
    private final Function<Player, Optional<Role>> roles;
    private final Supplier<List<Player>> alive;
    private final Culprits culprits = new Culprits();
    private final Gadgets gadgets = new Gadgets();
    private final Holds holds;
    private final Traps traps;
    private final Guns guns;
    private final Signals signals;
    private final HealStations healStations;
    private final Masks masks;
    private final Set<UUID> spoofers = new HashSet<>();
    private final Set<UUID> vests = new HashSet<>();
    private BukkitTask ticker;
    private BukkitTask watcher;

    public Equipment(Plugin plugin, TttConfig.Shop config, Messages messages, Function<Player, Optional<Role>> roles,
                     Supplier<List<Player>> alive) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.roles = roles;
        this.alive = alive;
        this.holds = new Holds(plugin);
        this.traps = new Traps(config, messages, culprits, gadgets, this::isTraitor, item -> item(item, 1), holds);
        this.guns = new Guns(config, messages, alive, holds);
        this.signals = new Signals(config, messages, gadgets, this::isTraitor, alive, traps::hidden, item -> item(item, 1));
        this.healStations = new HealStations(config.healStation(), messages, culprits, alive);
        this.masks = new Masks(plugin, messages);
    }

    public void start() {
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, SECOND, SECOND);
        watcher = plugin.getServer().getScheduler().runTaskTimer(plugin, signals::watch, HALF_SECOND, HALF_SECOND);
    }

    public void stop() {
        if (ticker != null) {
            ticker.cancel();
            watcher.cancel();
        }
        masks.restoreAll();
        gadgets.clear();
        traps.clear();
        guns.clear();
        holds.clear();
        signals.clear();
        healStations.clear();
        culprits.clear();
        spoofers.clear();
        vests.clear();
    }

    public ItemStack item(ShopItem item, int amount) {
        var builder = Items.of(item.material(), amount)
                .name(messages.get("shop.items." + item.key() + ".name"))
                .lore(messages.getLines("shop.items." + item.key() + ".lore"));
        item.model().ifPresent(model -> model.mark(builder));
        return item.tag(builder.build());
    }

    public void deliver(Player buyer, ShopItem item) {
        switch (item) {
            case SPOOFER -> spoofers.add(buyer.getUniqueId());
            case VEST -> vests.add(buyer.getUniqueId());
            case RADAR -> {
                signals.radar(buyer);
                give(buyer, item(item, 1));
            }
            case CREEPER_ARROWS -> {
                if (!buyer.getInventory().contains(Material.BOW)) {
                    give(buyer, ItemStack.of(Material.BOW));
                }
                give(buyer, item(item, config.creeperArrows()));
            }
            case SILENCED_PISTOL -> give(buyer, item(item, config.pistol().shots()));
            case TASER -> give(buyer, item(item, config.taser().charges()));
            default -> give(buyer, item(item, 1));
        }
    }

    /** Goods that are a state rather than an item, and of which a player has at most one. */
    public boolean owns(Player player, ShopItem item) {
        return switch (item) {
            case SPOOFER -> spoofers.contains(player.getUniqueId());
            case VEST -> vests.contains(player.getUniqueId());
            default -> false;
        };
    }

    /** Whether {@code player} shows as a traitor in a test; a spoofer hides them once and is used up. */
    public boolean showsAsTraitor(Player player) {
        return isTraitor(player) && !spoofers.remove(player.getUniqueId());
    }

    /**
     * A right-click with a good in hand, on {@code clicked} from {@code face} or into the air. True when the good took
     * the click; goods placed as blocks leave it to {@link #place}, and goods for corpses only keep it from the world.
     */
    public boolean use(Player player, ItemStack hand, Optional<Block> clicked, BlockFace face) {
        var good = HotbarItem.of(hand, ShopItem.class);
        if (good.isEmpty()) {
            return false;
        }
        switch (good.get()) {
            case MEDIPACK -> heal(player, hand);
            case SILENCED_PISTOL, TASER -> guns.fire(player, good.get(), hand);
            case JAMMER -> {
                signals.jam(player);
                hand.subtract();
            }
            case BEAR_TRAP, TIME_BOMB, MOTION_SENSOR -> {
                if (clicked.isPresent() && face == BlockFace.UP && setDown(player, good.get(), clicked.get().getRelative(face))) {
                    hand.subtract();
                }
            }
            case POISON -> {
                if (clicked.isPresent() && healStations.poison(player, clicked.get())) {
                    hand.subtract();
                }
            }
            case LANDMINE, FAKE_CHEST, HEAL_STATION, CREEPER_ARROWS -> {
                return false;
            }
            default -> {
            }
        }
        return true;
    }

    /** Lets a bought landmine, fake chest or heal station be placed; nothing else goes into the town. */
    public boolean place(Player player, ItemStack hand, Block block) {
        var good = HotbarItem.of(hand, ShopItem.class);
        if (good.isEmpty()) {
            return false;
        }
        if (good.get() == ShopItem.HEAL_STATION) {
            healStations.place(block);
            return true;
        }
        return traps.place(player, good.get(), block);
    }

    public void step(Player player, Block block) {
        traps.step(player, block);
    }

    public boolean openFake(Player player, Block block) {
        return traps.openFake(player, block);
    }

    public void moved(Player player, Block to) {
        traps.moved(player, to);
    }

    /** A click on something a good left in the world: a time bomb's wires, a motion sensor's log. */
    public boolean clickGadget(Player player, Entity clicked) {
        return traps.defuse(player, clicked, () -> alive.get().contains(player)) || signals.read(player, clicked);
    }

    /** The knife kills with one stab in the back and is used up doing it. */
    public boolean backstab(Player attacker, Player victim) {
        var hand = attacker.getInventory().getItemInMainHand();
        if (HotbarItem.of(hand, ShopItem.class).filter(item -> item == ShopItem.KNIFE).isEmpty()) {
            return false;
        }
        var facing = victim.getLocation().getDirection().setY(0);
        var towardAttacker = attacker.getLocation().toVector().subtract(victim.getLocation().toVector()).setY(0);
        if (facing.lengthSquared() == 0 || towardAttacker.lengthSquared() == 0
                || facing.normalize().dot(towardAttacker.normalize()) > BACKSTAB_DOT) {
            return false;
        }
        attacker.getInventory().setItemInMainHand(null);
        return true;
    }

    /** What share of a hit's damage reaches {@code victim}; a vest takes the rest. */
    public double damageTaken(Player victim) {
        return vests.contains(victim.getUniqueId()) ? 1 - config.vest() : 1;
    }

    public boolean stunned(Player player) {
        return guns.stunned(player);
    }

    public boolean jammed(Location location) {
        return signals.jammed(location);
    }

    /** Puts the mask on: {@code wearer} looks and is named like the dead for a while. */
    public void wearMask(Player wearer, String name, PlayerProfile face) {
        masks.wear(wearer, name, face, config.maskSeconds());
        wearer.sendMessage(messages.get("shop.mask.worn"));
    }

    /** The player who owns the good that dealt {@code source}, even if they are dead now. */
    public Optional<Player> culprit(Player victim, DamageSource source) {
        return culprits.of(victim, source).map(Bukkit::getPlayer);
    }

    /** A player out of the round loses what they were: a mask, a vest, a stun, a running radar or jammer. */
    public void forget(Player player) {
        masks.restore(player);
        vests.remove(player.getUniqueId());
        spoofers.remove(player.getUniqueId());
        guns.forget(player);
        holds.release(player);
        signals.forget(player);
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
        culprits.own(creeper, UUID.fromString(owner));
    }

    private boolean setDown(Player player, ShopItem good, Block space) {
        return good == ShopItem.MOTION_SENSOR ? signals.placeSensor(player, space) : traps.set(player, good, space);
    }

    private void heal(Player player, ItemStack hand) {
        double max = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        if (player.getHealth() >= max) {
            return;
        }
        player.setHealth(Math.min(max, player.getHealth() + config.medipack()));
        hand.subtract();
        Sounds.HEAL.play(player);
    }

    private void tick() {
        traps.tick();
        signals.tick();
        healStations.tick();
    }

    private boolean isTraitor(Player player) {
        return roles.apply(player).filter(role -> role == Role.TRAITOR).isPresent();
    }

    private static void give(Player player, ItemStack item) {
        player.getInventory().addItem(item).values()
                .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
    }
}
