package me.blockcraft.sternenfront.combat;

import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.sternenfront.game.Blaster;
import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.game.SoundCue;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * What a fight looks like: muzzle flashes, impacts, shockwaves, explosions, tethers, exhaust, vent steam and damaged
 * fighters. A particle a player must read from afar is forced, because the client otherwise drops it beyond 32 blocks
 * and on reduced particle settings. Wherever a model carries the effect, it is one display in {@link Flares}.
 */
public final class BattleEffects {

    private static final String RING = "sternenfront/fx/ring";
    private static final String SCORCH = "sternenfront/fx/scorch";
    private static final String SABER_SLASH = "sternenfront/fx/saber_slash";
    private static final String SABER_CRESCENT = "sternenfront/fx/saber_crescent";
    private static final String SABER_SCAR = "sternenfront/fx/saber_slash_cold";
    private static final String SMOKE_PUFF = "sternenfront/fx/smoke_puff";
    private static final Color PUSH_COLOUR = Color.fromRGB(0xB8E5FF);
    private static final Color TETHER_COLOUR = Color.fromRGB(0xD0E2EE);
    private static final Color GRIP_COLOUR = Color.fromRGB(0xC050FF);
    private static final Color LIGHTNING_COLOUR = Color.fromRGB(0xA8B4FF);
    private static final Color LIGHTNING_CORE = Color.fromRGB(0xEAF4FF);
    private static final Vector UP = new Vector(0, 1, 0);
    private static final Vector STILL = new Vector();
    private static final int VENT_INTERVAL = 3;
    private static final double VENTING_HEAT = 0.85;
    private static final double HOT_BARREL = 0.65;
    private static final double OFF_THE_WALL = 0.12;
    private static final double SCORCH_LIFT = 0.03;
    private static final double SLASH_LIFT = 0.04;
    private static final double BLOCK_BEHIND = 0.01;
    private static final float SIZZLE_VOLUME = 0.35f;
    private static final int GROUND_PROBE = 4;
    private static final int FLASH_TICKS = 2;
    private static final int TRACER_TICKS = 3;
    private static final int PUSH_TICKS = 8;
    private static final int RALLY_TICKS = 12;
    private static final int SHIELD_RIPPLE_TICKS = 6;
    private static final float SHIELD_RIPPLE_DIAMETER = 1.8f;
    private static final int BLAST_FLASH_TICKS = 4;
    private static final int SHOCKWAVE_TICKS = 8;
    private static final int SMOKE_FADE_TICKS = 15;

    private record Shot(LivingEntity actor, Blaster blaster, long tick) { }
    private record Tether(Player player, Location anchor, long until) { }

    private final SternenfrontGame game;
    private final PresentationConfig config;
    private final Flares flares;
    private final Flares scorches;
    private final Ambience ambience;
    private final Map<UUID, Shot> shots = new HashMap<>();
    private final List<Tether> tethers = new ArrayList<>();
    private final Map<UUID, Long> nextSaberMark = new HashMap<>();
    private final boolean enabled;
    private long wallHits;

    public BattleEffects(SternenfrontGame game) {
        this.game = game;
        config = PresentationConfig.from(game.plugin().getConfig());
        enabled = config.effects();
        flares = new Flares(config.liveEffects());
        scorches = new Flares(config.scorch().marks());
        ambience = new Ambience(game.map(), config.ambient());
    }

    public PresentationConfig config() {
        return config;
    }

    public Flares flares() {
        return flares;
    }

    public void fire(LivingEntity actor, Faction faction, Blaster blaster, Vector direction, double heat) {
        if (!enabled) {
            return;
        }
        shots.put(actor.getUniqueId(), new Shot(actor, blaster, game.tick()));
        var held = actor.getEquipment().getItemInMainHand();
        if (HotbarItem.of(held, Blaster.class).filter(blaster::equals).isPresent()) {
            blaster.firingModel().mark(held);
            actor.getEquipment().setItemInMainHand(held);
        }
        var muzzle = muzzle(actor, direction);
        if (muzzle.getBlock().isPassable()) {
            var flash = config.muzzleFlash();
            boolean heavy = blaster.precision() || blaster.profile() == Blaster.REPEATER;
            flares.flash(flash(faction), muzzle, heavy ? flash.heavyScale() : flash.scale(), flash.ticks());
            muzzle.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, muzzle, 2, 0.04, 0.04, 0.04, 0.03);
            if (heat > HOT_BARREL) {
                muzzle.getWorld().spawnParticle(Particle.WHITE_SMOKE, muzzle, 2, 0.03, 0.03, 0.03, 0.01);
            }
        }
    }

    /** Where the barrel ends for someone aiming along {@code direction}: ahead of the eyes, right and a little low, short of a wall. */
    public Location muzzle(LivingEntity actor, Vector direction) {
        var eye = actor.getEyeLocation();
        var side = direction.clone().crossProduct(new Vector(0, 1, 0));
        if (side.lengthSquared() > .001) {
            side.normalize().multiply(.2);
        }
        var offset = direction.clone().multiply(.8).add(side).add(new Vector(0, -.2, 0));
        double distance = offset.length();
        var wall = eye.getWorld().rayTraceBlocks(eye, offset.clone().normalize(), distance, FluidCollisionMode.NEVER, true);
        return wall == null ? eye.clone().add(offset)
                : wall.getHitPosition().subtract(offset.normalize().multiply(.04)).toLocation(eye.getWorld());
    }

    public double recoil(LivingEntity actor) {
        var shot = shots.get(actor.getUniqueId());
        return shot == null ? 0 : Math.clamp(1 - (game.tick() - shot.tick()) / 3.0, 0, 1);
    }

    public void push(Location origin, Vector facing, double range, double angle) {
        if (!enabled) {
            return;
        }
        var normal = facing.clone().normalize();
        var wall = origin.getWorld().rayTraceBlocks(origin, normal, range, FluidCollisionMode.NEVER, true);
        double reach = wall == null ? range : wall.getHitPosition().distance(origin.toVector());
        float diameter = (float) (2 * reach * Math.tan(Math.toRadians(angle / 2)));
        flares.ring(RING, origin.clone().add(normal.clone().multiply(0.6)), normal, normal.clone().multiply(reach),
                diameter, PUSH_TICKS, PUSH_COLOUR);
        origin.getWorld().spawnParticle(Particle.SMALL_GUST, origin.clone().add(normal.clone().multiply(2)), 3, 0.4, 0.4, 0.4, 0, null, true);
    }

    public void rally(Location origin, Faction faction, double radius) {
        if (enabled) {
            pulse(origin, faction, radius, RALLY_TICKS);
        }
    }

    /** A ring on the ground widening to {@code radius}, as a rally call or a med station sends out. */
    public void pulse(Location origin, Faction faction, double radius, int ticks) {
        if (enabled) {
            flares.ring(RING, onGround(origin), UP, STILL, (float) (radius * 2), ticks, faction.bolt());
        }
    }

    /** A ripple on a shield wall where a bolt struck it. */
    public void impact(Location at, Vector normal, Color color) {
        if (enabled && flares.hasRoom()) {
            flares.ring(RING, at.clone().add(normal.clone().multiply(0.06)), normal, STILL, SHIELD_RIPPLE_DIAMETER,
                    SHIELD_RIPPLE_TICKS, color);
        }
    }

    public void grapple(Player player, Location anchor) {
        if (enabled) {
            tethers.removeIf(tether -> tether.player().equals(player));
            tethers.add(new Tether(player, anchor.clone(), game.tick() + 20));
        }
    }

    /** A bolt met a fighter: a flash and sparks a player can read from across the map. */
    public void boltHit(Location at, Faction faction) {
        burst(at, faction);
    }

    /**
     * A bolt met a wall. Everything is lifted off the face first: a particle inside the block is hidden by it and
     * lit by its light level of zero. A shield wall ripples elsewhere and takes no mark.
     */
    public void boltOnWall(Location at, Vector normal, Block block, boolean shield, Faction faction) {
        var clear = at.clone().add(normal.clone().multiply(OFF_THE_WALL));
        burst(clear, faction);
        if (shield || !enabled) {
            return;
        }
        at.getWorld().spawnParticle(Particle.BLOCK, clear, 5, 0.05, 0.05, 0.05, 0.1, block.getBlockData());
        var scorch = config.scorch();
        if (block.getType().isOccluding() && wallHits++ % scorch.every() == 0) {
            scorches.mark(SCORCH, at.clone().add(normal.clone().multiply(SCORCH_LIFT)), normal, scorch.scale(),
                    scorch.ticks(), scorch.fadeTicks(), block);
        }
    }

    /** A lit blade reached a solid block: at most one mark per {@code striker} and {@code presentation.saber-mark.cooldown-ticks}. */
    public void saberStrike(Player striker, RayTraceResult wall, Vector along, Color blade) {
        var block = wall.getHitBlock();
        var face = wall.getHitBlockFace();
        if (!enabled || block == null || face == null || !block.isSolid()
                || game.tick() < nextSaberMark.getOrDefault(striker.getUniqueId(), 0L)) {
            return;
        }
        nextSaberMark.put(striker.getUniqueId(), game.tick() + config.saberMark().cooldownTicks());
        saberMark(wall.getHitPosition().toLocation(block.getWorld()), face.getDirection(), along, blade);
    }

    /**
     * Molten sparks, a sizzle and a cut in the block's face, running along {@code along}. The glow lies over a scar that
     * stays: it thins away first, and the scar lasts and fades like a scorch mark, under the same cap.
     */
    public void saberMark(Location at, Vector normal, Vector along, Color blade) {
        if (!enabled) {
            return;
        }
        var world = at.getWorld();
        var clear = at.clone().add(normal.clone().multiply(OFF_THE_WALL));
        world.spawnParticle(Particle.LAVA, clear, 3, 0.05, 0.05, 0.05, 0, null, true);
        world.spawnParticle(Particle.ELECTRIC_SPARK, clear, 10, 0.12, 0.12, 0.12, 0.14, null, true);
        SoundCue.SABER_HIT.play(clear, SIZZLE_VOLUME);
        var block = at.clone().subtract(normal.clone().multiply(BLOCK_BEHIND)).getBlock();
        if (block.getType().isOccluding()) {
            var mark = config.saberMark();
            double tilt = Math.toRadians(mark.tiltDegrees()) * ThreadLocalRandom.current().nextDouble(-1, 1);
            var turn = Flares.lying(normal, along, tilt);
            scorches.mark(SABER_SCAR, at.clone().add(normal.clone().multiply(SCORCH_LIFT)), turn, mark.scale(),
                    mark.ticks(), mark.fadeTicks(), block);
            scorches.cooling(SABER_SLASH, blade, at.clone().add(normal.clone().multiply(SLASH_LIFT)), turn, mark.scale(),
                    mark.glowTicks(), block);
        }
    }

    /** A bolt that ran out of range goes out in the air. */
    public void boltFizzle(Location at) {
        at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at, 3, 0.1, 0.1, 0.1, 0.03);
    }

    private void burst(Location at, Faction faction) {
        var world = at.getWorld();
        world.spawnParticle(Particle.ELECTRIC_SPARK, at, 10, 0.1, 0.1, 0.1, 0.12, null, true);
        world.spawnParticle(Particle.DUST, at, 6, 0.12, 0.12, 0.12, 0, new Particle.DustOptions(faction.bolt(), 1.4f), true);
        if (enabled) {
            flares.flash(flash(faction), at, config.bolt().impactFlash(), FLASH_TICKS);
        }
    }

    /** The long rifle's hit arrives at once, so what shows it is a line from the barrel that thins away. */
    public void tracer(LivingEntity shooter, Vector direction, ItemStack bolt, float modelLength, Location end) {
        if (enabled) {
            var from = muzzle(shooter, direction);
            flares.beam(bolt, from, end, config.bolt().width(), (float) from.distance(end) / modelLength, TRACER_TICKS);
        }
    }

    /** Flash, shockwave, thrown ground and smoke that stays a moment, for anything that blows up. */
    public void explosion(Location at, Faction faction, double radius) {
        var world = at.getWorld();
        world.spawnParticle(Particle.EXPLOSION, at, 3, radius * 0.2, radius * 0.1, radius * 0.2, 0, null, true);
        if (!enabled) {
            return;
        }
        float size = (float) radius;
        flares.burst(flash(faction), at.clone().add(0, 0.4, 0), size * 0.5f, size * 1.5f, BLAST_FLASH_TICKS);
        flares.ring(RING, onGround(at), UP, STILL, size * 2.4f, SHOCKWAVE_TICKS, faction.bolt());
        world.spawnParticle(Particle.FLASH, at, 1, 0, 0, 0, 0, faction.bolt(), true);
        world.spawnParticle(Particle.ELECTRIC_SPARK, at, 30, radius * 0.25, radius * 0.15, radius * 0.25, 0.25, null, true);
        world.spawnParticle(Particle.DUST, at, 20, radius * 0.3, radius * 0.15, radius * 0.3, 0,
                new Particle.DustOptions(faction.bolt(), 2f), true);
        var ground = groundBelow(at);
        if (ground != null && config.explosion().debris() > 0) {
            world.spawnParticle(Particle.BLOCK, ground.getLocation().add(0.5, 1.2, 0.5), config.explosion().debris(),
                    radius * 0.3, 0.2, radius * 0.3, 0.3, ground.getBlockData(), true);
        }
        smoke(at, radius);
    }

    private void smoke(Location at, double radius) {
        var random = ThreadLocalRandom.current();
        var explosion = config.explosion();
        float size = (float) radius;
        for (int puff = 0; puff < explosion.smokePuffs(); puff++) {
            var spot = at.clone().add(random.nextDouble(-0.35, 0.35) * radius, 0.5, random.nextDouble(-0.35, 0.35) * radius);
            flares.puff(SMOKE_PUFF, spot, size * 0.35f, size * 0.8f, random.nextDouble(2, 3.5), explosion.smokeTicks(), SMOKE_FADE_TICKS);
        }
        for (int wisp = 0; wisp < 6; wisp++) {
            var spot = at.clone().add(random.nextDouble(-0.4, 0.4) * radius, 0.4, random.nextDouble(-0.4, 0.4) * radius);
            at.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, spot, 0, random.nextDouble(-0.015, 0.015),
                    random.nextDouble(0.04, 0.08), random.nextDouble(-0.015, 0.015), 1, null, true);
        }
    }

    /** One tick of a jump pack or a jetpack: two jets from the back, pointing down, with the smoke they leave. */
    public void exhaust(Player player) {
        var base = player.getLocation();
        var facing = base.getDirection().setY(0);
        if (facing.lengthSquared() > 0) {
            facing.normalize();
        }
        var side = facing.clone().crossProduct(UP).multiply(0.18);
        var back = base.clone().add(0, 0.9, 0).subtract(facing.multiply(0.3));
        for (var nozzle : List.of(back.clone().add(side), back.clone().subtract(side))) {
            nozzle.getWorld().spawnParticle(Particle.FLAME, nozzle, 0, 0, -0.35, 0, 1, null, true);
            nozzle.getWorld().spawnParticle(Particle.SMOKE, nozzle.subtract(0, 0.5, 0), 0, 0, -0.2, 0, 1, null, true);
        }
    }

    void saberSweep(Location origin, List<SaberCrescent.Sample> samples, Color colour) {
        var glow = new Particle.DustTransition(Color.WHITE, colour, 0.8f);
        var trail = new Particle.DustOptions(colour, 0.65f);
        for (var sample : samples) {
            for (double radius = Math.max(0.4, sample.reach() - 0.65); radius <= sample.reach(); radius += 0.2) {
                var at = origin.clone().add(sample.direction().clone().multiply(radius));
                origin.getWorld().spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, trail, true);
            }
            if (sample.reach() > 0.4) {
                var tip = origin.clone().add(sample.direction().clone().multiply(sample.reach()));
                origin.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, tip, 1, 0, 0, 0, 0, glow, true);
            }
        }
        double yaw = Math.toRadians(origin.getYaw());
        var normal = new Vector(Math.cos(yaw), 0, Math.sin(yaw)).crossProduct(origin.getDirection()).normalize();
        for (var segment : SaberCrescent.segments(samples)) {
            flares.crescent(SABER_CRESCENT, origin.clone().add(segment.offset()), segment.turn(normal), segment.scale(), colour,
                    config.saberTrailTicks());
        }
    }

    /** The trail of a thrown saber along the piece of flight it just covered, in the blade's colour. */
    public void saberTrail(Location from, Location to, Color colour) {
        var line = to.toVector().subtract(from.toVector());
        double length = line.length();
        if (length == 0) {
            return;
        }
        var direction = line.multiply(1 / length);
        var dust = new Particle.DustOptions(colour, 1.6f);
        for (double along = 0; along < length; along += 0.4) {
            from.getWorld().spawnParticle(Particle.DUST, from.clone().add(direction.clone().multiply(along)), 1, 0, 0, 0, 0, dust, true);
        }
        to.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, to, 2, 0.1, 0.1, 0.1, 0.03, null, true);
    }

    public void rocketTrail(Location from, Location to) {
        var line = to.toVector().subtract(from.toVector());
        double distance = line.length();
        if (distance == 0) {
            return;
        }
        var direction = line.multiply(1 / distance);
        for (double along = 0; along < distance; along += 0.35) {
            var at = from.clone().add(direction.clone().multiply(along));
            at.getWorld().spawnParticle(Particle.FLAME, at, 1, .025, .025, .025, 0, null, true);
            at.getWorld().spawnParticle(Particle.SMOKE, at, 1, .04, .04, .04, .01);
        }
    }

    public void lightning(Location eye, Location to) {
        var from = eye.clone().subtract(0, 0.2, 0);
        var line = to.toVector().subtract(from.toVector());
        int steps = Math.max(1, (int) Math.ceil(line.length() / 0.3));
        var random = ThreadLocalRandom.current();
        for (int strand = 0; strand < (enabled ? 3 : 1); strand++) {
            var dust = new Particle.DustOptions(strand == 0 ? LIGHTNING_CORE : LIGHTNING_COLOUR, strand == 0 ? 1 : 1.4f);
            for (int step = 0; step <= steps; step++) {
                double share = (double) step / steps;
                double jitter = Math.sin(Math.PI * share) * 0.18;
                var at = from.clone().add(line.clone().multiply(share))
                        .add(random.nextDouble(-1, 1) * jitter, random.nextDouble(-1, 1) * jitter, random.nextDouble(-1, 1) * jitter);
                from.getWorld().spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, dust, true);
            }
        }
        to.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, to, 5, .12, .2, .12, .02, null, true);
    }

    /** One tick of a force grip: a turning ring round the victim's chest and, every other tick, the line from the gripper. */
    public void grip(Player gripper, LivingEntity victim, long tick) {
        var chest = victim.getEyeLocation().subtract(0, 0.4, 0);
        var dust = new Particle.DustOptions(GRIP_COLOUR, 1.3f);
        for (int point = 0; point < 6; point++) {
            double angle = tick * 0.35 + point * Math.PI / 3;
            var at = chest.clone().add(Math.cos(angle) * 0.6, Math.sin(tick * 0.2) * 0.3, Math.sin(angle) * 0.6);
            chest.getWorld().spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, dust, true);
        }
        if (tick % 2 != 0 || !gripper.getWorld().equals(victim.getWorld())) {
            return;
        }
        var from = gripper.getEyeLocation().subtract(0, 0.3, 0);
        var line = chest.toVector().subtract(from.toVector());
        double length = line.length();
        var direction = line.normalize();
        for (double along = 1; along < length; along += 1) {
            chest.getWorld().spawnParticle(Particle.DUST, from.clone().add(direction.clone().multiply(along)), 1, 0, 0, 0, 0, dust, true);
        }
    }

    public void tick(long tick) {
        flares.tick();
        scorches.tick();
        shots.values().removeIf(shot -> {
            if (!shot.actor().isValid() || tick - shot.tick() >= 3) {
                restore(shot);
                return true;
            }
            return false;
        });
        tethers.removeIf(tether -> !game.onField(tether.player()) || tick >= tether.until()
                || !tether.player().getWorld().equals(tether.anchor().getWorld())
                || tether.player().getEyeLocation().distanceSquared(tether.anchor()) < 2);
        if (!enabled) {
            return;
        }
        ambience.tick(tick);
        if (tick % VENT_INTERVAL == 0) {
            game.combatants().stream().filter(actor -> venting(actor, tick)).forEach(this::vent);
        }
        if (tick % config.damaged().intervalTicks() == 0) {
            game.combatants().stream().filter(this::badlyHurt).forEach(BattleEffects::smoulder);
        }
        if (tick % 2 != 0) {
            return;
        }
        for (var tether : tethers) {
            var from = tether.player().getEyeLocation().subtract(0, .2, 0);
            var line = tether.anchor().toVector().subtract(from.toVector());
            double length = line.length();
            var direction = line.normalize();
            var hit = from.getWorld().rayTraceBlocks(from, direction, length, FluidCollisionMode.NEVER, true);
            double reach = hit == null ? length : Math.min(length, hit.getHitPosition().distance(from.toVector()));
            var dust = new Particle.DustOptions(TETHER_COLOUR, 0.9f);
            for (double along = 0; along < reach; along += .6) {
                from.getWorld().spawnParticle(Particle.DUST, from.clone().add(direction.clone().multiply(along)), 1, 0, 0, 0, 0, dust, true);
            }
        }
    }

    private boolean venting(LivingEntity actor, long tick) {
        return actor instanceof Player player
                ? game.fighter(player).heat().venting(tick)
                : game.bots().heat(actor) >= VENTING_HEAT;
    }

    private void vent(LivingEntity actor) {
        var at = muzzle(actor, actor.getEyeLocation().getDirection());
        at.getWorld().spawnParticle(Particle.CLOUD, at, 2, 0.06, 0.06, 0.06, 0.03, null, true);
        at.getWorld().spawnParticle(Particle.WHITE_SMOKE, at, 2, 0.05, 0.05, 0.05, 0.02, null, true);
    }

    private boolean badlyHurt(LivingEntity actor) {
        var max = actor.getAttribute(Attribute.MAX_HEALTH);
        return max != null && actor.getHealth() < max.getValue() * config.damaged().below();
    }

    private static void smoulder(LivingEntity actor) {
        var chest = actor.getEyeLocation().subtract(0, 0.5, 0);
        actor.getWorld().spawnParticle(Particle.SMOKE, chest, 3, 0.15, 0.2, 0.15, 0.01);
        actor.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, chest, 3, 0.2, 0.3, 0.2, 0.05);
    }

    private static String flash(Faction faction) {
        return "sternenfront/fx/flash_" + faction.key();
    }

    private static Block groundBelow(Location at) {
        var block = at.getBlock();
        for (int depth = 0; depth <= GROUND_PROBE; depth++) {
            var below = block.getRelative(0, -depth, 0);
            if (below.isSolid()) {
                return below;
            }
        }
        return null;
    }

    private static Location onGround(Location at) {
        var ground = groundBelow(at);
        var spot = at.clone();
        if (ground != null) {
            spot.setY(ground.getY() + 1.15);
        }
        return spot;
    }

    private static void restore(Shot shot) {
        if (shot.actor().isValid()) {
            if (shot.actor() instanceof Player player) {
                var inventory = player.getInventory();
                for (int slot = 0; slot < inventory.getSize(); slot++) {
                    var item = inventory.getItem(slot);
                    if (restore(item, shot.blaster())) {
                        inventory.setItem(slot, item);
                    }
                }
            } else {
                var held = shot.actor().getEquipment().getItemInMainHand();
                if (restore(held, shot.blaster())) {
                    shot.actor().getEquipment().setItemInMainHand(held);
                }
            }
        }
    }

    private static boolean restore(ItemStack item, Blaster blaster) {
        if (HotbarItem.of(item, Blaster.class).filter(blaster::equals).isPresent()
                && item.getItemMeta().getCustomModelDataComponent().getStrings().contains(blaster.firingModel().key())) {
            blaster.model().mark(item);
            return true;
        }
        return false;
    }

    public void clear() {
        shots.values().forEach(BattleEffects::restore);
        shots.clear();
        flares.clear();
        scorches.clear();
        tethers.clear();
        nextSaberMark.clear();
    }
}
