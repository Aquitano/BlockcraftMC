package me.blockcraft.api.game;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import me.blockcraft.api.item.Items;
import me.blockcraft.common.calendar.NetworkCalendar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrowableProjectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

/**
 * A throwing fight that only pushes: snowballs in winter, pumpkins at Halloween, eggs at Easter. The thrown item is
 * an egg in the season's look that never runs out and never hatches; a hit knocks the target back when
 * {@code counts} lets the throw count, bursts into the season's crumbs and tells {@code onHit}. The lobby offers it
 * as a toy and Duell as a mode.
 */
public final class ThrowFight implements Listener {

    public enum Ammo {
        SNOWBALL(Material.SNOWBALL, "block.snow.break"),
        PUMPKIN(Material.CARVED_PUMPKIN, "block.pumpkin.carve"),
        EGG(Material.EGG, "entity.turtle.egg_break");

        private final Material look;
        private final Key hitSound;

        Ammo(Material look, String hitSound) {
            this.look = look;
            this.hitSound = Key.key(hitSound);
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** What the season throws: pumpkins at Halloween, eggs at Easter, snowballs any other time. */
        public static Ammo of(NetworkCalendar calendar) {
            return calendar.is(NetworkCalendar.Occasion.HALLOWEEN) ? PUMPKIN
                    : calendar.is(NetworkCalendar.Occasion.EASTER) ? EGG : SNOWBALL;
        }
    }

    /** A game's {@code throw-fight} section: during which occasions it is on, and how hard and how often a throw pushes. */
    public record Settings(Set<NetworkCalendar.Occasion> during, double knockback, int cooldownTicks) {

        public static Settings fromYaml(Map<String, Object> config) {
            var during = EnumSet.noneOf(NetworkCalendar.Occasion.class);
            if (config.get("during") instanceof List<?> occasions) {
                for (var occasion : NetworkCalendar.Occasion.values()) {
                    if (occasions.stream().map(String::valueOf).anyMatch(name -> name.equalsIgnoreCase(occasion.name()))) {
                        during.add(occasion);
                    }
                }
            } else {
                during.addAll(List.of(NetworkCalendar.Occasion.WINTER, NetworkCalendar.Occasion.HALLOWEEN, NetworkCalendar.Occasion.EASTER));
            }
            return new Settings(Set.copyOf(during),
                    config.get("knockback") instanceof Number knockback ? Math.clamp(knockback.doubleValue(), 0, 3) : 0.5,
                    config.get("cooldown-ticks") instanceof Number cooldown ? Math.clamp(cooldown.intValue(), 0, 200) : 10);
        }

        /** The season's ammunition while one of {@code during} is celebrated. */
        public Optional<Ammo> ammo(NetworkCalendar calendar) {
            return during.stream().anyMatch(calendar::is) ? Optional.of(Ammo.of(calendar)) : Optional.empty();
        }
    }

    private static final NamespacedKey MARK = new NamespacedKey("blockcraft", "throw_fight");
    private static final int CRUMBS = 12;

    private final BiPredicate<Player, Player> counts;
    private final BiConsumer<Player, Player> onHit;
    private final double strength;
    private final int cooldownTicks;

    public ThrowFight(BiPredicate<Player, Player> counts, BiConsumer<Player, Player> onHit, double strength, int cooldownTicks) {
        this.counts = counts;
        this.onHit = onHit;
        this.strength = strength;
        this.cooldownTicks = cooldownTicks;
    }

    /** The throwable in the ammo's look, named as the caller says. */
    public static ItemStack item(Ammo ammo, Component name) {
        var item = Items.of(Material.EGG).name(name).model(ammo.look.getKey()).build();
        item.editMeta(meta -> meta.getPersistentDataContainer().set(MARK, PersistentDataType.STRING, ammo.name()));
        return item;
    }

    private static boolean marked(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(MARK, PersistentDataType.STRING);
    }

    private static Ammo ammo(ItemStack item) {
        return Ammo.valueOf(item.getItemMeta().getPersistentDataContainer().get(MARK, PersistentDataType.STRING));
    }

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(PlayerLaunchProjectileEvent event) {
        if (marked(event.getItemStack())) {
            event.setShouldConsume(false);
            event.getPlayer().setCooldown(Material.EGG, cooldownTicks);
        }
    }

    @EventHandler
    public void onHatch(PlayerEggThrowEvent event) {
        if (marked(event.getEgg().getItem())) {
            event.setHatching(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof ThrowableProjectile thrown) || !marked(thrown.getItem())) {
            return;
        }
        var ammo = ammo(thrown.getItem());
        var at = thrown.getLocation();
        at.getWorld().spawnParticle(Particle.ITEM, at, CRUMBS, 0.15, 0.15, 0.15, 0.05, ItemStack.of(ammo.look));
        at.getWorld().playSound(Sound.sound(ammo.hitSound, Sound.Source.PLAYER, 0.8f, 1.2f), at.getX(), at.getY(), at.getZ());
        thrown.remove();
        event.setCancelled(true);
        if (!(event.getHitEntity() instanceof Player victim) || !(thrown.getShooter() instanceof Player shooter)
                || shooter.equals(victim) || !counts.test(shooter, victim)) {
            return;
        }
        var velocity = thrown.getVelocity();
        if (velocity.lengthSquared() > 0) {
            victim.knockback(strength, -velocity.getX(), -velocity.getZ());
            victim.playHurtAnimation(shooter.getYaw());
        }
        onHit.accept(shooter, victim);
    }
}
