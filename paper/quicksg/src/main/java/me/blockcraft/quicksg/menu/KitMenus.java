package me.blockcraft.quicksg.menu;

import me.blockcraft.api.coins.CoinsRepository;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.message.Messages;
import me.blockcraft.quicksg.game.Game;
import me.blockcraft.quicksg.kit.Kit;
import me.blockcraft.quicksg.storage.KitRepository;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public final class KitMenus {

    private final Messages messages;
    private final Game game;
    private final KitRepository kits;
    private final CoinsRepository coins;
    private final int price;

    public KitMenus(Messages messages, Game game, KitRepository kits, CoinsRepository coins, int price) {
        this.messages = messages;
        this.game = game;
        this.kits = kits;
        this.coins = coins;
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
        new KitSelectorMenu(this, kits.owned(player.getUniqueId())).open(player);
    }

    void openDetail(Player player, Kit kit) {
        new KitDetailMenu(this, kit, kits.owns(player.getUniqueId(), kit)).open(player);
    }

    void select(Player player, Kit kit, boolean owned) {
        Menu.close(player);
        if (!owned) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 4.0f, 4.0f);
            player.sendMessage(messages.get("kit.not-owned"));
            return;
        }
        game.selectKit(player, kit);
        player.sendMessage(messages.get("kit.selected", kitName(kit)));
    }

    void buy(Player player, Kit kit, boolean owned) {
        Menu.close(player);
        if (owned) {
            player.sendMessage(messages.get("kit.already-owned"));
            return;
        }
        if (!coins.withdraw(player.getUniqueId(), player.getName(), price)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 4.0f, 4.0f);
            player.sendMessage(messages.get("kit.not-enough-coins"));
            return;
        }
        kits.unlock(player.getUniqueId(), player.getName(), kit);
        player.sendMessage(messages.get("kit.bought", kitName(kit)));
        player.sendMessage(messages.get("kit.bought-cost", Formatter.number("price", price)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 4.0f, 4.0f);
    }

    TagResolver kitName(Kit kit) {
        return Placeholder.component("kit", messages.get(kit.messageKey() + ".name"));
    }
}
