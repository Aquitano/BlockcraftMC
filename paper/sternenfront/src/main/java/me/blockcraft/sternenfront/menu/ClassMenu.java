package me.blockcraft.sternenfront.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.pack.MenuTile;
import me.blockcraft.api.pack.UiFont;
import me.blockcraft.common.message.Messages;
import me.blockcraft.sternenfront.game.CombatClass;
import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.game.Loadouts;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The four roles in the lobby as the shown faction fields them, as portrait cards; a click selects one and the menu
 * stays open. Under a divider, sunk into the panel so it reads as no choice, is what the selected role carries: its
 * blaster, its abilities on F and Q and its stats. A player without a faction yet sees the one that would seat them
 * now, and the banner in the toolbar says so.
 */
public final class ClassMenu extends Menu {

    private static final int ROWS = 6;
    static final List<Integer> SLOTS = List.of(18, 23, 27, 32);
    private static final int BLASTER_SLOT = 46;
    private static final List<Integer> ABILITY_SLOTS = List.of(48, 50);
    private static final int STATS_SLOT = 52;

    private final Messages messages;
    private final Loadouts loadouts;
    private final Faction faction;
    private final Supplier<CombatClass> selected;
    private final BiConsumer<Player, CombatClass> choose;

    public ClassMenu(Messages messages, Loadouts loadouts, Faction faction, boolean factionChosen, Supplier<CombatClass> selected,
                     BiConsumer<Player, CombatClass> choose, Optional<Consumer<Player>> difficulty) {
        super(ROWS, UiFont.overlaid(difficulty.isPresent() ? UiFont.Overlay.STERNENFRONT_CLASS_WITH_BOTS : UiFont.Overlay.STERNENFRONT_CLASS,
                messages.get("class.title")));
        this.messages = messages;
        this.loadouts = loadouts;
        this.faction = faction;
        this.selected = selected;
        this.choose = choose;
        closeButton(ToolbarButton.LEFT);
        var name = Placeholder.component("faction", messages.get("faction." + faction.key() + ".name").colorIfAbsent(faction.text()));
        toolbar(ToolbarButton.MIDDLE, Items.of(Material.valueOf(faction.blocks().name() + "_BANNER")).name(messages.get("class.faction", name))
                .lore(messages.getLines(factionChosen ? "class.faction-chosen" : "class.faction-likely")).hideAttributes().build(), ignored -> { });
        difficulty.ifPresent(open -> toolbar(ToolbarButton.RIGHT, MenuIcon.TOOL_SETTINGS.item().name(messages.get("bot.difficulty.open"))
                .lore(messages.getLines("bot.difficulty.open-lore")).build(), open));
        var classes = CombatClass.values();
        for (int index = 0; index < classes.length; index++) {
            tile(SLOTS.get(index), tileOf(faction, classes[index]), MenuTile.Shape.WIDE, null);
        }
        refresh();
    }

    static MenuTile tileOf(Faction faction, CombatClass combatClass) {
        return MenuTile.valueOf("STERNENFRONT_" + faction.name() + "_" + combatClass.name());
    }

    private void refresh() {
        var current = selected.get();
        var classes = CombatClass.values();
        for (int index = 0; index < classes.length; index++) {
            boolean chosen = classes[index] == current;
            set(SLOTS.get(index), loadouts.roleCard(faction, classes[index], chosen,
                    List.of(messages.get(chosen ? "class.selected" : "class.select"))));
        }
        info(BLASTER_SLOT, loadouts.blaster(current.blaster(faction)));
        var abilities = current.abilities(faction);
        for (int key = 0; key < ABILITY_SLOTS.size(); key++) {
            info(ABILITY_SLOTS.get(key), key < abilities.size() ? loadouts.abilityCard(faction, abilities.get(key), key) : null);
        }
        info(STATS_SLOT, loadouts.statsCard(faction, current));
    }

    @Override
    protected boolean isEnabled(Player player, int slot, InventoryClickEvent event) {
        return !ToolbarButton.MIDDLE.covers(slot);
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        int index = SLOTS.indexOf(slot);
        if (index >= 0) {
            choose.accept(player, CombatClass.values()[index]);
            refresh();
        }
    }
}
