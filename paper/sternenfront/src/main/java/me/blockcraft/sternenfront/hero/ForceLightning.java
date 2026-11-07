package me.blockcraft.sternenfront.hero;

import me.blockcraft.sternenfront.combat.BattleEffects;
import me.blockcraft.sternenfront.combat.Guard;
import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.game.LineOfSight;
import me.blockcraft.sternenfront.game.SoundCue;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import org.bukkit.FluidCollisionMode;
import org.bukkit.World;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ForceLightning {

    /** {@code struck} holds whom the last pulse reached, whom the bolts follow until the next. */
    private record Cast(Player player, Faction faction, World world, LightningBurst burst, List<LivingEntity> struck) { }

    /** A bolt keeps its shape this long before it jumps to a new one. */
    private static final int RESHAPE_TICKS = 2;

    private final SternenfrontGame game;
    private final HeroConfig.Lightning rules;
    private final Map<UUID, Cast> casts = new HashMap<>();

    ForceLightning(SternenfrontGame game, HeroConfig.Lightning rules) {
        this.game = game;
        this.rules = rules;
    }

    boolean use(Player player, Faction faction, long tick) {
        var cast = new Cast(player, faction, player.getWorld(), new LightningBurst(rules, tick), new ArrayList<>());
        casts.put(player.getUniqueId(), cast);
        if (channel(cast, tick)) {
            casts.remove(player.getUniqueId(), cast);
        }
        return true;
    }

    void tick(long tick) {
        for (var cast : List.copyOf(casts.values())) {
            var id = cast.player().getUniqueId();
            if (casts.get(id) == cast && channel(cast, tick)) {
                casts.remove(id, cast);
            }
        }
    }

    /** One tick of the channel: damage on its pulses, the bolts on every tick. True once it ended. */
    private boolean channel(Cast cast, long tick) {
        var player = cast.player();
        if (!cast.burst().active(tick) || !game.onField(player) || player.isInsideVehicle()
                || !player.getWorld().equals(cast.world()) || game.heroOf(player).filter(Hero.SITH::equals).isEmpty()) {
            return true;
        }
        if (cast.burst().pulse(tick) && pulse(cast)) {
            return true;
        }
        draw(cast, tick);
        return false;
    }

    private void draw(Cast cast, long tick) {
        var player = cast.player();
        var hand = BattleEffects.hand(player, true);
        var ends = cast.struck().stream().filter(target -> game.onField(target) && target.getWorld().equals(cast.world()))
                .map(target -> target.getLocation().add(0, target.getHeight() * 0.6, 0)).toList();
        if (ends.isEmpty()) {
            var eye = player.getEyeLocation();
            var facing = eye.getDirection();
            var wall = cast.world().rayTraceBlocks(eye, facing, rules.range(), FluidCollisionMode.NEVER, true);
            ends = List.of(wall == null ? eye.clone().add(facing.multiply(rules.range())) : wall.getHitPosition().toLocation(cast.world()));
        }
        long shape = player.getUniqueId().getLeastSignificantBits() * 31 + tick / RESHAPE_TICKS;
        for (int bolt = 0; bolt < ends.size(); bolt++) {
            game.effects().lightning(player, hand, ends.get(bolt), shape * 7 + bolt);
        }
        game.effects().crackle(player, hand);
    }

    /** Strikes what the cone holds now; true if the channel ended under it. */
    private boolean pulse(Cast cast) {
        var player = cast.player();
        var eye = player.getEyeLocation();
        var facing = eye.getDirection();
        var targets = game.targets().stream()
                .filter(target -> target.getWorld().equals(cast.world()) && game.hittable(cast.faction(), target)
                        && !game.isProtected(target))
                .filter(target -> inCone(eye.toVector(), facing, target.getBoundingBox()))
                .filter(target -> LineOfSight.clear(eye, nearest(eye.toVector(), target.getBoundingBox()).subtract(eye.toVector())))
                .sorted(Comparator.comparingDouble(target -> target.getLocation().distanceSquared(eye)))
                .limit(rules.targets()).toList();
        cast.struck().clear();
        for (var target : targets) {
            if (casts.get(player.getUniqueId()) != cast || !game.onField(player)) {
                return true;
            }
            if (!game.hittable(cast.faction(), target) || game.isProtected(target)) {
                continue;
            }
            cast.struck().add(target);
            var at = target.getLocation().add(0, target.getHeight() * 0.6, 0);
            var incoming = at.toVector().subtract(eye.toVector());
            incoming = incoming.lengthSquared() < 0.001 ? facing.clone() : incoming.normalize();
            var guard = target instanceof Player defender ? game.combat().block(defender, incoming, cast.burst().damage())
                    : Guard.Result.NONE;
            if (guard == Guard.Result.NONE || guard == Guard.Result.BROKEN) {
                game.strike(player, target, cast.burst().damage(), DamageType.MAGIC);
            }
        }
        SoundCue.FORCE_LIGHTNING.play(eye, 0.7f);
        return false;
    }

    private boolean inCone(Vector eye, Vector facing, BoundingBox target) {
        var toward = nearest(eye, target).subtract(eye);
        return toward.lengthSquared() <= rules.range() * rules.range() && (toward.lengthSquared() < 0.001
                || facing.dot(toward.normalize()) >= Math.cos(Math.toRadians(rules.angle() / 2)));
    }

    private static Vector nearest(Vector eye, BoundingBox target) {
        return new Vector(Math.clamp(eye.getX(), target.getMinX(), target.getMaxX()),
                Math.clamp(eye.getY(), target.getMinY(), target.getMaxY()),
                Math.clamp(eye.getZ(), target.getMinZ(), target.getMaxZ()));
    }

    boolean casting(Player player) {
        return casts.containsKey(player.getUniqueId());
    }

    void forget(Player player) {
        casts.remove(player.getUniqueId());
    }

    void clear() {
        casts.clear();
    }
}
