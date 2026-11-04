package me.blockcraft.ttt.task.game;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Seven chronicle volumes stand jumbled on a shelf; the player swaps two at a time until they read one to seven. The
 * start is dealt so that at least three swaps are needed, otherwise a lucky deal would be over in a click or two.
 */
public final class ChronicleGame extends ChoreGame {

    private static final int VOLUMES = 7;
    private static final int FIRST_SLOT = 10;
    private static final int MIN_SWAPS = 3;
    private static final int NO_SELECTION = -1;

    private final int[] volumes = deal(ThreadLocalRandom.current());
    private int selected = NO_SELECTION;

    public ChronicleGame(Plugin plugin, Messages messages, Component title, Runnable solved) {
        super(plugin, 3, messages, title, solved);
        for (int index = 0; index < VOLUMES; index++) {
            draw(index);
        }
    }

    static int[] deal(ThreadLocalRandom random) {
        int[] order = new int[VOLUMES];
        do {
            for (int index = 0; index < VOLUMES; index++) {
                order[index] = index + 1;
            }
            for (int index = VOLUMES - 1; index > 0; index--) {
                int other = random.nextInt(index + 1);
                int held = order[index];
                order[index] = order[other];
                order[other] = held;
            }
        } while (swapsNeeded(order) < MIN_SWAPS);
        return order;
    }

    static int swapsNeeded(int[] order) {
        boolean[] seen = new boolean[order.length];
        int cycles = 0;
        for (int start = 0; start < order.length; start++) {
            if (seen[start]) {
                continue;
            }
            cycles++;
            for (int at = start; !seen[at]; at = order[at] - 1) {
                seen[at] = true;
            }
        }
        return order.length - cycles;
    }

    @Override
    protected boolean automaticClickSound(int slot, InventoryClickEvent event) {
        return !isVolumeSlot(slot);
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (!isVolumeSlot(slot) || over()) {
            return;
        }
        int index = slot - FIRST_SLOT;
        if (selected == NO_SELECTION) {
            selected = index;
            Sounds.MENU_CLICK.play(player);
            draw(index);
            return;
        }
        int previous = selected;
        selected = NO_SELECTION;
        if (previous != index) {
            swap(player, previous, index);
        } else {
            Sounds.MENU_CLICK.play(player);
        }
        draw(previous);
        draw(index);
        if (sorted()) {
            solve(player);
        }
    }

    private void swap(Player player, int first, int second) {
        boolean firstWasPlaced = placed(first);
        boolean secondWasPlaced = placed(second);
        int held = volumes[first];
        volumes[first] = volumes[second];
        volumes[second] = held;
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
        if (placed(first) && !firstWasPlaced) {
            Sounds.NOTE_PLING.note(player, volumes[first]);
        }
        if (placed(second) && !secondWasPlaced) {
            Sounds.NOTE_PLING.note(player, volumes[second]);
        }
    }

    private boolean sorted() {
        for (int index = 0; index < VOLUMES; index++) {
            if (!placed(index)) {
                return false;
            }
        }
        return true;
    }

    private boolean placed(int index) {
        return volumes[index] == index + 1;
    }

    private boolean isVolumeSlot(int slot) {
        return slot >= FIRST_SLOT && slot < FIRST_SLOT + VOLUMES;
    }

    private void draw(int index) {
        int volume = volumes[index];
        var book = Items.of(index == selected ? Material.ENCHANTED_BOOK : Material.BOOK, volume)
                .name(messages.get("tasks.games.chronicle.book", Formatter.number("volume", volume)));
        if (placed(index)) {
            book.lore(messages.get("tasks.games.chronicle.placed"));
        }
        set(FIRST_SLOT + index, book.build());
    }
}
