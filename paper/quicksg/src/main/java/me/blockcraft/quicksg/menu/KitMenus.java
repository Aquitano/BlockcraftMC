package me.blockcraft.quicksg.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.dialog.ConfirmDialog;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.shop.Shop;
import me.blockcraft.api.shop.Unlocks;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.quicksg.game.Game;
import me.blockcraft.quicksg.kit.Kit;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** Opens the kit GUIs and performs the purchases they trigger; the database work runs off the main thread. */
public final class KitMenus {

    private final Plugin plugin;
    private final Messages messages;
    private final Game game;
    private final Unlocks unlocks;
    private final Shop shop;
    private final int price;

    public KitMenus(Plugin plugin, Messages messages, Game game, Unlocks unlocks, Shop shop, int price) {
        this.plugin = plugin;
        this.messages = messages;
        this.game = game;
        this.unlocks = unlocks;
        this.shop = shop;
        this.price = price;
    }

    Messages messages() {
        return messages;
    }

    int price() {
        return price;
    }

    public void openSelector(Player player) {
        if (!game.isLobby()) {
            return;
        }
        Tasks.async(plugin, () -> Kit.ownedAmong(unlocks.owned(player.getUniqueId(), Kit.ITEM_PREFIX)),
                owned -> new KitSelectorMenu(this, owned).open(player));
    }

    void openDetail(Player player, Kit kit) {
        Tasks.async(plugin, () -> kit.free() || unlocks.owns(player.getUniqueId(), kit.item()),
                owned -> new KitDetailMenu(this, kit, owned).open(player));
    }

    void select(Player player, Kit kit, boolean owned) {
        Menu.close(player);
        if (!owned) {
            Sounds.MENU_DENY.play(player);
            player.sendMessage(messages.get("kit.not-owned"));
            return;
        }
        game.selectKit(player, kit);
        player.sendMessage(messages.get("kit.selected", kitName(kit)));
    }

    void buy(Player player, Kit kit) {
        new ConfirmDialog(icon(kit), buyer -> purchase(buyer, kit), buyer -> openDetail(buyer, kit)).open(player);
    }

    private void purchase(Player player, Kit kit) {
        Menu.close(player);
        Tasks.async(plugin, () -> shop.buy(player.getUniqueId(), BlockcraftApi.get().realName(player), kit.item(), price), purchase -> {
            switch (purchase.outcome()) {
                case BOUGHT -> {
                    Sounds.PURCHASE.play(player);
                    player.sendMessage(messages.get("kit.bought", kitName(kit)));
                    player.sendMessage(messages.get("kit.bought-cost", Formatter.number("price", price)));
                }
                case ALREADY_OWNED -> player.sendMessage(messages.get("kit.already-owned"));
                case NOT_ENOUGH_COINS -> {
                    Sounds.MENU_DENY.play(player);
                    player.sendMessage(messages.get("kit.not-enough-coins"));
                }
            }
        });
    }

    ItemStack icon(Kit kit) {
        return Items.of(kit.icon())
                .name(messages.get("menu.kits.entry", kitName(kit)))
                .lore(messages.get("menu.kit-detail.price-coins", Formatter.number("price", price)))
                .build();
    }

    TagResolver kitName(Kit kit) {
        return Placeholder.component("kit", messages.get(kit.messageKey() + ".name"));
    }
}
