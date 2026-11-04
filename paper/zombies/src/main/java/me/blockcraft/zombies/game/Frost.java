package me.blockcraft.zombies.game;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.pack.PackModel;
import me.blockcraft.zombies.config.ZombiesConfig;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Its touch and the cold around it chill fighters: they slow down and see their screen ice over. */
final class Frost implements Ability {

    private static final Color ICE = Color.fromRGB(0x9E, 0xD8, 0xF0);
    private final ZombiesGame game;
    private final ZombiesConfig.Frost config;

    Frost(ZombiesGame game, ZombiesConfig.Frost config) {
        this.game = game;
        this.config = config;
    }

    @Override
    public void equip(Mob mob, EntityEquipment equipment) {
        equipment.setHelmet(PackModel.ZOMBIES_FROST_CROWN.item().build());
        equipment.setChestplate(Enemies.gear(Items.of(Material.LEATHER_CHESTPLATE).color(ICE)));
        equipment.setLeggings(Enemies.gear(Items.of(Material.LEATHER_LEGGINGS).color(ICE)));
        equipment.setBoots(Enemies.gear(Items.of(Material.LEATHER_BOOTS).color(ICE)));
    }

    @Override
    public boolean hunt(Enemies.Enemy enemy, Player target, int tick) {
        var mob = enemy.mob;
        mob.getWorld().spawnParticle(Particle.SNOWFLAKE, mob.getLocation().add(0, 1, 0), 6, 0.4, 0.6, 0.4, 0.01);
        double radius = config.auraRadius();
        for (var fighter : game.fighters()) {
            if (fighter.getLocation().distanceSquared(mob.getLocation()) <= radius * radius && mob.hasLineOfSight(fighter)) {
                chill(fighter, config.auraFreezeTicks());
            }
        }
        return false;
    }

    @Override
    public void struck(Enemies.Enemy enemy, Player player) {
        chill(player, config.touchFreezeTicks());
        player.getWorld().spawnParticle(Particle.BLOCK, player.getLocation().add(0, 1, 0), 16, 0.3, 0.5, 0.3, Material.ICE.createBlockData());
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.6f, 1.6f);
    }

    private void chill(Player player, int freezeTicks) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, game.config().enemies().get(EnemyKind.FROST).slowTicks(), config.slowness()));
        // A fully frozen player takes vanilla freeze damage, so the frost stops one tick short of it.
        player.setFreezeTicks(Math.min(player.getMaxFreezeTicks() - 1, player.getFreezeTicks() + freezeTicks));
        player.getWorld().spawnParticle(Particle.SNOWFLAKE, player.getLocation().add(0, 1, 0), 4, 0.3, 0.5, 0.3, 0);
    }
}
