package me.blockcraft.rush.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.Stat;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.game.TopList;
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
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Set;

/** Opens every EmeraldRush GUI and performs the purchases they trigger. */
public final class RushMenus {

    private final Plugin plugin;
    private final Game game;
    private final Messages messages;
    private final Spawns spawns;
    private final Unlocks unlocks;
    private final Shop shop;
    private final StatsRepository stats;
    private final TopList topList;
    private final ShopCatalog catalog;

    public RushMenus(Plugin plugin, Game game, Messages messages, Spawns spawns, Unlocks unlocks, Shop shop, StatsRepository stats) {
        this.plugin = plugin;
        this.game = game;
        this.messages = messages;
        this.spawns = spawns;
        this.unlocks = unlocks;
        this.shop = shop;
        this.stats = stats;
        this.topList = new TopList(plugin, stats, messages);
        this.catalog = new ShopCatalog(messages);
    }

    public void openPerks(Player player) {
        if (game.phase() != GamePhase.LOBBY) {
            return;
        }
        BlockcraftApi.get().playerTasks().latest(player, () -> ownedPerks(player), owned -> new PerkMenu(this, messages, owned).open(player));
    }

    void openPerk(Player player, Perk perk) {
        BlockcraftApi.get().playerTasks().latest(player, () -> unlocks.owns(player.getUniqueId(), perk.item()),
                owned -> new PerkBuyMenu(this, messages, perk, owned).open(player));
    }

    void buyPerk(Player player, Perk perk) {
        new ConfirmDialog(perkIcon(perk), buyer -> purchasePerk(buyer, perk), buyer -> openPerk(buyer, perk)).open(player);
    }

    ItemStack perkIcon(Perk perk) {
        return Items.of(perk.icon())
                .name(messages.get("perk.item", perkName(messages, perk)))
                .lore(messages.get("perk.price-lore", Formatter.number("price", perk.price())))
                .build();
    }

    private void purchasePerk(Player player, Perk perk) {
        BlockcraftApi.get().playerTasks().write(player, () -> shop.buy(player.getUniqueId(), BlockcraftApi.get().realName(player), perk.item(), perk.price()), purchase -> {
            switch (purchase.outcome()) {
                case BOUGHT -> {
                    Sounds.PURCHASE.play(player);
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
        openShop(player, ShopCategory.CHAIN);
    }

    void openShop(Player player, ShopCategory category) {
        BlockcraftApi.get().playerTasks().latest(player, () -> ownedPerks(player),
                owned -> new ShopMenu(this, messages, category, catalog.entries(category, owned)).open(player));
    }

    void buy(Player player, ShopEntry entry) {
        if (game.phase() != GamePhase.SHOPPING) {
            Menu.close(player);
            return;
        }
        if (player.getInventory().firstEmpty() == -1) {
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
        game.scoreboard().show(player);
    }

    void teleportToEnchant(Player player) {
        Sounds.TELEPORT.play(player);
        Menu.close(player);
        spawns.teleport(player, SpawnPoint.ENCHANT);
    }

    public void openStats(Player player) {
        List<Player> online = List.copyOf(Bukkit.getOnlinePlayers());
        BlockcraftApi.get().playerTasks().latest(player, () -> online.stream().map(other -> new StatsMenu.Entry(other, stats.get(other.getUniqueId()))).toList(),
                entries -> new StatsMenu(this, messages, entries).open(player));
    }

    void openGlobalStats(Player player) {
        new GlobalStatsMenu(this, messages).open(player);
    }

    public void showTop(Player player, Stat category) {
        topList.send(player, category);
    }

    private Set<Perk> ownedPerks(Player player) {
        return Perk.ownedAmong(unlocks.owned(player.getUniqueId(), Perk.ITEM_PREFIX));
    }

    static TagResolver perkName(Messages messages, Perk perk) {
        return Placeholder.parsed("perk", messages.raw("perk." + perk.key()));
    }

}
