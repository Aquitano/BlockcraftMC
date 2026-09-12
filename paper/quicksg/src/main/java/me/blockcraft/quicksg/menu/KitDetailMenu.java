package me.blockcraft.quicksg.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
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

    private final KitMenus menus;
    private final Kit kit;
    private final boolean owned;

    KitDetailMenu(KitMenus menus, Kit kit, boolean owned) {
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
        set(0, icon.build());
        set(1, Items.of(Material.NAME_TAG)
                .name(messages.get("menu.kit-detail.price"))
                .lore(kit.free()
                        ? messages.get("menu.kit-detail.price-free")
                        : messages.get("menu.kit-detail.price-coins", Formatter.number("price", menus.price())))
                .build());
        set(SLOT_SELECT, Items.of(Material.LEAD).name(messages.get("menu.kit-detail.select")).build());
        if (!kit.free()) {
            set(SLOT_BUY, owned
                    ? Items.of(Material.ORANGE_STAINED_GLASS_PANE).name(messages.get("menu.kit-detail.bought")).build()
                    : Items.of(Material.LIME_STAINED_GLASS_PANE).name(messages.get("menu.kit-detail.buy")).build());
        }
        var back = Items.of(Material.RED_STAINED_GLASS_PANE).name(messages.get("menu.kit-detail.back")).build();
        set(7, back);
        set(8, back);
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        switch (slot) {
            case SLOT_SELECT -> menus.select(player, kit, owned);
            case SLOT_BUY -> menus.buy(player, kit, owned);
            case 7, 8 -> menus.openSelector(player);
            default -> {
            }
        }
    }
}
