package me.blockcraft.starwars;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.tracker.EntityTracker;
import me.blockcraft.api.replay.RecordedModel;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Husk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.util.BoundingBox;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class Creatures implements Listener, AutoCloseable {
    record Profile(double width, double height, double depth) {}
    static final Map<String, Profile> PROFILES = Map.of(
            "nexu_cub", new Profile(.9, .8, 1.4), "nexu", new Profile(1.8, 2, 2.5),
            "acklay", new Profile(3.4, 4.4, 4), "rancor", new Profile(3, 6.1, 3.6),
            "reek", new Profile(2.3, 2.8, 4));

    static final class Actor {
        final String kind;
        final Husk entity;
        final EntityTracker tracker;
        final boolean practice;
        final double maxHealth;
        double practiceHealth;
        int resetAt;

        Actor(String kind, Husk entity, EntityTracker tracker, boolean practice, double maxHealth) {
            this.kind = kind;
            this.entity = entity;
            this.tracker = tracker;
            this.practice = practice;
            this.maxHealth = maxHealth;
            practiceHealth = maxHealth;
        }

        BoundingBox bounds() {
            var size = PROFILES.get(kind);
            var position = entity.getLocation();
            boolean sideways = Math.abs(Math.sin(Math.toRadians(position.getYaw()))) > .707;
            double x = (sideways ? size.depth() : size.width()) / 2;
            double z = (sideways ? size.width() : size.depth()) / 2;
            return BoundingBox.of(position, x, 0, z).expand(0, 0, 0, 0, size.height(), 0);
        }
    }

    private final StarWarsPlugin plugin;
    private final Messages messages;
    private final Map<UUID, Actor> actors = new HashMap<>();

    Creatures(StarWarsPlugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    Collection<Actor> actors() { return actors.values(); }

    Optional<Actor> spawn(String kind, Location at, boolean practice) {
        var model = BetterModel.model("sw_" + kind);
        if (model.isEmpty() || !PROFILES.containsKey(kind)) return Optional.empty();
        var settings = plugin.getConfig().getConfigurationSection("creatures." + kind);
        if (settings == null) return Optional.empty();
        double health = Math.clamp(settings.getDouble("health"), 1, 1024);
        var entity = at.getWorld().spawn(at, Husk.class, mob -> {
            mob.setPersistent(false);
            mob.addScoreboardTag("blockcraft.starwars");
            mob.setRemoveWhenFarAway(false);
            mob.setCanPickupItems(false);
            mob.setShouldBurnInDay(false);
            mob.setAdult();
            mob.setSilent(true);
            mob.setAI(!practice);
            mob.setInvulnerable(practice);
            mob.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
            mob.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(Math.clamp(settings.getDouble("damage"), 0, 100));
            mob.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(Math.clamp(settings.getDouble("speed"), .05, .5));
            mob.setHealth(health);
            mob.customName(messages.get("creatures." + kind));
        });
        var tracker = model.orElseThrow().getOrCreate(BukkitAdapter.adapt(entity));
        RecordedModel.mark(entity, "sw_" + kind);
        var actor = new Actor(kind, entity, tracker, practice, health);
        actors.put(entity.getUniqueId(), actor);
        return Optional.of(actor);
    }

    void hit(Actor actor, Player player, double damage, boolean loan) {
        if (!actor.entity.isValid() || actor.resetAt > 0 || (loan && !actor.practice)) return;
        if (!actor.practice) {
            actor.entity.damage(damage, player);
            return;
        }
        actor.practiceHealth = Math.max(0, actor.practiceHealth - damage);
        actor.tracker.animate("damage", AnimationModifier.DEFAULT_WITH_PLAY_ONCE);
        player.sendActionBar(messages.get("hit", Placeholder.component("creature", messages.get("creatures." + actor.kind)),
                Placeholder.unparsed("health", Integer.toString((int) Math.ceil(actor.practiceHealth))),
                Placeholder.unparsed("max", Integer.toString((int) actor.maxHealth))));
        if (actor.practiceHealth == 0) {
            actor.tracker.animate("death", AnimationModifier.DEFAULT_WITH_PLAY_ONCE);
            actor.resetAt = plugin.getServer().getCurrentTick() + 40;
            player.sendMessage(messages.get("defeated"));
        }
    }

    void tick(int now) {
        for (var actor : actors.values()) {
            if (actor.resetAt > 0 && now >= actor.resetAt) {
                actor.tracker.stopAnimation("death");
                actor.practiceHealth = actor.maxHealth;
                actor.resetAt = 0;
            }
        }
        actors.values().removeIf(actor -> {
            if (actor.entity.isValid()) return false;
            actor.tracker.close();
            return true;
        });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void attack(EntityDamageByEntityEvent event) {
        var actor = actors.get(event.getDamager().getUniqueId());
        if (actor != null) actor.tracker.animate("attack", AnimationModifier.DEFAULT_WITH_PLAY_ONCE);
    }

    @EventHandler
    public void target(EntityTargetLivingEntityEvent event) {
        var actor = actors.get(event.getEntity().getUniqueId());
        if (actor != null && (actor.practice || !(event.getTarget() instanceof Player))) event.setCancelled(true);
    }

    @EventHandler
    public void death(EntityDeathEvent event) {
        if (actors.containsKey(event.getEntity().getUniqueId())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @Override public void close() {
        actors.values().forEach(actor -> { actor.tracker.close(); actor.entity.remove(); });
        actors.clear();
    }
}
