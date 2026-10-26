package me.blockcraft.endergames.game;

import me.blockcraft.api.game.LootChests;
import me.blockcraft.api.game.LootTable;
import me.blockcraft.endergames.config.EnderGamesConfig.EnderChestSettings;
import me.blockcraft.endergames.world.RoundWorld;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The ender chests that wander the round's world: each stands somewhere inside the border with its own loot, marked
 * by a beam of particles, until it has stood long enough or was opened a while ago; then it vanishes and appears
 * somewhere else with fresh loot.
 */
final class EnderChests {

    private static final double INSIDE_BORDER = 0.9;
    private static final int BEAM_HEIGHT = 6;

    private final class Roamer {

        private Block block;
        private LootChests loot;
        private int secondsLeft;
        private boolean opened;

        private Roamer(Block block) {
            place(block);
        }

        private void place(Block at) {
            block = at;
            block.setType(Material.ENDER_CHEST, false);
            loot = new LootChests(settings.loot(), items);
            secondsLeft = settings.relocateSeconds();
            opened = false;
        }

        private Inventory inventory() {
            return loot.open(block.getLocation());
        }
    }

    private final RoundWorld world;
    private final EnderChestSettings settings;
    private final Function<LootTable.Entry, ItemStack> items;
    private final List<Roamer> roamers = new ArrayList<>();

    EnderChests(RoundWorld world, EnderChestSettings settings, Function<LootTable.Entry, ItemStack> items) {
        this.world = world;
        this.settings = settings;
        this.items = items;
    }

    void spawn(int count) {
        for (int i = 0; i < count; i++) {
            freeSpot().ifPresent(block -> roamers.add(new Roamer(block)));
        }
    }

    /** One second of the round: every chest shines and counts down, and the ones whose time is up move. */
    void tick() {
        for (var roamer : roamers) {
            if (--roamer.secondsLeft <= 0 || !roamer.block.getWorld().getWorldBorder().isInside(roamer.block.getLocation())) {
                relocate(roamer);
            }
            shine(roamer.block);
        }
    }

    boolean isRoamer(Block block) {
        return find(block).isPresent();
    }

    /** The loot of the chest at {@code block}; opening it starts the short wait before it moves on. */
    Optional<Inventory> open(Block block) {
        return find(block).map(roamer -> {
            if (!roamer.opened) {
                roamer.opened = true;
                roamer.secondsLeft = Math.min(roamer.secondsLeft, settings.afterOpenSeconds());
            }
            return roamer.inventory();
        });
    }

    void clear() {
        roamers.forEach(this::remove);
        roamers.clear();
    }

    private Optional<Roamer> find(Block block) {
        return roamers.stream().filter(roamer -> roamer.block.equals(block)).findFirst();
    }

    private void relocate(Roamer roamer) {
        var next = freeSpot();
        if (next.isEmpty()) {
            roamer.secondsLeft = settings.afterOpenSeconds();
            return;
        }
        remove(roamer);
        roamer.place(next.get());
        var location = roamer.block.getLocation().add(0.5, 0.5, 0.5);
        location.getWorld().playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
    }

    private void remove(Roamer roamer) {
        new ArrayList<>(roamer.inventory().getViewers()).forEach(HumanEntity::closeInventory);
        var location = roamer.block.getLocation().add(0.5, 0.5, 0.5);
        location.getWorld().spawnParticle(Particle.REVERSE_PORTAL, location, 40, 0.3, 0.3, 0.3, 0.05);
        roamer.block.setType(Material.AIR, false);
    }

    private Optional<Block> freeSpot() {
        return world.randomSpot(world.borderRadius() * INSIDE_BORDER)
                .map(Location::getBlock)
                .filter(block -> roamers.stream().noneMatch(roamer -> roamer.block.equals(block)));
    }

    private static void shine(Block block) {
        var base = block.getLocation().add(0.5, 0.5, 0.5);
        var world = base.getWorld();
        world.spawnParticle(Particle.PORTAL, base, 12, 0.3, 0.3, 0.3, 0.4);
        for (int y = 1; y <= BEAM_HEIGHT; y++) {
            world.spawnParticle(Particle.END_ROD, base.clone().add(0, y, 0), 1, 0, 0, 0, 0, null, true);
        }
    }
}
