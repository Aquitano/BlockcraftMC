package me.blockcraft.lobby.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.lobby.LobbyPermissions;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public final class ShopMenu extends Menu {

    private static final int HEADS_SLOT = 1;
    private static final int SHIRTS_SLOT = 4;
    private static final int PREMIUM_SLOT = 7;
    public static final String PREMIUM_ITEM = "lobby.premium";
    private static final Color SHIRT_ICON_COLOR = Color.fromRGB(0, 102, 204);

    private final Menus menus;

    ShopMenu(Menus menus) {
        super(1, menus.messages().get("shop.title"));
        this.menus = menus;
        var messages = menus.messages();
        set(HEADS_SLOT, Items.of(Material.PLAYER_HEAD).name(messages.get("shop.heads")).lore(messages.get("shop.heads-lore")).build());
        set(SHIRTS_SLOT, Items.of(Material.LEATHER_CHESTPLATE).color(SHIRT_ICON_COLOR)
                .name(messages.get("shop.shirts")).lore(messages.get("shop.shirts-lore")).build());
        set(PREMIUM_SLOT, premiumIcon());
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        switch (slot) {
            case HEADS_SLOT -> menus.openHeads(player);
            case SHIRTS_SLOT -> menus.openShirts(player);
            case PREMIUM_SLOT -> buyPremium(player);
            default -> close(player);
        }
    }

    private void buyPremium(Player player) {
        if (player.hasPermission(LobbyPermissions.PREMIUM)) {
            close(player);
            player.sendMessage(menus.messages().get("shop.already-premium"));
            return;
        }
        menus.purchase(player, premiumIcon(), PREMIUM_ITEM, menus.config().premiumPrice(),
                buyer -> menus.premiumRank().grant(buyer.getUniqueId()));
    }

    private ItemStack premiumIcon() {
        var messages = menus.messages();
        return Items.of(Material.GLISTERING_MELON_SLICE)
                .name(messages.get("shop.premium"))
                .lore(messages.get("shop.premium-lore"),
                        messages.get("shop.price-lore", Formatter.number("coins", menus.config().premiumPrice())))
                .build();
    }
}
