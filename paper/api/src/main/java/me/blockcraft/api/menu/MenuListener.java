package me.blockcraft.api.menu;

import me.blockcraft.api.settings.PlayerSettingChangedEvent;
import me.blockcraft.api.task.PlayerTasks;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.ItemStack;


public final class MenuListener implements Listener {

    private final PlayerTasks playerTasks;

    public MenuListener(PlayerTasks playerTasks) {
        this.playerTasks = playerTasks;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Menu menu) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!menu.locksPlayerInventory() && !menu.borrowsPlayerInventory() && staysOutOfMenu(event)) {
            return;
        }
        event.setCancelled(true);
        if (!menu.accepts(event.getClick())) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.isEmpty()) {
            return;
        }
        if (event.getClickedInventory() == menu.getInventory()) {
            menu.handleClick(player, event.getSlot(), clicked, event);
        } else if (menu.borrowsPlayerInventory() && event.getClickedInventory() == event.getView().getBottomInventory()) {
            menu.handleClick(player, event.getRawSlot(), clicked, event);
        }
    }

    /**
     * A click in the player's inventory or outside the window, which drops the held item. A shift-click or a
     * double-click in the player's inventory moves items into or out of the menu as well.
     */
    private static boolean staysOutOfMenu(InventoryClickEvent event) {
        var clicked = event.getClickedInventory();
        return (clicked == null || clicked == event.getView().getBottomInventory())
                && event.getAction() != InventoryAction.MOVE_TO_OTHER_INVENTORY
                && event.getAction() != InventoryAction.COLLECT_TO_CURSOR;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Menu menu)) {
            return;
        }
        int menuSlots = event.getView().getTopInventory().getSize();
        if (menu.locksPlayerInventory() || event.getRawSlots().stream().anyMatch(slot -> slot < menuSlots)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSettingChanged(PlayerSettingChangedEvent event) {
        var player = event.getPlayer();
        if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu menu) {
            menu.onSettingChanged(player, event.setting());
        }
    }

    @EventHandler
    public void onPack(PlayerResourcePackStatusEvent event) {
        var player = event.getPlayer();
        if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu menu) {
            menu.restyle(player);
        }
    }

    /** Whatever closes the menu, a click, a new menu, a death, a kick or the server stopping, the player's items come back. */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Menu menu)) {
            return;
        }
        menu.giveBack();
        if (event.getReason() == InventoryCloseEvent.Reason.PLAYER && event.getPlayer() instanceof Player player) {
            playerTasks.invalidate(player);
        }
    }

    /** The drops were counted before the menu closes: the player's own items fall in place of the menu's. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        if (!(event.getPlayer().getOpenInventory().getTopInventory().getHolder(false) instanceof Menu menu)
                || !menu.borrowsPlayerInventory()) {
            return;
        }
        var own = menu.lentFrom();
        menu.giveBack();
        event.getDrops().removeIf(PlayerSlots::lent);
        if (!event.getKeepInventory()) {
            event.getDrops().addAll(own);
        }
    }

    /** A crash keeps no promise to give anything back, so a menu's items saved with the player are dropped on arrival. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var inventory = event.getPlayer().getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (PlayerSlots.lent(inventory.getItem(slot))) {
                inventory.setItem(slot, null);
            }
        }
    }

    /** A menu's plugin going away, at a stop or a reload, ends its loans while its listener still hears the close. */
    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        for (var player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu menu && menu.borrowsPlayerInventory()) {
                player.closeInventory();
            }
        }
    }
}
