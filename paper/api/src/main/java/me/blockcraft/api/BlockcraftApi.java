package me.blockcraft.api;

import me.blockcraft.api.coins.CoinsRepository;
import me.blockcraft.api.network.ServerPresence;
import me.blockcraft.api.shop.Shop;
import me.blockcraft.api.shop.Unlocks;
import me.blockcraft.common.db.Database;
import me.blockcraft.common.network.ServerIdentity;
import me.blockcraft.common.network.ServerRepository;

/** Entry point for other plugins; populated while BlockcraftAPI is enabled. */
public final class BlockcraftApi {

    private static BlockcraftApi instance;

    private final Database database;
    private final CoinsRepository coins;
    private final Unlocks unlocks;
    private final Shop shop;
    private final ServerRepository servers;
    private final ServerIdentity server;
    private final ServerPresence presence;

    BlockcraftApi(Database database, ServerRepository servers, ServerIdentity server, ServerPresence presence) {
        this.database = database;
        this.coins = new CoinsRepository(database);
        this.unlocks = new Unlocks(database);
        this.shop = new Shop(coins, unlocks);
        this.servers = servers;
        this.server = server;
        this.presence = presence;
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
}
