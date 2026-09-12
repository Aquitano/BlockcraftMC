package me.blockcraft.api;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.command.CoinsCommand;
import me.blockcraft.api.menu.MenuListener;
import me.blockcraft.common.config.YamlFile;
import me.blockcraft.common.db.Database;
import me.blockcraft.common.db.DatabaseConfig;
import me.blockcraft.common.message.Messages;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class BlockcraftApiPlugin extends JavaPlugin {

    private Database database;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var config = YamlFile.loadWithDefaults(getDataPath().resolve("config.yml"), () -> getResource("config.yml"));
        database = Database.connect(DatabaseConfig.fromYaml(YamlFile.section(config, "database")), getName());

        var api = new BlockcraftApi(database);
        api.coins().createTable();
        BlockcraftApi.install(api);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);

        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"));
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(CoinsCommand.create(api.coins(), messages), "Zeigt deine Coins", List.of()));
    }

    @Override
    public void onDisable() {
        BlockcraftApi.uninstall();
        if (database != null) {
            database.close();
        }
    }
}
