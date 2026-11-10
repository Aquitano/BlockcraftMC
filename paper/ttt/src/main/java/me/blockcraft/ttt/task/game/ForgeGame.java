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

/**
 * A chore at the forge: the bellows pump a nine-slot heat gauge that cools on its own, and the fire has to stay in the
 * green band for a while. Leaving the band only pauses the count, so a bad moment costs time and not the whole attempt.
 */
public final class ForgeGame extends ChoreGame {

    private static final int SLOTS = 9;
    private static final int BAND_START = 3;
    private static final int BAND_END = 5;
    private static final int COOLED_AFTER_FLARE = 2;
    private static final int GOAL_STEPS = 9;
    private static final int STEP_TICKS = 10;
    private static final int BAND_ROW = SLOTS;
    private static final int HEAT_ROW = 2 * SLOTS;
    private static final int BELLOWS = 3 * SLOTS + 4;

    private int heat;
    private int steps;

    public ForgeGame(Plugin plugin, Messages messages, Component title, Runnable solved) {
        super(plugin, 4, messages, title, solved);
        for (int i = 0; i < SLOTS; i++) {
            set(BAND_ROW + i, i >= BAND_START && i <= BAND_END
                    ? gauge(Material.LIME_STAINED_GLASS_PANE, "band")
                    : gauge(Material.GRAY_STAINED_GLASS_PANE, "band"));
        }
        set(BELLOWS, Items.of(Material.LEATHER).name(messages.get("tasks.games.forge.bellows")).glow().build());
        draw();
    }

    @Override
    protected void started(Player player) {
        repeat(player, STEP_TICKS, () -> {
            heat = Math.max(0, heat - 1);
            if (inBand()) {
                steps++;
            }
            draw();
            if (steps >= GOAL_STEPS) {
                player.playSound(player, Sound.BLOCK_FIRE_AMBIENT, 1f, 1f);
                solve(player);
            }
        });
    }

    @Override
    protected boolean automaticClickSound(int slot, InventoryClickEvent event) {
        return slot != BELLOWS;
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot != BELLOWS || over()) {
            return;
        }
        heat++;
        if (heat >= SLOTS) {
            heat = COOLED_AFTER_FLARE;
            Sounds.MENU_DENY.play(player);
            player.playSound(player, Sound.ENTITY_BLAZE_SHOOT, 0.6f, 1f);
        } else {
            player.playSound(player, Sound.ENTITY_BLAZE_SHOOT, 0.4f, 0.5f + heat * 0.1f);
        }
        draw();
    }

    private boolean inBand() {
        return heat - 1 >= BAND_START && heat - 1 <= BAND_END;
    }

    private void draw() {
        for (int i = 0; i < SLOTS; i++) {
            set(HEAT_ROW + i, i < heat
                    ? gauge(i >= BAND_START && i <= BAND_END ? Material.ORANGE_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE, "heat")
                    : gauge(Material.GRAY_STAINED_GLASS_PANE, "heat"));
            set(i, i < steps
                    ? gauge(Material.LIME_STAINED_GLASS_PANE, "progress")
                    : gauge(Material.BLACK_STAINED_GLASS_PANE, "progress"));
        }
    }

    private ItemStack gauge(Material material, String name) {
        return Items.of(material).name(messages.get("tasks.games.forge." + name)).build();
    }
}
