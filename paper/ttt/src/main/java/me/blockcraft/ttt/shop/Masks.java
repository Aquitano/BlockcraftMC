package me.blockcraft.ttt.shop;

import com.destroystokyo.paper.profile.PlayerProfile;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A traitor in a mask wears a dead player's face and name for a while. Paper's setPlayerProfile changes what
 * {@link Player#getName()} returns, so chat, the tab list and every message about the wearer name the dead; the
 * profile they had before, a nick's included, is what {@link #restore} puts back.
 */
final class Masks {

    private record Worn(PlayerProfile original, int generation) {
    }

    private final Plugin plugin;
    private final Messages messages;
    private final Map<UUID, Worn> worn = new HashMap<>();
    private int generation;

    Masks(Plugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    void wear(Player wearer, String name, PlayerProfile face, int seconds) {
        var id = wearer.getUniqueId();
        var original = worn.containsKey(id) ? worn.get(id).original() : wearer.getPlayerProfile();
        int mine = ++generation;
        worn.put(id, new Worn(original, mine));
        var profile = Bukkit.createProfileExact(id, name);
        profile.setProperties(face.getProperties());
        wearer.setPlayerProfile(profile);
        BlockcraftApi.get().tabTeams().update(wearer);
        Tasks.later(plugin, seconds * 20L, () -> {
            var current = worn.get(id);
            if (current != null && current.generation() == mine) {
                var player = Bukkit.getPlayer(id);
                if (player != null && restore(player)) {
                    BlockcraftApi.get().notifications().feedback(player, messages.get("shop.mask.fallen"));
                }
            }
        });
    }

    /** Returns false when the player wore no mask. */
    boolean restore(Player player) {
        var current = worn.remove(player.getUniqueId());
        if (current == null) {
            return false;
        }
        player.setPlayerProfile(current.original());
        BlockcraftApi.get().tabTeams().update(player);
        return true;
    }

    void restoreAll() {
        Map.copyOf(worn).keySet().forEach(id -> {
            var player = Bukkit.getPlayer(id);
            if (player != null) {
                restore(player);
            }
        });
        worn.clear();
    }
}
