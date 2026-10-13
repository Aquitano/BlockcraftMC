package me.blockcraft.rush.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.dialog.ConfirmDialog;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.shop.Shop;
import me.blockcraft.api.shop.Unlocks;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.rush.config.SpawnPoint;
import me.blockcraft.rush.config.Spawns;
import me.blockcraft.rush.game.Game;
import me.blockcraft.rush.game.GamePhase;
import me.blockcraft.rush.kit.Perk;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Set;

/** Opens every EmeraldRush GUI and performs the purchases they trigger. */
public final class RushMenus {

    private final Game game;
    private final Messages messages;
    private final Spawns spawns;
    private final Unlocks unlocks;
    private final Shop shop;
    private final ShopCatalog catalog;

    public RushMenus(Game game, Messages messages, Spawns spawns, Unlocks unlocks, Shop shop) {
        this.game = game;
        this.messages = messages;
        this.spawns = spawns;
        this.unlocks = unlocks;
        this.shop = shop;
        this.catalog = new ShopCatalog(messages);
    }

    public void openPerks(Player player) {
        if (game.phase() != GamePhase.LOBBY) {
            return;
        }
        BlockcraftApi.get().playerTasks().latest(player, () -> ownedPerks(player), owned -> {
            if (game.phase() == GamePhase.LOBBY) {
                new PerkMenu(this, messages, owned).open(player);
            }
        });
    }

    void openPerk(Player player, Perk perk) {
        if (game.phase() != GamePhase.LOBBY) {
            return;
        }
        BlockcraftApi.get().playerTasks().latest(player, () -> unlocks.owns(player.getUniqueId(), perk.item()),
                owned -> {
                    if (game.phase() == GamePhase.LOBBY) {
                        new PerkBuyMenu(this, messages, perk, owned).open(player);
                    }
                });
    }

    void buyPerk(Player player, Perk perk) {
        if (game.phase() != GamePhase.LOBBY) {
            return;
        }
        new ConfirmDialog(perkIcon(perk), buyer -> purchasePerk(buyer, perk), buyer -> openPerk(buyer, perk)).open(player);
    }

    ItemStack perkIcon(Perk perk) {
        return Items.of(perk.icon())
                .name(messages.get("perk.item", perkName(messages, perk)))
                .lore(messages.get("perk.price-lore", Formatter.number("price", perk.price())))
                .build();
    }

    private void purchasePerk(Player player, Perk perk) {
        if (game.phase() != GamePhase.LOBBY) {
            player.sendMessage(messages.get("perk.round-started"));
            return;
        }
        BlockcraftApi.get().playerTasks().write(player, () -> shop.buy(player.getUniqueId(), BlockcraftApi.get().realName(player), perk.item(), perk.price()), purchase -> {
            switch (purchase.outcome()) {
                case BOUGHT -> {
                    BlockcraftApi.get().notifications().rewardSound(player, Sounds.PURCHASE);
                    player.sendMessage(messages.get("perk.bought", perkName(messages, perk)));
                    player.sendMessage(messages.get("perk.bought-price", Formatter.number("price", perk.price())));
                }
                case ALREADY_OWNED -> player.sendMessage(messages.get("perk.already-owned"));
                case NOT_ENOUGH_COINS -> {
                    Sounds.MENU_DENY.play(player);
                    player.sendMessage(messages.get("perk.not-enough-coins"));
                }
            }
        });
    }

    public void openShop(Player player) {
        if (!canShop(player)) {
            return;
        }
        BlockcraftApi.get().playerTasks().latest(player, () -> ownedPerks(player),
                owned -> openShop(player, ShopCategory.CHAIN, owned));
    }

    void openShop(Player player, ShopCategory category, Set<Perk> owned) {
        if (canShop(player)) {
            new ShopMenu(this, messages, category, stock(category, owned), owned, player).open(player);
        }
    }

    List<ShopEntry> stock(ShopCategory category, Set<Perk> owned) {
        return catalog.entries(category, owned);
    }

    int balance(Player player) {
        return game.emeralds(player);
    }

    private boolean canShop(Player player) {
        return game.phase() == GamePhase.SHOPPING && game.isAlive(player);
    }

    void buy(Player player, ShopEntry entry) {
        if (!canShop(player)) {
            Menu.close(player);
            return;
        }
        if (!entry.hasCapacity().test(player)) {
            Sounds.MENU_DENY.play(player);
            player.sendMessage(messages.get("shop.inventory-full"));
            return;
        }
        if (!game.withdrawEmeralds(player, entry.price())) {
            Sounds.MENU_DENY.play(player);
            player.sendMessage(messages.get("shop.not-enough"));
            return;
        }
        entry.reward().accept(player);
        game.bought(player);
        BlockcraftApi.get().notifications().rewardSound(player, Sounds.PURCHASE);
        int remaining = game.emeralds(player);
        BlockcraftApi.get().notifications().feedback(player, messages.pick(remaining == 0 ? "shop.exact-change" : "shop.bought",
                Formatter.number("price", entry.price()), Formatter.number("emeralds", remaining)));
        game.scoreboard().show(player);
    }

    void teleportToEnchant(Player player) {
        if (!canShop(player)) {
            Menu.close(player);
            return;
        }
        Sounds.TELEPORT.play(player);
        Menu.close(player);
        spawns.teleport(player, SpawnPoint.ENCHANT);
    }

    private Set<Perk> ownedPerks(Player player) {
        return Perk.ownedAmong(unlocks.owned(player.getUniqueId(), Perk.ITEM_PREFIX));
    }

    static TagResolver perkName(Messages messages, Perk perk) {
        return Placeholder.parsed("perk", messages.raw("perk." + perk.key()));
    }

}
