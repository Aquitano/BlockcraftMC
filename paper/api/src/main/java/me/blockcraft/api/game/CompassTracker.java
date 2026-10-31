package me.blockcraft.api.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;

/**
 * The compass a round hands out late in play: using it points it at the nearest player in range who is visible to
 * the user, and tells how far away they are. The game decides who may use it and who counts as a target; the
 * {@code tracker.found} and {@code tracker.empty} messages are the game's own.
 */
public final class CompassTracker {

    /** {@code tracker.range-horizontal}, {@code tracker.range-vertical} and {@code tracker.cooldown-ticks}. */
    public record Settings(double rangeHorizontal, double rangeVertical, int cooldownTicks) {

        public static Settings from(ConfigurationSection config) {
            return new Settings(
                    config.getDouble("tracker.range-horizontal", 100.0),
                    config.getDouble("tracker.range-vertical", 25.0),
                    Math.max(0, config.getInt("tracker.cooldown-ticks", 20)));
        }
    }

    private final Messages messages;
    private final Settings settings;

    public CompassTracker(Messages messages, Settings settings) {
        this.messages = messages;
        this.settings = settings;
    }

    public void track(Player player, List<Player> candidates) {
        var compass = player.getInventory().getItemInMainHand();
        if (player.hasCooldown(compass)) {
            return;
        }
        player.setCooldown(compass, settings.cooldownTicks());
        var origin = player.getLocation();
        var target = candidates.stream()
                .filter(other -> !other.equals(player) && !other.isDead() && player.canSee(other))
                .filter(other -> other.getWorld().equals(origin.getWorld()))
                .filter(other -> {
                    var location = other.getLocation();
                    return Math.abs(location.getX() - origin.getX()) <= settings.rangeHorizontal()
                            && Math.abs(location.getZ() - origin.getZ()) <= settings.rangeHorizontal()
                            && Math.abs(location.getY() - origin.getY()) <= settings.rangeVertical();
                })
                .min(Comparator.comparingDouble(other -> origin.distanceSquared(other.getLocation())));
        target.ifPresentOrElse(near -> {
            player.setCompassTarget(near.getLocation());
            BlockcraftApi.get().notifications().feedback(player, messages.get("tracker.found",
                    Placeholder.unparsed("player", near.getName()),
                    Formatter.number("distance", (int) origin.distance(near.getLocation()))));
            Sounds.NOTIFY.play(player);
        }, () -> {
            player.setCompassTarget(origin);
            BlockcraftApi.get().notifications().feedback(player, messages.get("tracker.empty"));
            Sounds.MENU_DENY.play(player);
        });
    }
}
