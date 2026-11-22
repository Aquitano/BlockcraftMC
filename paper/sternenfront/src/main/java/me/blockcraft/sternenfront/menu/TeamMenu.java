package me.blockcraft.sternenfront.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.pack.UiFont;
import me.blockcraft.common.message.Messages;
import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.game.Teams;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * The Kolonien on the left, the Hegemonie on the right, each a card of four columns by five rows that the pack draws
 * with the faction's soldier; its tooltip says who is in it, and a click anywhere on it joins. Without the pack, each
 * card is its banner between panes.
 */
public final class TeamMenu extends Menu {

    private static final int ROWS = 6;
    private static final int CARD_COLUMNS = 4;
    private static final int CARD_ROWS = 5;
    private static final Map<Integer, Faction> SLOTS = Map.of(9, Faction.KOLONIEN, 14, Faction.HEGEMONIE);

    private final Component title;
    private final BiConsumer<Player, Faction> choose;

    public TeamMenu(Messages messages, Teams teams, int capacity, BiConsumer<Player, Faction> choose) {
        super(ROWS, messages.get("faction.title"));
        this.title = messages.get("faction.title");
        this.choose = choose;
        closeButton(ToolbarButton.LEFT);
        SLOTS.forEach((slot, faction) -> {
            area(slot, CARD_COLUMNS, CARD_ROWS);
            var lore = new ArrayList<>(messages.getLines("faction." + faction.key() + ".lore"));
            lore.add(Component.empty());
            lore.add(messages.get("faction.size", Formatter.number("players", teams.size(faction)), Formatter.number("max", capacity)));
            teams.members(faction).stream().map(Bukkit::getOfflinePlayer).map(OfflinePlayer::getName)
                    .forEach(name -> lore.add(messages.get("faction.member", Placeholder.unparsed("player", String.valueOf(name)))));
            lore.add(Component.empty());
            lore.add(messages.get("faction.join"));
            set(slot, Items.of(Material.valueOf(faction.blocks().name() + "_BANNER"))
                    .name(messages.get("faction." + faction.key() + ".name").colorIfAbsent(faction.text()))
                    .lore(lore).hideAttributes().build());
        });
    }

    @Override
    protected Optional<Component> screen() {
        return Optional.of(UiFont.menuTitle(ROWS, true, false, UiFont.overlaid(UiFont.Overlay.STERNENFRONT_FACTIONS, title)));
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        var faction = SLOTS.get(slot);
        if (faction != null) {
            choose.accept(player, faction);
            Menu.close(player);
        }
    }
}
