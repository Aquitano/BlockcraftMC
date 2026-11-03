package me.blockcraft.ttt.shop;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.message.Messages;
import me.blockcraft.ttt.task.Sabotage;
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
import java.util.Optional;

/**
 * The role's goods from the first row on, each with its price and whether the points reach; a click buys. In TTT 2 a
 * traitor's shop has a row of sabotage below the goods, free but resting between uses.
 */
public final class ShopMenu extends Menu {

    /** What the round makes of a purchase; the menu only shows it. */
    public interface Till {
        int points(Player player);

        int price(ShopItem item);

        void buy(Player player, ShopItem item);

        void sabotage(Player player, Sabotage.Kind kind);
    }

    private static final int COLUMNS = 9;

    private final Messages messages;
    private final Equipment equipment;
    private final Till till;
    private final Optional<Sabotage> sabotage;
    private final int balanceSlot;
    private final Map<Integer, ShopItem> goods = new HashMap<>();
    private final Map<Integer, Sabotage.Kind> sabotages = new HashMap<>();

    public ShopMenu(Messages messages, Equipment equipment, Till till, Component title, List<ShopItem> goods,
                    Optional<Sabotage> sabotage, Player viewer) {
        super(rows(goods.size(), sabotage.isPresent()), title);
        this.messages = messages;
        this.equipment = equipment;
        this.till = till;
        this.sabotage = sabotage;
        for (int index = 0; index < goods.size(); index++) {
            this.goods.put(index, goods.get(index));
        }
        int sabotageRow = goodsRows(goods.size()) * COLUMNS;
        var kinds = Sabotage.Kind.values();
        sabotage.ifPresent(ignored -> {
            for (int index = 0; index < kinds.length; index++) {
                sabotages.put(sabotageRow + 3 + index, kinds[index]);
            }
        });
        balanceSlot = rows(goods.size(), sabotage.isPresent()) * COLUMNS - 5;
        closeButton();
        refresh(viewer);
    }

    private void refresh(Player viewer) {
        int points = till.points(viewer);
        goods.forEach((slot, item) -> set(slot, icon(item, points)));
        sabotage.ifPresent(running -> sabotages.forEach((slot, kind) -> set(slot, sabotageIcon(kind, running.secondsUntilReady()))));
        info(balanceSlot, Items.of(Material.EMERALD).name(messages.get("shop.balance", Formatter.number("points", points))).build());
    }

    private ItemStack icon(ShopItem item, int points) {
        int price = till.price(item);
        var lore = new ArrayList<>(messages.getLines("shop.items." + item.key() + ".lore"));
        lore.add(Component.empty());
        lore.add(messages.get("shop.price", Formatter.number("price", price)));
        lore.add(points >= price ? messages.get("shop.buy") : messages.get("shop.missing", Formatter.number("points", price - points)));
        return Items.of(equipment.item(item, 1)).lore(lore).build();
    }

    private ItemStack sabotageIcon(Sabotage.Kind kind, int resting) {
        var lore = new ArrayList<>(messages.getLines("tasks.sabotage.kinds." + kind.key() + ".lore"));
        lore.add(Component.empty());
        lore.add(resting > 0 ? messages.get("tasks.sabotage.resting-lore", Formatter.number("seconds", resting))
                : messages.get("tasks.sabotage.ready"));
        return Items.of(kind.icon()).name(messages.get("tasks.sabotage.kinds." + kind.key() + ".name")).lore(lore).build();
    }

    @Override
    protected boolean locksPlayerInventory() {
        return false;
    }

    @Override
    protected boolean automaticClickSound(int slot, InventoryClickEvent event) {
        return !goods.containsKey(slot) && !sabotages.containsKey(slot);
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        var item = goods.get(slot);
        if (item != null) {
            till.buy(player, item);
            refresh(player);
            return;
        }
        var kind = sabotages.get(slot);
        if (kind != null) {
            till.sabotage(player, kind);
            refresh(player);
        }
    }

    private static int goodsRows(int goods) {
        return Math.max(1, (goods + COLUMNS - 1) / COLUMNS);
    }

    private static int rows(int goods, boolean sabotage) {
        return goodsRows(goods) + (sabotage ? 1 : 0) + 1;
    }
}
