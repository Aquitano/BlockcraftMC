package me.blockcraft.api.menu;

import me.blockcraft.api.pack.MenuTile;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class MenuTileTest {
    @Test
    public void everySlotOfACardUsesItsActionAndEnabledCheck() {
        for (var shape : MenuTile.Shape.values()) {
            var menu = new RecordingMenu();
            menu.tile(9, MenuTile.BEDWARS, shape, null);
            var area = new MenuTile.Placement(9, MenuTile.BEDWARS, shape);

            area.slots().forEach(slot -> menu.handleClick(null, slot, null, null));

            assertEquals(area.slots().mapToObj(ignored -> 9).toList(), menu.actions);
            assertEquals(menu.actions, menu.enabled);
        }
    }

    @Test
    public void anInformationalCardBlocksItsWholeArea() {
        var menu = new RecordingMenu();
        menu.tile(9, MenuTile.COINS, MenuTile.Shape.WIDE, null);
        menu.info(9, null);

        new MenuTile.Placement(9, MenuTile.COINS, MenuTile.Shape.WIDE).slots()
                .forEach(slot -> menu.handleClick(null, slot, null, null));

        assertEquals(List.of(), menu.actions);
    }

    @Test
    public void cardsCannotWrapRowsOverlapOrExceedTheMenu() {
        var menu = new RecordingMenu();
        menu.tile(9, MenuTile.HEADS, MenuTile.Shape.LARGE, null);

        assertThrows(IllegalArgumentException.class, () -> menu.tile(10, MenuTile.HATS, MenuTile.Shape.LARGE, null));
        assertThrows(IllegalArgumentException.class, () -> menu.tile(17, MenuTile.HATS, MenuTile.Shape.WIDE, null));
        assertThrows(IllegalArgumentException.class, () -> menu.tile(27, MenuTile.HATS, MenuTile.Shape.WIDE, null));
    }

    private static final class RecordingMenu extends Menu {
        private final List<Integer> actions = new ArrayList<>();
        private final List<Integer> enabled = new ArrayList<>();

        RecordingMenu() {
            super(3, Component.empty());
        }

        @Override
        protected boolean automaticClickSound(int slot, InventoryClickEvent event) {
            return false;
        }

        @Override
        protected boolean isEnabled(Player player, int slot, InventoryClickEvent event) {
            enabled.add(slot);
            return true;
        }

        @Override
        protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
            actions.add(slot);
        }
    }
}
