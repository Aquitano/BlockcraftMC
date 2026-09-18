package me.blockcraft.core.nick;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.settings.PlayerSettingsLoadedEvent;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.settings.PlayerSetting;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Optional;

/**
 * Carries a nick from server to server, and wears or drops one by itself for players who asked for that. The stored
 * settings decide it, so the work waits for {@link PlayerSettingsLoadedEvent} rather than the join: a nick applied
 * first and taken back a moment later would flicker for everyone watching the tab list.
 */
public final class NickListener implements Listener {

    private final Nicks nicks;
    private final Messages messages;

    public NickListener(Nicks nicks, Messages messages) {
        this.nicks = nicks;
        this.messages = messages;
    }

    @EventHandler
    public void onSettingsLoaded(PlayerSettingsLoadedEvent event) {
        var player = event.getPlayer();
        var api = BlockcraftApi.get();
        boolean auto = api.settings().enabled(player, PlayerSetting.AUTO_NICK);
        if (auto && api.isLobby() && !api.settings().enabled(player, PlayerSetting.KEEP_NICK)) {
            nicks.dropStored(player, () -> player.sendMessage(messages.get("nick.auto-off")));
            return;
        }
        nicks.applyStored(player, stored -> stored.ifPresentOrElse(
                nick -> player.sendMessage(messages.get("nick.still-active", Nicks.shown(player, nick))),
                () -> {
                    if (auto && !api.isLobby()) {
                        nickForTheRound(player);
                    }
                }));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        nicks.forget(event.getPlayer());
    }

    private void nickForTheRound(Player player) {
        nicks.assign(player, Optional.empty(), nick -> nick.ifPresent(worn ->
                player.sendMessage(messages.get("nick.auto", Nicks.shown(player, worn)))));
    }
}
