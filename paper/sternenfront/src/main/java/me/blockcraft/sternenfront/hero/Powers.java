package me.blockcraft.sternenfront.hero;

import me.blockcraft.api.pack.PackModel;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.presentation.ScreenEffects;
import me.blockcraft.sternenfront.appearance.Displays;
import me.blockcraft.sternenfront.game.Blast;
import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.game.LineOfSight;
import me.blockcraft.sternenfront.game.SoundCue;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Display;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * What the heroes' abilities leave in the world while they last: a thrown lightsaber on its way out and back, a grip
 * holding its victim up, a jetpack's thrust, and an orbital strike from the thrown beacon to the last blast. Pushes,
 * grapples and rallies happen at once. Everything ends with its hero, and all of it with the round.
 */
public final class Powers {

    private static final double SABER_REACH = 0.6;
    private static final double CATCH_DISTANCE = 1.5;
    private static final float SPIN_DEGREES = 45;
    private static final long THROW_TIMEOUT = 100;
    private static final int GRIP_PULSE_TICKS = 10;
    private static final double JETPACK_SINK = -0.12;
    private static final double JETPACK_PUSH = 0.04;
    private static final double JETPACK_TOP_SPEED = 0.7;
    private static final int JETPACK_SOUND_TICKS = 8;
    private static final long FALL_SAFE_TICKS = 100;
    private static final double GRAPPLE_LIFT = 0.35;
    private static final double BEACON_LIFT = 0.15;
    private static final double BEACON_THROW = 1.0;
    private static final long BEACON_LANDS_LATEST = 40;
    private static final int BLAST_GAP_TICKS = 3;
    private static final double BLAST_RADIUS = 3.5;
    private static final int BEAM_HEIGHT = 30;
    private static final Color BEAM_COLOUR = Color.fromRGB(0xFF2020);

    private record Flight(Player thrower, Faction faction, Hero hero, ItemDisplay display, Location position,
                          Vector heading, double out, boolean returning, Set<UUID> struck, long thrownAt) {

        Flight move(Location to, Vector heading, double out, boolean returning) {
            return new Flight(thrower, faction, hero, display, to, heading, out, returning, struck, thrownAt);
        }
    }

    private record Grip(Player gripper, LivingEntity victim, long endsAt, double perPulse) { }

    private static final class Strike {
        private final Player thrower;
        private final Faction faction;
        private final Item beacon;
        private final long thrownAt;
        private Location marked;
        private long markedAt;
        private List<Location> impacts = List.of();
        private TextDisplay warning;

        private Strike(Player thrower, Faction faction, Item beacon, long thrownAt) {
            this.thrower = thrower;
            this.faction = faction;
            this.beacon = beacon;
            this.thrownAt = thrownAt;
        }
    }

    private final SternenfrontGame game;
    private final HeroConfig config;
    private final Sabers sabers;
    private final List<Flight> flights = new ArrayList<>();
    private final List<Grip> grips = new ArrayList<>();
    private final Map<UUID, Long> jetpacks = new HashMap<>();
    private final List<Strike> strikes = new ArrayList<>();

    public Powers(SternenfrontGame game, HeroConfig config, Sabers sabers) {
        this.game = game;
        this.config = config;
        this.sabers = sabers;
    }

    /** Whether the ability took effect; one that found nothing to act on, such as a grip on nobody, costs no cooldown. */
    public boolean use(Player player, Faction faction, Hero hero, HeroAbility ability, long tick) {
        return switch (ability) {
            case FORCE_PUSH -> push(player, faction);
            case SABER_THROW -> throwSaber(player, faction, hero, tick);
            case FORCE_GRIP -> grip(player, faction, tick);
            case JETPACK -> jetpack(player, tick);
            case GRAPPLE -> grapple(player, tick);
            case ORBITAL_STRIKE -> orbital(player, faction, tick);
            case RALLY -> rally(player, faction);
        };
    }

    private boolean push(Player player, Faction faction) {
        var push = config.push();
        var eye = player.getEyeLocation();
        var facing = eye.getDirection();
        double halfAngle = Math.toRadians(push.angle() / 2);
        game.effects().push(eye, facing, push.range(), push.angle());
        SoundCue.FORCE_PUSH.play(eye, 1.5f);
        if (game.plugin().getConfig().getBoolean("presentation.effects", true)) {
            int effectTicks = Math.clamp(game.plugin().getConfig().getInt("presentation.gameplay.force-push-ticks", 12), 0, 200);
            if (effectTicks > 0) BlockcraftApi.get().visuals().accent(player, ScreenEffects.Atmosphere.FOCUS, effectTicks);
        }
        for (var target : game.targets()) {
            if (!target.getWorld().equals(player.getWorld())) {
                continue;
            }
            var toward = target.getLocation().add(0, target.getHeight() / 2, 0).toVector().subtract(eye.toVector());
            double distance = toward.length();
            if (distance == 0 || distance > push.range() || facing.angle(toward) > halfAngle
                    || !game.hittable(faction, target) || !LineOfSight.clear(eye, toward)) {
                continue;
            }
            game.strike(player, target, push.damage(), DamageType.MAGIC);
            target.setVelocity(toward.normalize().multiply(push.strength()).setY(push.up()));
        }
        return true;
    }

    private boolean throwSaber(Player player, Faction faction, Hero hero, long tick) {
        if (!Sabers.isLit(player.getInventory().getItem(0)) || flights.stream().anyMatch(flight -> flight.thrower().equals(player))) {
            return false;
        }
        var eye = player.getEyeLocation();
        var heading = eye.getDirection();
        var start = eye.clone();
        var blade = sabers.item(player, hero, true);
        var display = Displays.item(start, blade, spawned -> {
            spawned.setBrightness(Displays.LIT);
            spawned.setTeleportDuration(1);
        });
        sabers.unlit(player, hero);
        SoundCue.SABER_THROW.play(eye, 1.2f);
        flights.add(new Flight(player, faction, hero, display, start, heading, 0, false, new HashSet<>(), tick));
        return true;
    }

    private boolean grip(Player player, Faction faction, long tick) {
        var grip = config.grip();
        var eye = player.getEyeLocation();
        var facing = eye.getDirection();
        var wall = player.getWorld().rayTraceBlocks(eye, facing, grip.range(), FluidCollisionMode.NEVER, true);
        double reach = wall == null ? grip.range() : wall.getHitPosition().distance(eye.toVector());
        var hit = player.getWorld().rayTraceEntities(eye, facing, reach, 0.6,
                entity -> entity instanceof LivingEntity target && game.hittable(faction, target));
        if (hit == null || !(hit.getHitEntity() instanceof LivingEntity victim)) {
            return false;
        }
        int ticks = grip.seconds() * 20;
        victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, ticks, 0, false, false, true));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 3, false, false, true));
        SoundCue.FORCE_GRIP.play(victim.getLocation(), 1.2f);
        grips.removeIf(each -> each.victim().equals(victim));
        grips.add(new Grip(player, victim, tick + ticks, grip.damage() / Math.max(1, ticks / GRIP_PULSE_TICKS)));
        return true;
    }

    private boolean jetpack(Player player, long tick) {
        long until = tick + config.jetpack().seconds() * 20L;
        jetpacks.put(player.getUniqueId(), until);
        game.fighter(player).fallSafeUntil(until + FALL_SAFE_TICKS);
        SoundCue.JETPACK.play(player.getLocation(), 1f);
        return true;
    }

    private boolean grapple(Player player, long tick) {
        var grapple = config.grapple();
        var eye = player.getEyeLocation();
        var hit = player.getWorld().rayTraceBlocks(eye, eye.getDirection(), grapple.range(), FluidCollisionMode.NEVER, true);
        if (hit == null) {
            return false;
        }
        var anchor = hit.getHitPosition();
        var line = anchor.clone().subtract(eye.toVector());
        double distance = line.length();
        game.effects().grapple(player, anchor.toLocation(player.getWorld()));
        var pull = anchor.clone().subtract(player.getLocation().toVector()).normalize()
                .multiply(Math.min(grapple.speed(), 0.4 + distance * 0.12));
        player.setVelocity(pull.setY(Math.max(pull.getY(), 0) + GRAPPLE_LIFT));
        game.fighter(player).fallSafeUntil(tick + FALL_SAFE_TICKS);
        SoundCue.GRAPPLE.play(eye, 1f);
        return true;
    }

    /** Throws the orbital beacon; where it lands, a beam marks the spot, and the barrage follows. */
    public boolean orbital(Player player, Faction faction, long tick) {
        var eye = player.getEyeLocation();
        var stack = PackModel.STERNENFRONT_ORBITAL_BEACON.item().build();
        stack.editMeta(meta -> meta.setMaxStackSize(1));
        var beacon = player.getWorld().dropItem(eye, stack, dropped -> {
            dropped.setPersistent(false);
            dropped.setCanPlayerPickup(false);
            dropped.setCanMobPickup(false);
            dropped.setWillAge(false);
            dropped.setVelocity(eye.getDirection().multiply(BEACON_THROW).add(new Vector(0, BEACON_LIFT, 0)));
        });
        strikes.add(new Strike(player, faction, beacon, tick));
        return true;
    }

    private boolean rally(Player player, Faction faction) {
        var rally = config.rally();
        game.effects().rally(player.getLocation(), faction, rally.radius());
        int ticks = rally.seconds() * 20;
        for (var ally : game.combatants()) {
            if (ally.getWorld().equals(player.getWorld())
                    && ally.getLocation().distanceSquared(player.getLocation()) <= rally.radius() * rally.radius()
                    && game.onField(ally) && game.factionOf(ally).filter(faction::equals).isPresent()) {
                ally.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 0, false, false, true));
                ally.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, ticks, 0, false, false, true));
                ally.getWorld().spawnParticle(Particle.DUST, ally.getLocation().add(0, 1, 0), 8, .3, .5, .3, 0,
                        new Particle.DustOptions(faction.bolt(), 1.2f), true);
            }
        }
        SoundCue.RALLY.play(player.getLocation(), 2f);
        return true;
    }

    public void tick(long tick) {
        var airborne = List.copyOf(flights);
        flights.clear();
        for (var flight : airborne) {
            var next = fly(flight, tick);
            if (next != null) {
                flights.add(next);
            }
        }
        for (var grip : List.copyOf(grips)) {
            if (grips.contains(grip) && hold(grip, tick)) {
                grips.remove(grip);
            }
        }
        jetpacks.entrySet().removeIf(entry -> thrust(entry.getKey(), entry.getValue(), tick));
        strikes.removeIf(strike -> rain(strike, tick));
    }

    /** One tick of a thrown saber; null once it is back in its hero's hand or gone. */
    private Flight fly(Flight flight, long tick) {
        var thrower = flight.thrower();
        boolean holder = game.onField(thrower) && game.heroOf(thrower).filter(flight.hero()::equals).isPresent();
        if (!holder || tick - flight.thrownAt() > THROW_TIMEOUT
                || !thrower.getWorld().equals(flight.position().getWorld())) {
            flight.display().remove();
            if (holder) {
                sabers.relight(thrower, flight.hero());
            }
            return null;
        }
        var speed = config.saberThrow().speed();
        var position = flight.position();
        var heading = flight.heading();
        boolean returning = flight.returning();
        if (returning) {
            var home = thrower.getEyeLocation();
            if (home.distanceSquared(position) <= CATCH_DISTANCE * CATCH_DISTANCE) {
                flight.display().remove();
                sabers.relight(thrower, flight.hero());
                return null;
            }
            heading = home.toVector().subtract(position.toVector()).normalize();
            speed = Math.min(speed, home.distance(position));
        } else {
            speed = Math.min(speed, config.saberThrow().range() - flight.out());
            if (speed <= 0) {
                return flight.move(position, heading, flight.out(), true);
            }
        }
        var wall = position.getWorld().rayTraceBlocks(position, heading, speed, FluidCollisionMode.NEVER, true);
        double travelled = wall == null ? speed : Math.max(0, wall.getHitPosition().distance(position.toVector()) - 0.01);
        var next = position.clone().add(heading.clone().multiply(travelled));
        next.setYaw(flight.display().getLocation().getYaw() + SPIN_DEGREES);
        flight.display().teleport(next);
        game.effects().saberTrail(position, next, sabers.colour(thrower, flight.hero()));
        for (var target : game.targets()) {
            if (!target.getWorld().equals(position.getWorld()) || !game.hittable(flight.faction(), target)
                    || flight.struck().contains(target.getUniqueId())
                    || !SaberSweep.hits(position.toVector(), next.toVector(), target.getBoundingBox(), SABER_REACH)) {
                continue;
            }
            var toward = target.getLocation().add(0, target.getHeight() / 2, 0).toVector().subtract(position.toVector());
            if (LineOfSight.clear(position, toward)) {
                flight.struck().add(target.getUniqueId());
                if (game.strike(thrower, target, config.saberThrow().damage(), DamageType.TRIDENT)) {
                    SoundCue.SABER_HIT.play(next, 0.8f);
                }
            }
        }
        if (wall != null && returning) {
            flight.display().remove();
            sabers.relight(thrower, flight.hero());
            return null;
        }
        if (wall != null) {
            game.effects().saberStrike(thrower, wall, heading, sabers.colour(thrower, flight.hero()));
        }
        return flight.move(next, heading, flight.out() + travelled, returning || wall != null);
    }

    /** True once the grip let go. */
    private boolean hold(Grip grip, long tick) {
        var victim = grip.victim();
        boolean holding = tick < grip.endsAt() && game.onField(grip.gripper()) && victim.isValid()
                && game.factionOf(grip.gripper()).filter(faction -> game.hittable(faction, victim)).isPresent();
        if (!holding) {
            victim.removePotionEffect(PotionEffectType.LEVITATION);
            victim.removePotionEffect(PotionEffectType.SLOWNESS);
            return true;
        }
        game.effects().grip(grip.gripper(), victim, tick);
        if ((grip.endsAt() - tick) % GRIP_PULSE_TICKS == 0) {
            game.strike(grip.gripper(), victim, grip.perPulse(), DamageType.MAGIC);
        }
        return false;
    }

    /** One tick of thrust: up while jump is held, a slow sink otherwise; true once the fuel ran out. */
    private boolean thrust(UUID id, long until, long tick) {
        var player = Bukkit.getPlayer(id);
        if (player == null || tick >= until || !game.onField(player)) {
            return true;
        }
        var input = player.getCurrentInput();
        var velocity = player.getVelocity();
        velocity.setY(input.isJump() ? config.jetpack().thrust() : Math.max(velocity.getY(), JETPACK_SINK));
        if (input.isForward()) {
            var facing = player.getLocation().getDirection().setY(0);
            if (facing.lengthSquared() > 0) {
                velocity.add(facing.normalize().multiply(JETPACK_PUSH));
            }
        }
        var horizontal = new Vector(velocity.getX(), 0, velocity.getZ());
        if (horizontal.length() > JETPACK_TOP_SPEED) {
            horizontal.normalize().multiply(JETPACK_TOP_SPEED);
            velocity.setX(horizontal.getX()).setZ(horizontal.getZ());
        }
        player.setVelocity(velocity);
        var feet = player.getLocation();
        game.effects().exhaust(player);
        if (tick % JETPACK_SOUND_TICKS == 0) {
            SoundCue.JETPACK.play(feet, 0.6f);
        }
        return false;
    }

    /** True once the last blast fell, or the beacon vanished before it marked a spot. */
    private boolean rain(Strike strike, long tick) {
        var orbital = config.orbital();
        if (strike.marked == null) {
            var beacon = strike.beacon;
            if (!beacon.isValid()) {
                return true;
            }
            if (beacon.isOnGround() || tick - strike.thrownAt >= BEACON_LANDS_LATEST) {
                strike.marked = beacon.getLocation();
                strike.markedAt = tick;
                strike.impacts = BlastPattern.plan(orbital.blasts(), orbital.radius(), ThreadLocalRandom.current()).stream()
                        .map(impact -> strike.marked.clone().add(impact.x(), 0.5, impact.z())).toList();
                strike.warning = strike.marked.getWorld().spawn(strike.marked.clone().add(0, 3, 0), TextDisplay.class, label -> {
                    label.setPersistent(false);
                    label.setBillboard(Display.Billboard.CENTER);
                    label.setSeeThrough(false);
                    label.setBrightness(Displays.LIT);
                    label.setShadowed(true);
                });
                SoundCue.ORBITAL_INCOMING.play(strike.marked, 3f);
            }
            return false;
        }
        var at = strike.marked;
        long since = tick - strike.markedAt;
        if (since < orbital.delayTicks()) {
            strike.warning.text(game.messages().get("combat.orbital-warning",
                    Placeholder.styling("tint", strike.faction.text()),
                    Placeholder.component("faction", game.messages().get("faction." + strike.faction.key() + ".name")),
                    Formatter.number("seconds", (orbital.delayTicks() - since + 19) / 20)));
            if (since % 5 == 0) {
                var dust = new Particle.DustOptions(BEAM_COLOUR, 1.4f);
                for (var impact : strike.impacts) {
                    for (int step = 0; step < 24; step++) {
                        double angle = step * Math.PI * 2 / 24;
                        var point = impact.clone().add(Math.cos(angle) * BLAST_RADIUS, -0.35, Math.sin(angle) * BLAST_RADIUS);
                        at.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dust);
                    }
                }
            }
            if (since % 2 == 0) {
                var dust = new Particle.DustOptions(BEAM_COLOUR, 2f);
                for (int height = 0; height < BEAM_HEIGHT; height += 2) {
                    at.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, height, 0), 1, 0.05, 0.3, 0.05, 0, dust, true);
                }
            }
            return false;
        }
        long blast = since - orbital.delayTicks();
        strike.warning.remove();
        if (blast % BLAST_GAP_TICKS == 0 && blast / BLAST_GAP_TICKS < orbital.blasts()) {
            blast(strike, strike.impacts.get((int) (blast / BLAST_GAP_TICKS)));
        }
        if (blast / BLAST_GAP_TICKS >= orbital.blasts() - 1) {
            strike.beacon.remove();
            return true;
        }
        return false;
    }

    private void blast(Strike strike, Location at) {
        game.effects().explosion(at, strike.faction, BLAST_RADIUS);
        at.getWorld().spawnParticle(Particle.FLAME, at, 20, 1, 0.5, 1, 0.05, null, true);
        SoundCue.ORBITAL_IMPACT.play(at, 2.5f);
        game.blastAround(at, new Blast(strike.thrower, strike.faction, BLAST_RADIUS, config.orbital().damage(), false), (target, away) -> { });
    }

    /** The hero fell or left: their saber stops in the air, their jetpack and grip end. Strikes already thrown land. */
    public void forget(Player player) {
        jetpacks.remove(player.getUniqueId());
        grips.removeIf(grip -> {
            boolean theirs = grip.gripper().equals(player) || grip.victim().equals(player);
            if (theirs) {
                grip.victim().removePotionEffect(PotionEffectType.LEVITATION);
            }
            return theirs;
        });
        flights.removeIf(flight -> {
            boolean theirs = flight.thrower().equals(player);
            if (theirs) {
                flight.display().remove();
            }
            return theirs;
        });
    }

    public void clear() {
        flights.forEach(flight -> flight.display().remove());
        flights.clear();
        grips.forEach(grip -> grip.victim().removePotionEffect(PotionEffectType.LEVITATION));
        grips.clear();
        jetpacks.clear();
        strikes.forEach(strike -> {
            strike.beacon.remove();
            if (strike.warning != null) {
                strike.warning.remove();
            }
        });
        strikes.clear();
    }
}
