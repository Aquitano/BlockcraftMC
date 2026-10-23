package me.blockcraft.api.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MenuListenerTest {

    private static final int MENU_SLOTS = 9;
    private static final int OWN_SLOT = MENU_SLOTS + 4;
    private static final int OUTSIDE = -999;

    private final MenuListener listener = new MenuListener(null);
    private final Menu shop = new Menu(1, Component.empty()) {
        @Override
        protected boolean locksPlayerInventory() {
            return false;
        }

        @Override
        protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        }
    };
    private final Inventory top = fake(Inventory.class, (proxy, method, args) -> switch (method.getName()) {
        case "getHolder" -> shop;
        case "getSize" -> MENU_SLOTS;
        default -> throw new AssertionError(method.getName());
    });
    private final Inventory bottom = fake(Inventory.class, (proxy, method, args) -> {
        throw new AssertionError(method.getName());
    });
    private final Player player = fake(Player.class, (proxy, method, args) -> {
        throw new AssertionError(method.getName());
    });
    private final InventoryView view = fake(InventoryView.class, (proxy, method, args) -> switch (method.getName()) {
        case "getTopInventory" -> top;
        case "getBottomInventory" -> bottom;
        case "getPlayer" -> player;
        case "getInventory" -> (int) args[0] == OUTSIDE ? null : (int) args[0] < MENU_SLOTS ? top : bottom;
        case "convertSlot" -> args[0];
        default -> throw new AssertionError(method.getName());
    });

    @Test
    public void aShopLeavesTheOwnInventoryToTheGameButKeepsItemsOutOfItsSlots() {
        assertFalse(click(OWN_SLOT, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        assertFalse(click(OWN_SLOT, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP));
        assertFalse(click(OUTSIDE, ClickType.LEFT, InventoryAction.DROP_ALL_CURSOR));
        assertTrue(click(OWN_SLOT, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY));
        assertTrue(click(OWN_SLOT, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR));

        assertFalse(drag(OWN_SLOT, OWN_SLOT + 1));
        assertTrue(drag(OWN_SLOT, MENU_SLOTS - 1));
    }

    private boolean click(int rawSlot, ClickType type, InventoryAction action) {
        var slotType = rawSlot == OUTSIDE ? SlotType.OUTSIDE : SlotType.CONTAINER;
        var event = new InventoryClickEvent(view, slotType, rawSlot, type, action);
        listener.onClick(event);
        return event.isCancelled();
    }

    private boolean drag(int... rawSlots) {
        var slots = new HashMap<Integer, ItemStack>();
        for (int slot : rawSlots) {
            slots.put(slot, null);
        }
        var event = new InventoryDragEvent(view, null, null, false, slots);
        listener.onDrag(event);
        return event.isCancelled();
    }

    private static <T> T fake(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
