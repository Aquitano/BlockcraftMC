package me.blockcraft.sternenfront.secret;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.pack.PackModel;
import me.blockcraft.common.message.Messages;
import me.blockcraft.sternenfront.appearance.Displays;
import me.blockcraft.sternenfront.arena.SecretSpots;
import me.blockcraft.sternenfront.game.SoundCue;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The dune with the rib in it. A deployed player who stands still on its top first feels the sand rumble and shake,
 * and after a while the head of the beast below breaks out of the sand in front of them, snaps twice and sinks back.
 * Nobody is hurt; the player has found the secret, and the dune lies quiet for a while.
 */
final class SandBeast {

    private static final int CHECK_TICKS = 5;
    private static final double MOVED = 0.2;
    private static final double AHEAD = 3.5;
    private static final float BURIED = -5f;
    private static final float LIFT = (float) Math.toRadians(-28);
    private static final float BITE = (float) Math.toRadians(8);

    private record Still(Location at, long since) {
    }

    private final SternenfrontGame game;
    private final Messages messages;
    private final Discoveries discoveries;
    private final SecretSpots.Dune dune;
    private final long rumbleTicks;
    private final long stillTicks;
    private final long restTicks;
    private final float scale;
    private final Map<UUID, Still> standing = new HashMap<>();
    private Optional<ItemDisplay> head = Optional.empty();
    private long erupted = Long.MIN_VALUE;
    private long quietUntil;

    SandBeast(SternenfrontGame game, Messages messages, Discoveries discoveries, SecretSpots.Dune dune, SecretsConfig config) {
        this.game = game;
        this.messages = messages;
        this.discoveries = discoveries;
        this.dune = dune;
        this.rumbleTicks = config.rumbleSeconds() * 20L;
        this.stillTicks = config.stillSeconds() * 20L;
        this.restTicks = config.restSeconds() * 20L;
        this.scale = config.beastScale();
    }

    void tick(long tick) {
        head.ifPresent(shown -> animate(shown, tick - erupted));
        if (tick % CHECK_TICKS != 0) {
            return;
        }
        var top = dune.top();
        for (var player : top.getWorld().getPlayers()) {
            if (!game.onField(player) || !onTop(player.getLocation())) {
                standing.remove(player.getUniqueId());
                continue;
            }
            var at = player.getLocation();
            var still = standing.get(player.getUniqueId());
            if (still == null || still.at().distanceSquared(at) > MOVED * MOVED) {
                standing.put(player.getUniqueId(), new Still(at, tick));
                continue;
            }
            long stood = tick - still.since();
            if (tick < quietUntil || head.isPresent() || stood < rumbleTicks) {
                continue;
            }
            if (stood >= stillTicks) {
                erupt(player, tick);
            } else {
                rumble(player, stood, tick);
            }
        }
    }

    private boolean onTop(Location at) {
        var top = dune.top();
        double dx = at.getX() - top.getX();
        double dz = at.getZ() - top.getZ();
        return dx * dx + dz * dz <= dune.radius() * dune.radius() && Math.abs(at.getY() - top.getY()) < 1.5;
    }

    private void rumble(Player player, long stood, long tick) {
        if (stood - rumbleTicks < CHECK_TICKS) {
            BlockcraftApi.get().notifications().feedback(player, messages.get("secret.rumble"));
        }
        boolean strong = stood * 3 > stillTicks * 2;
        if (strong || tick % 10 == 0) {
            var at = player.getLocation();
            SoundCue.SAND_BEAST_RUMBLE.play(at, strong ? 1f : 0.6f);
            at.getWorld().spawnParticle(Particle.BLOCK, at, strong ? 24 : 10, 1.2, 0.1, 1.2, 0, Material.SAND.createBlockData());
            player.sendHurtAnimation(ThreadLocalRandom.current().nextFloat() * 360);
        }
    }

    private void erupt(Player player, long tick) {
        standing.clear();
        erupted = tick;
        quietUntil = tick + restTicks;
        var facing = player.getLocation().getDirection().setY(0);
        if (facing.lengthSquared() == 0) {
            facing = new Vector(0, 0, 1);
        }
        var at = player.getLocation().add(facing.normalize().multiply(AHEAD));
        at.setY(dune.top().getY() + 1);
        at.setDirection(player.getLocation().toVector().subtract(at.toVector()).setY(0));
        at.setPitch(0);
        head = Optional.of(Displays.item(at, PackModel.STERNENFRONT_SAND_BEAST.item().build(),
                shown -> shown.setTransformation(pose(BURIED, 0))));
        SoundCue.SAND_BEAST_ROAR.play(at, 3f);
        burst(at, 60);
        player.setVelocity(player.getVelocity().setY(0.45));
        discoveries.find(player, Secret.SAND_BEAST);
    }

    /** Ticks after the eruption: up out of the sand, a lift of the head and two bites, then down again. */
    private void animate(ItemDisplay shown, long age) {
        var at = shown.getLocation();
        if (age == 1) {
            move(shown, 6, pose(0, 0));
        } else if (age == 8 || age == 15) {
            move(shown, 3, pose(0, LIFT));
        } else if (age == 11 || age == 18) {
            move(shown, 2, pose(0, BITE));
            at.getWorld().playSound(at, Sound.ENTITY_EVOKER_FANGS_ATTACK, SoundCategory.HOSTILE, 1.5f, 0.5f);
            burst(at, 15);
        } else if (age == 34) {
            move(shown, 20, pose(BURIED, 0));
            burst(at, 30);
        } else if (age >= 56) {
            shown.remove();
            head = Optional.empty();
        }
    }

    private void move(ItemDisplay shown, int ticks, Transformation to) {
        shown.setInterpolationDelay(0);
        shown.setInterpolationDuration(ticks);
        shown.setTransformation(to);
    }

    private Transformation pose(float depth, float pitch) {
        return new Transformation(new Vector3f(0, depth, 0), new AxisAngle4f(pitch, 1, 0, 0), new Vector3f(scale), new AxisAngle4f());
    }

    private static void burst(Location at, int count) {
        at.getWorld().spawnParticle(Particle.BLOCK, at, count, 1.5, 0.6, 1.5, 0.1, Material.SAND.createBlockData());
        at.getWorld().spawnParticle(Particle.CLOUD, at, count / 4, 1.2, 0.4, 1.2, 0.02);
    }
}
