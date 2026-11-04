package me.blockcraft.archive.menu;

import me.blockcraft.api.menu.ListMenu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.archive.exhibit.ExhibitCollection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

final class CollectionMenu extends ListMenu<ExhibitCollection> {

    private final ArchiveMenus menus;

    CollectionMenu(ArchiveMenus menus, List<ExhibitCollection> collections) {
        super(rowsFor(collections.size()), menus.messages().get("menu.sections.title"));
        this.menus = menus;
        set(lastRowSlot(), menus.entranceButton());
        if (!menus.suggestedExhibits().isEmpty()) {
            set(lastRowSlot() + 1, MenuIcon.ARCHIVE_POINT.item().name(menus.messages().get("menu.suggestions.name"))
                    .lore(menus.messages().getLines("menu.suggestions.lore")).build());
        }
        closeButton();
        show(collections);
    }

    @Override
    protected ItemStack icon(ExhibitCollection collection) {
        var messages = menus.messages();
        return MenuIcon.ARCHIVE.item().name(menus.collectionName(collection.origin()))
                .lore(messages.get(collection.exhibits().size() == 1 ? "menu.section.one-exhibit" : "menu.section.exhibits",
                                Formatter.number("count", collection.exhibits().size())),
                        messages.get("menu.section.open")).build();
    }

    @Override
    protected Component empty() {
        return menus.messages().get("menu.sections.empty");
    }

    @Override
    protected void onSelect(Player player, ExhibitCollection collection, InventoryClickEvent event) {
        menus.openCollection(player, collection.origin());
    }

    @Override
    protected void onButton(Player player, int slot, InventoryClickEvent event) {
        if (slot == lastRowSlot()) {
            menus.toEntrance(player);
        } else if (slot == lastRowSlot() + 1) {
            menus.openSuggestions(player);
        }
    }
}
