package me.blockcraft.sternenfront.vehicle;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.world.BlocksChangedEvent;
import me.blockcraft.sternenfront.combat.Melee;
import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.game.SoundCue;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import me.blockcraft.sternenfront.hero.Hero;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Pig;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class Speeders implements Listener {

    private static final int PAD_SPACING = 6;
    private static final int RIDDEN_POSE_TICKS = 2;
    private static final int PARKED_POSE_TICKS = 10;
    private static final int WAKE_TICKS = 2;
    private static final int DAMAGE_EFFECT_TICKS = 4;
    private static final int STEP_LOOKAHEAD_TICKS = 3;
    private static final int BOARDING_GRACE_TICKS = 10;
    private static final double RAM_REACH = 0.4;
    private static final double EXPLOSION_EDGE_SHARE = 0.3;
    private static final double EXPLOSION_PUSH = 0.8;

    private static final class Craft {
        private final Faction faction;
        private final Location home;
        private final Map<UUID, Long> rammed = new HashMap<>();
        private Pig body;
        private ItemDisplay model;
        private Interaction seat;
        private double speed;
        private double shownSpeed;
        private double shownHeight;
        private double turning;
        private SpeederMotion.Boost boost = SpeederMotion.Boost.FULL;
        private SpeederPose pose = SpeederPose.LEVEL;
        private Transformation sent;
        private boolean damaged;
        private Vector previous;
        private long boarded;
        private long lastPull = -1;
        private long nextSpawn;
        private long unattended;

        private Craft(Faction faction, Location home) {
            this.faction = faction;
            this.home = home;
        }
    }

    private final SternenfrontGame game;
    private final SpeederConfig config;
    private final List<Craft> craft = new ArrayList<>();

    public Speeders(SternenfrontGame game, SpeederConfig config) {
        this.game = game;
        this.config = config;
        game.plugin().getServer().getPluginManager().registerEvents(this, game.plugin());
    }

    public void start() {
        if (!config.enabled()) {
            return;
        }
        for (var faction : Faction.values()) {
            var first = game.map().headquarters().get(faction).spawns().getFirst().clone().add(0, 0, 16);
            var blocks = new ArrayList<Block>();
            for (int pad = 0; pad < config.perFaction(); pad++) {
                var home = first.clone().add(0, 0, (double) pad * PAD_SPACING);
                for (int x = -1; x <= 1; x++) {
                    for (int z = -2; z <= 2; z++) {
                        var block = home.clone().add(x, -1, z).getBlock();
                        block.setType(x == 0 ? Material.LIGHT_GRAY_CONCRETE : Material.GRAY_CONCRETE, false);
                        blocks.add(block);
                    }
                }
                craft.add(new Craft(faction, home));
            }
            Bukkit.getPluginManager().callEvent(new BlocksChangedEvent(List.copyOf(blocks)));
        }
    }

    public Optional<Faction> faction(Entity entity) {
        return craft.stream().filter(each -> entity.equals(each.body)).map(each -> each.faction).findFirst();
    }

    public List<Pig> entities() {
        return craft.stream().map(each -> each.body).filter(body -> body != null && body.isValid() && !body.isDead()).toList();
    }

    public void tick(long tick) {
        for (var each : craft) {
            if (each.body == null) {
                if (tick >= each.nextSpawn) {
                    spawn(each, tick);
                }
                continue;
            }
            if (!each.body.isValid() || each.body.isDead()) {
                destroy(each, tick);
                continue;
            }
            var rider = each.body.getPassengers().stream().filter(Player.class::isInstance).map(Player.class::cast).findFirst();
            if (rider.isPresent() && (!game.onField(rider.get()) || game.factionOf(rider.get()).filter(each.faction::equals).isEmpty())) {
                each.body.eject();
                rider = Optional.empty();
            }
            if (rider.isPresent()) {
                double moved = each.body.getLocation().toVector().subtract(each.previous).setY(0).length();
                drive(each, rider.get());
                each.unattended = tick;
                if (tick % 10 == 0) {
                    BlockcraftApi.get().notifications().status(rider.get(), game.messages().get("speeder.driving",
                            Formatter.number("health", Math.ceil(each.body.getHealth())),
                            Formatter.number("speed", Math.round(moved * 20)),
                            Formatter.number("boost", Math.round(each.boost.charge() * 100))));
                }
                ram(each, rider.get(), tick);
                if (each.body == null) {
                    continue;
                }
            } else {
                each.speed = 0;
                each.turning = 0;
                each.boost = each.boost.next(false, config.boost());
                each.body.setVelocity(new Vector(0, each.body.getVelocity().getY(), 0));
                if (tick - each.unattended >= config.idleReturnTicks() && each.body.getLocation().distanceSquared(each.home) > 9) {
                    each.body.teleport(each.home);
                    each.shownHeight = each.home.getY();
                    each.unattended = tick;
                }
            }
            if (!game.map().playArea().contains(each.body.getLocation().toVector()) || each.body.isInWater()) {
                destroy(each, tick);
                continue;
            }
            each.previous = each.body.getLocation().toVector();
            present(each, rider.isPresent(), tick);
        }
    }

    private void spawn(Craft each, long tick) {
        each.body = each.home.getWorld().spawn(each.home, Pig.class, pig -> {
            pig.setPersistent(false);
            pig.setSilent(true);
            pig.setInvisible(true);
            pig.setAdult();
            pig.setAgeLock(true);
            pig.setSaddle(true);
            pig.setCollidable(true);
            pig.getAttribute(Attribute.STEP_HEIGHT).setBaseValue(SpeederMotion.STEP_HEIGHT);
            pig.getAttribute(Attribute.MAX_HEALTH).setBaseValue(config.health());
            pig.setHealth(config.health());
        });
        Bukkit.getMobGoals().removeAllGoals(each.body);
        each.previous = each.body.getLocation().toVector();
        each.model = each.home.getWorld().spawn(each.home, ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.setItemStack(modelItem(each.faction, false));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setTeleportDuration(config.presentation().teleportTicks());
            display.customName(game.messages().get("speeder.name", Placeholder.component("faction",
                    game.messages().get("faction." + each.faction.key() + ".name"))));
            display.setCustomNameVisible(true);
        });
        each.seat = each.home.getWorld().spawn(each.home, Interaction.class, interaction -> {
            interaction.setPersistent(false);
            interaction.setInteractionWidth(1.4f);
            interaction.setInteractionHeight(1.1f);
            interaction.setResponsive(true);
        });
        each.unattended = tick;
        each.boost = SpeederMotion.Boost.FULL;
        each.pose = SpeederPose.LEVEL;
        each.sent = null;
        each.damaged = false;
        each.shownHeight = each.home.getY();
        each.shownSpeed = 0;
        each.turning = 0;
        each.rammed.clear();
    }

    private static ItemStack modelItem(Faction faction, boolean damaged) {
        return Items.of(Material.MINECART)
                .model(Key.key("blockcraft", "sternenfront/speeder/" + faction.key() + (damaged ? "_damaged" : ""))).build();
    }

    private void drive(Craft each, Player rider) {
        var input = rider.getCurrentInput();
        int throttle = (input.isForward() ? 1 : 0) - (input.isBackward() ? 1 : 0);
        int steer = (input.isRight() ? 1 : 0) - (input.isLeft() ? 1 : 0);
        each.boost = each.boost.next(input.isSprint() && throttle > 0, config.boost());
        double top = each.boost.active() ? config.boost().speed() : config.speed();
        each.speed = SpeederMotion.speed(each.speed, throttle, top, config.handling());
        double yawChange = SpeederMotion.turn(steer, each.speed, config.steering());
        each.turning = yawChange / config.steering().degrees();
        each.body.setRotation(each.body.getYaw() + (float) yawChange, 0);
        var velocity = each.body.getLocation().getDirection().setY(0).normalize().multiply(each.speed);
        var occupied = each.body.getBoundingBox().union(rider.getBoundingBox());
        if (Math.abs(each.speed) > 0.02 && SpeederMotion.blocked(occupied, velocity, each.body.isOnGround(),
                each.body::wouldCollideUsing)) {
            each.speed = 0;
            velocity.zero();
        }
        velocity.setY(each.body.getVelocity().getY());
        each.body.setVelocity(velocity);
    }

    private void ram(Craft each, Player rider, long tick) {
        var ram = config.ram();
        var at = each.body.getLocation();
        var forward = at.getDirection().setY(0).normalize();
        var path = each.body.getBoundingBox().expand(RAM_REACH).expandDirectional(forward.clone().multiply(each.speed));
        double damage = SpeederMotion.ramDamage(each.speed, config.speed(), ram.damageAtTopSpeed());
        int hits = 0;
        for (var target : game.combatants()) {
            if (!game.hittable(each.faction, target) || !target.getWorld().equals(at.getWorld())
                    || !path.overlaps(target.getBoundingBox())
                    || !SpeederMotion.rams(each.speed, ram.minimumSpeed(), tick,
                    each.rammed.getOrDefault(target.getUniqueId(), SpeederMotion.NEVER), ram.cooldownTicks())) {
                continue;
            }
            each.rammed.put(target.getUniqueId(), tick);
            if (game.strike(rider, target, damage, DamageType.MAGIC)) {
                hits++;
                var contact = target.getLocation().add(0, target.getHeight() / 2, 0);
                SpeederEffects.rammed(contact);
                SoundCue.SABER_CLASH.play(contact, 1f, 0.7f);
                if (!target.isDead()) {
                    target.setVelocity(SpeederMotion.knockAside(forward,
                            target.getLocation().toVector().subtract(at.toVector()), ram.knockback()));
                }
            }
        }
        if (hits > 0) {
            game.endProtection(rider);
            each.body.setNoDamageTicks(0);
            each.body.damage(ram.craftDamage() * hits);
        }
    }

    private void present(Craft each, boolean ridden, long tick) {
        var presentation = config.presentation();
        var at = each.body.getLocation();
        var forward = at.getDirection().setY(0).normalize();
        boolean stepAhead = ridden && each.speed != 0 && each.body.wouldCollideUsing(
                each.body.getBoundingBox().shift(forward.clone().multiply(each.speed * STEP_LOOKAHEAD_TICKS)));
        double height = SpeederMotion.smoothHeight(each.shownHeight,
                at.getY() + (stepAhead ? SpeederMotion.STEP_HEIGHT : 0), presentation.smoothing());
        double climb = (height - each.shownHeight) * (each.speed < 0 ? -1 : 1);
        double surge = Math.clamp((each.speed - each.shownSpeed) / config.handling().acceleration(), -1, 1);
        each.pose = each.pose.next(each.turning, surge, climb, presentation);
        each.shownHeight = height;
        each.shownSpeed = each.speed;
        place(each.model, at.clone().set(at.getX(), height, at.getZ()));
        place(each.seat, at);
        int poseTicks = ridden ? RIDDEN_POSE_TICKS : PARKED_POSE_TICKS;
        if (tick % poseTicks == 0) {
            double phase = 2 * Math.PI * (tick + each.home.getBlockZ() * 7L) / presentation.bobPeriodTicks();
            double lift = presentation.modelHeight() + Math.sin(phase) * presentation.bobHeight();
            var transformation = each.pose.transformation(lift, presentation.pivotHeight());
            if (!transformation.equals(each.sent)) {
                each.model.setInterpolationDuration(poseTicks);
                each.model.setInterpolationDelay(0);
                each.model.setTransformation(transformation);
                each.sent = transformation;
            }
        }
        if (ridden && tick % WAKE_TICKS == 0) {
            double pace = Math.min(1, Math.abs(each.speed) / config.speed());
            SpeederEffects.wake(at, forward, presentation.tail(), each.faction, pace, each.boost.active());
        }
        boolean damaged = each.body.getHealth() < config.health() * config.damagedShare();
        if (damaged != each.damaged) {
            each.damaged = damaged;
            each.model.setItemStack(modelItem(each.faction, damaged));
        }
        if (damaged && tick % DAMAGE_EFFECT_TICKS == 0) {
            SpeederEffects.damaged(at);
        }
    }

    private static void place(Entity entity, Location at) {
        if (!entity.getLocation().equals(at)) {
            entity.teleport(at);
        }
    }

    /** True if the player rides a craft, whose gun then takes their click whether or not it can fire yet. */
    public boolean fire(Player player) {
        var riding = craft.stream().filter(each -> each.body != null && each.body.getPassengers().contains(player)).findFirst();
        if (riding.isEmpty()) {
            return false;
        }
        var each = riding.get();
        // One click raises both an interact and an arm swing.
        boolean samePull = each.lastPull == game.tick();
        each.lastPull = game.tick();
        if (!samePull && game.tick() - each.boarded >= BOARDING_GRACE_TICKS) {
            var cannon = config.cannon();
            var at = each.body.getLocation();
            var nose = at.getDirection().setY(0).normalize();
            var muzzle = at.clone().add(nose.clone().multiply(cannon.muzzleAhead())).add(0, cannon.muzzleHeight(), 0);
            if (game.fireCannon(player, each.faction, cannon.stats(), muzzle, nose)) {
                SpeederEffects.flash(muzzle, each.faction);
            }
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRide(PlayerInteractEntityEvent event) {
        var each = craft.stream().filter(value -> event.getRightClicked().equals(value.seat)
                || event.getRightClicked().equals(value.body)).findFirst();
        if (each.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        var player = event.getPlayer();
        var value = each.get();
        if (!game.onField(player) || game.factionOf(player).filter(value.faction::equals).isEmpty()
                || value.body == null || !value.body.getPassengers().isEmpty() || player.isInsideVehicle()) {
            return;
        }
        player.clearActiveItem();
        value.body.addPassenger(player);
        value.boarded = game.tick();
        player.sendMessage(game.messages().get("speeder.controls"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        var each = craft.stream().filter(value -> event.getEntity().equals(value.seat)).findFirst();
        if (each.isPresent()) {
            event.setCancelled(true);
            if (event.getDamager() instanceof Player player && game.onField(player) && !player.isInsideVehicle() && each.get().body != null
                    && player.getWorld().equals(each.get().body.getWorld())
                    && player.getEyeLocation().distanceSquared(each.get().body.getEyeLocation()) <= 16
                    && player.hasLineOfSight(each.get().body)) {
                player.attack(each.get().body);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        var own = faction(event.getEntity());
        if (own.isPresent() && event instanceof EntityDamageByEntityEvent hit && hit.getDamager() instanceof Player player
                && game.heroOf(player).filter(Hero::bladed).isPresent() && Melee.swing(hit)) {
            event.setCancelled(true);
            return;
        }
        if (own.isPresent() && event.getDamageSource().getCausingEntity() instanceof LivingEntity attacker
                && game.factionOf(attacker).filter(own.get()::equals).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        craft.stream().filter(each -> event.getEntity().equals(each.body)).findFirst().ifPresent(each -> {
            event.getDrops().clear();
            event.setDroppedExp(0);
            var at = each.body.getLocation().add(0, 0.5, 0);
            var rider = each.body.getPassengers().stream().filter(Player.class::isInstance).map(Player.class::cast)
                    .findFirst().orElse(null);
            destroy(each, game.tick());
            explode(each.faction, rider, at);
        });
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player && faction(event.getDismounted()).isPresent()) {
            game.fighter(player).fallSafeUntil(game.tick() + 60);
        }
    }

    /** An empty craft has nobody to credit, so it only makes the noise. */
    private void explode(Faction faction, Player rider, Location at) {
        game.effects().explosion(at, faction, config.explosion().radius());
        SoundCue.GRENADE_EXPLODE.play(at, 2.5f, 0.8f);
        if (rider == null) {
            return;
        }
        var explosion = config.explosion();
        for (var target : game.targets()) {
            if (!target.getWorld().equals(at.getWorld()) || !game.hittable(faction, target)) {
                continue;
            }
            var center = target.getLocation().add(0, target.getHeight() / 2, 0);
            var between = center.toVector().subtract(at.toVector());
            double distance = between.length();
            if (distance > explosion.radius() || distance > 0 && at.getWorld().rayTraceBlocks(at, between.clone().normalize(),
                    distance, FluidCollisionMode.NEVER, true) != null) {
                continue;
            }
            double share = 1 - (1 - EXPLOSION_EDGE_SHARE) * distance / explosion.radius();
            game.blast(rider, target, explosion.damage() * share, at);
            if (distance > 0 && !target.isDead()) {
                target.setVelocity(target.getVelocity().add(between.normalize().multiply(EXPLOSION_PUSH)));
            }
        }
    }

    private void destroy(Craft each, long tick) {
        if (each.body != null) {
            each.body.getWorld().spawnParticle(Particle.SMOKE, each.body.getLocation(), 12, 0.5, 0.3, 0.5, 0.02);
            each.body.eject();
            each.body.remove();
            each.model.remove();
            each.seat.remove();
            each.body = null;
        }
        each.nextSpawn = tick + config.respawnTicks();
        each.speed = 0;
    }

    public void clear() {
        craft.forEach(each -> destroy(each, game.tick()));
        craft.clear();
    }
}
