package me.blockcraft.sternenfront.menu;

import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.common.message.Messages;
import me.blockcraft.sternenfront.game.CombatClass;
import me.blockcraft.sternenfront.game.Loadouts;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** The four classes in the lobby, each as its blaster with what it carries; a click picks the one to start as. */
public final class ClassMenu extends Menu {

    private static final int ROWS = 3;
    static final List<Integer> SLOTS = List.of(10, 12, 14, 16);

    private final BiConsumer<Player, CombatClass> choose;
    private final Optional<Consumer<Player>> difficulty;

    public ClassMenu(Messages messages, Loadouts loadouts, CombatClass selected, BiConsumer<Player, CombatClass> choose,
                     Optional<Consumer<Player>> difficulty) {
        super(ROWS, messages.get("class.title"));
        this.choose = choose;
        this.difficulty = difficulty;
        var classes = CombatClass.values();
        for (int index = 0; index < classes.length; index++) {
            var combatClass = classes[index];
            boolean chosen = combatClass == selected;
            set(SLOTS.get(index), loadouts.card(combatClass, chosen,
                    List.of(messages.get(chosen ? "class.selected" : "class.select"))));
        }
        difficulty.ifPresent(open -> set(22, MenuIcon.TOOL_SETTINGS.item().name(messages.get("bot.difficulty.open"))
                .lore(messages.getLines("bot.difficulty.open-lore")).build()));
        closeButton();
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot == 22) {
            difficulty.ifPresent(open -> open.accept(player));
            return;
        }
        int index = SLOTS.indexOf(slot);
        if (index >= 0) {
            choose.accept(player, CombatClass.values()[index]);
            Menu.close(player);
        }
    }
}
