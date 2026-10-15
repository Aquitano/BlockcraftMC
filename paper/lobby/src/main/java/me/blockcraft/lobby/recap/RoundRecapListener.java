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
import org.bukkit.event.player.PlayerQuitEvent;
import me.blockcraft.common.round.RoundRecap;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.plugin.Plugin;

/**
 * Opens the recap of the round a player just left once they have arrived: the game showed it in chat while the
 * round was ending, the lobby shows it once more as a dialog, after the arrival card has had its moment.
 */
public final class RoundRecapListener implements Listener {

    private static final long AFTER_ARRIVAL_TICKS = 50L;

    private record Recent(RoundRecap recap, Instant received) { }

    private final Map<UUID, Recent> recent = new HashMap<>();
    private final Plugin plugin;
    private final Messages messages;
    private final LobbyConfig config;

    public RoundRecapListener(Plugin plugin, Messages messages, LobbyConfig config) {
        this.plugin = plugin;
        this.messages = messages;
        this.config = config;
        BlockcraftApi.get().profiles().recentRound(this::open);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        var id = player.getUniqueId();
        var current = BlockcraftApi.get().playerTasks().currentScreen(player);
        Tasks.later(plugin, AFTER_ARRIVAL_TICKS, () -> {
            if (!plugin.isEnabled() || !player.isOnline()) {
                return;
            }
            var api = BlockcraftApi.get();
            api.playerTasks().write(player, () -> api.recaps().take(id, config.recapMaxAge()), recap -> {
                if (!plugin.isEnabled()) {
                    return;
                }
                recap.ifPresent(found -> {
                    recent.put(id, new Recent(found, Instant.now()));
                    if (current.getAsBoolean()) {
                        open(player);
                    }
                });
            });
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        recent.remove(event.getPlayer().getUniqueId());
    }

    private void open(Player player) {
        if (!plugin.isEnabled() || !player.isOnline()) {
            return;
        }
        var entry = recent.get(player.getUniqueId());
        if (entry == null || entry.received().plus(config.recapMaxAge()).isBefore(Instant.now())) {
            player.sendMessage(messages.get("recap.empty"));
            return;
        }
        var recap = entry.recap();
        new RoundRecapDialog(plugin, messages, player, recap, gameName(recap.game())).open(player);
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
