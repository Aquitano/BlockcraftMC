package me.blockcraft.zombies.game;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BannerPatternLayers;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.pack.PackModel;
import me.blockcraft.zombies.config.ZombiesConfig;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.util.Vector;

/** Carries a riot shield that takes most of a hit from the front; the team has to flank it. */
final class ShieldBearer implements Ability {

    private final ZombiesConfig.ShieldBearer config;

    ShieldBearer(ZombiesConfig.ShieldBearer config) {
        this.config = config;
    }

    @Override
    public void equip(Mob mob, EntityEquipment equipment) {
        equipment.setHelmet(PackModel.ZOMBIES_SHIELD_HELM.item().build());
        equipment.setChestplate(Enemies.gear(Items.of(Material.CHAINMAIL_CHESTPLATE)));
        equipment.setLeggings(Enemies.gear(Items.of(Material.CHAINMAIL_LEGGINGS)));
        var shield = Enemies.gear(Items.of(Material.SHIELD));
        shield.setData(DataComponentTypes.BASE_COLOR, DyeColor.BLACK);
        shield.setData(DataComponentTypes.BANNER_PATTERNS, BannerPatternLayers.bannerPatternLayers()
                .add(new Pattern(DyeColor.WHITE, PatternType.STRIPE_MIDDLE))
                .add(new Pattern(DyeColor.GRAY, PatternType.BORDER))
                .build());
        equipment.setItemInOffHand(shield);
    }

    @Override
    public double shot(Enemies.Enemy enemy, Player shooter, double damage) {
        var mob = enemy.mob;
        double yaw = Math.toRadians(mob.getBodyYaw());
        var facing = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        if (!blocks(facing, shooter.getLocation().toVector().subtract(mob.getLocation().toVector()), config.frontAngle())) {
            return damage;
        }
        mob.getWorld().playSound(mob.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1, 1);
        return damage * (1 - config.blockedShare());
    }

    /** Whether a shooter in direction {@code toShooter} stands within {@code frontAngle} degrees of {@code facing}, seen from above. */
    static boolean blocks(Vector facing, Vector toShooter, double frontAngle) {
        var flat = toShooter.clone().setY(0);
        return flat.lengthSquared() > 0 && Math.toDegrees(facing.clone().setY(0).angle(flat)) <= frontAngle;
    }
}
