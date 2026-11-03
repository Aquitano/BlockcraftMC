package me.blockcraft.ttt.task.game;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.ThreadLocalRandom;

/**
 * A timing chore: a marker sweeps along a row and a click must land while it is on the notch. Each hit locks a gear
 * and speeds the sweep up, a miss loosens them all. The sweep runs on one 1-tick clock so a round can change its speed
 * without a second task.
 */
public final class MillworkGame extends ChoreGame {

    private static final int WIDTH = 9;
    private static final int NOTCH_ROW = 0;
    private static final int MARKER_ROW = WIDTH;
    private static final int GEAR_FIRST = 21;
    private static final int[] TICKS_PER_STEP = {5, 4, 3};

    private final ItemStack off = Items.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build();
    private final ItemStack notchItem;
    private final ItemStack markerItem;
    private int gears;
    private int notch;
    private int tick;
    private int drawnMarker = -1;

    public MillworkGame(Plugin plugin, Messages messages, Component title, Runnable solved) {
        super(plugin, 3, messages, title, solved);
        notchItem = Items.of(Material.LIME_STAINED_GLASS_PANE).name(messages.get("tasks.games.millwork.notch")).build();
        markerItem = Items.of(Material.GOLD_BLOCK).name(messages.get("tasks.games.millwork.marker")).build();
        nextRound();
        drawGears();
        drawSweep();
    }

    @Override
    protected void started(Player player) {
        repeat(player, 1, () -> {
            tick++;
            if (markerPosition(tick, TICKS_PER_STEP[gears], WIDTH) != drawnMarker) {
                drawSweep();
            }
        });
    }

    @Override
    protected boolean automaticClickSound(int slot, InventoryClickEvent event) {
        return slot >= MARKER_ROW + WIDTH;
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot >= MARKER_ROW + WIDTH || over()) {
            return;
        }
        if (markerPosition(tick, TICKS_PER_STEP[gears], WIDTH) != notch) {
            gears = 0;
            Sounds.MENU_DENY.play(player);
        } else {
            gears++;
            Sounds.NOTE_PLING.note(player, gears * 4);
            player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 1f);
        }
        drawGears();
        if (gears == TICKS_PER_STEP.length) {
            solve(player);
            return;
        }
        nextRound();
        drawSweep();
    }

    static int markerPosition(int tick, int ticksPerStep, int width) {
        int period = 2 * (width - 1);
        int step = (tick / ticksPerStep) % period;
        return step < width ? step : period - step;
    }

    private void nextRound() {
        int previous = notch;
        do {
            notch = ThreadLocalRandom.current().nextInt(2, WIDTH);
        } while (notch == previous);
        tick = 0;
    }

    private void drawSweep() {
        int marker = markerPosition(tick, TICKS_PER_STEP[gears], WIDTH);
        drawnMarker = marker;
        for (int column = 0; column < WIDTH; column++) {
            set(NOTCH_ROW + column, column == notch ? notchItem : off);
            set(MARKER_ROW + column, column == marker ? markerItem : off);
        }
    }

    private void drawGears() {
        for (int gear = 0; gear < TICKS_PER_STEP.length; gear++) {
            var key = gear < gears ? "tasks.games.millwork.gear" : "tasks.games.millwork.gap";
            var material = gear < gears ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
            set(GEAR_FIRST + gear, Items.of(material).name(messages.get(key)).build());
        }
    }
}
