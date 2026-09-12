package me.blockcraft.api;

import me.blockcraft.api.coins.CoinsRepository;
import me.blockcraft.common.db.Database;
import me.blockcraft.common.network.ServerAddress;

/** Entry point for other plugins; populated while BlockcraftAPI is enabled. */
public final class BlockcraftApi {

    private static BlockcraftApi instance;

    private final Database database;
    private final CoinsRepository coins;
    private final ServerAddress server;

    BlockcraftApi(Database database, ServerAddress server) {
        this.database = database;
        this.coins = new CoinsRepository(database);
        this.server = server;
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

    /** This server as the proxy knows it. */
    public ServerAddress server() {
        return server;
    }
}
