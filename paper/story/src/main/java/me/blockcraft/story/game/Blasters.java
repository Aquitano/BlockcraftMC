package me.blockcraft.story.game;

import me.blockcraft.api.game.ShotEvent;
import me.blockcraft.api.pack.PackModel;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

/**
 * Sternenfront's look without its combat: the carbine's model and sound on a hitscan bolt drawn as a line of light.
 * Players fire with the use key; droids fire through {@link #fire}.
 */
public final class Blasters implements Listener {

    private static final Sound SHOT = Sound.sound(Key.key("blockcraft", "sternenfront.blaster.carbine"), Sound.Source.PLAYER, 1, 1);
    private static final Sound IMPACT = Sound.sound(Key.key("blockcraft", "sternenfront.bolt.impact"), Sound.Source.PLAYER, 0.8f, 1);
    private static final Color PLAYER_BOLT = Color.fromRGB(0x4FA8FF);
    public static final Color DROID_BOLT = Color.fromRGB(0xFF3B30);

    private final NamespacedKey marker;
    private final double damage;
    private final int cooldownTicks;
    private final double range;

    public Blasters(Plugin plugin, double damage, int cooldownTicks, double range) {
        this.marker = new NamespacedKey(plugin, "blaster");
        this.damage = damage;
        this.cooldownTicks = cooldownTicks;
        this.range = range;
    }

    public ItemStack item(Component name) {
        return PackModel.STERNENFRONT_CARBINE.item().name(name)
                .edit(meta -> meta.getPersistentDataContainer().set(marker, PersistentDataType.BOOLEAN, true)).build();
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        var item = event.getItem();
        if (item == null || !item.getPersistentDataContainer().has(marker)) return;
        event.setCancelled(true);
        var player = event.getPlayer();
        if (player.hasCooldown(item)) return;
        player.setCooldown(item, cooldownTicks);
        var eye = player.getEyeLocation();
        fire(player, eye, eye.getDirection(), range, damage, PLAYER_BOLT);
    }

    /** One bolt along {@code direction}: the first living thing it meets that is not on the shooter's side takes the hit. */
    public void fire(LivingEntity shooter, Location from, Vector direction, double reach, double hit, Color color) {
        boolean byPlayer = shooter instanceof Player;
        var world = from.getWorld();
        var trace = world.rayTrace(from, direction, reach, FluidCollisionMode.NEVER, true, 0.2,
                entity -> entity instanceof LivingEntity && entity != shooter && entity instanceof Player != byPlayer
                        && !(entity instanceof Mannequin));
        var end = trace == null ? from.clone().add(direction.clone().normalize().multiply(reach)) : trace.getHitPosition().toLocation(world);
        var step = end.toVector().subtract(from.toVector());
        double length = step.length();
        var dust = new Particle.DustOptions(color, 0.7f);
        for (double travelled = 0.8; travelled < length; travelled += 0.35) {
            world.spawnParticle(Particle.DUST, from.clone().add(step.clone().multiply(travelled / length)), 1, 0, 0, 0, 0, dust);
        }
        world.playSound(SHOT, from.getX(), from.getY(), from.getZ());
        if (trace != null && trace.getHitEntity() instanceof LivingEntity target) {
            target.damage(hit, shooter);
            world.playSound(IMPACT, end.getX(), end.getY(), end.getZ());
        }
        new ShotEvent(shooter).callEvent();
    }
}
