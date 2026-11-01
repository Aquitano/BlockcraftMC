package me.blockcraft.lobby.activity;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.DyedItemColor;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.Locations;
import me.blockcraft.common.calendar.NetworkCalendar;
import me.blockcraft.common.calendar.NetworkCalendar.Occasion;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.lobby.ChunkTickets;
import me.blockcraft.lobby.config.ScenesConfig;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Bat;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Vex;
import org.bukkit.entity.Witch;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/**
 * Staged scenes that stand in the lobby only while their occasion lasts. In winter two teams of figures crouch behind
 * snow walls and pelt each other with snowballs; a figure a player hits throws back at them. At Halloween a witch
 * stirs her bubbling cauldron, bats circle their spot and a ghost drifts along its path. Everything is spawned fresh
 * and never saved, at the map's {@code snowfight-a-<n>}, {@code snowfight-b-<n>}, {@code halloween-witch} (facing her
 * cauldron), {@code halloween-bats} and {@code halloween-ghost-<n>} spots.
 */
public final class SeasonalScenes implements Listener, AutoCloseable {

    private static final long STEP_TICKS = 2L;
    private static final long CHECK_TICKS = 20L;
    private static final long STAND_TICKS = 16L;
    private static final double THROW_SPEED = 0.9;
    private static final double SNOWBALL_GRAVITY = 0.03;
    private static final double EFFECT_RANGE = 48;
    private static final Vector3f WALL = new Vector3f(1.6f, 0.95f, 0.5f);
    private static final NamespacedKey SCENE_SNOWBALL = new NamespacedKey("blockcraft", "scene_snowball");
    private static final ItemStack SNOW = ItemStack.of(Material.SNOWBALL);

    private record Team(List<Mannequin> figures) {
    }

    private final Plugin plugin;
    private final NetworkCalendar calendar;
    private final Locations<String> spots;
    private final ScenesConfig config;
    private final ChunkTickets.Owner held;
    private final List<Entity> winter = new ArrayList<>();
    private final List<Entity> halloween = new ArrayList<>();
    private final List<Team> teams = new ArrayList<>();
    private final Set<UUID> answering = new HashSet<>();
    private final List<Bat> bats = new ArrayList<>();
    private final List<Location> ghostPath = new ArrayList<>();
    private final BukkitTask check;
    private final BukkitTask step;
    private Witch witch;
    private Vex ghost;
    private Location witchSpot;
    private Location batSpot;
    private long ticks;
    private double ghostDistance;

    public SeasonalScenes(Plugin plugin, ChunkTickets chunks, NetworkCalendar calendar, Locations<String> spots, ScenesConfig config) {
        this.plugin = plugin;
        this.calendar = calendar;
        this.spots = spots;
        this.config = config;
        this.held = chunks.owner();
        this.check = Bukkit.getScheduler().runTaskTimer(plugin, this::check, 1L, CHECK_TICKS);
        this.step = Bukkit.getScheduler().runTaskTimer(plugin, this::step, STEP_TICKS, STEP_TICKS);
    }

    /** The {@code /lobbyset} keys of the scenes. */
    public List<String> spots() {
        var keys = new ArrayList<String>();
        for (var team : List.of("a", "b")) {
            IntStream.rangeClosed(1, config.snowfightFigures()).forEach(n -> keys.add("snowfight-" + team + "-" + n));
        }
        keys.add("halloween-witch");
        keys.add("halloween-bats");
        IntStream.rangeClosed(1, config.ghostSpots()).forEach(n -> keys.add("halloween-ghost-" + n));
        return keys;
    }

    /** Sets the scenes up again from the spots, after staff moved one. */
    public void refresh() {
        clearWinter();
        clearHalloween();
        check();
    }

    private void check() {
        if (calendar.is(Occasion.WINTER) == winter.isEmpty()) {
            if (winter.isEmpty()) {
                spawnWinter();
            } else {
                clearWinter();
            }
        }
        if (calendar.is(Occasion.HALLOWEEN) == halloween.isEmpty()) {
            if (halloween.isEmpty()) {
                spawnHalloween();
            } else {
                clearHalloween();
            }
        }
    }

    private void spawnWinter() {
        for (var side : List.of("a", "b")) {
            var color = side.equals("a") ? Color.fromRGB(0xB02E26) : Color.fromRGB(0x3C44AA);
            var figures = new ArrayList<Mannequin>();
            for (int n = 1; n <= config.snowfightFigures(); n++) {
                spots.get("snowfight-" + side + "-" + n).ifPresent(spot -> {
                    held.add(spot.getChunk());
                    var figure = Figures.spawn(spot, skin(winter.size() / 2), spawned -> {
                        var cap = ItemStack.of(Material.LEATHER_HELMET);
                        cap.setData(DataComponentTypes.DYED_COLOR, DyedItemColor.dyedItemColor(color));
                        spawned.getEquipment().setHelmet(cap);
                        spawned.getEquipment().setItemInMainHand(ItemStack.of(Material.SNOWBALL));
                    });
                    crouch(figure);
                    figures.add(figure);
                    winter.add(figure);
                    winter.add(wall(spot));
                });
            }
            teams.add(new Team(figures));
        }
        if (winter.isEmpty()) {
            teams.clear();
        }
    }

    /** The {@code n}th fighter's skin: the configured textures in turn, the default look without any. */
    private ResolvableProfile skin(int n) {
        var textures = config.snowfightSkins();
        return textures.isEmpty() ? Mannequin.defaultProfile() : ResolvableProfile.resolvableProfile()
                .addProperty(new ProfileProperty("textures", textures.get(n % textures.size()))).build();
    }

    /** A low snow wall a step in front of a figure, across its view. */
    private static BlockDisplay wall(Location spot) {
        var at = spot.clone().add(spot.getDirection().setY(0).normalize().multiply(1.1));
        return at.getWorld().spawn(at, BlockDisplay.class, wall -> {
            wall.setBlock(Material.SNOW_BLOCK.createBlockData());
            wall.setPersistent(false);
            wall.setTransformation(new Transformation(new Vector3f(WALL).mul(-0.5f, 0, -0.5f), new Quaternionf(), WALL, new Quaternionf()));
        });
    }

    private void spawnHalloween() {
        spots.get("halloween-witch").ifPresent(spot -> {
            held.add(spot.getChunk());
            witchSpot = spot;
            witch = spot.getWorld().spawn(spot, Witch.class, this::still);
            halloween.add(witch);
        });
        spots.get("halloween-bats").ifPresent(spot -> {
            held.add(spot.getChunk());
            batSpot = spot;
            for (int i = 0; i < config.bats(); i++) {
                var bat = spot.getWorld().spawn(spot, Bat.class, spawned -> {
                    still(spawned);
                    spawned.setAwake(true);
                });
                bats.add(bat);
                halloween.add(bat);
            }
        });
        ghostPath.clear();
        IntStream.rangeClosed(1, config.ghostSpots()).mapToObj(n -> spots.get("halloween-ghost-" + n))
                .flatMap(Optional::stream).forEach(ghostPath::add);
        if (pathLength() > 0) {
            ghostPath.forEach(point -> held.add(point.getChunk()));
            ghost = ghostPath.getFirst().getWorld().spawn(ghostPath.getFirst(), Vex.class, spawned -> {
                still(spawned);
                spawned.getEquipment().clear();
            });
            halloween.add(ghost);
        }
    }

    private void still(Mob mob) {
        mob.setAI(false);
        mob.setInvulnerable(true);
        mob.setSilent(true);
        mob.setPersistent(false);
        mob.setRemoveWhenFarAway(false);
        mob.setCollidable(false);
    }

    private void step() {
        ticks += STEP_TICKS;
        if (!teams.isEmpty() && ticks % config.throwTicks() < STEP_TICKS) {
            volley();
        }
        if (witch != null && witch.isValid()) {
            stir();
        }
        if (batSpot != null) {
            circle();
        }
        if (ghost != null && ghost.isValid()) {
            drift();
        }
    }

    private void volley() {
        var random = ThreadLocalRandom.current();
        int side = random.nextInt(2);
        var throwers = teams.get(side).figures();
        var targets = teams.get(1 - side).figures();
        if (throwers.isEmpty() || targets.isEmpty()) {
            return;
        }
        var thrower = throwers.get(random.nextInt(throwers.size()));
        var target = targets.get(random.nextInt(targets.size()));
        if (!answering.contains(thrower.getUniqueId())) {
            pelt(thrower, target.getLocation().add(0, 1.4, 0));
        }
    }

    /** The figure stands up, throws a snowball in an arc that lands on {@code target}, and ducks back behind its wall. */
    private void pelt(Mannequin thrower, Location target) {
        if (!thrower.isValid()) {
            return;
        }
        thrower.setPose(Pose.STANDING, true);
        var from = thrower.getEyeLocation();
        var gap = target.toVector().subtract(from.toVector());
        double flight = Math.max(6, gap.length() / THROW_SPEED);
        var velocity = gap.multiply(1 / flight).add(new Vector(0, SNOWBALL_GRAVITY * flight / 2, 0));
        thrower.setRotation((float) Math.toDegrees(Math.atan2(-gap.getX(), gap.getZ())), 0);
        thrower.swingMainHand();
        thrower.launchProjectile(Snowball.class, velocity,
                snowball -> snowball.getPersistentDataContainer().set(SCENE_SNOWBALL, PersistentDataType.BOOLEAN, true));
        Bukkit.getScheduler().runTaskLater(plugin, () -> crouch(thrower), STAND_TICKS);
    }

    private static void crouch(Mannequin figure) {
        if (figure.isValid() && Mannequin.validPoses().contains(Pose.SNEAKING)) {
            figure.setPose(Pose.SNEAKING, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHit(ProjectileHitEvent event) {
        var projectile = event.getEntity();
        if (projectile.getPersistentDataContainer().has(SCENE_SNOWBALL)) {
            event.setCancelled(true);
            var at = projectile.getLocation();
            at.getWorld().spawnParticle(Particle.ITEM, at, 10, 0.15, 0.15, 0.15, 0.05, SNOW);
            at.getWorld().playSound(Sound.sound(Key.key("block.snow.break"), Sound.Source.NEUTRAL, 0.7f, 1.3f), at.getX(), at.getY(), at.getZ());
            projectile.remove();
            return;
        }
        if (event.getHitEntity() instanceof Mannequin figure && projectile.getShooter() instanceof Player player) {
            answer(figure, player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPunch(PrePlayerAttackEntityEvent event) {
        if (event.getAttacked() instanceof Mannequin figure && answer(figure, event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** A snowball fighter that was hit throws back at whoever hit it; false if {@code figure} is no fighter. */
    private boolean answer(Mannequin figure, Player player) {
        boolean fighter = teams.stream().anyMatch(team -> team.figures().contains(figure));
        if (fighter && answering.add(figure.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                answering.remove(figure.getUniqueId());
                if (player.isOnline() && player.getWorld().equals(figure.getWorld())) {
                    pelt(figure, player.getLocation().add(0, 1.2, 0));
                }
            }, config.answerTicks());
        }
        return fighter;
    }

    private void stir() {
        double sway = Math.sin(ticks / 12.0) * 25;
        witch.setRotation((float) (witchSpot.getYaw() + sway), 35);
        var cauldron = witchSpot.clone().add(witchSpot.getDirection().setY(0).normalize()).add(0, 0.3, 0);
        effects(cauldron, player -> {
            player.spawnParticle(Particle.WITCH, cauldron, 2, 0.25, 0.1, 0.25, 0);
            player.spawnParticle(Particle.BUBBLE_POP, cauldron, 2, 0.25, 0.05, 0.25, 0.02);
        });
        if (ticks % 140 < STEP_TICKS) {
            var random = ThreadLocalRandom.current();
            var sound = random.nextBoolean() ? "entity.witch.celebrate" : "block.brewing_stand.brew";
            cauldron.getWorld().playSound(Sound.sound(Key.key(sound), Sound.Source.HOSTILE, 0.8f, random.nextFloat(0.8f, 1.1f)),
                    cauldron.getX(), cauldron.getY(), cauldron.getZ());
        }
    }

    private void circle() {
        double lap = config.batLapSeconds() * 20;
        for (int i = 0; i < bats.size(); i++) {
            var bat = bats.get(i);
            if (!bat.isValid()) {
                continue;
            }
            double angle = Math.PI * 2 * (ticks / lap + (double) i / bats.size());
            double radius = config.batRadius() * (0.8 + 0.2 * Math.sin(angle * 3 + i));
            var at = batSpot.clone().add(Math.cos(angle) * radius, Math.sin(angle * 2 + i) * 0.8, Math.sin(angle) * radius);
            at.setYaw((float) Math.toDegrees(angle) + 180);
            bat.teleport(at);
        }
    }

    private void drift() {
        ghostDistance += config.ghostSpeed() * STEP_TICKS;
        double along = ghostDistance % pathLength();
        for (int i = 0; i < ghostPath.size(); i++) {
            var from = ghostPath.get(i);
            var to = ghostPath.get((i + 1) % ghostPath.size());
            double leg = from.distance(to);
            if (along <= leg && leg > 0) {
                var heading = to.toVector().subtract(from.toVector());
                var at = from.clone().add(heading.clone().multiply(along / leg)).add(0, Math.sin(ticks / 10.0) * 0.3, 0);
                at.setDirection(heading);
                at.setPitch(0);
                ghost.teleport(at);
                effects(at, player -> player.spawnParticle(Particle.WHITE_ASH, at.clone().add(0, 0.4, 0), 3, 0.2, 0.2, 0.2, 0));
                return;
            }
            along -= leg;
        }
    }

    /** The ghost's round from its first spot through the others and back. */
    private double pathLength() {
        double total = 0;
        for (int i = 0; i < ghostPath.size(); i++) {
            total += ghostPath.get(i).distance(ghostPath.get((i + 1) % ghostPath.size()));
        }
        return total;
    }

    /** Particles near {@code at} go only to players close by who have effects on. */
    private static void effects(Location at, Consumer<Player> show) {
        var settings = BlockcraftApi.get().settings();
        for (var player : at.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(at) <= EFFECT_RANGE * EFFECT_RANGE && settings.enabled(player, PlayerSetting.EFFECTS)) {
                show.accept(player);
            }
        }
    }

    private void clearWinter() {
        winter.forEach(Entity::remove);
        winter.clear();
        teams.clear();
        answering.clear();
        releaseIfIdle();
    }

    private void clearHalloween() {
        halloween.forEach(Entity::remove);
        halloween.clear();
        bats.clear();
        ghostPath.clear();
        witch = null;
        ghost = null;
        witchSpot = null;
        batSpot = null;
        releaseIfIdle();
    }

    private void releaseIfIdle() {
        if (winter.isEmpty() && halloween.isEmpty()) {
            held.clear();
        }
    }

    @Override
    public void close() {
        check.cancel();
        step.cancel();
        clearWinter();
        clearHalloween();
    }
}
