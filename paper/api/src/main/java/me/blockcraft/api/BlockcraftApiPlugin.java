package me.blockcraft.api;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.coins.CoinsJoinListener;
import me.blockcraft.api.command.CoinsCommand;
import me.blockcraft.api.command.CommandRegistry;
import me.blockcraft.api.command.LevelCommand;
import me.blockcraft.api.menu.MenuListener;
import me.blockcraft.api.network.ServerHeartbeat;
import me.blockcraft.api.network.ServerPresence;
import me.blockcraft.api.player.RealNames;
import me.blockcraft.api.rank.TabTeams;
import me.blockcraft.api.settings.PlayerSettings;
import me.blockcraft.api.xp.Levels;
import me.blockcraft.api.xp.Xp;
import me.blockcraft.api.xp.XpReward;
import me.blockcraft.common.config.YamlFile;
import me.blockcraft.common.db.Database;
import me.blockcraft.common.db.DatabaseConfig;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.ServerRepository;
import me.blockcraft.common.xp.XpRepository;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Map;

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

        var loaded = Messages.load(getDataPath(), () -> getResource("messages.yml"));
        var tags = TagResolver.resolver(Messages.tagsOf(YamlFile.section(config, "network")), loaded.tagsOf("palette"));
        var messages = loaded.with(tags);
        var settings = new PlayerSettings(this, database);
        var realNames = new RealNames();
        var xpRepository = new XpRepository(database);
        xpRepository.createTable();
        var xp = new Xp(this, xpRepository, new Levels(number(YamlFile.section(config, "xp"), "level-step", 100)),
                xpRewards(YamlFile.section(YamlFile.section(config, "xp"), "rewards")), messages);
        var api = new BlockcraftApi(database, settings, messages, servers, self, presence, realNames, xp, tags);
        api.coins().createTable();
        api.unlocks().createTable();
        settings.createTable();
        BlockcraftApi.install(api);
        int startingCoins = number(config, "starting-coins", 10);
        getServer().getPluginManager().registerEvents(realNames, this);
        getServer().getPluginManager().registerEvents(xp, this);
        getServer().getPluginManager().registerEvents(new CoinsJoinListener(this, api.coins(), startingCoins), this);
        getServer().getPluginManager().registerEvents(settings, this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        getServer().getPluginManager().registerEvents(new TabTeams(LuckPermsProvider.get(), messages), this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = new CommandRegistry(event.registrar(), messages);
            commands.register(CoinsCommand.create(api.coins(), messages));
            commands.register(LevelCommand.create(xp, messages));
        });

        int heartbeatSeconds = number(config, "heartbeat-seconds", 5);
        heartbeat.start(heartbeatSeconds * TICKS_PER_SECOND);
    }

    private static Map<XpReward, Integer> xpRewards(Map<String, Object> section) {
        var rewards = new EnumMap<XpReward, Integer>(XpReward.class);
        for (var reward : XpReward.values()) {
            rewards.put(reward, number(section, reward.key(), 0));
        }
        return rewards;
    }

    private static int number(Map<String, Object> section, String key, int fallback) {
        return section.get(key) instanceof Number value ? value.intValue() : fallback;
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
