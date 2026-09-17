package me.blockcraft.lobby.recap;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.config.LobbyConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

/**
 * Opens the recap of the round a player just left once they have arrived: the game showed it in chat while the
 * round was ending, the lobby shows it once more as a dialog, after the arrival card has had its moment.
 */
public final class RoundRecapListener implements Listener {

    private static final long AFTER_ARRIVAL_TICKS = 50L;

    private final Plugin plugin;
    private final Messages messages;
    private final LobbyConfig config;

    public RoundRecapListener(Plugin plugin, Messages messages, LobbyConfig config) {
        this.plugin = plugin;
        this.messages = messages;
        this.config = config;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        var id = player.getUniqueId();
        Tasks.later(plugin, AFTER_ARRIVAL_TICKS, () -> Tasks.async(plugin,
                () -> BlockcraftApi.get().recaps().take(id, config.recapMaxAge()),
                recap -> recap.ifPresent(found -> {
                    if (player.isOnline()) {
                        new RoundRecapDialog(plugin, messages, player, found, gameName(found.game())).open(player);
                    }
                })));
    }

    /** The navigator's name for the group, since the lobby names games by their card. */
    private Component gameName(String group) {
        return config.navigatorGroups().entrySet().stream()
                .filter(entry -> entry.getValue().equals(group))
                .findFirst()
                .map(entry -> messages.get("navigator." + entry.getKey().key()))
                .orElseGet(() -> Component.text(group));
    }
}
