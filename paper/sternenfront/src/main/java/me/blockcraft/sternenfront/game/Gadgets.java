package me.blockcraft.sternenfront.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.presentation.ScreenEffects;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.world.BlocksChangedEvent;
import me.blockcraft.sternenfront.appearance.Displays;
import me.blockcraft.sternenfront.appearance.Hologram;
import me.blockcraft.sternenfront.config.SternenfrontConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What the abilities leave in the world: grenades and mining charges on their fuse, shield walls until they fall,
 * med stations until they run dry, units anchored in siege mode, and the trail of a jump pack. Everything here goes
 * away at the end of the round, and a shield wall gives its blocks back.
 */
public final class Gadgets {

    private static final int BEEP_TICKS = 10;
    private static final double GRENADE_LIFT = 0.15;
    private static final double PUSH = 0.5;
    private static final double PUSH_UP = 0.3;
    private static final int TRAIL_TICKS = 15;
    private static final long FALL_SAFE_TICKS = 100;
    private static final double STATION_HEIGHT = 0.5;
    private static final int STATION_PULSE_TICKS = 18;
    private static final double GROUND_PROBE = 0.05;
    private static final int TICKS_PER_SECOND = 20;
    private static final NamespacedKey SIEGE_SPEED = new NamespacedKey("sternenfront", "siege_speed");
    private static final NamespacedKey SIEGE_JUMP = new NamespacedKey("sternenfront", "siege_jump");
    private static final double SIEGE_RING_RADIUS = 1.2;
    private static final float SIEGE_RELEASE_SHIFT = 1.3f;
    private static final float CHARGE_BLAST_SHIFT = 0.7f;

    private record Grenade(LivingEntity thrower, Faction faction, Ability kind, SternenfrontConfig.Grenade stats,
                           double damageScale, Item item, long thrownAt) {

        long explodesAt() {
            return thrownAt + stats.fuseTicks();
        }
    }

    private record Siege(LivingEntity unit, Faction faction, long until) { }

    private record Wall(Material material, List<Block> blocks, long fallsAt, Faction faction, Vector normal, Hologram hologram) { }

    private record Station(UUID owner, Faction faction, ItemDisplay display, long endsAt) { }

    private record Trail(Player player, long until) { }

    private final SternenfrontGame game;
    private final List<Grenade> grenades = new ArrayList<>();
    private final List<Wall> walls = new ArrayList<>();
    private final List<Station> stations = new ArrayList<>();
    private final List<Trail> trails = new ArrayList<>();
    private final List<Siege> sieges = new ArrayList<>();

    public Gadgets(SternenfrontGame game) {
        this.game = game;
    }

    /** Whether the ability took effect; one that found no room, such as a wall against a wall, costs no cooldown. */
    public boolean use(Player player, Fighter fighter, Faction faction, Ability ability, long tick) {
        return switch (ability) {
            case GRENADE -> throwExplosive(player, faction, Ability.GRENADE, game.config().grenade(),
                    player.getEyeLocation().getDirection(), 1, tick);
            case MINING_CHARGE -> throwCharge(player, faction, player.getEyeLocation().getDirection(), 1, tick);
            case SIEGE_MODE -> siege(player, faction, tick);
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

    /** A bot's own throw passes its aim and its difficulty's damage; a player's is thrown where they look, in full. */
    public boolean throwCharge(LivingEntity thrower, Faction faction, Vector direction, double damageScale, long tick) {
        boolean thrown = throwExplosive(thrower, faction, Ability.MINING_CHARGE, game.config().miningCharge(), direction, damageScale, tick);
        SoundCue.CHARGE_ARM.play(thrower.getEyeLocation(), 1f);
        return thrown;
    }

    private boolean throwExplosive(LivingEntity thrower, Faction faction, Ability kind, SternenfrontConfig.Grenade stats,
                                   Vector direction, double damageScale, long tick) {
        var eye = thrower.getEyeLocation();
        var stack = kind.model().item().build();
        stack.editMeta(meta -> meta.setMaxStackSize(1));
        var item = thrower.getWorld().dropItem(eye, stack, dropped -> {
            dropped.setPersistent(false);
            dropped.setCanPlayerPickup(false);
            dropped.setCanMobPickup(false);
            dropped.setWillAge(false);
            dropped.setVelocity(direction.clone().multiply(stats.throwSpeed()).add(new Vector(0, GRENADE_LIFT, 0)));
        });
        grenades.add(new Grenade(thrower, faction, kind, stats, damageScale, item, tick));
        thrower.getWorld().playSound(eye, Sound.ENTITY_SNOWBALL_THROW, 1f, 0.6f);
        return true;
    }

    /** Anchors the unit: it cannot walk or jump until the time is up, and its gun fires as {@code siege-mode} says. */
    public boolean siege(LivingEntity unit, Faction faction, long tick) {
        if (!grounded(unit) || sieging(unit)) {
            return false;
        }
        int ticks = game.config().siegeMode().seconds() * TICKS_PER_SECOND;
        root(unit);
        sieges.add(new Siege(unit, faction, tick + ticks));
        SoundCue.SIEGE_LOCK.play(unit.getLocation(), 1f);
        if (unit instanceof Player player && game.plugin().getConfig().getBoolean("presentation.effects", true)) {
            int effectTicks = Math.clamp(game.plugin().getConfig().getInt("presentation.gameplay.siege-mode-ticks", 20), 0, 200);
            if (effectTicks > 0) BlockcraftApi.get().visuals().accent(player, ScreenEffects.Atmosphere.HOLOGRAM, effectTicks);
        }
        return true;
    }

    public boolean sieging(LivingEntity unit) {
        return sieges.stream().anyMatch(siege -> siege.unit().equals(unit));
    }

    /** Ends a siege early, for a unit that dies, leaves or becomes a hero. */
    public void release(LivingEntity unit) {
        sieges.removeIf(siege -> {
            boolean own = siege.unit().equals(unit);
            if (own) {
                lift(unit);
            }
            return own;
        });
    }

    /** Modifiers rather than a slowness effect, which a force grip on the same unit would add to and take away. */
    private static void root(LivingEntity unit) {
        AttributeScale.apply(unit, Attribute.MOVEMENT_SPEED, SIEGE_SPEED, -1);
        AttributeScale.apply(unit, Attribute.JUMP_STRENGTH, SIEGE_JUMP, -1);
    }

    private static void lift(LivingEntity unit) {
        AttributeScale.remove(unit, Attribute.MOVEMENT_SPEED, SIEGE_SPEED);
        AttributeScale.remove(unit, Attribute.JUMP_STRENGTH, SIEGE_JUMP);
    }

    private static boolean grounded(LivingEntity unit) {
        return unit.getLocation().subtract(0, GROUND_PROBE, 0).getBlock().isSolid();
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
        Hologram hologram = null;
        if (game.plugin().getConfig().getBoolean("presentation.effects", true)) {
            hologram = new Hologram(game.plugin(), center.getLocation());
            for (var block : placed) {
                hologram.part("grid_" + faction.key(), block.getLocation().add(.5, .5, .5).setDirection(ahead));
            }
            hologram.refresh();
        }
        walls.add(new Wall(material, List.copyOf(placed), tick + (long) shield.seconds() * TICKS_PER_SECOND,
                faction, ahead, hologram));
        Bukkit.getPluginManager().callEvent(new BlocksChangedEvent(List.copyOf(placed)));
        SoundCue.SHIELD_UP.play(center.getLocation(), 1f);
        return true;
    }

    private boolean placeStation(Player player, Faction faction, long tick) {
        if (!grounded(player)) {
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
        var display = Displays.item(at, Ability.MED_STATION.model().item().build(), spawned -> spawned.setBrightness(Displays.LIT));
        stations.add(new Station(player.getUniqueId(), faction, display,
                tick + (long) game.config().medStation().seconds() * TICKS_PER_SECOND));
        player.getWorld().playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.4f);
        return true;
    }

    public void tick(long tick) {
        grenades.removeIf(grenade -> fuse(grenade, tick));
        walls.removeIf(wall -> {
            if (tick < wall.fallsAt()) {
                if (wall.hologram() != null && tick % 10 == 0) {
                    wall.hologram().refresh();
                }
                return false;
            }
            fall(wall);
            return true;
        });
        stations.removeIf(station -> run(station, tick));
        sieges.removeIf(siege -> hold(siege, tick));
        trails.removeIf(trail -> {
            if (!trail.player().isOnline() || tick >= trail.until()) {
                return true;
            }
            game.effects().exhaust(trail.player());
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
                new Particle.DustOptions(grenade.faction().bolt(), 1.3f), true);
        if (tick < grenade.explodesAt()) {
            long since = tick - grenade.thrownAt();
            if (since % BEEP_TICKS == 0) {
                float rising = 1.0f + (float) since / grenade.stats().fuseTicks();
                SoundCue.GRENADE_BEEP.play(at, 0.8f, rising);
            }
            return false;
        }
        explode(grenade, at.add(0, 0.3, 0));
        item.remove();
        return true;
    }

    private void explode(Grenade grenade, Location at) {
        var stats = grenade.stats();
        game.effects().explosion(at, grenade.faction(), stats.radius());
        SoundCue.GRENADE_EXPLODE.play(at, 2f, grenade.kind() == Ability.MINING_CHARGE ? CHARGE_BLAST_SHIFT : 1f);
        game.blastAround(at, new Blast(grenade.thrower(), grenade.faction(), stats.radius(), stats.damage() * grenade.damageScale(), true),
                (target, away) -> {
                    if (away.lengthSquared() > 0) {
                        target.setVelocity(target.getVelocity().add(away.normalize().multiply(PUSH).setY(PUSH_UP)));
                    }
                });
    }

    /** Whether the struck block belongs to a shield wall, which then shows the ripple. */
    public boolean impact(Block block, Location at, Vector incoming) {
        var struck = walls.stream().filter(wall -> wall.blocks().contains(block) && block.getType() == wall.material()).findFirst();
        struck.ifPresent(wall -> {
            var normal = wall.normal().clone();
            if (normal.dot(incoming) > 0) {
                normal.multiply(-1);
            }
            game.effects().impact(at, normal, wall.faction().bolt());
        });
        return struck.isPresent();
    }

    private static void fall(Wall wall) {
        if (wall.hologram() != null) {
            wall.hologram().close();
        }
        var fallen = wall.blocks().stream().filter(block -> block.getType() == wall.material()).toList();
        fallen.forEach(block -> block.setType(Material.AIR, false));
        if (!fallen.isEmpty()) {
            Bukkit.getPluginManager().callEvent(new BlocksChangedEvent(fallen));
            var first = fallen.getFirst().getLocation();
            SoundCue.SHIELD_DOWN.play(first, 1f);
        }
    }

    /** True once the siege is over: its time is up, the unit fell or it mounted a vehicle. */
    private boolean hold(Siege siege, long tick) {
        var unit = siege.unit();
        if (unit.isValid() && !unit.isInsideVehicle() && tick < siege.until()) {
            if (tick % (TICKS_PER_SECOND / 2) == 0) {
                var at = unit.getLocation();
                for (int step = 0; step < 12; step++) {
                    double angle = Math.PI * 2 * step / 12;
                    at.getWorld().spawnParticle(Particle.DUST, at.clone().add(Math.cos(angle) * SIEGE_RING_RADIUS, 0.1, Math.sin(angle) * SIEGE_RING_RADIUS),
                            1, 0, 0, 0, 0, new Particle.DustOptions(siege.faction().bolt(), 0.9f));
                }
            }
            return false;
        }
        lift(unit);
        if (unit.isValid()) {
            SoundCue.SIEGE_LOCK.play(unit.getLocation(), 1f, SIEGE_RELEASE_SHIFT);
        }
        return true;
    }

    /** Heals every second while it lasts; true once it ran dry. */
    private boolean run(Station station, long tick) {
        var display = station.display();
        if (tick >= station.endsAt() || !display.isValid()) {
            display.remove();
            return true;
        }
        var at = display.getLocation();
        if (tick % TICKS_PER_SECOND == 0) {
            double radius = game.config().medStation().radius();
            game.effects().pulse(at, station.faction(), radius, STATION_PULSE_TICKS);
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
        sieges.forEach(siege -> lift(siege.unit()));
        sieges.clear();
    }
}
