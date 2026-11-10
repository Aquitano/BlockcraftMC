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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A brewing chore: the recipe card lists the ingredients in order, the shelf below holds them among decoys. A wrong
 * ingredient spoils the brew and the same recipe starts over, so a slip costs time but never the recipe.
 */
public final class PotionGame extends ChoreGame {

    private static final List<Material> INGREDIENTS = List.of(
            Material.NETHER_WART, Material.GLISTERING_MELON_SLICE, Material.SUGAR, Material.GLOWSTONE_DUST,
            Material.REDSTONE, Material.BLAZE_POWDER, Material.SPIDER_EYE, Material.RABBIT_FOOT, Material.GHAST_TEAR,
            Material.MAGMA_CREAM, Material.GOLDEN_CARROT, Material.FERMENTED_SPIDER_EYE, Material.PHANTOM_MEMBRANE);
    private static final int SHELF_SIZE = 8;
    private static final int RECIPE_FIRST_SLOT = 2;
    private static final int SHELF_FIRST_SLOT = 19;

    private final List<Material> recipe;
    private final Map<Integer, Material> shelf = new HashMap<>();
    private int brewed;

    public PotionGame(Plugin plugin, Messages messages, Component title, Runnable solved) {
        super(plugin, 4, messages, title, solved);
        var shuffled = new ArrayList<>(INGREDIENTS);
        Collections.shuffle(shuffled, ThreadLocalRandom.current());
        recipe = List.copyOf(shuffled.subList(0, ThreadLocalRandom.current().nextInt(3, 5)));
        var onShelf = new ArrayList<>(shuffled.subList(0, SHELF_SIZE));
        Collections.shuffle(onShelf, ThreadLocalRandom.current());
        for (int index = 0; index < SHELF_SIZE; index++) {
            shelf.put(SHELF_FIRST_SLOT + index, onShelf.get(index));
        }
        shelf.forEach((slot, material) -> set(slot, Items.of(material).build()));
        drawRecipe();
    }

    @Override
    protected boolean automaticClickSound(int slot, InventoryClickEvent event) {
        return !shelf.containsKey(slot);
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        var ingredient = shelf.get(slot);
        if (ingredient == null || over()) {
            return;
        }
        if (ingredient != recipe.get(brewed)) {
            player.playSound(player, Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 0.6f, 1f);
            Sounds.MENU_DENY.play(player);
            brewed = 0;
            drawRecipe();
            return;
        }
        player.playSound(player, Sound.BLOCK_BREWING_STAND_BREW, 0.8f, 1f);
        Sounds.NOTE_PLING.note(player, brewed * 3);
        brewed++;
        drawRecipe();
        if (brewed == recipe.size()) {
            solve(player);
        }
    }

    private void drawRecipe() {
        for (int step = 0; step < recipe.size(); step++) {
            var ingredient = Component.translatable(recipe.get(step).translationKey());
            var resolver = Placeholder.component("ingredient", ingredient);
            var card = step < brewed
                    ? Items.of(Material.LIME_STAINED_GLASS_PANE).name(messages.get("tasks.games.potion.done", resolver)).build()
                    : Items.of(recipe.get(step)).lore(messages.get("tasks.games.potion.step", Formatter.number("step", step + 1))).build();
            set(RECIPE_FIRST_SLOT + step, card);
        }
    }
}
