package me.blockcraft.sternenfront.secret;

import me.blockcraft.api.pack.PackModel;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The five kyber crystals: lit displays that turn and bob, sparkle and hum to whoever stands close. A deployed player
 * who walks into one has found it, and it is gone for them for the rest of the round.
 */
final class KyberCrystals {

    private static final float SCALE = 0.6f;
    private static final int TURN_TICKS = 40;
    private static final double REACH = 0.35;
    private static final Particle.DustOptions SPARKLE = new Particle.DustOptions(Color.fromRGB(0xC9A6FF), 0.7f);

    private record Crystal(Secret secret, Location at, ItemDisplay display) {
    }

    private final Plugin plugin;
    private final SternenfrontGame game;
    private final Discoveries discoveries;
    private final List<Crystal> crystals = new ArrayList<>();

    KyberCrystals(Plugin plugin, SternenfrontGame game, List<Location> spots, Discoveries discoveries) {
        this.plugin = plugin;
        this.game = game;
        this.discoveries = discoveries;
        for (int i = 0; i < spots.size() && i < Secret.CRYSTALS.size(); i++) {
            var at = spots.get(i);
            at.getChunk().addPluginChunkTicket(plugin);
            crystals.add(new Crystal(Secret.CRYSTALS.get(i), at, at.getWorld().spawn(at, ItemDisplay.class, display -> {
                display.setPersistent(false);
                display.setItemStack(PackModel.STERNENFRONT_KYBER_CRYSTAL.item().build());
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                display.setBrightness(new Display.Brightness(15, 15));
                display.setTransformation(turned(0));
            })));
        }
    }

    void tick(long tick) {
        if (tick % 5 == 0) {
            pickUp();
        }
        for (var crystal : crystals) {
            var at = crystal.at();
            if (tick % 10 == 0) {
                at.getWorld().spawnParticle(Particle.DUST, at, 2, 0.2, 0.25, 0.2, 0, SPARKLE);
            }
            if (tick % TURN_TICKS == 0) {
                var display = crystal.display();
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(TURN_TICKS);
                display.setTransformation(turned(tick / TURN_TICKS));
            }
            if (tick % 60 == 0) {
                at.getWorld().playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.AMBIENT, 0.25f, 0.5f);
            }
        }
    }

    /** A quarter turn further each step, a little up and down in turn. */
    private static Transformation turned(long step) {
        return new Transformation(new Vector3f(0, step % 2 == 0 ? 0 : 0.08f, 0),
                new AxisAngle4f((float) (step % 4 * Math.PI / 2), 0, 1, 0), new Vector3f(SCALE), new AxisAngle4f());
    }

    private void pickUp() {
        for (var crystal : crystals) {
            for (var player : crystal.at().getWorld().getPlayers()) {
                if (player.canSee(crystal.display()) && game.onField(player)
                        && player.getBoundingBox().expand(REACH).contains(crystal.at().toVector())) {
                    player.hideEntity(plugin, crystal.display());
                    discoveries.find(player, crystal.secret());
                }
            }
        }
    }
}
