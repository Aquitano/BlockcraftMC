package me.blockcraft.endergames.game;

import me.blockcraft.endergames.config.EnderGamesConfig.SwapSettings;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/** Every now and then two players of the fight trade places, so that no pair can team up for long. */
final class PlayerSwaps {

    private static final int BLINDNESS_TICKS = 20;

    private final SwapSettings settings;
    private final Random random = new Random();
    private int secondsLeft;

    PlayerSwaps(SwapSettings settings) {
        this.settings = settings;
        rewind();
    }

    /** One second of the fight; when a swap is due, the two players it moved. */
    Optional<SwapPairs<Player>> tick(List<Player> alive) {
        if (--secondsLeft > 0) {
            return Optional.empty();
        }
        rewind();
        var pair = SwapPairs.pick(alive, random);
        pair.ifPresent(swap -> {
            var first = swap.first().getLocation();
            var second = swap.second().getLocation();
            move(swap.first(), second);
            move(swap.second(), first);
        });
        return pair;
    }

    private void rewind() {
        secondsLeft = random.nextInt(settings.minSeconds(), settings.maxSeconds() + 1);
    }

    private static void move(Player player, Location to) {
        player.closeInventory();
        player.leaveVehicle();
        player.teleport(to);
        player.setFallDistance(0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, BLINDNESS_TICKS, 0, true, false, false));
        to.getWorld().playSound(to, Sound.ENTITY_PLAYER_TELEPORT, 1.0f, 0.6f);
        to.getWorld().spawnParticle(Particle.PORTAL, to.clone().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.6);
    }
}
