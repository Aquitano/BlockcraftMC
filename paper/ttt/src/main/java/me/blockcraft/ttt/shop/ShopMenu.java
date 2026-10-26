package me.blockcraft.ttt.shop;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The role's goods in the first row, each with its price and whether the points reach; a click buys. */
public final class ShopMenu extends Menu {

    /** What the round makes of a purchase; the menu only shows it. */
    public interface Till {
        int points(Player player);

        int price(ShopItem item);

        void buy(Player player, ShopItem item);
    }

    private static final int FIRST_SLOT = 0;
    private static final int BALANCE_SLOT = 13;

    private final Messages messages;
    private final Equipment equipment;
    private final Till till;
    private final Map<Integer, ShopItem> slots = new HashMap<>();

    public ShopMenu(Messages messages, Equipment equipment, Till till, Component title, List<ShopItem> goods, Player viewer) {
        super(2, title);
        this.messages = messages;
        this.equipment = equipment;
        this.till = till;
        for (int index = 0; index < goods.size(); index++) {
            slots.put(FIRST_SLOT + index, goods.get(index));
        }
        closeButton();
        refresh(viewer);
    }

    private void refresh(Player viewer) {
        int points = till.points(viewer);
        slots.forEach((slot, item) -> set(slot, icon(item, points)));
        info(BALANCE_SLOT, Items.of(Material.EMERALD).name(messages.get("shop.balance", Formatter.number("points", points))).build());
    }

    private ItemStack icon(ShopItem item, int points) {
        int price = till.price(item);
        var lore = new ArrayList<>(messages.getLines("shop.items." + item.key() + ".lore"));
        lore.add(Component.empty());
        lore.add(messages.get("shop.price", Formatter.number("price", price)));
        lore.add(points >= price ? messages.get("shop.buy") : messages.get("shop.missing", Formatter.number("points", price - points)));
        return Items.of(equipment.item(item, 1)).lore(lore).build();
    }

    @Override
    protected boolean locksPlayerInventory() {
        return false;
    }

    @Override
    protected boolean automaticClickSound(int slot, InventoryClickEvent event) {
        return !slots.containsKey(slot);
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        var item = slots.get(slot);
        if (item != null) {
            till.buy(player, item);
            refresh(player);
        }
    }
}
