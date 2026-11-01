package me.blockcraft.core.staff;

import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class TrollMode implements Listener, AutoCloseable {

    public enum Trick {
        PUSH(Material.SLIME_BALL),
        LAUNCH(Material.FIREWORK_ROCKET),
        CREEPER(Material.CREEPER_HEAD),
        EXPLOSION(Material.TNT),
        THUNDER(Material.LIGHTNING_ROD),
        CONFETTI(Material.PINK_DYE),
        GUARDIAN(Material.PRISMARINE_SHARD),
        FAKEOP(Material.COMMAND_BLOCK),
        FAKEDEOP(Material.BARRIER),
        LEVITATE(Material.FEATHER, PotionEffectType.LEVITATION),
        SLOW(Material.COBWEB, PotionEffectType.SLOWNESS),
        BLIND(Material.INK_SAC, PotionEffectType.BLINDNESS),
        NAUSEA(Material.PUFFERFISH, PotionEffectType.NAUSEA),
        LAG(Material.CLOCK);

        private final Material material;
        private final PotionEffectType potion;

        Trick(Material material) {
            this(material, null);
        }

        Trick(Material material, PotionEffectType potion) {
            this.material = material;
            this.potion = potion;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public Material material() {
            return material;
        }
    }

    private record ActiveEffect(UUID actor, Player target, Trick trick, BukkitTask task) {
    }

    private static final int EFFECT_TICKS = 120;
    private static final int LAG_STEP_TICKS = 10;
    private static final long COOLDOWN_NANOS = 2_000_000_000L;

    private final Plugin plugin;
    private final Messages messages;
    private final Map<UUID, ActiveEffect> active = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public TrollMode(Plugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public boolean coolingDown(Player target) {
        var until = cooldowns.get(target.getUniqueId());
        return until != null && System.nanoTime() - until < 0;
    }

    public boolean apply(Player actor, Player target, Trick trick) {
        if (coolingDown(target)) {
            return false;
        }
        if (trick.potion != null) {
            var previous = active.get(target.getUniqueId());
            if (target.hasPotionEffect(trick.potion) && (previous == null || previous.trick().potion != trick.potion)) {
                return false;
            }
            stop(target);
            if (!target.addPotionEffect(new PotionEffect(trick.potion, EFFECT_TICKS, 0, false, false, true))) {
                return false;
            }
            var task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> stop(target), EFFECT_TICKS);
            active.put(target.getUniqueId(), new ActiveEffect(actor.getUniqueId(), target, trick, task));
        } else {
            var at = target.getLocation().add(0, 1, 0);
            switch (trick) {
                case PUSH -> {
                    var push = target.getLocation().getDirection().setY(0);
                    if (push.lengthSquared() < 0.001) {
                        push = new Vector(0, 0, 1);
                    }
                    target.setVelocity(push.normalize().multiply(-0.8).setY(0.35));
                }
                case LAUNCH -> target.setVelocity(new Vector(0, 0.9, 0));
                case CREEPER -> target.playSound(at, Sound.ENTITY_CREEPER_PRIMED, 1, 1);
                case EXPLOSION -> {
                    target.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1);
                    target.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1, 1);
                }
                case THUNDER -> {
                    target.spawnParticle(Particle.ELECTRIC_SPARK, at, 40, 0.5, 1, 0.5, 0.1);
                    target.playSound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1, 1);
                }
                case CONFETTI -> {
                    target.spawnParticle(Particle.FIREWORK, at, 60, 0.6, 0.8, 0.6, 0.05);
                    target.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1, 1);
                }
                case GUARDIAN -> {
                    target.spawnParticle(Particle.ELDER_GUARDIAN, at, 1);
                    target.playSound(at, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 1);
                }
                case FAKEOP, FAKEDEOP -> target.sendMessage(messages.get("troll.effects." + trick.key() + ".message",
                        Placeholder.unparsed("player", target.getName())));
                case LAG -> {
                    stop(target);
                    var task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Rubberband(target),
                            LAG_STEP_TICKS, LAG_STEP_TICKS);
                    active.put(target.getUniqueId(), new ActiveEffect(actor.getUniqueId(), target, trick, task));
                }
                default -> throw new IllegalStateException("Potion trick has no potion: " + trick);
            }
        }
        cooldowns.put(target.getUniqueId(), System.nanoTime() + COOLDOWN_NANOS);
        return true;
    }

    public void stop(Player target) {
        var effect = active.remove(target.getUniqueId());
        if (effect != null) {
            effect.task().cancel();
            if (effect.trick().potion != null) {
                target.removePotionEffect(effect.trick().potion);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionChange(EntityPotionEffectEvent event) {
        var effect = active.get(event.getEntity().getUniqueId());
        if (effect != null && event.getModifiedType().equals(effect.trick().potion)) {
            active.remove(event.getEntity().getUniqueId());
            effect.task().cancel();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        stop(player);
        cooldowns.remove(player.getUniqueId());
        List.copyOf(active.values()).stream().filter(effect -> effect.actor().equals(player.getUniqueId()))
                .forEach(effect -> stop(effect.target()));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        stop(event.getEntity());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        stop(event.getPlayer());
    }

    private final class Rubberband implements Runnable {
        private final Player target;
        private Location anchor;
        private int steps;

        Rubberband(Player target) {
            this.target = target;
        }

        @Override
        public void run() {
            if (++steps * LAG_STEP_TICKS >= EFFECT_TICKS) {
                stop(target);
            } else if (anchor == null) {
                anchor = target.getLocation();
            } else {
                var now = target.getLocation();
                target.teleport(anchor.setRotation(now.getYaw(), now.getPitch()));
                anchor = null;
            }
        }
    }

    @Override
    public void close() {
        List.copyOf(active.values()).forEach(effect -> stop(effect.target()));
        cooldowns.clear();
    }
}
