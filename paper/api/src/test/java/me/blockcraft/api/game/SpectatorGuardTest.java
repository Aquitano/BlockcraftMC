package me.blockcraft.api.game;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.PlayerInventory;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SpectatorGuardTest {

    @Test
    public void staffMayInspectAnotherPlayerWithoutExemptingTheirOwnInventoryOrChests() {
        var staff = player(true);
        var target = player(false);
        var guard = new SpectatorGuard(player -> true);
        var inspection = view(staff, inventory(PlayerInventory.class, target));
        var opened = new InventoryOpenEvent(inspection);
        guard.onInventoryOpen(opened);
        assertFalse(opened.isCancelled());
        var click = new InventoryClickEvent(inspection, InventoryType.SlotType.CONTAINER, 0,
                ClickType.LEFT, InventoryAction.PICKUP_ALL);
        guard.onInventoryClick(click);
        assertFalse(click.isCancelled());

        for (var inventory : new Inventory[]{inventory(PlayerInventory.class, staff), inventory(Inventory.class, null)}) {
            var denied = new InventoryOpenEvent(view(staff, inventory));
            guard.onInventoryOpen(denied);
            assertTrue(denied.isCancelled());
        }
        var denied = new InventoryOpenEvent(view(target, inventory(PlayerInventory.class, staff)));
        guard.onInventoryOpen(denied);
        assertTrue(denied.isCancelled());
    }

    private static Player player(boolean permission) {
        return fake(Player.class, (proxy, method, args) -> switch (method.getName()) {
            case "hasPermission" -> permission;
            case "equals" -> proxy == args[0];
            default -> throw new AssertionError(method.getName());
        });
    }

    private static <T extends Inventory> T inventory(Class<T> type, Player holder) {
        return fake(type, (proxy, method, args) -> switch (method.getName()) {
            case "getHolder" -> holder;
            default -> throw new AssertionError(method.getName());
        });
    }

    private static InventoryView view(Player viewer, Inventory inventory) {
        return fake(InventoryView.class, (proxy, method, args) -> switch (method.getName()) {
            case "getPlayer" -> viewer;
            case "getTopInventory", "getInventory" -> inventory;
            case "convertSlot" -> args[0];
            default -> throw new AssertionError(method.getName());
        });
    }

    private static <T> T fake(Class<T> type, InvocationHandler calls) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, calls));
    }
}
