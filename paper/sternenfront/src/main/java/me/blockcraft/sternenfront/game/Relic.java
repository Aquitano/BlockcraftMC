package me.blockcraft.sternenfront.game;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Optional;
import java.util.UUID;

/**
 * The relic blade: it waits for its time, floats turning over the temple ruin until someone walks into it, and
 * stays with its champion until they fall, when it waits again.
 */
public final class Relic {

    private static final double REACH = 1.5;
    private static final float TURN_DEGREES = 6;
    private static final double BOB = 0.15;
    private static final double BOB_SPEED = 0.1;
    private static final Display.Brightness LIT = new Display.Brightness(15, 15);
    /** The blade's model points along +z; turned to point up, it stands on the altar, its middle 0.6 above it. */
    private static final Transformation UPRIGHT = new Transformation(new Vector3f(0, -0.6f, 0), new AxisAngle4f(),
            new Vector3f(0.7f), new AxisAngle4f((float) -Math.PI / 2, 1, 0, 0));

    private final SternenfrontGame game;
    private final Location home;
    private Optional<ItemDisplay> shown = Optional.empty();
    private Optional<UUID> champion = Optional.empty();
    private long appearsAt;

    public Relic(SternenfrontGame game, Location home, long appearsAt) {
        this.game = game;
        this.home = home.clone();
        this.appearsAt = appearsAt;
    }

    public void tick(long tick) {
        if (shown.isEmpty()) {
            if (champion.isEmpty() && tick >= appearsAt) {
                appear();
            }
            return;
        }
        var display = shown.get();
        var at = home.clone().add(0, Math.sin(tick * BOB_SPEED) * BOB, 0);
        at.setYaw(display.getLocation().getYaw() + TURN_DEGREES);
        display.teleport(at);
        if (tick % 4 == 0) {
            home.getWorld().spawnParticle(Particle.END_ROD, home, 2, 0.3, 0.6, 0.3, 0.01);
        }
        home.getWorld().getNearbyPlayers(home, REACH).stream()
                .filter(game::onField)
                .findFirst()
                .ifPresent(this::take);
    }

    private void appear() {
        shown = Optional.of(home.getWorld().spawn(home, ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.setItemStack(game.loadouts().blade());
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setTransformation(UPRIGHT);
            display.setBrightness(LIT);
            display.setGlowing(true);
            display.setTeleportDuration(1);
        }));
        home.getWorld().playSound(home, Sound.BLOCK_BEACON_ACTIVATE, 2f, 0.6f);
        game.relicAppeared();
    }

    private void take(Player player) {
        shown.ifPresent(ItemDisplay::remove);
        shown = Optional.empty();
        champion = Optional.of(player.getUniqueId());
        home.getWorld().playSound(home, Sound.ITEM_TRIDENT_THUNDER, 1f, 1.4f);
        game.crown(player);
    }

    /** The champion fell or left; the blade comes back {@code delayTicks} later. */
    public void lost(long tick, long delayTicks) {
        champion = Optional.empty();
        appearsAt = tick + delayTicks;
    }

    public void clear() {
        shown.ifPresent(ItemDisplay::remove);
        shown = Optional.empty();
        champion = Optional.empty();
        appearsAt = Long.MAX_VALUE;
    }
}
