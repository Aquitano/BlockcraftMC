package me.blockcraft.ttt.shop;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.ShotEvent;
import me.blockcraft.common.message.Messages;
import me.blockcraft.ttt.config.TttConfig;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The silenced pistol and the taser hit at once along the line of sight; walls stop them. The pistol's few shots hurt
 * and barely make a sound, the taser's charges hold a suspect still for a few seconds, unable to strike or shoot.
 * Each item's stack size is its ammunition.
 */
final class Guns {

    private static final int PISTOL_COOLDOWN = 15;
    private static final int TASER_COOLDOWN = 20;
    private static final double RAY_SIZE = 0.25;

    private final TttConfig.Shop config;
    private final Messages messages;
    private final Supplier<List<Player>> alive;
    private final Holds holds;
    private final Map<UUID, Integer> readyAt = new HashMap<>();
    private final Map<UUID, Integer> stunnedUntil = new HashMap<>();

    Guns(TttConfig.Shop config, Messages messages, Supplier<List<Player>> alive, Holds holds) {
        this.config = config;
        this.messages = messages;
        this.alive = alive;
        this.holds = holds;
    }

    void fire(Player shooter, ShopItem gun, ItemStack hand) {
        int now = Bukkit.getCurrentTick();
        if (stunned(shooter) || readyAt.getOrDefault(shooter.getUniqueId(), 0) > now) {
            return;
        }
        boolean pistol = gun == ShopItem.SILENCED_PISTOL;
        readyAt.put(shooter.getUniqueId(), now + (pistol ? PISTOL_COOLDOWN : TASER_COOLDOWN));
        hand.subtract();
        Bukkit.getPluginManager().callEvent(new ShotEvent(shooter));
        double range = pistol ? config.pistol().range() : config.taser().range();
        var target = target(shooter, range);
        trail(shooter, target.map(Player::getEyeLocation).map(eye -> eye.distance(shooter.getEyeLocation())).orElse(range), pistol);
        if (pistol) {
            shooter.getWorld().playSound(shooter.getLocation(), Sound.BLOCK_TRIPWIRE_CLICK_ON, 0.5f, 1.8f);
            target.ifPresent(victim -> victim.damage(config.pistol().damage(), shooter));
            return;
        }
        shooter.getWorld().playSound(shooter.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 0.8f, 1.6f);
        target.ifPresent(victim -> stun(shooter, victim));
    }

    boolean stunned(Player player) {
        return stunnedUntil.getOrDefault(player.getUniqueId(), 0) > Bukkit.getCurrentTick();
    }

    void forget(Player player) {
        stunnedUntil.remove(player.getUniqueId());
    }

    void clear() {
        readyAt.clear();
        stunnedUntil.clear();
    }

    private void stun(Player shooter, Player victim) {
        int ticks = config.taser().seconds() * 20;
        stunnedUntil.put(victim.getUniqueId(), Bukkit.getCurrentTick() + ticks);
        holds.hold(victim, ticks);
        victim.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, victim.getLocation().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.1);
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_BEE_STING, 1f, 0.6f);
        BlockcraftApi.get().notifications().feedback(victim, messages.get("shop.taser.hit-you", Placeholder.unparsed("player", shooter.getName())));
        BlockcraftApi.get().notifications().feedback(shooter, messages.get("shop.taser.hit", Placeholder.unparsed("player", victim.getName())));
    }

    private Optional<Player> target(Player shooter, double range) {
        var living = alive.get();
        var hit = shooter.getWorld().rayTrace(shooter.getEyeLocation(), shooter.getEyeLocation().getDirection(), range,
                FluidCollisionMode.NEVER, true, RAY_SIZE,
                entity -> entity instanceof Player player && !player.equals(shooter) && living.contains(player));
        return Optional.ofNullable(hit).map(result -> result.getHitEntity() instanceof Player player ? player : null);
    }

    private static void trail(Player shooter, double length, boolean pistol) {
        var eye = shooter.getEyeLocation();
        Vector step = eye.getDirection().multiply(0.5);
        var point = eye.clone().add(step.clone().multiply(2));
        var particle = pistol ? Particle.SMOKE : Particle.ELECTRIC_SPARK;
        for (double travelled = 1.0; travelled < length; travelled += 0.5) {
            shooter.getWorld().spawnParticle(particle, point, 1, 0, 0, 0, 0);
            point.add(step);
        }
    }
}
