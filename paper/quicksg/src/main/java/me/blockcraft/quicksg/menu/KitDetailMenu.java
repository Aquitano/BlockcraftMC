package me.blockcraft.quicksg.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.quicksg.kit.Kit;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

final class KitDetailMenu extends Menu {

    private static final int SLOT_SELECT = 3;
    private static final int SLOT_BUY = 6;
    private static final int BACK = 8;

    private final KitMenus menus;
    private final Kit kit;
    private final boolean owned;

    KitDetailMenu(KitMenus menus, Kit kit, boolean owned, Player player) {
        super(1, menus.messages().get("menu.kit-detail.title", menus.kitName(kit)));
        this.menus = menus;
        this.kit = kit;
        this.owned = owned;
        Messages messages = menus.messages();
        var icon = Items.of(kit.icon())
                .name(messages.get("menu.kits.entry", menus.kitName(kit)))
                .lore(messages.getLines(kit.messageKey() + ".contents"));
        if (kit == Kit.DIAMOND) {
            icon.glow();
        }
        info(0, icon.build());
        info(1, Items.of(Material.NAME_TAG)
                .name(messages.get("menu.kit-detail.price"))
                .lore(kit.free()
                        ? messages.get("menu.kit-detail.price-free")
                        : messages.get("menu.kit-detail.price-coins", Formatter.number("price", menus.price())))
                .build());
        refresh(player);
        if (!kit.free()) {
            if (owned) {
                info(SLOT_BUY, Items.of(Material.ORANGE_STAINED_GLASS_PANE).name(messages.get("menu.kit-detail.bought")).build());
            } else {
                set(SLOT_BUY, Items.of(Material.LIME_STAINED_GLASS_PANE).name(messages.get("menu.kit-detail.buy")).build());
            }
        }
        backButton(BACK, menus::openSelector);
    }

    private void refresh(Player player) {
        if (owned) {
            var messages = menus.messages();
            if (menus.selected(player, kit)) {
                info(SLOT_SELECT, Items.of(Material.LIME_DYE).name(messages.get("menu.kit-detail.selected")).glow().build());
            } else {
                set(SLOT_SELECT, Items.of(Material.LIME_DYE).name(messages.get("menu.kit-detail.select")).build());
            }
        }
    }

    @Override
    protected Sounds clickSound(int slot, InventoryClickEvent event) {
        return slot == SLOT_SELECT ? Sounds.EQUIP : Sounds.MENU_CLICK;
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        switch (slot) {
            case SLOT_SELECT -> {
                menus.select(player, kit, owned);
                refresh(player);
            }
            case SLOT_BUY -> {
                if (!owned && !kit.free()) {
                    menus.buy(player, kit);
                }
            }
            default -> {
            }
        }
    }
}
