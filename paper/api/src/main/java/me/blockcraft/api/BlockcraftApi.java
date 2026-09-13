package me.blockcraft.api;

import me.blockcraft.api.coins.CoinsRepository;
import me.blockcraft.api.cosmetic.Effects;
import me.blockcraft.api.network.ServerPresence;
import me.blockcraft.api.player.RealNames;
import me.blockcraft.api.settings.PlayerSettings;
import me.blockcraft.api.shop.Shop;
import me.blockcraft.api.shop.Unlocks;
import me.blockcraft.common.db.Database;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.ServerIdentity;
import me.blockcraft.common.network.ServerRepository;
import org.bukkit.entity.Player;

/** Entry point for other plugins; populated while BlockcraftAPI is enabled. */
public final class BlockcraftApi {

    private static BlockcraftApi instance;

    private final Database database;
    private final CoinsRepository coins;
    private final Unlocks unlocks;
    private final Shop shop;
    private final PlayerSettings settings;
    private final Effects effects;
    private final Messages messages;
    private final ServerRepository servers;
    private final ServerIdentity server;
    private final ServerPresence presence;
    private final RealNames realNames;

    BlockcraftApi(Database database, PlayerSettings settings, Messages messages, ServerRepository servers, ServerIdentity server,
                  ServerPresence presence, RealNames realNames) {
        this.database = database;
        this.coins = new CoinsRepository(database);
        this.unlocks = new Unlocks(database);
        this.shop = new Shop(coins, unlocks);
        this.settings = settings;
        this.effects = new Effects(settings);
        this.messages = messages;
        this.servers = servers;
        this.server = server;
        this.presence = presence;
        this.realNames = realNames;
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

    /** The name the player logged in with, whatever nick they wear; the one to write into name columns. */
    public String realName(Player player) {
        return realNames.of(player);
    }
}
