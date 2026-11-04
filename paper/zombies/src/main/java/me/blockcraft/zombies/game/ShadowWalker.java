package me.blockcraft.zombies.game;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.pack.PackModel;
import me.blockcraft.zombies.config.ZombiesConfig;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.util.Vector;

/** Moves only while no fighter looks at it; watched, it freezes in place and falls silent. */
final class ShadowWalker implements Ability {

    private static final Color SHADOW = Color.fromRGB(0x16, 0x16, 0x1C);
    private final ZombiesGame game;
    private final ZombiesConfig.Shadow config;

    ShadowWalker(ZombiesGame game, ZombiesConfig.Shadow config) {
        this.game = game;
        this.config = config;
    }

    @Override
    public void equip(Mob mob, EntityEquipment equipment) {
        equipment.setHelmet(PackModel.ZOMBIES_SHADOW_HOOD.item().build());
        equipment.setChestplate(Enemies.gear(Items.of(Material.LEATHER_CHESTPLATE).color(SHADOW)));
        equipment.setLeggings(Enemies.gear(Items.of(Material.LEATHER_LEGGINGS).color(SHADOW)));
        equipment.setBoots(Enemies.gear(Items.of(Material.LEATHER_BOOTS).color(SHADOW)));
    }

    @Override
    public boolean holds(Enemies.Enemy enemy) {
        var mob = enemy.mob;
        var center = mob.getLocation().add(0, mob.getHeight() / 2, 0).toVector();
        boolean watched = game.fighters().stream().anyMatch(fighter -> {
            var eye = fighter.getEyeLocation();
            return watches(eye.toVector(), eye.getDirection(), center, config.watchAngle()) && fighter.hasLineOfSight(mob);
        });
        if (watched == mob.isAware()) {
            // An unaware mob keeps its physics: it still falls and takes knockback, it only stops deciding to move.
            mob.setAware(!watched);
            mob.setSilent(watched);
            if (watched) {
                mob.getPathfinder().stopPathfinding();
                mob.setVelocity(new Vector(0, Math.min(0, mob.getVelocity().getY()), 0));
            }
        }
        return watched;
    }

    @Override
    public boolean hunt(Enemies.Enemy enemy, Player target, int tick) {
        enemy.mob.getWorld().spawnParticle(Particle.LARGE_SMOKE, enemy.mob.getLocation().add(0, 0.2, 0), 3, 0.25, 0.1, 0.25, 0);
        return false;
    }

    /** Whether someone at {@code eye} looking along {@code look} has {@code target} within {@code angle} degrees. */
    static boolean watches(Vector eye, Vector look, Vector target, double angle) {
        var toward = target.clone().subtract(eye);
        return toward.lengthSquared() > 0 && Math.toDegrees(look.angle(toward)) <= angle;
    }
}
