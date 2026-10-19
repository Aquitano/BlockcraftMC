package me.blockcraft.quicksg.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.quicksg.kit.Kit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Set;

final class KitSelectorMenu extends Menu {


    private static final int FIRST_KIT = 11;

    private final KitMenus menus;

    KitSelectorMenu(KitMenus menus, Set<Kit> owned, Kit selected) {
        super(3, menus.messages().get("menu.kits.title"));
        this.menus = menus;
        var messages = menus.messages();
        for (var kit : Kit.values()) {
            var lore = new ArrayList<>(messages.getLines(kit.messageKey() + ".contents"));
            lore.add(Component.empty());
            boolean unlocked = kit.free() || owned.contains(kit);
            lore.add(messages.get(kit == selected ? "menu.kits.selected" : unlocked ? "menu.kits.owned" : "menu.kits.locked",
                    Formatter.number("price", menus.price())));
            lore.add(messages.get("menu.kits.inspect"));
            var icon = Items.of(kit.icon()).name(messages.get("menu.kits.entry", menus.kitName(kit)))
                    .lore(lore).hideAttributes();
            if (kit == selected) {
                icon.glow();
            }
            set(FIRST_KIT + kit.ordinal(), icon.build());
        }
        closeButton();
    }

    @Override
    protected boolean fillEmptySlots() {
        return false;
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        Kit.bySlot(slot - FIRST_KIT).ifPresentOrElse(kit -> menus.openDetail(player, kit), () -> close(player));
    }
}
