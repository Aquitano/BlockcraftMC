package me.blockcraft.api.menu;

import me.blockcraft.api.sound.Sounds;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class MenuFeedbackTest {

    @Test
    public void anActionOwningItsFeedbackRunsOnceWithoutPreparingAGenericClickSound() {
        var attempts = new AtomicInteger();
        var menu = new Menu(1, Component.empty()) {
            @Override
            protected boolean automaticClickSound(int slot, InventoryClickEvent event) {
                return false;
            }

            @Override
            protected Sounds clickSound(int slot, InventoryClickEvent event) {
                throw new AssertionError("This action supplies its own sound after the attempt");
            }

            @Override
            protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
                attempts.incrementAndGet();
            }
        };

        menu.handleClick(null, 0, null, null);

        assertEquals(1, attempts.get());
    }
}
