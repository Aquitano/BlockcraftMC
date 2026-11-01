package me.blockcraft.lobby.listener;

import me.blockcraft.lobby.item.LobbyItem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.PlayerInventory;

import java.util.stream.Stream;

/** The lobby hotbar items never move or drop, not even for a builder, whom {@code WorldProtection} lets do both. */
public final class InventoryLockListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && movesLobbyItem(event, player.getInventory())) {
            event.setCancelled(true);
        }
    }

    /** A number key or F moves the hotbar or off-hand item into the hovered slot, which may well be empty. */
    private static boolean movesLobbyItem(InventoryClickEvent event, PlayerInventory inventory) {
        var swappedIn = switch (event.getClick()) {
            case NUMBER_KEY -> inventory.getItem(event.getHotbarButton());
            case SWAP_OFFHAND -> inventory.getItemInOffHand();
            default -> null;
        };
        return Stream.of(event.getCurrentItem(), event.getCursor(), swappedIn)
                .anyMatch(item -> LobbyItem.of(item).isPresent());
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (LobbyItem.of(event.getOffHandItem()).isPresent() || LobbyItem.of(event.getMainHandItem()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (LobbyItem.of(event.getItemDrop().getItemStack()).isPresent()) {
            event.setCancelled(true);
        }
    }
}
