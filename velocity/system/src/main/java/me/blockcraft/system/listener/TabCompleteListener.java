package me.blockcraft.system.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.PlayerAvailableCommandsEvent;
import com.velocitypowered.api.event.player.TabCompleteEvent;
import me.blockcraft.system.SystemPermissions;

/** Hides command completion from players without the tab permission, for old and new clients alike. */
public final class TabCompleteListener {

    @Subscribe
    public void onTabComplete(TabCompleteEvent event) {
        if (event.getPartialMessage().startsWith("/") && !event.getPlayer().hasPermission(SystemPermissions.TAB_COMPLETE)) {
            event.getSuggestions().clear();
        }
    }

    @Subscribe
    public void onAvailableCommands(PlayerAvailableCommandsEvent event) {
        if (!event.getPlayer().hasPermission(SystemPermissions.TAB_COMPLETE)) {
            event.getRootNode().getChildren().clear();
        }
    }
}
