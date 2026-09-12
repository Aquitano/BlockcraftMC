package me.blockcraft.api;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.coins.CoinsJoinListener;
import me.blockcraft.api.command.CoinsCommand;
import me.blockcraft.api.menu.MenuListener;
import me.blockcraft.api.network.ServerHeartbeat;
import me.blockcraft.api.network.ServerPresence;
import me.blockcraft.common.config.YamlFile;
import me.blockcraft.common.db.Database;
import me.blockcraft.common.db.DatabaseConfig;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.ServerRepository;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class BlockcraftApiPlugin extends JavaPlugin {

    private static final long TICKS_PER_SECOND = 20L;

    private Database database;
    private ServerHeartbeat heartbeat;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var config = YamlFile.loadWithDefaults(getDataPath().resolve("config.yml"), () -> getResource("config.yml"));
        var databaseConfig = DatabaseConfig.fromYaml(YamlFile.section(config, "database"));
        database = Database.connect(databaseConfig, getName());

        var servers = new ServerRepository(database);
        servers.createTable();
        var presence = new ServerPresence();
        heartbeat = new ServerHeartbeat(this, servers, ServerHeartbeat.local(databaseConfig, getServer().getPort()), presence);
        var self = heartbeat.self();
        getLogger().info("Announcing as " + self.name() + " (group " + self.group() + ") at " + self.host() + ":" + self.port());

        var api = new BlockcraftApi(database, servers, self, presence);
        api.coins().createTable();
        BlockcraftApi.install(api);
        int startingCoins = ((Number) config.getOrDefault("starting-coins", 10)).intValue();
        getServer().getPluginManager().registerEvents(new CoinsJoinListener(this, api.coins(), startingCoins), this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);

        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"));
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(CoinsCommand.create(api.coins(), messages), "Zeigt deine Coins", List.of()));

        int heartbeatSeconds = ((Number) config.getOrDefault("heartbeat-seconds", 5)).intValue();
        heartbeat.start(heartbeatSeconds * TICKS_PER_SECOND);
    }

    @Override
    public void onDisable() {
        BlockcraftApi.uninstall();
        if (heartbeat != null) {
            heartbeat.stop();
        }
        if (database != null) {
            database.close();
        }
    }
}
