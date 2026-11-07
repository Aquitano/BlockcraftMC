package me.blockcraft.sternenfront.hero;

import me.blockcraft.sternenfront.combat.Guard;
import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.game.LineOfSight;
import me.blockcraft.sternenfront.game.SoundCue;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import org.bukkit.FluidCollisionMode;
import org.bukkit.World;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ForceLightning {

    private record Cast(Player player, Faction faction, World world, LightningBurst burst) { }

    private final SternenfrontGame game;
    private final HeroConfig.Lightning rules;
    private final Map<UUID, Cast> casts = new HashMap<>();

    ForceLightning(SternenfrontGame game, HeroConfig.Lightning rules) {
        this.game = game;
        this.rules = rules;
    }

    boolean use(Player player, Faction faction, long tick) {
        var cast = new Cast(player, faction, player.getWorld(), new LightningBurst(rules, tick));
        casts.put(player.getUniqueId(), cast);
        if (pulse(cast, tick)) {
            casts.remove(player.getUniqueId(), cast);
        }
        return true;
    }

    void tick(long tick) {
        for (var cast : List.copyOf(casts.values())) {
            var id = cast.player().getUniqueId();
            if (casts.get(id) == cast && pulse(cast, tick)) {
                casts.remove(id, cast);
            }
        }
    }

    private boolean pulse(Cast cast, long tick) {
        var player = cast.player();
        if (!cast.burst().active(tick) || !game.onField(player) || player.isInsideVehicle()
                || !player.getWorld().equals(cast.world()) || game.heroOf(player).filter(Hero.SITH::equals).isEmpty()) {
            return true;
        }
        if (!cast.burst().pulse(tick)) {
            return false;
        }
        var eye = player.getEyeLocation();
        var facing = eye.getDirection();
        var targets = game.targets().stream()
                .filter(target -> target.getWorld().equals(cast.world()) && game.hittable(cast.faction(), target)
                        && !game.isProtected(target))
                .filter(target -> inCone(eye.toVector(), facing, target.getBoundingBox()))
                .filter(target -> LineOfSight.clear(eye, nearest(eye.toVector(), target.getBoundingBox()).subtract(eye.toVector())))
                .sorted(Comparator.comparingDouble(target -> target.getLocation().distanceSquared(eye)))
                .limit(rules.targets()).toList();
        for (var target : targets) {
            if (casts.get(player.getUniqueId()) != cast || !game.onField(player)) {
                return true;
            }
            if (!game.hittable(cast.faction(), target) || game.isProtected(target)) {
                continue;
            }
            var at = target.getLocation().add(0, target.getHeight() * 0.6, 0);
            game.effects().lightning(player, eye, at);
            var incoming = at.toVector().subtract(eye.toVector());
            incoming = incoming.lengthSquared() < 0.001 ? facing.clone() : incoming.normalize();
            var guard = target instanceof Player defender ? game.combat().block(defender, incoming, cast.burst().damage())
                    : Guard.Result.NONE;
            if (guard == Guard.Result.NONE || guard == Guard.Result.BROKEN) {
                game.strike(player, target, cast.burst().damage(), DamageType.MAGIC);
            }
        }
        if (targets.isEmpty()) {
            var wall = cast.world().rayTraceBlocks(eye, facing, rules.range(), FluidCollisionMode.NEVER, true);
            var end = wall == null ? eye.clone().add(facing.multiply(rules.range())) : wall.getHitPosition().toLocation(cast.world());
            game.effects().lightning(player, eye, end);
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

    void forget(Player player) {
        casts.remove(player.getUniqueId());
    }

    void clear() {
        casts.clear();
    }
}
