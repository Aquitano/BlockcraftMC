package me.blockcraft.core.staff;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.PlayerInventory;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;

public class InvseeGuardTest {

    @Test
    public void viewingPermissionDoesNotGrantEditingAndEditorsKeepTheirAccess() {
        for (boolean editor : new boolean[]{false, true}) {
            var viewer = fake(Player.class, (proxy, method, args) -> switch (method.getName()) {
                case "hasPermission" -> editor;
                case "equals" -> proxy == args[0];
                default -> throw new AssertionError(method.getName());
            });
            var target = fake(Player.class, (proxy, method, args) -> null);
            var inventory = fake(PlayerInventory.class, (proxy, method, args) -> switch (method.getName()) {
                case "getHolder" -> target;
                default -> throw new AssertionError(method.getName());
            });
            var view = fake(InventoryView.class, (proxy, method, args) -> switch (method.getName()) {
                case "getPlayer" -> viewer;
                case "getTopInventory", "getInventory" -> inventory;
                case "convertSlot" -> args[0];
                default -> throw new AssertionError(method.getName());
            });
            var event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0,
                    ClickType.LEFT, InventoryAction.PICKUP_ALL);
            new InvseeGuard().onClick(event);
            assertEquals(!editor, event.isCancelled());
            assertEquals(!editor, InvseeGuard.isReadOnly(viewer, view));
        }
    }

    private static <T> T fake(Class<T> type, InvocationHandler calls) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, calls));
    }
}
