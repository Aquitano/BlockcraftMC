package me.blockcraft.rush.menu;

import me.blockcraft.api.coins.CoinsRepository;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.rush.config.SpawnPoint;
import me.blockcraft.rush.config.Spawns;
import me.blockcraft.rush.game.Game;
import me.blockcraft.rush.game.GamePhase;
import me.blockcraft.rush.kit.Perk;
import me.blockcraft.rush.storage.KitRepository;
import me.blockcraft.rush.storage.PlayerStats;
import me.blockcraft.rush.storage.StatCategory;
import me.blockcraft.rush.storage.StatsRepository;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;

/** Opens every EmeraldRush GUI and performs the purchases they trigger. */
public final class RushMenus {

    private static final int TOP_SIZE = 10;

    private final Plugin plugin;
    private final Game game;
    private final Messages messages;
    private final Spawns spawns;
    private final KitRepository kits;
    private final StatsRepository stats;
    private final CoinsRepository coins;
    private final ShopCatalog catalog;

    private enum PerkPurchase { BOUGHT, ALREADY_OWNED, NOT_ENOUGH_COINS }

    public RushMenus(Plugin plugin, Game game, Messages messages, Spawns spawns, KitRepository kits, StatsRepository stats,
                     CoinsRepository coins) {
        this.plugin = plugin;
        this.game = game;
        this.messages = messages;
        this.spawns = spawns;
        this.kits = kits;
        this.stats = stats;
        this.coins = coins;
        this.catalog = new ShopCatalog(messages);
    }

    public void openPerks(Player player) {
        if (game.phase() != GamePhase.LOBBY) {
            return;
        }
        Tasks.async(plugin, () -> kits.owned(player.getUniqueId()), owned -> new PerkMenu(this, messages, owned).open(player));
    }

    void openPerk(Player player, Perk perk) {
        Tasks.async(plugin, () -> kits.owns(player.getUniqueId(), perk), owned -> new PerkBuyMenu(this, messages, perk, owned).open(player));
    }

    void buyPerk(Player player, Perk perk) {
        Menu.close(player);
        Tasks.async(plugin, () -> {
            if (kits.owns(player.getUniqueId(), perk)) {
                return PerkPurchase.ALREADY_OWNED;
            }
            if (!coins.withdraw(player.getUniqueId(), player.getName(), perk.price())) {
                return PerkPurchase.NOT_ENOUGH_COINS;
            }
            kits.grant(player.getUniqueId(), player.getName(), perk);
            return PerkPurchase.BOUGHT;
        }, result -> {
            switch (result) {
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
        Tasks.async(plugin, () -> kits.owned(player.getUniqueId()),
                owned -> new ShopMenu(this, messages, category, catalog.entries(category, owned)).open(player));
    }

    void buy(Player player, ShopEntry entry) {
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

    public void showTop(Player player, StatCategory category) {
        Tasks.async(plugin, () -> stats.top(category, TOP_SIZE), top -> {
            player.sendMessage(messages.get("top.separator"));
            int rank = 1;
            for (var entry : top) {
                player.sendMessage(messages.get(rank == 1 ? "top.first" : "top.entry",
                        Formatter.number("rank", rank++),
                        Placeholder.unparsed("player", String.valueOf(entry.name())),
                        Formatter.number("value", entry.value())));
            }
            player.sendMessage(messages.get("top.separator"));
        });
    }

    static TagResolver perkName(Messages messages, Perk perk) {
        return Placeholder.parsed("perk", messages.raw("perk." + perk.key()));
    }

    public static TagResolver statsPlaceholders(PlayerStats stats) {
        return TagResolver.resolver(
                Formatter.number("wins", stats.wins()),
                Formatter.number("loses", stats.loses()),
                Formatter.number("deaths", stats.deaths()),
                Formatter.number("kills", stats.kills()),
                Formatter.number("points", stats.points()));
    }
}
