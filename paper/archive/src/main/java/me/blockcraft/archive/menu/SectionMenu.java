package me.blockcraft.archive.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.ListMenu;
import me.blockcraft.archive.exhibit.Section;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

final class SectionMenu extends ListMenu<Section> {

    private final ArchiveMenus menus;

    SectionMenu(ArchiveMenus menus, List<Section> sections) {
        super(rowsFor(sections.size()), menus.messages().get("menu.sections.title"));
        this.menus = menus;
        set(lastRowSlot(), menus.entranceButton());
        closeButton();
        show(sections);
    }

    @Override
    protected ItemStack icon(Section section) {
        var messages = menus.messages();
        var lore = new ArrayList<Component>();
        lore.add(messages.get("menu.section.origin", Placeholder.unparsed("origin", section.origin())));
        lore.add(messages.get(section.exhibits().size() == 1 ? "menu.section.one-exhibit" : "menu.section.exhibits",
                Formatter.number("count", section.exhibits().size())));
        lore.add(messages.get("menu.section.open"));
        var icon = section.exhibits().getFirst().icon();
        return Items.of(icon.isItem() ? icon : Material.PAPER).name(menus.sectionName(section)).lore(lore).hideAttributes().build();
    }

    @Override
    protected Component empty() {
        return menus.messages().get("menu.sections.empty");
    }

    @Override
    protected void onSelect(Player player, Section section, InventoryClickEvent event) {
        new ExhibitListMenu(menus, section, false).open(player);
    }

    @Override
    protected void onButton(Player player, int slot, InventoryClickEvent event) {
        if (slot == lastRowSlot()) {
            menus.toEntrance(player);
        }
    }
}
