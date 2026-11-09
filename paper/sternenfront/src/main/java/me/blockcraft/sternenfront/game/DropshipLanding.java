package me.blockcraft.sternenfront.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.presentation.PresentationScope;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.sternenfront.appearance.Displays;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Deploying by lander: the fighter rides their side's craft down beside the spawn, its doors open on the way in, and it
 * sets them down on the spot before it climbs away. Over stalls and low walls it hovers and drops them instead.
 * Teammates who deploy at the same spawn a moment later ride along. Riders stay as they were while choosing, hidden and
 * unhurt, until the craft sets them down. The craft is display entities only, so it blocks nobody. Under a roof it comes
 * down from just below it, straight and from lower, and a spawn without room for that keeps the plain fade.
 */
final class DropshipLanding implements Listener, AutoCloseable {

    /** When a craft does what, in ticks from its spawning high above the spawn. */
    record Settings(boolean enabled, double height, double glide, int descentTicks, int doorTicks, int settleTicks,
                    int liftTicks, int shareTicks) {

        static Settings from(ConfigurationSection config) {
            int descent = Math.clamp(config.getInt("presentation.landing.descent-ticks"), 4, LONGEST_GLIDE_TICKS);
            return new Settings(config.getBoolean("presentation.landing.enabled"),
                    Math.clamp(config.getDouble("presentation.landing.height"), 4, 48),
                    Math.clamp(config.getDouble("presentation.landing.glide"), 0, 16), descent,
                    Math.clamp(config.getInt("presentation.landing.door-ticks"), 1, descent),
                    Math.clamp(config.getInt("presentation.landing.settle-ticks"), 0, 40),
                    Math.clamp(config.getInt("presentation.landing.lift-ticks"), 1, 100),
                    Math.clamp(config.getInt("presentation.landing.share-ticks"), 0, descent));
        }

        int doorsOpenAt() {
            return descentTicks - doorTicks;
        }

        int releaseAt() {
            return descentTicks + settleTicks;
        }

        int endAt() {
            return releaseAt() + liftTicks;
        }

        boolean boardable(int age) {
            return age <= shareTicks && age < doorsOpenAt();
        }

        /** How far down the craft has come, 0 at the top and 1 on the ground, at the steady pace the client draws it. */
        double descended(int age) {
            return Math.clamp((double) age / descentTicks, 0, 1);
        }

        /** How far it has climbed away again, as a share of its height, gathering speed. */
        double climbed(int age) {
            double progress = Math.clamp((double) (age - releaseAt()) / liftTicks, 0, 1);
            return progress * progress;
        }
    }

    /*
     * The bay as SternenfrontArt builds it, in model units: its door planes, floor and the rider's seat beside the open
     * door, and the hull around it for the open-sky check.
     */
    private static final float SCALE = 3;
    private static final double BAY_SIDE = .42;
    private static final double FLOOR = .14;
    private static final double SEAT = .18;
    private static final double DOOR_SIDE = .455;
    private static final double SLIDING_DOOR_HEIGHT = .53;
    private static final double SLIDE = 1;
    private static final float RAMP_FOLD = (float) Math.toRadians(100);
    private static final double HULL_LENGTH = 1.25;
    private static final double HULL_SIDE = .45;
    private static final double WING_SPAN = 1.3;
    private static final double WING_UNDERSIDE = .7;
    private static final double HULL_HEIGHT = 1.45;
    /** A player sits this far below what they ride: Minecraft's vehicle attachment for players. */
    private static final double RIDER_DROP = .6;
    /** How far past the door plane a rider steps out. */
    private static final double STEP_OUT = .6;
    /**
     * The craft comes down in one move the client draws over the whole descent, at most this long: a teleport of a
     * vehicle drags its riders along, and Grim counts each one a rider's client does not accept as a violation.
     */
    private static final int LONGEST_GLIDE_TICKS = 59;
    /** It climbs away empty in short steps, gathering speed. */
    private static final int STEP_TICKS = 2;
    private static final int ENGINE_SOUND_TICKS = 8;
    private static final float VIEW_RANGE = 3;
    private static final float RIDER_PITCH = 12;
    private static final double SKY_SAMPLE_BLOCKS = 2;
    private static final double WASH_HEIGHT = 7;
    /** Under a roof the craft comes down from this far below it, and not at all with less than the lowest descent. */
    private static final double ROOF_GAP = .5;
    private static final double LOWEST_DESCENT = 2;
    /** Over stalls and walls up to this high the craft hovers and drops its riders, whom the fall does not hurt. */
    private static final double HIGHEST_HOVER = 6;
    private static final int DROP_SAFE_TICKS = 40;
    private static final int PROMPT_TICKS = 2;

    private record Rider(Player player, Location spot, Consumer<Location> land) { }

    private final class Craft {
        private final Faction faction;
        private final Spawn spawn;
        private final Location ground;
        private final Placement placement;
        private final Vector forward;
        private final Location landing;
        private final PresentationScope scope;
        private final List<Rider> riders = new ArrayList<>();
        private ItemDisplay hull;
        private final List<ItemDisplay> doors = new ArrayList<>();
        private int age;

        private Craft(Faction faction, Spawn spawn, Location ground, Placement placement) {
            this.faction = faction;
            this.spawn = spawn;
            this.ground = ground;
            this.placement = placement;
            forward = forward(placement.door());
            landing = DropshipLanding.centre(ground, placement.door()).add(placement.door().clone().multiply(SEAT * SCALE))
                    .add(0, RIDER_DROP + FLOOR * SCALE + placement.hover(), 0).setDirection(forward);
            scope = new PresentationScope(game.plugin());
        }

        private Location at(int tick) {
            if (tick >= settings.releaseAt()) {
                double climbed = settings.climbed(tick);
                return landing.clone().add(forward.clone().multiply(placement.glide() * 2 * climbed))
                        .add(0, placement.height() * climbed, 0);
            }
            double left = 1 - settings.descended(tick);
            return landing.clone().subtract(forward.clone().multiply(placement.glide() * left)).add(0, placement.height() * left, 0);
        }

        /** Below the middle of the hull, which stands off the seat away from the open door. */
        private Location centre(Location at) {
            return at.clone().subtract(placement.door().clone().multiply(SEAT * SCALE));
        }

        /** How far a rider drops from the door onto the ground: nothing once the craft stands on it. */
        private double drop() {
            return placement.hover() > 0 ? placement.hover() + FLOOR * SCALE : 0;
        }

        private boolean sliding() {
            return faction == Faction.KOLONIEN;
        }
    }

    private final SternenfrontGame game;
    private final Settings settings;
    private final List<Craft> crafts = new ArrayList<>();
    private final Map<UUID, Craft> riding = new HashMap<>();

    DropshipLanding(SternenfrontGame game, Settings settings) {
        this.game = game;
        this.settings = settings;
        game.plugin().getServer().getPluginManager().registerEvents(this, game.plugin());
    }

    boolean riding(Player player) {
        return riding.containsKey(player.getUniqueId());
    }

    /**
     * Seats the player in a craft bound for {@code spot}, one of their side's on its way to the same spawn if it is still
     * high enough, and calls {@code land} with where it sets them down. False when no craft can come down there, which
     * leaves the player as they were. The assault's dropship already stands where its spawn lies, so nothing lands there.
     */
    boolean board(Player player, Faction faction, Spawn spawn, Location spot, Consumer<Location> land) {
        if (!settings.enabled() || spawn instanceof Spawn.Dropship || !game.plugin().getConfig().getBoolean("presentation.effects")
                || !BlockcraftApi.get().resourcePack().applied(player) || riding(player)) {
            return false;
        }
        var shared = crafts.stream().filter(craft -> craft.faction == faction && craft.spawn.equals(spawn)
                && settings.boardable(craft.age) && !craft.scope.closed()).findFirst();
        var craft = shared.orElseGet(() -> launch(faction, spawn, spot));
        if (craft == null) {
            return false;
        }
        var seat = craft.at(craft.age).setDirection(craft.placement.door());
        seat.setPitch(RIDER_PITCH);
        var from = spread(craft, craft.riders.size());
        if (!player.teleport(seat) || !craft.hull.addPassenger(player)) {
            if (craft.riders.isEmpty()) {
                craft.scope.close();
            }
            return false;
        }
        craft.riders.add(new Rider(player, from, land));
        riding.put(player.getUniqueId(), craft);
        // The client shows its own prompt to dismount on boarding, which a rider here cannot; this line replaces it.
        Tasks.later(game.plugin(), PROMPT_TICKS, () -> {
            if (riding(player)) BlockcraftApi.get().notifications().status(player, game.messages().get("deploy.landing"));
        });
        return true;
    }

    /**
     * Where a craft comes down beside {@code spot}: the door it opens onto the spot, which faces the way the spot does if
     * there is room for that, how high it hovers over what stands under it, and how far it comes down and glides in.
     */
    private record Placement(Vector door, double hover, double height, double glide) { }

    /** A craft that sets its rider down on {@code spot}, or null without room for one. */
    private Craft launch(Faction faction, Spawn spawn, Location spot) {
        var placement = place(spot);
        if (placement == null) {
            return null;
        }
        var craft = new Craft(faction, spawn, spot.clone(), placement);
        var start = craft.at(0);
        craft.hull = Displays.item(start, Displays.model("sternenfront/lander/" + faction.key()), display -> {
            display.setTransformation(transformation(new Vector3f(0, 0, 0), new Quaternionf()));
            display.setBrightness(Displays.LIT);
            display.setTeleportDuration(settings.descentTicks());
            display.setViewRange(VIEW_RANGE);
            display.setShadowRadius(SCALE * .6f);
            display.setShadowStrength(.8f);
        });
        craft.scope.own(craft.hull::remove);
        for (int side : new int[] {1, -1}) {
            var door = Displays.item(start, Displays.model("sternenfront/lander/" + faction.key() + "_door"), display -> {
                display.setTransformation(door(craft, side, false));
                display.setBrightness(Displays.LIT);
                display.setViewRange(VIEW_RANGE);
            });
            craft.scope.own(door::remove);
            craft.doors.add(door);
            craft.hull.addPassenger(door);
        }
        crafts.add(craft);
        craft.scope.own(() -> crafts.remove(craft));
        craft.scope.own(() -> List.copyOf(craft.riders).forEach(rider -> drop(craft, rider)));
        var task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!craft.scope.closed()) {
                    fly(craft);
                }
            }
        }.runTaskTimer(game.plugin(), 1, 1);
        craft.scope.own(task::cancel);
        SoundCue.ORBITAL_INCOMING.play(spot, 1.5f, 1.4f);
        return craft;
    }

    /**
     * The first side the door can open towards where the craft comes down from the open sky hovering no higher than a
     * rider drops: the way the spot faces, then across it, then away from it, since a rider steps out looking through
     * the door. Failing that, the first side with room under a roof comes straight down from just below it.
     */
    private Placement place(Location spot) {
        var facing = spot.getDirection().setY(0);
        facing = facing.lengthSquared() == 0 ? new Vector(0, 0, 1) : facing.normalize();
        var doors = List.of(facing, new Vector(-facing.getZ(), 0, facing.getX()), new Vector(facing.getZ(), 0, -facing.getX()),
                facing.clone().multiply(-1));
        Placement roofed = null;
        for (var door : doors) {
            var centre = centre(spot, door);
            var forward = forward(door);
            double hover = hover(centre, forward, door, spot.getY());
            if (hover <= HIGHEST_HOVER && clearAbove(spot, hover)) {
                return new Placement(door, hover, settings.height(), settings.glide());
            }
            double room = roofHeight(centre, forward, door, spot.getY()) - HULL_HEIGHT * SCALE - ROOF_GAP;
            if (roofed == null && room >= LOWEST_DESCENT) {
                roofed = new Placement(door, 0, Math.min(settings.height(), room), 0);
            }
        }
        return roofed;
    }

    private static Vector forward(Vector door) {
        return new Vector(-door.getZ(), 0, door.getX());
    }

    /** Under the middle of the hull, on the ground, when the door towards {@code door} opens onto {@code spot}. */
    private static Location centre(Location spot, Vector door) {
        return spot.clone().subtract(door.clone().multiply(BAY_SIDE * SCALE + STEP_OUT));
    }

    /** A column under the craft, and how high over the ground the craft's underside is there. */
    private record Column(Location at, double underside) { }

    /**
     * Columns under the hull every two blocks and under both wingtips, and behind the hull along the glide, under the
     * height it comes down at there.
     */
    private List<Column> columns(Location centre, Vector forward, Vector left, double glide) {
        var columns = new ArrayList<Column>();
        double length = HULL_LENGTH * SCALE;
        for (double along = -length - glide; along <= length; along += SKY_SAMPLE_BLOCKS) {
            double behind = Math.max(0, -length - along);
            double underside = glide == 0 ? 0 : settings.height() * behind / glide;
            for (double across : new double[] {-HULL_SIDE * SCALE, 0, HULL_SIDE * SCALE}) {
                columns.add(new Column(centre.clone().add(forward.clone().multiply(along)).add(left.clone().multiply(across)), underside));
            }
        }
        for (double across : new double[] {-WING_SPAN * SCALE, WING_SPAN * SCALE}) {
            columns.add(new Column(centre.clone().add(left.clone().multiply(across)), WING_UNDERSIDE * SCALE));
        }
        return columns;
    }

    /** How high the craft must hover for its underside to clear everything under it, sky above; past the top, a roof. */
    private double hover(Location centre, Vector forward, Vector left, double ground) {
        var world = centre.getWorld();
        int bottom = (int) Math.floor(ground);
        int top = (int) Math.ceil(ground + settings.height() + HULL_HEIGHT * SCALE);
        double hover = 0;
        for (var column : columns(centre, forward, left, settings.glide())) {
            int x = column.at().getBlockX();
            int z = column.at().getBlockZ();
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                return Double.MAX_VALUE;
            }
            for (int y = top; y >= bottom; y--) {
                if (!world.getBlockAt(x, y, z).isPassable()) {
                    hover = Math.max(hover, y + 1 - ground - column.underside());
                    break;
                }
            }
        }
        return hover;
    }

    /** How high the lowest roof over the craft's place on the ground is; a step of one block under it is fine. */
    private double roofHeight(Location centre, Vector forward, Vector left, double ground) {
        var world = centre.getWorld();
        int bottom = (int) Math.floor(ground) + 1;
        int roof = (int) Math.ceil(ground + settings.height() + HULL_HEIGHT * SCALE) + 1;
        for (var column : columns(centre, forward, left, 0)) {
            int x = column.at().getBlockX();
            int z = column.at().getBlockZ();
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                return 0;
            }
            for (int y = bottom; y < roof; y++) {
                if (!world.getBlockAt(x, y, z).isPassable()) {
                    roof = y;
                }
            }
        }
        return roof - ground;
    }

    /** Whether a rider dropped from a hovering craft falls freely onto the spot. */
    private boolean clearAbove(Location spot, double hover) {
        var block = spot.getBlock();
        for (int up = 0; up <= Math.ceil(hover + FLOOR * SCALE) + 1; up++) {
            if (!block.getRelative(0, up, 0).isPassable()) {
                return false;
            }
        }
        return true;
    }

    /** The first rider steps out onto the spot itself, the next beside them along the hull where there is footing. */
    private Location spread(Craft craft, int rider) {
        var fitting = new ArrayList<Location>();
        for (int step : new int[] {1, -1, 2, -2}) {
            var spot = craft.ground.clone().add(craft.forward.clone().multiply(step));
            var feet = spot.getBlock();
            if (feet.isPassable() && feet.getRelative(0, 1, 0).isPassable() && !feet.getRelative(0, -1, 0).isPassable()) {
                fitting.add(spot);
            }
        }
        return rider == 0 || fitting.isEmpty() ? craft.ground.clone() : fitting.get((rider - 1) % fitting.size());
    }

    private void fly(Craft craft) {
        int age = craft.age++;
        craft.riders.removeIf(rider -> {
            boolean gone = !rider.player().isOnline() || rider.player().isDead()
                    || age < settings.releaseAt() && !craft.hull.equals(rider.player().getVehicle());
            if (gone) riding.remove(rider.player().getUniqueId(), craft);
            return gone;
        });
        if (craft.riders.isEmpty() && age < settings.releaseAt() || age >= settings.endAt()) {
            craft.scope.close();
            return;
        }
        if (age == 0) {
            craft.hull.teleport(craft.landing);
        }
        if (age >= settings.releaseAt() && age % STEP_TICKS == 0) {
            craft.hull.setTeleportDuration(STEP_TICKS);
            craft.hull.teleport(craft.at(age + STEP_TICKS));
        }
        if (age % ENGINE_SOUND_TICKS == 0) {
            SoundCue.JETPACK.play(craft.hull.getLocation(), 1.5f, age < settings.releaseAt() ? .55f : .7f);
        }
        if (age % STEP_TICKS == 0) {
            wash(craft, craft.at(age));
        }
        if (age == settings.doorsOpenAt()) {
            openDoors(craft);
        }
        if (age == settings.descentTicks()) {
            touchDown(craft);
        }
        if (age == settings.releaseAt() - 1) {
            craft.riders.forEach(rider -> rider.player().leaveVehicle());
        }
        if (age == settings.releaseAt()) {
            List.copyOf(craft.riders).forEach(rider -> setDown(craft, rider));
        }
    }

    private void openDoors(Craft craft) {
        for (int index = 0; index < craft.doors.size(); index++) {
            var door = craft.doors.get(index);
            door.setInterpolationDelay(0);
            door.setInterpolationDuration(settings.doorTicks());
            door.setTransformation(door(craft, index == 0 ? 1 : -1, true));
        }
        var at = craft.hull.getLocation();
        at.getWorld().playSound(at, craft.sliding() ? Sound.BLOCK_PISTON_CONTRACT : Sound.BLOCK_IRON_TRAPDOOR_OPEN,
                SoundCategory.PLAYERS, 1.2f, .6f);
    }

    /** Thrust stirring the ground under the craft, more of it the lower it hangs. */
    private void wash(Craft craft, Location at) {
        var world = at.getWorld();
        double altitude = at.getY() - craft.ground.getY();
        var centre = craft.centre(at);
        world.spawnParticle(Particle.WHITE_SMOKE, centre.clone().subtract(0, 1, 0), 3, .6, .2, .6, .02, null, true);
        if (altitude < WASH_HEIGHT) {
            int dust = (int) (4 + 16 * (1 - altitude / WASH_HEIGHT));
            var ground = new Location(world, centre.getX(), craft.ground.getY() + .2, centre.getZ());
            world.spawnParticle(Particle.DUST_PLUME, ground, dust, SCALE * .6, .1, SCALE * .6, .04, null, true);
        }
    }

    private void touchDown(Craft craft) {
        var at = craft.centre(craft.landing);
        at.setY(craft.ground.getY() + .15);
        var world = at.getWorld();
        game.effects().flares().ring("sternenfront/fx/ring", at, new Vector(0, 1, 0), new Vector(), SCALE * 2.5f, 12, craft.faction.bolt());
        world.spawnParticle(Particle.DUST_PLUME, at, 50, SCALE * .7, .2, SCALE * .7, .08, null, true);
        var under = craft.ground.clone().subtract(0, .5, 0).getBlock();
        if (!under.isPassable()) {
            world.spawnParticle(Particle.BLOCK, at, 30, SCALE * .6, .2, SCALE * .6, .2, under.getBlockData(), true);
        }
        if (craft.drop() == 0) {
            SoundCue.ORBITAL_IMPACT.play(at, 1.2f, 1.4f);
        }
    }

    /**
     * Out of the craft and onto their spot, looking where they looked inside it, turned as far as the door was from the
     * way the spot faces: whoever looked out of the door looks the way the spot does.
     */
    private void setDown(Craft craft, Rider rider) {
        var player = rider.player();
        var spot = rider.spot().clone().add(0, craft.drop(), 0);
        float door = craft.ground.clone().setDirection(craft.placement.door()).getYaw();
        spot.setYaw(player.getLocation().getYaw() + craft.ground.getYaw() - door);
        spot.setPitch(player.getLocation().getPitch());
        drop(craft, rider);
        rider.land().accept(spot);
        if (craft.drop() > 0 && game.onField(player)) {
            game.fighter(player).makeFallSafeUntil(game.tick() + DROP_SAFE_TICKS);
        }
    }

    private void drop(Craft craft, Rider rider) {
        craft.riders.remove(rider);
        riding.remove(rider.player().getUniqueId(), craft);
        rider.player().leaveVehicle();
    }

    /** The player leaves their craft without being set down, which goes on without them, or goes if it was only theirs. */
    void cancel(Player player) {
        var craft = riding.get(player.getUniqueId());
        if (craft == null) return;
        craft.riders.stream().filter(rider -> rider.player().equals(player)).findFirst().ifPresent(rider -> drop(craft, rider));
        if (craft.riders.isEmpty() && craft.age < settings.releaseAt()) {
            craft.scope.close();
        }
    }

    /**
     * Only the craft gets a rider off, a tick before it sets them down, since leaving a vehicle moves the player as well
     * and Grim counts the second of two moves in one tick as refused. A sneak does not, while what Minecraft cannot
     * refuse, as a teleport, leaves them choosing.
     */
    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player) || !riding(player) || riding.get(player.getUniqueId()).age >= settings.releaseAt()) {
            return;
        }
        if (event.isCancellable()) {
            event.setCancelled(true);
        } else {
            var craft = riding.remove(player.getUniqueId());
            craft.riders.removeIf(rider -> rider.player().equals(player));
        }
    }

    private Transformation door(Craft craft, int side, boolean open) {
        var turn = new Quaternionf().rotationY(side > 0 ? 0 : (float) Math.PI);
        if (craft.sliding()) {
            return transformation(new Vector3f((float) (side * DOOR_SIDE), (float) SLIDING_DOOR_HEIGHT, open ? (float) -SLIDE : 0), turn);
        }
        return transformation(new Vector3f((float) (side * DOOR_SIDE), (float) FLOOR, 0), open ? turn.rotateZ(-RAMP_FOLD) : turn);
    }

    /**
     * Places a part's model, given in model units from the craft's centre on the ground, around the seat the display
     * stands on. Minecraft's item-display renderer adds a half turn after this transformation, which the right rotation undoes.
     */
    private static Transformation transformation(Vector3f offset, Quaternionf turn) {
        var translation = new Vector3f((float) (offset.x - SEAT) * SCALE, (float) (offset.y * SCALE - RIDER_DROP - FLOOR * SCALE),
                offset.z * SCALE);
        return new Transformation(translation, turn, new Vector3f(SCALE), new Quaternionf().rotationY((float) Math.PI));
    }

    @Override
    public void close() {
        List.copyOf(crafts).forEach(craft -> craft.scope.close());
        riding.clear();
    }
}
