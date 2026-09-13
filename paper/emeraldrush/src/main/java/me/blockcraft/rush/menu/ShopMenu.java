package me.blockcraft.rush.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ShopMenu extends Menu {

    private static final int ENCHANT = 8;

    private final RushMenus menus;
    private final Map<Integer, ShopEntry> entries = new HashMap<>();

    ShopMenu(RushMenus menus, Messages messages, ShopCategory category, List<ShopEntry> stock) {
        super(2, messages.get("shop.title", Placeholder.parsed("category", messages.raw("shop.category." + category.key()))));
        this.menus = menus;
        for (var tab : ShopCategory.values()) {
            var icon = Items.of(tab.icon()).name(messages.get("shop.nav." + tab.key()));
            if (tab == category) {
                icon.glow();
            }
            set(tab.navSlot(), icon.build());
        }
        set(ENCHANT, Items.of(Material.ENCHANTING_TABLE).name(messages.get("shop.nav.enchant")).build());
        for (var entry : stock) {
            entries.put(entry.slot(), entry);
            set(entry.slot(), entry.icon());
        }
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot == ENCHANT) {
            menus.teleportToEnchant(player);
            return;
        }
        ShopCategory.byNavSlot(slot).ifPresentOrElse(category -> menus.openShop(player, category), () -> {
            var entry = entries.get(slot);
            if (entry != null) {
                menus.buy(player, entry);
            }
        });
    }
}
