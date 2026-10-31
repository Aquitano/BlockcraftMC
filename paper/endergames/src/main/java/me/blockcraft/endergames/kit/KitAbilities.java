package me.blockcraft.endergames.kit;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.endergames.config.EnderGamesConfig.Abilities;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Animals;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * What the kits can do beyond the items they start with, for players who fight the round. The numbers come from
 * the {@code abilities} section of config.yml.
 */
public final class KitAbilities implements Listener {

    private static final int TICKS = 20;
    private static final double HEAD_HEIGHT = 0.4;
    private static final double KANGAROO_FORWARD = 1.2;
    private static final double KANGAROO_UP = 0.9;
    private static final int SPEED_AMPLIFIER = 1;
    private static final int MAX_RESISTANCE_AMPLIFIER = 1;
    private static final double STOMP_HEIGHT = 2.0;

    private final KitPlayers players;
    private final Abilities numbers;
    private final Messages messages;
    private final Map<UUID, Long> invulnerableUntil = new HashMap<>();

    public KitAbilities(KitPlayers players, Abilities numbers, Messages messages) {
        this.players = players;
        this.numbers = numbers;
        this.messages = messages;
    }

    private boolean has(Player player, Kit kit) {
        return players.activeKit(player).filter(kit::equals).isPresent();
    }

    /** The Teleporter comes out of a swap healed, fed and briefly untouchable. */
    public void swapped(Player player) {
        if (!has(player, Kit.TELEPORTER)) {
            return;
        }
        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
        player.setFoodLevel(20);
        invulnerableUntil.put(player.getUniqueId(), System.nanoTime() + numbers.teleporterInvulnerableSeconds() * 1_000_000_000L);
        BlockcraftApi.get().notifications().feedback(player, messages.get("ability.teleporter",
                Formatter.number("seconds", numbers.teleporterInvulnerableSeconds())));
        Sounds.HEAL.play(player);
    }

    /** The Tracker's compass names the nearest player and their kit, however far away. */
    public void track(Player tracker) {
        var origin = tracker.getLocation();
        players.fighting().stream()
                .filter(other -> !other.equals(tracker) && other.getWorld().equals(origin.getWorld()) && tracker.canSee(other))
                .min(Comparator.comparingDouble(other -> origin.distanceSquared(other.getLocation())))
                .ifPresentOrElse(target -> {
                    tracker.setCompassTarget(target.getLocation());
                    var kit = players.activeKit(target).map(each -> messages.get("kit." + each.key() + ".name"))
                            .orElseGet(() -> messages.get("kit.none"));
                    BlockcraftApi.get().notifications().feedback(tracker, messages.get("tracker.kit-found",
                            Placeholder.unparsed("player", target.getName()), Placeholder.component("kit", kit),
                            Formatter.number("distance", (int) origin.distance(target.getLocation()))));
                    Sounds.NOTIFY.play(tracker);
                }, () -> {
                    BlockcraftApi.get().notifications().feedback(tracker, messages.get("tracker.empty"));
                    Sounds.MENU_DENY.play(tracker);
                });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || players.activeKit(player).isEmpty()) {
            return;
        }
        var until = invulnerableUntil.get(player.getUniqueId());
        if (until != null && System.nanoTime() - until < 0) {
            event.setCancelled(true);
            return;
        }
        var kit = players.activeKit(player).get();
        var type = event.getDamageSource().getDamageType();
        switch (kit) {
            case ENDERMAN -> event.setCancelled(type == DamageType.ENDER_PEARL);
            case BOMBER -> event.setCancelled(type == DamageType.EXPLOSION || type == DamageType.PLAYER_EXPLOSION);
            case THOR -> event.setCancelled(type == DamageType.LIGHTNING_BOLT);
            case STOMPER -> {
                if (type == DamageType.FALL) {
                    stomp(player, event.getDamage());
                    event.setDamage(Math.min(event.getDamage(), numbers.stomperMaxFallDamage()));
                }
            }
            default -> {
            }
        }
    }

    /** Whoever stands where the Stomper lands takes the fall the Stomper would have taken. */
    private void stomp(Player stomper, double damage) {
        var landing = stomper.getLocation();
        var hit = players.fighting().stream()
                .filter(other -> !other.equals(stomper) && other.getWorld().equals(landing.getWorld()))
                .filter(other -> {
                    var location = other.getLocation();
                    return Math.hypot(location.getX() - landing.getX(), location.getZ() - landing.getZ()) <= numbers.stomperRadius()
                            && Math.abs(location.getY() - landing.getY()) <= STOMP_HEIGHT;
                })
                .toList();
        if (hit.isEmpty()) {
            return;
        }
        hit.forEach(victim -> victim.damage(damage, stomper));
        landing.getWorld().playSound(landing, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.6f);
        landing.getWorld().spawnParticle(Particle.EXPLOSION, landing, 1);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        var damager = event.getDamager();
        if (damager instanceof Player attacker && has(attacker, Kit.SKORPION)) {
            poison(victim, numbers.scorpionMeleeSeconds());
        }
        if (damager instanceof AbstractArrow arrow && arrow.getShooter() instanceof Player shooter) {
            if (has(shooter, Kit.SKORPION)) {
                poison(victim, numbers.scorpionArrowSeconds());
            }
            if (has(shooter, Kit.BOGENSCHUETZE) && victim instanceof Player target && headshot(arrow, target)) {
                event.setDamage(event.getDamage() * numbers.headshotMultiplier());
                BlockcraftApi.get().notifications().feedback(shooter, messages.get("ability.headshot",
                        Placeholder.unparsed("player", target.getName())));
                Sounds.KILL.play(shooter);
            }
        }
        if (victim instanceof Player scared && has(scared, Kit.ANGSTHASE)) {
            scared.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, numbers.scaredySeconds() * TICKS, SPEED_AMPLIFIER));
        }
    }

    private static boolean headshot(AbstractArrow arrow, Player target) {
        var helmet = target.getInventory().getHelmet();
        return (helmet == null || helmet.isEmpty()) && arrow.getLocation().getY() >= target.getEyeLocation().getY() - HEAD_HEIGHT;
    }

    private static void poison(LivingEntity victim, int seconds) {
        if (seconds > 0) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, seconds * TICKS, 0));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPotion(EntityPotionEffectEvent event) {
        var effect = event.getNewEffect();
        if (event.getEntity() instanceof Player player && has(player, Kit.SKORPION)
                && effect != null && effect.getType().equals(PotionEffectType.POISON)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onKill(EntityDeathEvent event) {
        var dead = event.getEntity();
        var killer = dead.getKiller();
        if (killer == null || killer.equals(dead)) {
            return;
        }
        var kit = players.activeKit(killer);
        if (kit.isEmpty()) {
            return;
        }
        boolean player = dead instanceof Player;
        switch (kit.get()) {
            case ENDERMAN -> {
                if (player && numbers.pearlsPerKill() > 0) {
                    give(killer, ItemStack.of(Material.ENDER_PEARL, numbers.pearlsPerKill()));
                }
            }
            case SPINNE -> {
                if (player && numbers.cobwebsPerKill() > 0) {
                    give(killer, ItemStack.of(Material.COBWEB, numbers.cobwebsPerKill()));
                }
            }
            case VAMPIR -> {
                double max = killer.getAttribute(Attribute.MAX_HEALTH).getValue();
                killer.setHealth(player ? max : Math.min(max, killer.getHealth() + numbers.vampireMobHeal()));
                killer.getWorld().spawnParticle(Particle.HEART, killer.getLocation().add(0, 2, 0), 3, 0.3, 0.2, 0.3);
            }
            case BERSERKER -> {
                var current = killer.getPotionEffect(PotionEffectType.RESISTANCE);
                int amplifier = current == null ? 0 : Math.min(MAX_RESISTANCE_AMPLIFIER, current.getAmplifier() + 1);
                killer.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, numbers.berserkerSeconds() * TICKS, amplifier));
            }
            case JAEGER -> {
                if (dead instanceof Animals) {
                    event.getDrops().replaceAll(drop -> drop.withType(Cooking.cooked(drop.getType())));
                }
            }
            default -> {
            }
        }
    }

    private static void give(Player player, ItemStack item) {
        player.getInventory().addItem(item).values().forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        var player = event.getPlayer();
        if (!event.isFlying() || !has(player, Kit.KAENGURU)) {
            return;
        }
        event.setCancelled(true);
        player.setFlying(false);
        if (player.getHealth() < numbers.kangarooMinHealth()) {
            BlockcraftApi.get().notifications().feedback(player, messages.get("ability.kangaroo-weak"));
            return;
        }
        player.setAllowFlight(false);
        player.setVelocity(player.getLocation().getDirection().setY(0).normalize().multiply(KANGAROO_FORWARD).setY(KANGAROO_UP));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_RABBIT_JUMP, 1.0f, 0.8f);
    }

    /** The Känguru jumps again once it stands on something. */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        var player = event.getPlayer();
        if (player.getAllowFlight() || !event.hasChangedPosition() || !has(player, Kit.KAENGURU)) {
            return;
        }
        if (!event.getTo().clone().subtract(0, 0.1, 0).getBlock().isPassable()) {
            player.setAllowFlight(true);
        }
    }

    /** Not ignoreCancelled: Bukkit fires a right-click into the air as already cancelled, and the hammer must hear it. */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.useItemInHand() == Event.Result.DENY || event.getHand() != EquipmentSlot.HAND
                || !event.getAction().isRightClick() || event.getItem() == null) {
            return;
        }
        var player = event.getPlayer();
        var item = event.getItem();
        if (item.getType() == Material.GOLDEN_AXE && has(player, Kit.THOR)) {
            event.setCancelled(true);
            strike(player, item);
        } else if (item.getType() == Material.MUSHROOM_STEW && has(player, Kit.SUPPENMEISTER)) {
            double max = player.getAttribute(Attribute.MAX_HEALTH).getValue();
            if (player.getHealth() < max) {
                event.setCancelled(true);
                player.setHealth(Math.min(max, player.getHealth() + numbers.soupHeal()));
                player.getInventory().setItemInMainHand(ItemStack.of(Material.BOWL));
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1.0f, 1.2f);
            }
        }
    }

    private void strike(Player thor, ItemStack hammer) {
        if (thor.hasCooldown(hammer)) {
            return;
        }
        Optional.ofNullable(thor.getTargetBlockExact(numbers.thorRange())).ifPresentOrElse(target -> {
            var bolt = target.getWorld().strikeLightning(target.getLocation().add(0.5, 1, 0.5));
            bolt.setCausingPlayer(thor);
            thor.setCooldown(hammer, numbers.thorCooldownSeconds() * TICKS);
        }, () -> {
            BlockcraftApi.get().notifications().feedback(thor, messages.get("ability.thor-no-target"));
            Sounds.MENU_DENY.play(thor);
        });
    }

    /** The Holzfäller fells the whole tree with the log he breaks. */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        var block = event.getBlock();
        if (!Tag.LOGS.isTagged(block.getType()) || !has(event.getPlayer(), Kit.HOLZFAELLER)) {
            return;
        }
        var tool = event.getPlayer().getInventory().getItemInMainHand();
        Reach.from(block, KitAbilities::touching, each -> Tag.LOGS.isTagged(each.getType()), numbers.lumberjackMaxLogs())
                .forEach(log -> log.breakNaturally(tool));
    }

    private static List<Block> touching(Block block) {
        return IntStream.rangeClosed(-1, 1).boxed()
                .flatMap(x -> IntStream.rangeClosed(-1, 1).boxed()
                        .flatMap(y -> IntStream.rangeClosed(-1, 1)
                                .filter(z -> x != 0 || y != 0 || z != 0)
                                .mapToObj(z -> block.getRelative(x, y, z))))
                .toList();
    }

    /** Every axe the Holzfäller crafts comes out sharpened. */
    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        var result = event.getInventory().getResult();
        if (result == null || !Tag.ITEMS_AXES.isTagged(result.getType())
                || !(event.getView().getPlayer() instanceof Player player) || !has(player, Kit.HOLZFAELLER)) {
            return;
        }
        var sharpened = result.clone();
        sharpened.addEnchantment(Enchantment.SHARPNESS, 1);
        event.getInventory().setResult(sharpened);
    }

    public void forget(Player player) {
        invulnerableUntil.remove(player.getUniqueId());
    }

    public void reset() {
        invulnerableUntil.clear();
    }
}
