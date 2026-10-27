package me.blockcraft.archive.menu;

import me.blockcraft.api.menu.ListMenu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.archive.exhibit.Era;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** The old servers the archive keeps maps of; the last row's first slot leads back to the entrance hall. */
final class EraMenu extends ListMenu<Era> {

    private final ArchiveMenus menus;

    EraMenu(ArchiveMenus menus, List<Era> eras) {
        super(rowsFor(eras.size()), menus.messages().get("menu.eras.title"));
        this.menus = menus;
        var messages = menus.messages();
        set(lastRowSlot(), MenuIcon.ARCHIVE_ENTRANCE.item().name(messages.get("menu.entrance"))
                .lore(messages.getLines("menu.entrance-lore")).build());
        closeButton();
        show(eras);
    }

    @Override
    protected ItemStack icon(Era era) {
        var messages = menus.messages();
        var lore = new ArrayList<Component>();
        var from = era.from();
        var until = era.until();
        if (from.isPresent() && from.getAsInt() == until.getAsInt()) {
            lore.add(messages.get("menu.era.year", Formatter.number("year", from.getAsInt())));
        } else if (from.isPresent()) {
            lore.add(messages.get("menu.era.years", Formatter.number("from", from.getAsInt()),
                    Formatter.number("until", until.getAsInt())));
        }
        lore.add(messages.get(era.exhibits().size() == 1 ? "menu.era.one-exhibit" : "menu.era.exhibits",
                Formatter.number("count", era.exhibits().size())));
        lore.add(messages.get("menu.era.open"));
        return MenuIcon.ARCHIVE.item().name(messages.get("menu.era.name", Placeholder.unparsed("origin", era.origin())))
                .lore(lore).build();
    }

    @Override
    protected Component empty() {
        return menus.messages().get("menu.eras.empty");
    }

    @Override
    protected void onSelect(Player player, Era era, InventoryClickEvent event) {
        menus.openEra(player, era.origin());
    }

    @Override
    protected void onButton(Player player, int slot, InventoryClickEvent event) {
        if (slot == lastRowSlot()) {
            menus.toEntrance(player);
        }
    }
}
