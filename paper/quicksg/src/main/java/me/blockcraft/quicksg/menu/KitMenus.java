package me.blockcraft.quicksg.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.shop.Purchases;
import me.blockcraft.api.shop.Unlocks;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.quicksg.game.Game;
import me.blockcraft.quicksg.kit.Kit;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Opens the kit GUIs and performs the purchases they trigger; the database work runs off the main thread. */
public final class KitMenus {

    private final Messages messages;
    private final Game game;
    private final Unlocks unlocks;
    private final int price;

    public KitMenus(Messages messages, Game game, Unlocks unlocks, int price) {
        this.messages = messages;
        this.game = game;
        this.unlocks = unlocks;
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
        BlockcraftApi.get().playerTasks().latest(player, () -> Kit.ownedAmong(unlocks.owned(player.getUniqueId(), Kit.ITEM_PREFIX)),
                owned -> {
                    if (game.isLobby()) {
                        new KitSelectorMenu(this, owned, game.kit(player)).open(player, game::isLobby);
                    }
                });
    }

    void openDetail(Player player, Kit kit) {
        if (!game.isLobby()) {
            return;
        }
        BlockcraftApi.get().playerTasks().latest(player, () -> kit.free() || unlocks.owns(player.getUniqueId(), kit.item()),
                owned -> {
                    if (game.isLobby()) {
                        new KitDetailMenu(this, kit, owned, player).open(player, game::isLobby);
                    }
                });
    }

    void select(Player player, Kit kit, boolean owned) {
        if (!game.isLobby()) {
            return;
        }
        if (!owned) {
            Sounds.MENU_DENY.play(player);
            player.sendMessage(messages.get("kit.not-owned"));
            return;
        }
        game.selectKit(player, kit);
        BlockcraftApi.get().notifications().feedback(player, messages.get("kit.selected", kitName(kit)));
    }

    boolean selected(Player player, Kit kit) {
        return game.kit(player) == kit;
    }

    void buy(Player player, Kit kit) {
        if (!game.isLobby()) {
            return;
        }
        Purchases.confirm(player, icon(kit), kit.item(), price, buyer -> game.isLobby(),
                ignored -> { }, buyer -> openDetail(buyer, kit));
    }

    private ItemStack icon(Kit kit) {
        return Items.of(kit.icon())
                .name(messages.get("menu.kits.entry", kitName(kit)))
                .lore(messages.getLines(kit.messageKey() + ".contents"))
                .build();
    }

    TagResolver kitName(Kit kit) {
        return Placeholder.component("kit", messages.get(kit.messageKey() + ".name"));
    }
}
