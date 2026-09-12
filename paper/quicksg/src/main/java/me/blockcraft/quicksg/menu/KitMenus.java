package me.blockcraft.quicksg.menu;

import me.blockcraft.api.coins.CoinsRepository;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.quicksg.game.Game;
import me.blockcraft.quicksg.kit.Kit;
import me.blockcraft.quicksg.storage.KitRepository;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Opens the kit GUIs and performs the purchases they trigger; the database work runs off the main thread. */
public final class KitMenus {

    private final Plugin plugin;
    private final Messages messages;
    private final Game game;
    private final KitRepository kits;
    private final CoinsRepository coins;
    private final int price;

    private enum Purchase { BOUGHT, ALREADY_OWNED, NOT_ENOUGH_COINS }

    public KitMenus(Plugin plugin, Messages messages, Game game, KitRepository kits, CoinsRepository coins, int price) {
        this.plugin = plugin;
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
        Tasks.async(plugin, () -> kits.owned(player.getUniqueId()), owned -> new KitSelectorMenu(this, owned).open(player));
    }

    void openDetail(Player player, Kit kit) {
        Tasks.async(plugin, () -> kits.owns(player.getUniqueId(), kit), owned -> new KitDetailMenu(this, kit, owned).open(player));
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

    void buy(Player player, Kit kit) {
        Menu.close(player);
        Tasks.async(plugin, () -> {
            if (kits.owns(player.getUniqueId(), kit)) {
                return Purchase.ALREADY_OWNED;
            }
            if (!coins.withdraw(player.getUniqueId(), player.getName(), price)) {
                return Purchase.NOT_ENOUGH_COINS;
            }
            kits.unlock(player.getUniqueId(), player.getName(), kit);
            return Purchase.BOUGHT;
        }, result -> {
            switch (result) {
                case BOUGHT -> {
                    player.sendMessage(messages.get("kit.bought", kitName(kit)));
                    player.sendMessage(messages.get("kit.bought-cost", Formatter.number("price", price)));
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 4.0f, 4.0f);
                }
                case ALREADY_OWNED -> player.sendMessage(messages.get("kit.already-owned"));
                case NOT_ENOUGH_COINS -> {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 4.0f, 4.0f);
                    player.sendMessage(messages.get("kit.not-enough-coins"));
                }
            }
        });
    }

    TagResolver kitName(Kit kit) {
        return Placeholder.component("kit", messages.get(kit.messageKey() + ".name"));
    }
}
