package me.blockcraft.ttt.task;

import me.blockcraft.api.menu.Menu;
import me.blockcraft.ttt.arena.Chore;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Consumer;

final class TaskMenu extends Menu {

    record Choice(Chore chore, ItemStack icon) {
    }

    private final List<Choice> choices;
    private final Consumer<Chore> select;

    TaskMenu(Component title, List<Choice> choices, Consumer<Chore> select) {
        super(Math.max(1, (choices.size() + 8) / 9) + 1, title);
        this.choices = List.copyOf(choices);
        this.select = select;
        for (int slot = 0; slot < choices.size(); slot++) {
            set(slot, choices.get(slot).icon());
        }
        closeButton();
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot < 0 || slot >= choices.size()) {
            return;
        }
        var chore = choices.get(slot).chore();
        close(player, ignored -> select.accept(chore));
    }
}
