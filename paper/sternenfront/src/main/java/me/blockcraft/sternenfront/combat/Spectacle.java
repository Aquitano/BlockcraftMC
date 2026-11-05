package me.blockcraft.sternenfront.combat;

import me.blockcraft.sternenfront.appearance.Displays;
import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.game.SoundCue;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The round's two big moments. When a faction falls behind, its dropship comes in from high and far along the map's
 * axis and stays where it landed. In the last stand a sandstorm blows for the rest of the round: every player gets
 * their own cheap cloud of it, all in one wind. Both are only pictures and sound; nothing here touches the fight.
 */
public final class Spectacle {

    private static final int WAYPOINT_TICKS = 4;
    private static final int ENGINE_TICKS = 2;
    private static final int ENGINE_SOUND_TICKS = 8;
    private static final int IDLE_STEAM_TICKS = 40;
    private static final float DESCENT_PITCH = 12;
    private static final double SHORTEST_RUN = 0.6;
    private static final double GROUND_BLAST_FROM = 0.7;
    private static final float VIEW_RANGE = 4;
    private static final int STORM_SOUND_TICKS = 200;
    private static final Color SHADOW = Color.fromRGB(0x3A2C1C);
    private static final Color SAND_NEAR = Color.fromRGB(0xE2C48D);
    private static final Color SAND_FAR = Color.fromRGB(0x7A5C34);
    private static final ItemStack SAND = new ItemStack(Material.SAND);

    private static final class Ship {
        private final ItemDisplay display;
        private final Faction faction;
        private final Location start;
        private final Location landing;
        private int age;
        private boolean landed;

        private Ship(ItemDisplay display, Faction faction, Location start, Location landing) {
            this.display = display;
            this.faction = faction;
            this.start = start;
            this.landing = landing;
        }
    }

    private record Storm(StormWind wind, Vector direction) { }

    private final SternenfrontGame game;
    private final PresentationConfig config;
    private Ship ship;
    private Storm storm;

    public Spectacle(SternenfrontGame game) {
        this.game = game;
        config = PresentationConfig.from(game.plugin().getConfig());
    }

    public void assaultBegins(Faction trailing, Location landing) {
        if (!config.effects()) {
            return;
        }
        endShip();
        var assault = config.assault();
        var ground = landing.clone().add(0, assault.clearance(), 0);
        var start = approach(trailing, ground);
        var heading = ground.toVector().subtract(start.toVector()).setY(0);
        if (heading.lengthSquared() > 0) {
            start.setDirection(heading);
            ground.setDirection(heading);
        }
        start.setPitch(DESCENT_PITCH);
        ground.setPitch(0);
        var look = Displays.model("sternenfront/dropship/" + trailing.key());
        var display = Displays.item(start, look, spawned -> {
            // Minecraft's item-display renderer adds a half turn after this transformation.
            spawned.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(assault.scale()),
                    new Quaternionf().rotationY((float) Math.PI)));
            spawned.setBrightness(Displays.LIT);
            spawned.setTeleportDuration(WAYPOINT_TICKS);
            spawned.setViewRange(VIEW_RANGE);
            spawned.setShadowRadius(assault.scale() * 0.7f);
            spawned.setShadowStrength(1);
        });
        ship = new Ship(display, trailing, start, ground);
        SoundCue.ORBITAL_INCOMING.play(ground, 6f, 0.7f);
    }

    /** High over the far end of the run along the headquarters' axis, from the faction's own side if there is room. */
    private Location approach(Faction trailing, Location ground) {
        var map = game.map();
        var own = map.headquarters().get(trailing).zone().getCenter();
        var other = map.headquarters().get(trailing.enemy()).zone().getCenter();
        var axis = own.subtract(other).setY(0);
        axis = axis.lengthSquared() > 0 ? axis.normalize() : new Vector(1, 0, 0);
        var assault = config.assault();
        var start = clamped(ground, axis, assault.distance());
        if (start.distance(ground.clone().add(0, assault.height(), 0)) < assault.distance() * SHORTEST_RUN) {
            start = clamped(ground, axis.clone().multiply(-1), assault.distance());
        }
        return start;
    }

    private Location clamped(Location ground, Vector axis, double distance) {
        var box = game.map().playArea();
        var start = ground.clone().add(axis.clone().multiply(distance));
        start.setX(Math.clamp(start.getX(), box.getMinX(), box.getMaxX()));
        start.setZ(Math.clamp(start.getZ(), box.getMinZ(), box.getMaxZ()));
        start.setY(ground.getY() + config.assault().height());
        return start;
    }

    public void lastStandBegins() {
        if (game.map().rain()) {
            game.map().world().setThundering(true);
            game.map().world().setThunderDuration(Integer.MAX_VALUE);
            return;
        }
        if (!config.effects() || storm != null) {
            return;
        }
        var settings = config.storm();
        double heading = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        storm = new Storm(new StormWind(game.tick(), settings.gustSeconds() * 20, settings.gustTicks(), settings.gustBoost()),
                new Vector(Math.cos(heading), 0, Math.sin(heading)));
    }

    public void tick(long tick) {
        if (ship != null) {
            fly(ship);
        }
        if (storm != null) {
            blow(storm, tick);
        }
    }

    private void fly(Ship flying) {
        flying.age++;
        int ticks = config.assault().ticks();
        if (flying.landed) {
            if (flying.age % IDLE_STEAM_TICKS == 0) {
                flying.display.getWorld().spawnParticle(Particle.WHITE_SMOKE, flying.landing, 6, config.assault().scale() * 0.25, 0.5,
                        config.assault().scale() * 0.25, 0.02);
            }
            return;
        }
        double progress = Math.min(1, (double) flying.age / ticks);
        if (flying.age % WAYPOINT_TICKS == 0 || flying.age >= ticks) {
            flying.display.teleport(along(flying, (double) (flying.age + WAYPOINT_TICKS) / ticks));
        }
        var at = along(flying, progress);
        if (flying.age % ENGINE_TICKS == 0) {
            engines(at);
            shadow(at, progress);
        }
        if (flying.age % ENGINE_SOUND_TICKS == 0) {
            SoundCue.JETPACK.play(at, (float) (3 + 5 * progress), 0.5f);
        }
        if (flying.age >= ticks) {
            flying.landed = true;
            touchDown(flying);
        }
    }

    /** The ship slows towards the ground, its nose coming up. */
    private static Location along(Ship flying, double progress) {
        double eased = 1 - Math.pow(1 - Math.min(1, progress), 2);
        var at = flying.start.clone().add(flying.landing.toVector().subtract(flying.start.toVector()).multiply(eased));
        at.setPitch((float) (flying.start.getPitch() * (1 - eased)));
        return at;
    }

    private void engines(Location at) {
        var facing = at.getDirection().setY(0);
        if (facing.lengthSquared() == 0) {
            return;
        }
        float scale = config.assault().scale();
        var side = facing.normalize().clone().crossProduct(new Vector(0, 1, 0)).multiply(scale * 0.25);
        var tail = at.clone().subtract(facing.multiply(scale * 0.45));
        for (var engine : new Location[] {tail.clone().add(side), tail.clone().subtract(side)}) {
            at.getWorld().spawnParticle(Particle.FLAME, engine, 4, 0.25, 0.25, 0.25, 0.03, null, true);
            at.getWorld().spawnParticle(Particle.LARGE_SMOKE, engine, 2, 0.3, 0.3, 0.3, 0.02, null, true);
        }
    }

    private void shadow(Location at, double progress) {
        var world = at.getWorld();
        float scale = config.assault().scale();
        var ground = new Location(world, at.getX(), world.getHighestBlockYAt(at) + 1.2, at.getZ());
        world.spawnParticle(Particle.DUST, ground, 8, scale * 0.35, 0.05, scale * 0.35, 0, new Particle.DustOptions(SHADOW, 2.5f), true);
        if (progress > GROUND_BLAST_FROM) {
            int blast = (int) (6 + 30 * (progress - GROUND_BLAST_FROM) / (1 - GROUND_BLAST_FROM));
            world.spawnParticle(Particle.DUST_PLUME, ground, blast, scale * 0.5, 0.2, scale * 0.5, 0.05, null, true);
        }
    }

    private void touchDown(Ship flying) {
        var at = flying.landing;
        var world = at.getWorld();
        float scale = config.assault().scale();
        var effects = game.effects().flares();
        var ground = new Location(world, at.getX(), world.getHighestBlockYAt(at) + 1.15, at.getZ());
        effects.ring("sternenfront/fx/ring", ground, new Vector(0, 1, 0), new Vector(), scale * 4, 14, flying.faction.bolt());
        for (int puff = 0; puff < 4; puff++) {
            var random = ThreadLocalRandom.current();
            effects.puff("sternenfront/fx/smoke_puff", ground.clone().add(random.nextDouble(-1, 1) * scale, 0.5, random.nextDouble(-1, 1) * scale),
                    scale * 0.3f, scale * 0.7f, random.nextDouble(1.5, 3), 40, 15);
        }
        world.spawnParticle(Particle.DUST_PLUME, ground, 80, scale * 0.9, 0.4, scale * 0.9, 0.12, null, true);
        var below = ground.clone().subtract(0, 1.5, 0).getBlock();
        if (!below.getType().isAir()) {
            world.spawnParticle(Particle.BLOCK, ground, 40, scale * 0.7, 0.3, scale * 0.7, 0.3, below.getBlockData(), true);
        }
        SoundCue.ORBITAL_IMPACT.play(at, 5f, 0.6f);
    }

    private void blow(Storm blowing, long tick) {
        var settings = config.storm();
        var wind = blowing.wind();
        if (wind.gustStarts(tick)) {
            game.map().world().getPlayers().forEach(Spectacle::gustSound);
        }
        if (Math.floorMod(tick - wind.startedAt(), (long) STORM_SOUND_TICKS) == 0) {
            game.map().world().getPlayers().forEach(Spectacle::windSound);
        }
        boolean grains = tick % settings.intervalTicks() == 0;
        boolean haze = tick % settings.hazeTicks() == 0;
        if (!grains && !haze) {
            return;
        }
        for (var player : game.map().world().getPlayers()) {
            if (grains) {
                grains(player, blowing, tick);
            }
            if (haze) {
                haze(player, blowing, tick);
            }
        }
    }

    /** Sand carried by the wind, spawned upwind so it crosses the player, and none of it under a roof. */
    private void grains(Player player, Storm blowing, long tick) {
        var settings = config.storm();
        var wind = blowing.wind();
        var eye = player.getEyeLocation();
        var world = eye.getWorld();
        var random = ThreadLocalRandom.current();
        double radius = settings.radius();
        var drift = blowing.direction().clone().multiply(settings.windSpeed() * wind.strength(tick));
        var upwind = blowing.direction().clone().multiply(-radius * 0.5);
        int count = wind.particles(tick, settings.density());
        for (int grain = 0; grain < count; grain++) {
            double x = eye.getX() + upwind.getX() + random.nextDouble(-radius, radius);
            double y = eye.getY() + random.nextDouble(-2, 5);
            double z = eye.getZ() + upwind.getZ() + random.nextDouble(-radius, radius);
            if (y > world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z))) {
                player.spawnParticle(Particle.ITEM, x, y, z, 0, drift.getX(), 0.04, drift.getZ(), 1, SAND, true);
            }
        }
    }

    /** Two layers of slow dust, a pale one close by and a dark one further out, that thicken the air and dim the view. */
    private void haze(Player player, Storm blowing, long tick) {
        var settings = config.storm();
        var eye = player.getEyeLocation();
        if (eye.getY() < eye.getWorld().getHighestBlockYAt(eye)) {
            return;
        }
        int count = blowing.wind().particles(tick, settings.haze());
        double radius = settings.radius();
        var near = eye.clone().add(blowing.direction().clone().multiply(-3));
        player.spawnParticle(Particle.DUST, near, count, radius * 0.6, 2.5, radius * 0.6, 0, new Particle.DustOptions(SAND_NEAR, 2.4f), true);
        player.spawnParticle(Particle.DUST, eye, count, radius, 4, radius, 0, new Particle.DustOptions(SAND_FAR, 3.6f), true);
    }

    public void clear() {
        endShip();
        if (storm != null) {
            game.map().world().getPlayers().forEach(player -> player.stopSound(Sound.ITEM_ELYTRA_FLYING, SoundCategory.WEATHER));
        }
        storm = null;
    }

    private void endShip() {
        if (ship != null) {
            ship.display.remove();
            ship = null;
        }
    }

    private static void windSound(Player player) {
        player.playSound(player.getLocation(), Sound.ITEM_ELYTRA_FLYING, SoundCategory.WEATHER, 0.5f, 0.6f);
    }

    private static void gustSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, SoundCategory.WEATHER, 0.35f, 0.6f);
    }
}
