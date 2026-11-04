package me.blockcraft.ttt.task.game;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A ledger chore: a page of small sums, a few of them off by one to three, and the player strikes out exactly those.
 * Striking a correct sum is a mistake, and the page is dealt anew so that the answer cannot be learned by trial.
 */
public final class LedgerGame extends ChoreGame {

    private static final int FIRST_SLOT = 9;
    private static final int HINT_SLOT = 4;
    private static final int ENTRIES = 9;
    private static final int WRONG_ENTRIES = 3;
    private static final int LIMIT = 30;
    private static final int MAX_OFF = 3;

    record Entry(int left, boolean plus, int right, int shown) {

        int actual() {
            return plus ? left + right : left - right;
        }

        boolean wrong() {
            return shown != actual();
        }
    }

    private List<Entry> page = deal(ENTRIES, WRONG_ENTRIES);
    private boolean[] struck = new boolean[ENTRIES];
    private int open = WRONG_ENTRIES;

    public LedgerGame(Plugin plugin, Messages messages, Component title, Runnable solved) {
        super(plugin, 3, messages, title, solved);
        info(HINT_SLOT, Items.of(Material.WRITABLE_BOOK).name(messages.get("tasks.games.ledger.hint")).build());
        show();
    }

    static List<Entry> deal(int entries, int wrong) {
        var random = ThreadLocalRandom.current();
        var page = new ArrayList<Entry>(entries);
        for (int i = 0; i < entries; i++) {
            boolean plus = random.nextBoolean();
            int left = plus ? random.nextInt(2, 19) : random.nextInt(5, LIMIT);
            int right = plus ? random.nextInt(2, Math.min(19, LIMIT - left)) : random.nextInt(1, left);
            int actual = plus ? left + right : left - right;
            int off = random.nextInt(1, MAX_OFF + 1) * (random.nextBoolean() ? 1 : -1);
            int shown = i < wrong ? (actual + off < 0 ? actual - off : actual + off) : actual;
            page.add(new Entry(left, plus, right, shown));
        }
        Collections.shuffle(page, random);
        return page;
    }

    @Override
    protected boolean automaticClickSound(int slot, InventoryClickEvent event) {
        return slot < FIRST_SLOT || slot >= FIRST_SLOT + ENTRIES;
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        int index = slot - FIRST_SLOT;
        if (over() || index < 0 || index >= ENTRIES || struck[index]) {
            return;
        }
        if (!page.get(index).wrong()) {
            Sounds.MENU_DENY.play(player);
            page = deal(ENTRIES, WRONG_ENTRIES);
            struck = new boolean[ENTRIES];
            open = WRONG_ENTRIES;
            show();
            return;
        }
        struck[index] = true;
        player.playSound(player.getLocation(), Sound.ITEM_BRUSH_BRUSHING_GENERIC, 1f, 1.3f);
        show();
        if (--open == 0) {
            Sounds.NOTE_PLING.note(player, 19);
            solve(player);
        }
    }

    private void show() {
        for (int i = 0; i < ENTRIES; i++) {
            set(FIRST_SLOT + i, entry(page.get(i), struck[i]));
        }
    }

    private ItemStack entry(Entry entry, boolean struck) {
        var line = messages.get(struck ? "tasks.games.ledger.struck" : "tasks.games.ledger.entry",
                Formatter.number("first", entry.left()),
                Placeholder.unparsed("op", entry.plus() ? "+" : "-"),
                Formatter.number("second", entry.right()),
                Formatter.number("result", entry.shown()));
        return Items.of(struck ? Material.RED_DYE : Material.PAPER).name(line).build();
    }
}
