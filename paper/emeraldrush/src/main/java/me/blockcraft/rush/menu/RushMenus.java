package me.blockcraft.rush.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.Stat;
import me.blockcraft.api.game.Stats;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.game.TopList;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.shop.Shop;
import me.blockcraft.api.shop.Unlocks;
import me.blockcraft.api.task.Tasks;
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
import org.bukkit.Sound;
import org.bukkit.entity.Player;
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
        Tasks.async(plugin, () -> ownedPerks(player), owned -> new PerkMenu(this, messages, owned).open(player));
    }

    void openPerk(Player player, Perk perk) {
        Tasks.async(plugin, () -> unlocks.owns(player.getUniqueId(), perk.item()),
                owned -> new PerkBuyMenu(this, messages, perk, owned).open(player));
    }

    void buyPerk(Player player, Perk perk) {
        Menu.close(player);
        Tasks.async(plugin, () -> shop.buy(player.getUniqueId(), BlockcraftApi.get().realName(player), perk.item(), perk.price()), purchase -> {
            switch (purchase.outcome()) {
                case BOUGHT -> {
                    player.sendMessage(messages.get("perk.bought", perkName(messages, perk)));
                    player.sendMessage(messages.get("perk.bought-price", Formatter.number("price", perk.price())));
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 4.0f, 4.0f);
                }
                case ALREADY_OWNED -> player.sendMessage(messages.get("perk.already-owned"));
                case NOT_ENOUGH_COINS -> {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 4.0f, 4.0f);
                    player.sendMessage(messages.get("perk.not-enough-coins"));
                }
            }
        });
    }

    public void openShop(Player player) {
        openShop(player, ShopCategory.CHAIN);
    }

    void openShop(Player player, ShopCategory category) {
        Tasks.async(plugin, () -> ownedPerks(player),
                owned -> new ShopMenu(this, messages, category, catalog.entries(category, owned)).open(player));
    }

    void buy(Player player, ShopEntry entry) {
        if (game.phase() != GamePhase.SHOPPING) {
            Menu.close(player);
            return;
        }
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(messages.get("shop.inventory-full"));
            return;
        }
        if (!game.withdrawEmeralds(player, entry.price())) {
            player.sendMessage(messages.get("shop.not-enough"));
            return;
        }
        entry.reward().accept(player);
        game.scoreboard().show(player);
    }

    void teleportToEnchant(Player player) {
        Menu.close(player);
        spawns.teleport(player, SpawnPoint.ENCHANT);
    }

    public void openStats(Player player) {
        List<Player> online = List.copyOf(Bukkit.getOnlinePlayers());
        Tasks.async(plugin, () -> online.stream().map(other -> new StatsMenu.Entry(other, stats.get(other.getUniqueId()))).toList(),
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

    public static TagResolver statsPlaceholders(Stats stats) {
        return TagResolver.resolver(
                Formatter.number("wins", stats.wins()),
                Formatter.number("loses", stats.losses()),
                Formatter.number("deaths", stats.deaths()),
                Formatter.number("kills", stats.kills()),
                Formatter.number("points", stats.points()));
    }
}
