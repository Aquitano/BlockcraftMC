package me.blockcraft.lobby.listener;

import me.blockcraft.api.config.Locations;
import me.blockcraft.api.player.Arrival;
import me.blockcraft.api.settings.PlayerSettingsLoadedEvent;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.config.LobbyLocation;
import me.blockcraft.lobby.cosmetic.Pets;
import me.blockcraft.lobby.cosmetic.Trails;
import me.blockcraft.lobby.hider.PlayerHider;
import me.blockcraft.lobby.item.LobbyItem;
import me.blockcraft.lobby.item.LobbyItems;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class JoinQuitListener implements Listener {

    private final Messages messages;
    private final Locations<LobbyLocation> locations;
    private final LobbyItems items;
    private final Trails trails;
    private final Pets pets;
    private final PlayerHider hider;

    public JoinQuitListener(Messages messages, Locations<LobbyLocation> locations,
                            LobbyItems items, Trails trails, Pets pets, PlayerHider hider) {
        this.messages = messages;
        this.locations = locations;
        this.items = items;
        this.trails = trails;
        this.pets = pets;
        this.hider = hider;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        event.joinMessage(null);
        player.setGameMode(GameMode.ADVENTURE);
        Arrival.show(player, messages.get("join.title"));
        player.getInventory().setArmorContents(null);
        items.give(player, true);
        locations.get(LobbyLocation.SPAWN).ifPresent(player::teleport);
        player.setCollidable(false);
        hider.onJoin(player);
    }

    /** The hider choice is stored, so it is applied once the settings arrived rather than on join. */
    @EventHandler
    public void onSettingsLoaded(PlayerSettingsLoadedEvent event) {
        Player player = event.getPlayer();
        if (hider.applyStored(player)) {
            player.getInventory().setItem(LobbyItem.PLAYER_HIDER.slot(), items.hiderItem(false));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        event.quitMessage(null);
        trails.clear(player);
        hider.forget(player);
        pets.release(player.getUniqueId());
    }

}
