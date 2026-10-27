package me.blockcraft.archive.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.ListMenu;
import me.blockcraft.archive.exhibit.Era;
import me.blockcraft.archive.exhibit.Exhibit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;

/** The exhibits of one old server, each shown as the item its exhibit.yml names. */
final class ExhibitListMenu extends ListMenu<Exhibit> {

    private final ArchiveMenus menus;

    ExhibitListMenu(ArchiveMenus menus, Era era) {
        super(rowsFor(era.exhibits().size()), menus.messages().get("menu.era.title", Placeholder.unparsed("origin", era.origin())));
        this.menus = menus;
        backButton(menus::openEras);
        show(era.exhibits());
    }

    @Override
    protected ItemStack icon(Exhibit exhibit) {
        var messages = menus.messages();
        var lore = new ArrayList<Component>();
        exhibit.year().ifPresent(year -> lore.add(messages.get("menu.exhibit.year",
                Placeholder.unparsed("origin", exhibit.origin()), Formatter.number("year", year))));
        lore.addAll(menus.text(exhibit.description()));
        lore.add(messages.get(exhibit.checkpoints().size() == 1 ? "menu.exhibit.one-checkpoint" : "menu.exhibit.checkpoints",
                Formatter.number("count", exhibit.checkpoints().size())));
        lore.add(messages.get("menu.exhibit.open"));
        var icon = exhibit.icon().isItem() ? exhibit.icon() : Material.PAPER;
        return Items.of(icon).name(messages.get("menu.exhibit.name", Placeholder.unparsed("exhibit", exhibit.name())))
                .lore(lore).hideAttributes().build();
    }

    @Override
    protected void onSelect(Player player, Exhibit exhibit, InventoryClickEvent event) {
        menus.openExhibit(player, exhibit.key());
    }
}
