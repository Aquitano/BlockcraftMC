package me.blockcraft.core.command;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.settings.PlayerSettings;
import me.blockcraft.api.settings.PlayerSettingsLoadedEvent;
import me.blockcraft.common.settings.PlayerSetting;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Optional;

/** Puts back the {@code /ptime} clock a player chose on another server or before they rejoined. */
public final class PlayerTimeListener implements Listener {

    @EventHandler
    public void onSettingsLoaded(PlayerSettingsLoadedEvent event) {
        var player = event.getPlayer();
        if (!PlayerSettings.allowed(player, PlayerSetting.PLAYER_TIME)) {
            return;
        }
        BlockcraftApi.get().settings().get(player, PlayerSetting.PLAYER_TIME)
                .flatMap(PlayerTimeListener::ticks)
                .ifPresent(ticks -> player.setPlayerTime(ticks, false));
    }

    private static Optional<Long> ticks(String stored) {
        try {
            return Optional.of(Long.parseLong(stored));
        } catch (NumberFormatException malformed) {
            return Optional.empty();
        }
    }
}
