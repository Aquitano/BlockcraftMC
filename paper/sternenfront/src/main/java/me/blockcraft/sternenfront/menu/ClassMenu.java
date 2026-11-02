package me.blockcraft.sternenfront.menu;

import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.message.Messages;
import me.blockcraft.sternenfront.game.CombatClass;
import me.blockcraft.sternenfront.game.Loadouts;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.BiConsumer;

/** The four classes in the lobby, each as its blaster with what it carries; a click picks the one to start as. */
public final class ClassMenu extends Menu {

    private static final int ROWS = 3;
    static final List<Integer> SLOTS = List.of(10, 12, 14, 16);

    private final BiConsumer<Player, CombatClass> choose;

    public ClassMenu(Messages messages, Loadouts loadouts, CombatClass selected, BiConsumer<Player, CombatClass> choose) {
        super(ROWS, messages.get("class.title"));
        this.choose = choose;
        var classes = CombatClass.values();
        for (int index = 0; index < classes.length; index++) {
            var combatClass = classes[index];
            boolean chosen = combatClass == selected;
            set(SLOTS.get(index), loadouts.card(combatClass, chosen,
                    List.of(messages.get(chosen ? "class.selected" : "class.select"))));
        }
        closeButton();
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        int index = SLOTS.indexOf(slot);
        if (index >= 0) {
            choose.accept(player, CombatClass.values()[index]);
            Menu.close(player);
        }
    }
}
