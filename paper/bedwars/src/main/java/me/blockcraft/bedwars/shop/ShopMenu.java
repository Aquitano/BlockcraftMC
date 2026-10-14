package me.blockcraft.bedwars.shop;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The categories across the first row, the chosen one's offers centred below it. A click buys once, a shift-click
 * as often as the offer and the resources allow, and a number key buys one into that hotbar slot.
 */
final class ShopMenu extends Menu {

    private static final int COLUMNS = 9;

    private final Shop shop;
    private final Messages messages;
    private final Player buyer;
    private final int categoryRows;
    private final Map<Integer, ShopCatalog.Category> categories = new HashMap<>();
    private final Map<Integer, Offer> offers = new HashMap<>();
    private Optional<ShopCatalog.Category> selected = Optional.empty();

    ShopMenu(Shop shop, Messages messages, ShopCatalog catalog, Player buyer) {
        super(rows(catalog), messages.get("shop.title"));
        this.shop = shop;
        this.messages = messages;
        this.buyer = buyer;
        this.categoryRows = Slots.rows(catalog.categories().size());
        var slots = Slots.centered(catalog.categories().size());
        for (int index = 0; index < slots.length; index++) {
            categories.put(slots[index], catalog.categories().get(index));
        }
        draw();
    }

    private static int rows(ShopCatalog catalog) {
        int offerRows = catalog.categories().stream().mapToInt(category -> Slots.rows(category.offers().size())).max().orElse(1);
        return Slots.rows(catalog.categories().size()) + offerRows;
    }

    private void draw() {
        categories.forEach((slot, category) -> {
            var icon = Items.of(category.icon()).name(messages.get("shop.categories." + category.key())).hideAttributes();
            set(slot, selected.filter(category::equals).isPresent() ? icon.glow().build() : icon.build());
        });
        offers.keySet().forEach(slot -> set(slot, null));
        offers.clear();
        selected.ifPresent(category -> {
            var slots = Slots.centered(category.offers().size());
            for (int index = 0; index < slots.length; index++) {
                int slot = categoryRows * COLUMNS + slots[index];
                category.offers().get(index).ifPresent(offer -> {
                    offers.put(slot, offer);
                    set(slot, shop.display(buyer, offer));
                });
            }
        });
    }

    @Override
    protected boolean accepts(ClickType click) {
        return switch (click) {
            case LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT, NUMBER_KEY -> true;
            default -> false;
        };
    }

    @Override
    protected Sounds clickSound(int slot, InventoryClickEvent event) {
        var offer = offers.get(slot);
        if (offer == null) {
            return Sounds.MENU_CLICK;
        }
        return shop.plan(buyer, offer, event.isShiftClick(), event.getHotbarButton()) instanceof Shop.Purchase.Done
                ? Sounds.MENU_CLICK
                : Sounds.MENU_DENY;
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        var category = categories.get(slot);
        if (category != null) {
            selected = Optional.of(category);
            draw();
            return;
        }
        var offer = offers.get(slot);
        if (offer != null) {
            shop.buy(player, offer, event.isShiftClick(), event.getHotbarButton());
        }
    }
}
