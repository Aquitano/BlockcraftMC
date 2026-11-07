package me.blockcraft.sternenfront.appearance;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.RoundPhaseChangeEvent;
import me.blockcraft.sternenfront.game.CombatClass;
import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.game.LineOfSight;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import me.blockcraft.sternenfront.hero.Hero;
import me.blockcraft.sternenfront.reinforcement.Reinforcement;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class BodyRigs implements Listener, AutoCloseable {

    private final Plugin plugin;
    private final SternenfrontGame game;
    private final Map<UUID, BodyRig> rigs = new HashMap<>();
    private final Map<UUID, BodyRig> targets = new HashMap<>();
    private final BukkitTask task;
    private final boolean droids;
    private final boolean infantry;
    private final float turnDegrees;
    private final Map<CombatClass, RigMotion> motions = new EnumMap<>(CombatClass.class);
    private final Optional<AnimatedBodies> animated;
    private final Color hurtTint;

    private BodyRigs(Plugin plugin, SternenfrontGame game, boolean droids, boolean infantry) {
        this.plugin = plugin;
        this.game = game;
        this.droids = droids;
        this.infantry = infantry;
        turnDegrees = (float) Math.clamp(game.plugin().getConfig().getDouble("appearance.body-turn-degrees", 12), 1, 45);
        hurtTint = Color.fromRGB(Optional.ofNullable(TextColor.fromHexString("#" + game.plugin().getConfig().getString("appearance.hurt-tint", "")))
                .map(TextColor::value).orElse(0xFF5050));
        for (var role : CombatClass.values()) {
            motions.put(role, RigMotion.from(game.plugin().getConfig(), role));
        }
        var animatedBodies = game.plugin().getConfig().getStringList("appearance.animated-bodies");
        // Without the plugin every body stays a BodyRig, so a server that lacks it still plays the round.
        animated = animatedBodies.isEmpty() || !plugin.getServer().getPluginManager().isPluginEnabled("BetterModel") ? Optional.empty()
                : Optional.of(new AnimatedBodies(animatedBodies, this::outlined, hurtTint.asRGB()));
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public static BodyRigs start(Plugin plugin, SternenfrontGame game, boolean droids, boolean infantry) {
        return new BodyRigs(plugin, game, droids, infantry);
    }

    private boolean outlined(Player viewer, LivingEntity actor) {
        var config = plugin.getConfig();
        return config.getBoolean("outlines.enabled", true) && game.onField(viewer) && BlockcraftApi.get().resourcePack().applied(viewer)
                && game.factionOf(viewer).isPresent() && !game.factionOf(viewer).equals(game.factionOf(actor))
                && actor.getWorld().equals(viewer.getWorld())
                && TargetOutlines.inRange(viewer.getEyeLocation(), actor, config.getDouble("outlines.range", 64));
    }

    public Optional<LivingEntity> actor(Entity clicked) {
        return rigs.values().stream().filter(rig -> rig.actor().equals(clicked) || rig.owns(clicked))
                .map(BodyRig::actor).findFirst();
    }

    public Optional<RigSnapshot> snapshot(Entity actor) {
        return Optional.ofNullable(rigs.get(actor.getUniqueId())).map(BodyRig::snapshot);
    }

    public static String family(Faction faction, CombatClass role) {
        return RigBody.of(faction, role).family();
    }

    public List<Entity> outlineParts(LivingEntity actor, Player viewer) {
        var rig = rigs.get(actor.getUniqueId());
        return rig != null && rig.shownTo(viewer) ? rig.outlineParts() : viewer.canSee(actor) ? List.of(actor) : List.of();
    }

    private void tick() {
        var present = new HashSet<UUID>();
        for (var actor : game.combatants()) {
            if (!eligible(actor)) {
                continue;
            }
            var id = actor.getUniqueId();
            present.add(id);
            var hero = actor instanceof Player player ? game.heroOf(player) : Optional.<Hero>empty();
            var unit = actor instanceof Player player ? game.reinforcementOf(player) : Optional.<Reinforcement>empty();
            var body = unit.map(RigBody::of).orElseGet(() -> RigBody.of(game.factionOf(actor).orElseThrow(), game.combatClassOf(actor), hero));
            if (animated.filter(bodies -> !(actor instanceof Player) && bodies.draws(body)).isPresent()) {
                animated.get().keep(actor, body, game.effects().recoil(actor), plugin.getServer().getOnlinePlayers());
                continue;
            }
            var existing = rigs.get(id);
            if (existing != null && existing.body() != body) {
                remove(id);
            }
            var rig = rigs.computeIfAbsent(id, ignored -> {
                var created = new BodyRig(plugin, actor, body, game::isPlaying,
                        () -> game.bots().aim(actor).orElseGet(() -> actor.getEyeLocation().getDirection()), turnDegrees,
                        hero.isPresent() ? Optional.empty() : Optional.of(motions.get(unit
                                .map(chosen -> chosen == Reinforcement.ARC_TROOPER ? CombatClass.HEAVY : CombatClass.SCOUT)
                                .orElseGet(() -> game.combatClassOf(actor)))));
                targets.put(created.targetId(), created);
                return created;
            });
            rig.tick(game.effects().recoil(actor));
        }
        animated.ifPresent(bodies -> bodies.forgetAbsent(present));
        for (var id : new HashSet<>(rigs.keySet())) {
            if (!present.contains(id)) {
                remove(id);
            }
        }
    }

    private boolean eligible(LivingEntity actor) {
        return actor.isValid() && game.onField(actor) && actor.getWorld().equals(game.map().world())
                && (droids && game.factionOf(actor).filter(Faction.HEGEMONIE::equals).isPresent()
                || infantry && game.factionOf(actor).filter(Faction.KOLONIEN::equals).isPresent())
                && (!(actor instanceof Player player) || !BlockcraftApi.get().buildMode().isEnabled(player));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        var rig = targets.get(event.getEntity().getUniqueId());
        if (rig == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getDamager() instanceof Player player && rig.shownTo(player) && !player.isInsideVehicle()
                && game.onField(player) && eligible(rig.actor()) && canReach(player, rig.actor())) {
            // Interaction attacks arrive before vanilla computes damage or resets the attack cooldown.
            player.attack(rig.actor());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        var rig = rigs.get(event.getEntity().getUniqueId());
        if (rig != null && event.getFinalDamage() > 0) {
            rig.hurt(hurtTint);
        }
    }

    private static boolean canReach(Player player, LivingEntity target) {
        var eye = player.getEyeLocation();
        if (!eye.getWorld().equals(target.getWorld())) {
            return false;
        }
        var attribute = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (attribute == null) {
            return false;
        }
        var hit = target.getBoundingBox().rayTrace(eye.toVector(), eye.getDirection(), attribute.getValue());
        return hit != null && LineOfSight.clear(eye, hit.getHitPosition().subtract(eye.toVector()));
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        remove(event.getPlayer().getUniqueId());
        rigs.values().forEach(rig -> rig.refresh(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        remove(event.getPlayer().getUniqueId());
        rigs.values().forEach(rig -> rig.forget(event.getPlayer()));
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        remove(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onPhase(RoundPhaseChangeEvent event) {
        if (event.phase() != GamePhase.INGAME) {
            clear();
        }
    }

    private void remove(UUID id) {
        var rig = rigs.remove(id);
        if (rig != null) {
            targets.remove(rig.targetId());
            rig.close();
        }
    }

    private void clear() {
        animated.ifPresent(AnimatedBodies::close);
        rigs.values().forEach(BodyRig::close);
        rigs.clear();
        targets.clear();
    }

    @Override
    public void close() {
        task.cancel();
        HandlerList.unregisterAll(this);
        clear();
    }
}
