package me.blockcraft.ttt.shop;

import me.blockcraft.api.game.BlockChanges;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.ttt.config.TttConfig;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The traitors' traps, which spare traitors: a landmine blows up under whoever steps on it, a fake chest under
 * whoever opens it, a bear trap holds and hurts whoever walks into it, and a time bomb goes off when its time runs
 * out unless someone cuts the right wire first. Only its owner is told which wire that is.
 */
final class Traps {

    private record BearTrap(UUID owner, ItemDisplay display) {
    }

    private static final class Bomb {
        private final UUID owner;
        private final Block space;
        private final ItemDisplay display;
        private final Interaction hitbox;
        private final TextDisplay timer;
        private final DefuseMenu.Wire wire;
        private int left;

        private Bomb(UUID owner, Block space, ItemDisplay display, Interaction hitbox, TextDisplay timer,
                     DefuseMenu.Wire wire, int left) {
            this.owner = owner;
            this.space = space;
            this.display = display;
            this.hitbox = hitbox;
            this.timer = timer;
            this.wire = wire;
            this.left = left;
        }
    }

    private final TttConfig.Shop config;
    private final Messages messages;
    private final Culprits culprits;
    private final Gadgets gadgets;
    private final Predicate<Player> spared;
    private final Function<ShopItem, ItemStack> look;
    private final Holds holds;
    private final Map<Block, UUID> mines = new HashMap<>();
    private final Map<Block, UUID> fakeChests = new HashMap<>();
    private final Map<Block, BearTrap> bearTraps = new HashMap<>();
    private final Map<UUID, Bomb> bombs = new HashMap<>();

    Traps(TttConfig.Shop config, Messages messages, Culprits culprits, Gadgets gadgets, Predicate<Player> spared,
          Function<ShopItem, ItemStack> look, Holds holds) {
        this.config = config;
        this.messages = messages;
        this.culprits = culprits;
        this.gadgets = gadgets;
        this.spared = spared;
        this.look = look;
        this.holds = holds;
    }

    /** A landmine or fake chest placed as a block. */
    boolean place(Player owner, ShopItem item, Block block) {
        switch (item) {
            case LANDMINE -> mines.put(block, owner.getUniqueId());
            case FAKE_CHEST -> fakeChests.put(block, owner.getUniqueId());
            default -> {
                return false;
            }
        }
        return true;
    }

    /** A bear trap or time bomb set down into {@code space}; false if there is no room. */
    boolean set(Player owner, ShopItem item, Block space) {
        if (!Gadgets.fits(space) || bearTraps.containsKey(space) || bombs.values().stream().anyMatch(bomb -> bomb.space.equals(space))) {
            owner.sendMessage(messages.get("shop.no-room"));
            Sounds.MENU_DENY.play(owner);
            return false;
        }
        float yaw = owner.getLocation().getYaw();
        var display = gadgets.show(space, yaw, look.apply(item));
        if (item == ShopItem.BEAR_TRAP) {
            bearTraps.put(space, new BearTrap(owner.getUniqueId(), display));
            owner.sendMessage(messages.get("shop.bear-trap.set"));
            return true;
        }
        var wires = DefuseMenu.Wire.first(config.timeBomb().wires());
        var wire = wires.get(ThreadLocalRandom.current().nextInt(wires.size()));
        int seconds = config.timeBomb().seconds();
        var hitbox = gadgets.hitbox(space, 0.8f, 0.6f);
        var timer = gadgets.label(space, 0.9, messages.get("shop.time-bomb.timer", Formatter.number("seconds", seconds)));
        bombs.put(hitbox.getUniqueId(), new Bomb(owner.getUniqueId(), space, display, hitbox, timer, wire, seconds));
        owner.sendMessage(messages.get("shop.time-bomb.armed", Formatter.number("seconds", seconds),
                Placeholder.component("wire", messages.get("shop.time-bomb.wires." + wire.key()))));
        space.getWorld().playSound(space.getLocation(), Sound.BLOCK_TRIPWIRE_ATTACH, 0.8f, 1.2f);
        return true;
    }

    void step(Player player, Block block) {
        var owner = mines.get(block);
        if (owner == null || spared.test(player)) {
            return;
        }
        mines.remove(block);
        BlockChanges.set(block, Material.AIR);
        culprits.explode(block.getLocation().add(0.5, 0.2, 0.5), owner, config.explosionPower());
    }

    /** True if {@code block} is a fake chest, which then blows up under anyone but a traitor. */
    boolean openFake(Player player, Block block) {
        var owner = fakeChests.get(block);
        if (owner == null) {
            return false;
        }
        if (spared.test(player)) {
            player.sendMessage(messages.get("shop.fake-chest-own"));
            return true;
        }
        fakeChests.remove(block);
        BlockChanges.set(block, Material.AIR);
        culprits.explode(block.getLocation().add(0.5, 0.5, 0.5), owner, config.explosionPower());
        return true;
    }

    /** Whoever but a traitor walks into a bear trap is held fast and hurt; the snap is heard around. */
    void moved(Player player, Block to) {
        var trap = bearTraps.get(to);
        if (trap == null || spared.test(player)) {
            return;
        }
        bearTraps.remove(to);
        gadgets.remove(trap.display());
        int ticks = config.bearTrap().seconds() * 20;
        holds.hold(player, ticks);
        player.getWorld().playSound(to.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1.6f, 0.6f);
        player.getWorld().playSound(to.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 1.6f);
        player.sendMessage(messages.get("shop.bear-trap.caught"));
        culprits.hurt(player, config.bearTrap().damage(), trap.owner());
    }

    /** True if {@code clicked} is a time bomb; it then shows its wires. */
    boolean defuse(Player player, Entity clicked, BooleanSupplier alive) {
        var id = clicked.getUniqueId();
        var bomb = bombs.get(id);
        if (bomb == null) {
            return false;
        }
        new DefuseMenu(messages, DefuseMenu.Wire.first(config.timeBomb().wires()), (cutter, wire) -> cut(cutter, id, wire))
                .open(player, () -> bombs.containsKey(id) && alive.getAsBoolean());
        return true;
    }

    private void cut(Player cutter, UUID id, DefuseMenu.Wire wire) {
        var bomb = bombs.remove(id);
        if (bomb == null) {
            return;
        }
        gadgets.remove(bomb.display, bomb.hitbox, bomb.timer);
        if (wire == bomb.wire) {
            cutter.sendMessage(messages.get("shop.time-bomb.defused"));
            Sounds.NOTE_PLING.play(cutter);
            bomb.space.getWorld().playSound(bomb.space.getLocation(), Sound.ENTITY_SHEEP_SHEAR, 1f, 1f);
            return;
        }
        cutter.sendMessage(messages.get("shop.time-bomb.wrong"));
        culprits.explode(bomb.space.getLocation().add(0.5, 0.2, 0.5), bomb.owner, config.timeBomb().power());
    }

    /** Once a second: every bomb ticks down, beeping faster at the end, and goes off at zero. */
    void tick() {
        var due = new ArrayList<Bomb>();
        for (var bomb : bombs.values()) {
            bomb.left--;
            bomb.timer.text(messages.get("shop.time-bomb.timer", Formatter.number("seconds", Math.max(0, bomb.left))));
            float pitch = bomb.left <= 10 ? 2.0f : 1.2f;
            bomb.space.getWorld().playSound(bomb.space.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.2f, pitch);
            if (bomb.left <= 0) {
                due.add(bomb);
            }
        }
        for (var bomb : due) {
            bombs.remove(bomb.hitbox.getUniqueId());
            gadgets.remove(bomb.display, bomb.hitbox, bomb.timer);
            culprits.explode(bomb.space.getLocation().add(0.5, 0.2, 0.5), bomb.owner, config.timeBomb().power());
        }
    }

    /** Where every hidden danger lies, for the mine detector. */
    List<Location> hidden() {
        var spots = new ArrayList<Location>();
        mines.keySet().forEach(block -> spots.add(block.getLocation().add(0.5, 0.2, 0.5)));
        fakeChests.keySet().forEach(block -> spots.add(block.getLocation().add(0.5, 1.0, 0.5)));
        bearTraps.keySet().forEach(block -> spots.add(block.getLocation().add(0.5, 0.2, 0.5)));
        bombs.values().forEach(bomb -> spots.add(bomb.space.getLocation().add(0.5, 0.4, 0.5)));
        return spots;
    }

    void clear() {
        mines.clear();
        fakeChests.clear();
        bearTraps.clear();
        bombs.clear();
    }
}
