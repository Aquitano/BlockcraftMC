package me.blockcraft.sternenfront.hero;

import me.blockcraft.sternenfront.appearance.Displays;
import me.blockcraft.sternenfront.game.Blast;
import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.game.SoundCue;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

final class WristRockets {

    private static final double HIT_SIZE = 0.15;
    private static final double WALL_CLEARANCE = 0.05;

    private static final class Rocket {
        private final Player owner;
        private final Faction faction;
        private final Vector direction;
        private final ItemDisplay display;
        private Location position;
        private double left;

        private Rocket(Player owner, Faction faction, Location position, Vector direction, ItemDisplay display, double range) {
            this.owner = owner;
            this.faction = faction;
            this.position = position;
            this.direction = direction;
            this.display = display;
            left = range;
        }
    }

    private final SternenfrontGame game;
    private final HeroConfig.Rocket rules;
    private final List<Rocket> rockets = new ArrayList<>();

    WristRockets(SternenfrontGame game, HeroConfig.Rocket rules) {
        this.game = game;
        this.rules = rules;
    }

    boolean fire(Player player, Faction faction) {
        var direction = player.getEyeLocation().getDirection();
        var from = player.getEyeLocation();
        var muzzle = game.effects().muzzle(player, direction);
        var display = Displays.item(muzzle.clone().setDirection(direction), Displays.model("sternenfront/fx/wrist_rocket"), spawned -> {
            spawned.setBrightness(Displays.LIT);
            spawned.setTeleportDuration(1);
            spawned.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1),
                    new Quaternionf().rotationY((float) Math.PI)));
        });
        rockets.add(new Rocket(player, faction, from, direction, display, rules.range()));
        SoundCue.WRIST_ROCKET_LAUNCH.play(muzzle, 1.2f);
        return true;
    }

    void tick() {
        var flying = List.copyOf(rockets);
        rockets.clear();
        for (var rocket : flying) {
            if (step(rocket)) {
                rockets.add(rocket);
            } else {
                rocket.display.remove();
            }
        }
    }

    private boolean step(Rocket rocket) {
        if (!rocket.display.isValid()) {
            return false;
        }
        var world = rocket.position.getWorld();
        double distance = Math.min(rules.speed(), rocket.left);
        var hit = world.rayTrace(rocket.position, rocket.direction, distance, FluidCollisionMode.NEVER, true, HIT_SIZE,
                entity -> entity instanceof LivingEntity target && game.hittable(rocket.faction, target));
        var next = hit == null ? rocket.position.clone().add(rocket.direction.clone().multiply(distance))
                : hit.getHitPosition().toLocation(world);
        game.effects().rocketTrail(rocket.position, next, rocket.owner);
        if (hit != null) {
            if (hit.getHitBlockFace() != null) {
                next.add(hit.getHitBlockFace().getDirection().multiply(WALL_CLEARANCE));
            }
            detonate(rocket, next);
            return false;
        }
        rocket.left -= distance;
        if (rocket.left <= 0) {
            detonate(rocket, next);
            return false;
        }
        rocket.position = next;
        rocket.display.teleport(next.clone().setDirection(rocket.direction));
        return true;
    }

    private void detonate(Rocket rocket, Location at) {
        game.effects().explosion(at, rocket.faction, rules.radius());
        SoundCue.WRIST_ROCKET_IMPACT.play(at, 2f);
        game.blastAround(at, new Blast(rocket.owner, rocket.faction, rules.radius(), rules.damage(), true), (target, away) -> { });
    }

    void clear() {
        rockets.forEach(rocket -> rocket.display.remove());
        rockets.clear();
    }
}
