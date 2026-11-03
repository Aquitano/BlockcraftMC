package me.blockcraft.ttt.task;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

/**
 * A puzzle chore: nine numbered tiles in a jumbled row, clicked from one to nine. A wrong tile starts it over. While
 * it is open the player sees nothing of who comes near.
 */
final class PuzzleMenu extends Menu {

    private static final int TILES = 9;

    private final Messages messages;
    private final Runnable solved;
    private final Map<Integer, Integer> numbers = new HashMap<>();
    private int next = 1;

    PuzzleMenu(Messages messages, Component title, Runnable solved) {
        super(2, title);
        this.messages = messages;
        this.solved = solved;
        List<Integer> order = new ArrayList<>(IntStream.rangeClosed(1, TILES).boxed().toList());
        Collections.shuffle(order, ThreadLocalRandom.current());
        for (int slot = 0; slot < TILES; slot++) {
            numbers.put(slot, order.get(slot));
        }
        closeButton();
        reset();
    }

    @Override
    protected boolean automaticClickSound(int slot, InventoryClickEvent event) {
        return !numbers.containsKey(slot);
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        var number = numbers.get(slot);
        if (number == null || number < next) {
            return;
        }
        if (number != next) {
            Sounds.MENU_DENY.play(player);
            reset();
            return;
        }
        Sounds.NOTE_BIT.note(player, next * 2);
        set(slot, tile(Material.LIME_CONCRETE, number));
        if (++next > TILES) {
            close(player);
            solved.run();
        }
    }

    private void reset() {
        next = 1;
        numbers.forEach((slot, number) -> set(slot, tile(Material.WHITE_CONCRETE, number)));
    }

    private ItemStack tile(Material material, int number) {
        return Items.of(material, number).name(messages.get("tasks.puzzle.tile", Formatter.number("number", number))).build();
    }
}
