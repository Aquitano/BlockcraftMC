package me.blockcraft.zombies.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.pack.PackModel;
import me.blockcraft.zombies.config.ZombiesConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Something under the floor that punishes standing still. A fighter who stays near one spot feels the ground rumble,
 * and if they stay, its head breaks out there and bites whoever is still close. Fighters busy at a window, a revive or
 * the generator are left alone.
 */
final class Burrower {

    private static final int CHECK_TICKS = 5;
    private static final double BITE_REACH = 2.2;
    private static final float BURIED = -2.5f;
    private static final float LIFT = (float) Math.toRadians(-28);
    private static final float BITE = (float) Math.toRadians(8);
    private static final BlockData SAND = Material.SAND.createBlockData();

    private record Stand(Location at, int since) { }

    private final ZombiesGame game;
    private final ZombiesConfig.Burrower config;
    private final Map<UUID, Stand> standing = new HashMap<>();
    private final Set<UUID> warned = new HashSet<>();
    private final Set<UUID> bitten = new HashSet<>();
    private Optional<ItemDisplay> head = Optional.empty();
    private int erupted;
    private int quietUntil;

    Burrower(ZombiesGame game, ZombiesConfig.Burrower config) {
        this.game = game;
        this.config = config;
    }

    void tick(int tick, int wave) {
        head.ifPresent(shown -> animate(shown, tick - erupted));
        if (wave < config.fromWave() || tick % CHECK_TICKS != 0) {
            return;
        }
        var fighters = game.fighters();
        var ids = fighters.stream().map(Player::getUniqueId).collect(Collectors.toSet());
        standing.keySet().retainAll(ids);
        warned.retainAll(ids);
        for (var player : fighters) {
            var at = player.getLocation();
            var stand = standing.get(player.getUniqueId());
            if (stand == null || tick < quietUntil || game.busy(player) || !stand.at().getWorld().equals(at.getWorld())
                    || stand.at().distanceSquared(at) > config.radius() * config.radius()) {
                standing.put(player.getUniqueId(), new Stand(at, tick));
                warned.remove(player.getUniqueId());
                continue;
            }
            int stood = tick - stand.since();
            if (stood < config.rumbleTicks() || head.isPresent()) {
                continue;
            }
            if (stood >= config.strikeTicks()) {
                erupt(player, tick);
                return;
            }
            rumble(player, stood, tick);
        }
    }

    private void rumble(Player player, int stood, int tick) {
        if (warned.add(player.getUniqueId())) {
            BlockcraftApi.get().notifications().feedback(player, game.messages().get("burrower.rumble"));
        }
        boolean strong = (stood - config.rumbleTicks()) * 2 >= config.strikeTicks() - config.rumbleTicks();
        if (strong || tick % 20 == 0) {
            var at = player.getLocation();
            at.getWorld().playSound(at, Sound.ENTITY_WARDEN_DIG, SoundCategory.HOSTILE, strong ? 0.9f : 0.5f, 0.7f);
            at.getWorld().spawnParticle(Particle.BLOCK, at, strong ? 20 : 8, 1.2, 0.1, 1.2, 0, floor(at));
            if (strong) {
                player.sendHurtAnimation(ThreadLocalRandom.current().nextFloat() * 360);
            }
        }
    }

    private void erupt(Player player, int tick) {
        standing.clear();
        warned.clear();
        bitten.clear();
        erupted = tick;
        quietUntil = tick + config.restTicks();
        var at = player.getLocation().toBlockLocation().add(0.5, 0, 0.5);
        var away = player.getLocation().getDirection().setY(0);
        at.setDirection(away.lengthSquared() == 0 ? new Vector(0, 0, 1) : away);
        head = Optional.of(at.getWorld().spawn(at, ItemDisplay.class, shown -> {
            shown.setPersistent(false);
            shown.setItemStack(PackModel.STERNENFRONT_SAND_BEAST.item().build());
            shown.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            shown.setTransformation(pose(BURIED, 0));
        }));
        at.getWorld().playSound(at, Sound.ENTITY_WARDEN_EMERGE, SoundCategory.HOSTILE, 2, 1.2f);
        burst(at, 50);
        for (var fighter : game.fighters()) {
            if (fighter.getLocation().distanceSquared(at) <= BITE_REACH * BITE_REACH) {
                fighter.setVelocity(fighter.getVelocity().setY(0.45));
            }
        }
    }

    /** Ticks after the eruption: up out of the floor, a lift of the head and two bites, then down again. */
    private void animate(ItemDisplay shown, int age) {
        var at = shown.getLocation();
        if (age == 1) {
            move(shown, 6, pose(0, 0));
        } else if (age == 8 || age == 15) {
            move(shown, 3, pose(0, LIFT));
        } else if (age == 11 || age == 18) {
            move(shown, 2, pose(0, BITE));
            at.getWorld().playSound(at, Sound.ENTITY_EVOKER_FANGS_ATTACK, SoundCategory.HOSTILE, 1.5f, 0.5f);
            burst(at, 12);
            bite(at);
        } else if (age == 34) {
            move(shown, 20, pose(BURIED, 0));
            burst(at, 25);
        } else if (age >= 56) {
            remove();
        }
    }

    private void bite(Location at) {
        for (var fighter : game.fighters()) {
            if (fighter.getLocation().distanceSquared(at) <= BITE_REACH * BITE_REACH && bitten.add(fighter.getUniqueId())) {
                game.hurt(fighter, config.damage());
            }
        }
    }

    private static void move(ItemDisplay shown, int ticks, Transformation to) {
        shown.setInterpolationDelay(0);
        shown.setInterpolationDuration(ticks);
        shown.setTransformation(to);
    }

    private Transformation pose(float depth, float pitch) {
        return new Transformation(new Vector3f(0, depth * config.scale(), 0), new AxisAngle4f(pitch, 1, 0, 0),
                new Vector3f(config.scale()), new AxisAngle4f());
    }

    private static void burst(Location at, int count) {
        at.getWorld().spawnParticle(Particle.BLOCK, at, count, 1.2, 0.5, 1.2, 0.1, floor(at));
        at.getWorld().spawnParticle(Particle.CLOUD, at, count / 4, 1, 0.3, 1, 0.02);
    }

    private static BlockData floor(Location at) {
        var ground = at.clone().subtract(0, 0.5, 0).getBlock();
        return ground.getType().isSolid() ? ground.getBlockData() : SAND;
    }

    private void remove() {
        head.ifPresent(ItemDisplay::remove);
        head = Optional.empty();
    }

    /** Forgets who stood where, as when a wave ends; the head sinks out of sight at once. */
    void reset() {
        standing.clear();
        warned.clear();
        remove();
    }
}
