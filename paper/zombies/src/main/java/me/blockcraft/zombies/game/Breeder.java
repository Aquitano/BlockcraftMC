package me.blockcraft.zombies.game;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.pack.PackModel;
import me.blockcraft.zombies.config.ZombiesConfig;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/** Big, slow and tough; when it dies it bursts into crawlers that join the running wave. */
final class Breeder implements Ability {

    private static final Color FLESH = Color.fromRGB(0x9C, 0x4F, 0x6E);
    private final Enemies enemies;
    private final ZombiesConfig.Breeder config;

    Breeder(Enemies enemies, ZombiesConfig.Breeder config) {
        this.enemies = enemies;
        this.config = config;
    }

    @Override
    public void equip(Mob mob, EntityEquipment equipment) {
        equipment.setHelmet(PackModel.ZOMBIES_BROOD_SACS.item().build());
        equipment.setChestplate(Enemies.gear(Items.of(Material.LEATHER_CHESTPLATE).color(FLESH)));
    }

    @Override
    public void died(Enemies.Enemy enemy) {
        var spot = enemy.mob.getLocation();
        var world = spot.getWorld();
        world.spawnParticle(Particle.BLOCK, spot.clone().add(0, 1.2, 0), 40, 0.6, 0.6, 0.6, Material.NETHER_WART_BLOCK.createBlockData());
        world.playSound(spot, Sound.ENTITY_SLIME_SQUISH, 1.2f, 0.5f);
        world.playSound(spot, Sound.ENTITY_TURTLE_EGG_HATCH, 1, 0.7f);
        var random = ThreadLocalRandom.current();
        for (int crawler = 0; crawler < config.brood(); crawler++) {
            var spawned = enemies.summon(EnemyKind.CRAWLER, enemy.window, spot, enemy.breached);
            if (spawned != null) {
                spawned.mob.setVelocity(new Vector(random.nextDouble(-0.3, 0.3), 0.35, random.nextDouble(-0.3, 0.3)));
            }
        }
    }
}
