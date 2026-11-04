package me.blockcraft.api.settings;

import me.blockcraft.common.settings.PlayerSetting;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

public final class PlayerSettingChangedEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final PlayerSetting setting;

    public PlayerSettingChangedEvent(Player player, PlayerSetting setting) {
        super(player);
        this.setting = setting;
    }

    public PlayerSetting setting() {
        return setting;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
