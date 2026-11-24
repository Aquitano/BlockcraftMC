package me.blockcraft.api.kit;

import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.Round;
import me.blockcraft.api.settings.KitPreferences;
import me.blockcraft.common.settings.PlayerSetting;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The kit each player takes into the round: {@code standard} until they pick another while the lobby is open. A pick
 * is remembered in the player's {@code setting} and comes back when they join again, as long as they still own it;
 * {@code changed} hears every player whose kit changed.
 */
public final class KitSelection<K extends Enum<K> & UnlockableKit> {

    private final Round round;
    private final K standard;
    private final Consumer<Player> changed;
    private final Map<UUID, K> kits = new HashMap<>();
    private final KitPreferences<K> preferences;

    public KitSelection(Plugin plugin, Round round, PlayerSetting setting, K standard, Predicate<K> free,
                        Consumer<Player> changed) {
        this.round = round;
        this.standard = standard;
        this.changed = changed;
        this.preferences = new KitPreferences<>(plugin, setting, List.of(standard.getDeclaringClass().getEnumConstants()),
                UnlockableKit::item, free, (player, kit) -> {
                    if (isOpen() && round.isPlaying(player)) {
                        kits.put(player.getUniqueId(), kit);
                        changed.accept(player);
                    }
                });
    }

    /** Whether kits can be picked, which only the lobby allows. */
    public boolean isOpen() {
        return round.phase() == GamePhase.LOBBY;
    }

    public K kit(Player player) {
        return kits.getOrDefault(player.getUniqueId(), standard);
    }

    /** False once the round started, as when a purchase completes too late, so that nobody holds a kit they never got. */
    public boolean select(Player player, K kit) {
        if (!isOpen()) {
            return false;
        }
        kits.put(player.getUniqueId(), kit);
        preferences.remember(player, kit);
        changed.accept(player);
        return true;
    }

    public void restore(Player player) {
        preferences.restore(player);
    }

    public void forget(Player player) {
        kits.remove(player.getUniqueId());
    }
}
