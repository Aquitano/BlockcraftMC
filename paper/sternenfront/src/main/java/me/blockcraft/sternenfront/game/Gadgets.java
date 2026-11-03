package me.blockcraft.sternenfront.game;

import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.world.BlocksChangedEvent;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.entity.Display;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What the abilities leave in the world: grenades on their fuse, shield walls until they fall, med stations until
 * they run dry, and the trail of a jump pack. Everything here goes away at the end of the round, and a shield wall
 * gives its blocks back.
 */
public final class Gadgets {

    private static final int BEEP_TICKS = 10;
    private static final double GRENADE_LIFT = 0.15;
    private static final double EDGE_SHARE = 1.0 / 3;
    private static final double PUSH = 0.5;
    private static final double PUSH_UP = 0.3;
    private static final int TRAIL_TICKS = 15;
    private static final long FALL_SAFE_TICKS = 100;
    private static final double STATION_HEIGHT = 0.5;
    private static final double GROUND_PROBE = 0.05;
    private static final int TICKS_PER_SECOND = 20;
    private static final Display.Brightness LIT = new Display.Brightness(15, 15);

    private record Grenade(Player thrower, Faction faction, Item item, long thrownAt, long explodesAt) { }

    private record Wall(Material material, List<Block> blocks, long fallsAt) { }

    private record Station(UUID owner, Faction faction, ItemDisplay display, long endsAt) { }

    private record Trail(Player player, long until) { }

    private final SternenfrontGame game;
    private final List<Grenade> grenades = new ArrayList<>();
    private final List<Wall> walls = new ArrayList<>();
    private final List<Station> stations = new ArrayList<>();
    private final List<Trail> trails = new ArrayList<>();

    public Gadgets(SternenfrontGame game) {
        this.game = game;
    }

    /** Whether the ability took effect; one that found no room, such as a wall against a wall, costs no cooldown. */
    public boolean use(Player player, Fighter fighter, Faction faction, Ability ability, long tick) {
        return switch (ability) {
            case GRENADE -> throwGrenade(player, faction, tick);
            case ADRENALINE -> adrenaline(player);
            case SHIELD_WALL -> raiseWall(player, faction, tick);
            case JUMP_PACK -> leap(player, fighter, game.config().jumpPack().forward(), game.config().jumpPack().up(), tick, SoundCue.JETPACK);
            case MED_STATION -> placeStation(player, faction, tick);
        };
    }

    /** A leap forward and up that spares the next landing, as the jump pack and a blade both give. */
    public boolean leap(Player player, Fighter fighter, double forward, double up, long tick, SoundCue sound) {
        var facing = player.getLocation().getDirection().setY(0);
        if (facing.lengthSquared() > 0) {
            facing.normalize().multiply(forward);
        }
        player.setVelocity(facing.setY(up));
        fighter.fallSafeUntil(tick + FALL_SAFE_TICKS);
        trails.add(new Trail(player, tick + TRAIL_TICKS));
        sound.play(player.getLocation(), 1f);
        return true;
    }

    private boolean throwGrenade(Player player, Faction faction, long tick) {
        var eye = player.getEyeLocation();
        var stack = Ability.GRENADE.model().item().build();
        stack.editMeta(meta -> meta.setMaxStackSize(1));
        var item = player.getWorld().dropItem(eye, stack, dropped -> {
            dropped.setPersistent(false);
            dropped.setCanPlayerPickup(false);
            dropped.setCanMobPickup(false);
            dropped.setWillAge(false);
            dropped.setVelocity(eye.getDirection().multiply(game.config().grenade().throwSpeed()).add(new Vector(0, GRENADE_LIFT, 0)));
        });
        grenades.add(new Grenade(player, faction, item, tick, tick + game.config().grenade().fuseTicks()));
        player.getWorld().playSound(eye, Sound.ENTITY_SNOWBALL_THROW, 1f, 0.6f);
        return true;
    }

    public boolean isGrenade(Item item) {
        return grenades.stream().anyMatch(grenade -> grenade.item().equals(item));
    }

    private boolean adrenaline(Player player) {
        int ticks = game.config().adrenalineSeconds() * TICKS_PER_SECOND;
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 1, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, ticks, 0, false, false, true));
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0);
        Sounds.HEAL.play(player);
        return true;
    }

    private boolean raiseWall(Player player, Faction faction, long tick) {
        var shield = game.config().shield();
        var facing = player.getLocation().getDirection();
        boolean alongX = Math.abs(facing.getX()) > Math.abs(facing.getZ());
        var ahead = alongX ? new Vector(Math.signum(facing.getX()), 0, 0) : new Vector(0, 0, Math.signum(facing.getZ()));
        var across = alongX ? BlockFace.SOUTH : BlockFace.EAST;
        var center = player.getLocation().getBlock().getRelative(ahead.getBlockX() * shield.distance(), 0, ahead.getBlockZ() * shield.distance());
        var material = Material.valueOf(faction.blocks().name() + "_STAINED_GLASS_PANE");
        var placed = new ArrayList<Block>();
        int half = shield.width() / 2;
        for (int offset = -half; offset <= shield.width() - 1 - half; offset++) {
            for (int height = 0; height < shield.height(); height++) {
                var block = center.getRelative(across, offset).getRelative(BlockFace.UP, height);
                if (!block.getType().isAir()) {
                    continue;
                }
                var data = (MultipleFacing) material.createBlockData();
                data.setFace(across, offset < shield.width() - 1 - half);
                data.setFace(across.getOppositeFace(), offset > -half);
                block.setBlockData(data, false);
                placed.add(block);
            }
        }
        if (placed.isEmpty()) {
            return false;
        }
        walls.add(new Wall(material, List.copyOf(placed), tick + (long) shield.seconds() * TICKS_PER_SECOND));
        Bukkit.getPluginManager().callEvent(new BlocksChangedEvent(List.copyOf(placed)));
        SoundCue.SHIELD_UP.play(center.getLocation(), 1f);
        return true;
    }

    private boolean placeStation(Player player, Faction faction, long tick) {
        if (!player.getLocation().subtract(0, GROUND_PROBE, 0).getBlock().isSolid()) {
            return false;
        }
        stations.removeIf(station -> {
            boolean own = station.owner().equals(player.getUniqueId());
            if (own) {
                station.display().remove();
            }
            return own;
        });
        var at = player.getLocation().getBlock().getLocation().add(0.5, STATION_HEIGHT, 0.5);
        at.setYaw(player.getLocation().getYaw() + 180);
        var display = player.getWorld().spawn(at, ItemDisplay.class, spawned -> {
            spawned.setPersistent(false);
            spawned.setItemStack(Ability.MED_STATION.model().item().build());
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            spawned.setBrightness(LIT);
        });
        stations.add(new Station(player.getUniqueId(), faction, display,
                tick + (long) game.config().medStation().seconds() * TICKS_PER_SECOND));
        player.getWorld().playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.4f);
        return true;
    }

    public void tick(long tick) {
        grenades.removeIf(grenade -> fuse(grenade, tick));
        walls.removeIf(wall -> {
            if (tick < wall.fallsAt()) {
                return false;
            }
            fall(wall);
            return true;
        });
        stations.removeIf(station -> run(station, tick));
        trails.removeIf(trail -> {
            if (!trail.player().isOnline() || tick >= trail.until()) {
                return true;
            }
            var feet = trail.player().getLocation();
            feet.getWorld().spawnParticle(Particle.FLAME, feet, 2, 0.1, 0, 0.1, 0.01);
            feet.getWorld().spawnParticle(Particle.CLOUD, feet, 1, 0.1, 0, 0.1, 0.01);
            return false;
        });
    }

    /** True once the grenade went off, or its item vanished. */
    private boolean fuse(Grenade grenade, long tick) {
        var item = grenade.item();
        if (!item.isValid()) {
            return true;
        }
        var at = item.getLocation();
        at.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, 0.2, 0), 1, 0, 0, 0, 0,
                new Particle.DustOptions(grenade.faction().bolt(), 0.8f));
        if (tick < grenade.explodesAt()) {
            long since = tick - grenade.thrownAt();
            if (since % BEEP_TICKS == 0) {
                float rising = 1.0f + (float) since / game.config().grenade().fuseTicks();
                SoundCue.GRENADE_BEEP.play(at, 0.8f, rising);
            }
            return false;
        }
        explode(grenade, at.add(0, 0.3, 0));
        item.remove();
        return true;
    }

    private void explode(Grenade grenade, Location at) {
        var world = at.getWorld();
        var stats = game.config().grenade();
        world.spawnParticle(Particle.EXPLOSION, at, 1);
        world.spawnParticle(Particle.ELECTRIC_SPARK, at, 40, 1.2, 0.8, 1.2, 0.2);
        world.spawnParticle(Particle.DUST, at, 30, 1.5, 0.8, 1.5, 0, new Particle.DustOptions(grenade.faction().bolt(), 1.6f));
        SoundCue.GRENADE_EXPLODE.play(at, 2f);
        for (var target : game.targets()) {
            if (!target.getWorld().equals(world)) {
                continue;
            }
            var center = target.getLocation().add(0, target.getHeight() / 2, 0);
            double distance = center.distance(at);
            boolean self = target.equals(grenade.thrower()) && game.onField(target);
            if (distance > stats.radius() || !self && !game.hittable(grenade.faction(), target) || !inSight(at, center)) {
                continue;
            }
            double share = 1 - (1 - EDGE_SHARE) * distance / stats.radius();
            game.blast(grenade.thrower(), target, stats.damage() * share, at);
            var away = center.toVector().subtract(at.toVector());
            if (away.lengthSquared() > 0) {
                target.setVelocity(target.getVelocity().add(away.normalize().multiply(PUSH).setY(PUSH_UP)));
            }
        }
    }

    private static boolean inSight(Location from, Location to) {
        var between = to.toVector().subtract(from.toVector());
        double distance = between.length();
        return distance == 0 || from.getWorld().rayTraceBlocks(from, between.normalize(), distance, FluidCollisionMode.NEVER, true) == null;
    }

    private static void fall(Wall wall) {
        var fallen = wall.blocks().stream().filter(block -> block.getType() == wall.material()).toList();
        fallen.forEach(block -> block.setType(Material.AIR, false));
        if (!fallen.isEmpty()) {
            Bukkit.getPluginManager().callEvent(new BlocksChangedEvent(fallen));
            var first = fallen.getFirst().getLocation();
            SoundCue.SHIELD_DOWN.play(first, 1f);
        }
    }

    /** Heals every second while it lasts; true once it ran dry. */
    private boolean run(Station station, long tick) {
        var display = station.display();
        if (tick >= station.endsAt() || !display.isValid()) {
            display.remove();
            return true;
        }
        var at = display.getLocation();
        if (tick % (TICKS_PER_SECOND / 2) == 0) {
            double radius = game.config().medStation().radius();
            for (int step = 0; step < 12; step++) {
                double angle = Math.PI * 2 * step / 12;
                at.getWorld().spawnParticle(Particle.DUST, at.clone().add(Math.cos(angle) * radius, -0.4, Math.sin(angle) * radius),
                        1, 0, 0, 0, 0, new Particle.DustOptions(station.faction().bolt(), 0.9f));
            }
        }
        if (tick % TICKS_PER_SECOND == 0) {
            double radius = game.config().medStation().radius();
            var owner = Bukkit.getPlayer(station.owner());
            for (var ally : game.combatants()) {
                if (ally.getWorld().equals(at.getWorld()) && ally.getLocation().distanceSquared(at) <= radius * radius
                        && game.onField(ally) && game.factionOf(ally).filter(station.faction()::equals).isPresent()
                        && (owner == null ? game.heal(ally, game.config().medStation().healPerSecond())
                        : game.heal(owner, ally, game.config().medStation().healPerSecond()))) {
                    ally.getWorld().spawnParticle(Particle.HEART, ally.getLocation().add(0, 2.1, 0), 1);
                }
            }
        }
        return false;
    }

    public void clear() {
        grenades.forEach(grenade -> grenade.item().remove());
        grenades.clear();
        walls.forEach(Gadgets::fall);
        walls.clear();
        stations.forEach(station -> station.display().remove());
        stations.clear();
        trails.clear();
    }
}
