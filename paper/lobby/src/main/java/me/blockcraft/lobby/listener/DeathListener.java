package me.blockcraft.lobby.listener;

import me.blockcraft.api.config.Locations;
import me.blockcraft.lobby.config.LobbyLocation;
import me.blockcraft.lobby.hider.PlayerHider;
import me.blockcraft.lobby.item.LobbyItems;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class DeathListener implements Listener {

    private final Locations<LobbyLocation> locations;
    private final LobbyItems items;
    private final PlayerHider hider;

    public DeathListener(Locations<LobbyLocation> locations, LobbyItems items, PlayerHider hider) {
        this.locations = locations;
        this.items = items;
        this.hider = hider;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.deathMessage(null);
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        event.setKeepLevel(true);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        locations.get(LobbyLocation.SPAWN).ifPresent(event::setRespawnLocation);
        items.give(event.getPlayer(), !hider.isHiding(event.getPlayer()));
    }
}
