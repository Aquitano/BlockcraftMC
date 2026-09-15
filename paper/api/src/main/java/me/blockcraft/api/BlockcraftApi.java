package me.blockcraft.api;

import me.blockcraft.api.coins.CoinsRepository;
import me.blockcraft.api.cosmetic.Effects;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.menu.Profiles;
import me.blockcraft.api.network.ServerPresence;
import me.blockcraft.api.player.RealNames;
import me.blockcraft.api.proxy.NetworkOperations;
import me.blockcraft.api.settings.PlayerSettings;
import me.blockcraft.api.shop.Unlocks;
import me.blockcraft.api.task.PlayerTasks;
import me.blockcraft.api.xp.Xp;
import me.blockcraft.api.xp.XpReward;
import me.blockcraft.common.db.Database;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.ServerIdentity;
import me.blockcraft.common.network.ServerRepository;
import me.blockcraft.common.shop.Shop;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;

/** Entry point for other plugins; populated while BlockcraftAPI is enabled. */
public final class BlockcraftApi {

    private static BlockcraftApi instance;

    private final NetworkOperations operations;
    private final PlayerTasks playerTasks;
    private final Database database;
    private final CoinsRepository coins;
    private final Unlocks unlocks;
    private final Shop shop;
    private final PlayerSettings settings;
    private final Effects effects;
    private final Profiles profiles;
    private final Messages messages;
    private final ServerRepository servers;
    private final ServerIdentity server;
    private final ServerPresence presence;
    private final RealNames realNames;
    private final Xp xp;
    private final TagResolver tags;

    BlockcraftApi(Plugin plugin, Database database, PlayerSettings settings, Map<String, StatsRepository> profileGames,
                  Messages messages, ServerRepository servers, ServerIdentity server, ServerPresence presence,
                  RealNames realNames, Xp xp, TagResolver tags) {
        this.operations = new NetworkOperations(plugin, messages);
        this.playerTasks = new PlayerTasks(plugin, messages);
        this.database = database;
        this.coins = new CoinsRepository(database);
        this.unlocks = new Unlocks(database);
        this.shop = new Shop(database);
        this.settings = settings;
        this.effects = new Effects(settings);
        this.profiles = new Profiles(plugin, coins, profileGames);
        this.messages = messages;
        this.servers = servers;
        this.server = server;
        this.presence = presence;
        this.realNames = realNames;
        this.xp = xp;
        this.tags = tags;
    }

    public static BlockcraftApi get() {
        if (instance == null) {
            throw new IllegalStateException("BlockcraftAPI is not enabled");
        }
        return instance;
    }

    static void install(BlockcraftApi api) {
        instance = api;
    }

    static void uninstall() {
        instance = null;
    }

    /** Typed requests to the proxy: reports and punishments, answered with a result. */
    public NetworkOperations operations() {
        return operations;
    }

    /** Database work behind menus, delivered only to a player who is still there for it. */
    public PlayerTasks playerTasks() {
        return playerTasks;
    }

    public Database database() {
        return database;
    }

    public CoinsRepository coins() {
        return coins;
    }

    /** Network-wide unlocks of cosmetics, kits, perks and secrets. */
    public Unlocks unlocks() {
        return unlocks;
    }

    /** Buys an unlock with coins, once, however many lobbies the player hops between. */
    public Shop shop() {
        return shop;
    }

    /** Remembered per-player choices such as the chat tag or kill effect. */
    public PlayerSettings settings() {
        return settings;
    }

    /** The player's own profile card: rank, level, coins and per-game stats. */
    public Profiles profiles() {
        return profiles;
    }

    /** Kill and win effects to play from a game's death and win handling. */
    public Effects effects() {
        return effects;
    }

    /** BlockcraftAPI's own messages: chat tag texts and other strings shared by every plugin. */
    public Messages messages() {
        return messages;
    }

    /** The live server list of the whole network. */
    public ServerRepository servers() {
        return servers;
    }

    /** This server as the proxy knows it. */
    public ServerIdentity server() {
        return server;
    }

    /** What this server tells the proxy about itself. */
    public ServerPresence presence() {
        return presence;
    }

    /** The network addresses and the colour palette, resolved in every message of every Paper plugin. */
    public TagResolver tags() {
        return tags;
    }

    /** Network XP and levels; games award {@link XpReward}s. */
    public Xp xp() {
        return xp;
    }

    /** The name the player logged in with, whatever nick they wear; the one to write into name columns. */
    public String realName(Player player) {
        return realNames.of(player);
    }
}
