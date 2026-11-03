package me.blockcraft.ttt.task.game;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.ThreadLocalRandom;

/**
 * A Simon-says chore: the organ lights its keys one by one, the player repeats the tune over three rounds. Clicks are
 * ignored while the tune plays, because a player who answers early would be guessing the tail.
 */
public final class OrganGame extends ChoreGame {

    private static final int FIRST_KEY_SLOT = 10;
    private static final int STATUS_SLOT = 4;
    private static final int FIRST_TUNE = 3;
    private static final int ROUNDS = 3;
    private static final int STEP_TICKS = 6;
    private static final int LEAD_IN_STEPS = 3;
    private static final Material[] DIM = {Material.RED_STAINED_GLASS, Material.ORANGE_STAINED_GLASS,
            Material.YELLOW_STAINED_GLASS, Material.LIME_STAINED_GLASS, Material.CYAN_STAINED_GLASS,
            Material.PURPLE_STAINED_GLASS};
    private static final Material[] LIT = {Material.RED_CONCRETE, Material.ORANGE_CONCRETE,
            Material.YELLOW_CONCRETE, Material.LIME_CONCRETE, Material.CYAN_CONCRETE, Material.PURPLE_CONCRETE};
    private static final int[] PITCH = {0, 4, 7, 12, 16, 19};

    private int round;
    private int[] tune = tune(FIRST_TUNE, DIM.length);
    private boolean listening;
    private int position = -LEAD_IN_STEPS;
    private int answered;
    private int lit = -1;

    public OrganGame(Plugin plugin, Messages messages, Component title, Runnable solved) {
        super(plugin, 3, messages, title, solved);
        for (int key = 0; key < DIM.length; key++) {
            show(key, false);
        }
        replay();
    }

    static int[] tune(int length, int keys) {
        var random = ThreadLocalRandom.current();
        var notes = new int[length];
        for (int i = 0; i < length; i++) {
            int note;
            do {
                note = random.nextInt(keys);
            } while (i > 0 && note == notes[i - 1]);
            notes[i] = note;
        }
        return notes;
    }

    @Override
    protected void started(Player player) {
        repeat(player, STEP_TICKS, () -> step(player));
    }

    @Override
    protected boolean automaticClickSound(int slot, InventoryClickEvent event) {
        return !isKey(slot);
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (!isKey(slot) || listening || over()) {
            return;
        }
        int key = slot - FIRST_KEY_SLOT;
        if (key != tune[answered]) {
            Sounds.MENU_DENY.play(player);
            Sounds.NOTE_BASS.note(player, 0);
            replay();
            return;
        }
        light(player, key);
        if (++answered < tune.length) {
            return;
        }
        if (++round == ROUNDS) {
            solve(player);
            return;
        }
        Sounds.LEVEL_UP.play(player);
        tune = tune(FIRST_TUNE + round, DIM.length);
        replay();
    }

    private void replay() {
        dim();
        listening = true;
        position = -LEAD_IN_STEPS;
        answered = 0;
        status();
    }

    private void step(Player player) {
        if (lit >= 0) {
            dim();
            return;
        }
        if (!listening) {
            return;
        }
        if (position < 0) {
            position++;
        } else if (position == tune.length) {
            listening = false;
            status();
        } else {
            light(player, tune[position++]);
        }
    }

    private void light(Player player, int key) {
        dim();
        lit = key;
        show(key, true);
        Sounds.NOTE_PLING.note(player, PITCH[key]);
    }

    private void dim() {
        if (lit >= 0) {
            show(lit, false);
            lit = -1;
        }
    }

    private void show(int key, boolean on) {
        set(FIRST_KEY_SLOT + key, Items.of((on ? LIT : DIM)[key]).name(messages.get("tasks.games.organ.key")).build());
    }

    private void status() {
        var name = messages.get(listening ? "tasks.games.organ.listen" : "tasks.games.organ.repeat",
                Formatter.number("round", round + 1), Formatter.number("rounds", ROUNDS));
        info(STATUS_SLOT, Items.of(Material.NOTE_BLOCK).name(name).build());
    }

    private static boolean isKey(int slot) {
        return slot >= FIRST_KEY_SLOT && slot < FIRST_KEY_SLOT + DIM.length;
    }
}
