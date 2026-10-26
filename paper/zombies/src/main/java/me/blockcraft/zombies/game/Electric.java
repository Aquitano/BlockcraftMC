package me.blockcraft.zombies.game;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.pack.PackModel;
import me.blockcraft.zombies.config.ZombiesConfig;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/** Zaps the nearest fighter, and the zap jumps on to fighters standing close by, so the team must spread out. */
final class Electric implements Ability {

    private static final Color SPARK = Color.fromRGB(0xF2, 0xD2, 0x3C);
    private final ZombiesGame game;
    private final ZombiesConfig.Electric config;

    Electric(ZombiesGame game, ZombiesConfig.Electric config) {
        this.game = game;
        this.config = config;
    }

    @Override
    public void equip(Mob mob, EntityEquipment equipment) {
        equipment.setHelmet(PackModel.ZOMBIES_TESLA_HELMET.item().build());
        equipment.setChestplate(Enemies.gear(Items.of(Material.LEATHER_CHESTPLATE).color(SPARK)));
        equipment.setLeggings(Enemies.gear(Items.of(Material.LEATHER_LEGGINGS).color(SPARK)));
    }

    @Override
    public boolean hunt(Enemies.Enemy enemy, Player target, int tick) {
        var mob = enemy.mob;
        mob.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, mob.getEyeLocation(), 4, 0.3, 0.3, 0.3, 0.05);
        if (tick < enemy.specialAt || mob.getLocation().distanceSquared(target.getLocation()) > config.range() * config.range()
                || !mob.hasLineOfSight(target)) {
            return false;
        }
        enemy.specialAt = tick + config.zapTicks();
        double damage = game.enemies().damage(enemy);
        var from = mob.getEyeLocation();
        mob.getWorld().playSound(from, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.7f);
        for (var fighter : chain(target, game.fighters(), player -> center(player).toVector(), config.jumpRange(), config.jumps())) {
            var to = center(fighter);
            arc(from, to);
            fighter.damage(damage, mob);
            damage *= config.jumpShare();
            from = to;
        }
        return false;
    }

    /** {@code first}, then up to {@code jumps} more, each the closest not yet struck within {@code range} of the one before. */
    static <T> List<T> chain(T first, Collection<T> candidates, Function<T, Vector> position, double range, int jumps) {
        var chain = new ArrayList<T>(List.of(first));
        var left = new ArrayList<T>(candidates);
        left.remove(first);
        while (chain.size() <= jumps) {
            var from = position.apply(chain.getLast());
            var next = left.stream().filter(candidate -> position.apply(candidate).distanceSquared(from) <= range * range)
                    .min(Comparator.comparingDouble(candidate -> position.apply(candidate).distanceSquared(from)));
            if (next.isEmpty()) {
                break;
            }
            chain.add(next.get());
            left.remove(next.get());
        }
        return chain;
    }

    private static Location center(Player player) {
        return player.getLocation().add(0, player.getHeight() / 2, 0);
    }

    private static void arc(Location from, Location to) {
        var world = from.getWorld();
        var line = to.toVector().subtract(from.toVector());
        double length = line.length();
        var random = ThreadLocalRandom.current();
        int steps = (int) Math.ceil(length / 0.25);
        for (int step = 0; step < steps; step++) {
            var point = from.clone().add(line.clone().multiply(step / (double) steps))
                    .add(random.nextDouble(-0.12, 0.12), random.nextDouble(-0.12, 0.12), random.nextDouble(-0.12, 0.12));
            world.spawnParticle(Particle.ELECTRIC_SPARK, point, 1, 0, 0, 0, 0);
            if (step % 4 == 0) {
                world.spawnParticle(Particle.END_ROD, point, 1, 0, 0, 0, 0);
            }
        }
        world.playSound(to, Sound.ENTITY_BEE_STING, 0.6f, 0.6f);
    }
}
