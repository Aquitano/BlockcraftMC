package me.blockcraft.rush.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.message.Messages;
import me.blockcraft.rush.kit.Perk;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ShopMenu extends Menu {

    private static final int BALANCE = 1;
    private static final int ENCHANT = 8;

    private final RushMenus menus;
    private final Messages messages;
    private final Set<Perk> owned;
    private ShopCategory category;
    private final Map<Integer, ShopEntry> entries = new HashMap<>();

    ShopMenu(RushMenus menus, Messages messages, ShopCategory category, List<ShopEntry> stock, Set<Perk> owned, Player player) {
        super(2, messages.get("shop.browse-title"));
        this.menus = menus;
        this.messages = messages;
        this.owned = Set.copyOf(owned);
        closeButton(17);
        this.category = category;
        set(ENCHANT, Items.of(Material.ENCHANTING_TABLE).name(messages.get("shop.nav.enchant")).build());
        stock.forEach(entry -> entries.put(entry.slot(), entry));
        refresh(player);
    }

    private void refresh(Player player) {
        for (var tab : ShopCategory.values()) {
            var icon = Items.of(tab.icon()).name(messages.get("shop.nav." + tab.key()));
            if (tab == category) {
                info(tab.navSlot(), icon.glow().build());
            } else {
                set(tab.navSlot(), icon.build());
            }
        }
        int balance = menus.balance(player);
        info(BALANCE, Items.of(Material.EMERALD)
                .name(messages.get("shop.balance", Formatter.number("emeralds", balance))).build());
        for (int slot = 9; slot < 17; slot++) {
            var entry = entries.get(slot);
            if (entry == null) {
                set(slot, null);
                continue;
            }
            var state = messages.get("shop.available");
            if (!entry.hasCapacity().test(player)) {
                state = messages.get("shop.no-space");
            } else if (balance < entry.price()) {
                state = messages.get("shop.missing", Formatter.number("emeralds", entry.price() - balance));
            }
            set(entry.slot(), Items.of(entry.icon().clone())
                    .lore(messages.get("shop.price", Formatter.number("price", entry.price())), state)
                    .build());
        }
    }

    @Override
    protected boolean automaticClickSound(int slot, InventoryClickEvent event) {
        return !entries.containsKey(slot);
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot == ENCHANT) {
            menus.teleportToEnchant(player);
            return;
        }
        ShopCategory.byNavSlot(slot).ifPresentOrElse(selected -> {
            category = selected;
            entries.clear();
            menus.stock(category, owned).forEach(entry -> entries.put(entry.slot(), entry));
            refresh(player);
        }, () -> {
            var entry = entries.get(slot);
            if (entry != null) {
                menus.buy(player, entry);
                refresh(player);
            }
        });
    }
}
