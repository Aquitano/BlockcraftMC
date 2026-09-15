package me.blockcraft.api;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.coins.CoinsJoinListener;
import me.blockcraft.api.command.BuildCommand;
import me.blockcraft.api.command.CoinsCommand;
import me.blockcraft.api.command.CommandRegistry;
import me.blockcraft.api.command.LevelCommand;
import me.blockcraft.api.command.MapCommand;
import me.blockcraft.api.dialog.ServerMenu;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.menu.MenuListener;
import me.blockcraft.api.network.ServerHeartbeat;
import me.blockcraft.api.network.ServerPresence;
import me.blockcraft.api.pack.ResourcePacks;
import me.blockcraft.api.player.RealNames;
import me.blockcraft.api.player.VanillaProgress;
import me.blockcraft.api.rank.TabTeams;
import me.blockcraft.api.settings.PlayerSettings;
import me.blockcraft.api.world.BuildMode;
import me.blockcraft.api.world.MapBundle;
import me.blockcraft.api.world.WorldTemplate;
import me.blockcraft.api.xp.Levels;
import me.blockcraft.api.xp.Xp;
import me.blockcraft.api.xp.XpReward;
import me.blockcraft.common.config.YamlFile;
import me.blockcraft.common.db.Database;
import me.blockcraft.common.db.DatabaseConfig;
import me.blockcraft.common.network.ProxyChannel;
import me.blockcraft.common.network.ServerRepository;
import me.blockcraft.common.settings.PlayerSettingRepository;
import me.blockcraft.common.xp.XpRepository;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BlockcraftApiPlugin extends JavaPlugin {

    private static final long TICKS_PER_SECOND = 20L;

    private Database database;
    private ServerHeartbeat heartbeat;

    @Override
    public void onEnable() {
        var resources = ApiResources.load(getDataPath(), this::getResource);
        var config = resources.config();
        var databaseConfig = DatabaseConfig.fromYaml(YamlFile.section(config, "database"));
        database = Database.connect(databaseConfig, getName());
        database.migrate();

        var servers = new ServerRepository(database);
        var presence = new ServerPresence();
        heartbeat = new ServerHeartbeat(this, servers, ServerHeartbeat.local(databaseConfig, getServer().getPort()), presence);
        var self = heartbeat.self();
        getLogger().info("Announcing as " + self.name() + " (group " + self.group() + ") at " + self.host() + ":" + self.port());

        var messages = resources.messages();
        var settingRepository = new PlayerSettingRepository(database);
        var settings = new PlayerSettings(this, settingRepository);
        var realNames = new RealNames();
        var xpRepository = new XpRepository(database);
        var xp = new Xp(this, xpRepository, new Levels(number(YamlFile.section(config, "xp"), "level-step", 100)),
                xpRewards(YamlFile.section(YamlFile.section(config, "xp"), "rewards")), messages);
        var map = MapBundle.load(getServer().getWorlds().getFirst(), WorldTemplate.configured(config));
        var buildMode = new BuildMode();
        var resourcePack = new ResourcePacks(messages);
        var api = new BlockcraftApi(this, database, settings, resourcePack,
                profileGames(YamlFile.section(YamlFile.section(config, "profile"), "games")),
                messages, servers, self, presence, realNames, xp, map, buildMode, resources.tags());
        BlockcraftApi.install(api);
        getLogger().info("Running map " + map.name() + map.template().map(template -> " from " + template.directory()).orElse(""));
        int startingCoins = number(config, "starting-coins", 10);
        getServer().getPluginManager().registerEvents(realNames, this);
        getServer().getPluginManager().registerEvents(xp, this);
        getServer().getPluginManager().registerEvents(new CoinsJoinListener(this, api.coins(), startingCoins), this);
        getServer().getPluginManager().registerEvents(settings, this);
        getServer().getPluginManager().registerEvents(resourcePack, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, ProxyChannel.NAME, resourcePack);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        getServer().getPluginManager().registerEvents(new ServerMenu(this), this);
        getServer().getPluginManager().registerEvents(new TabTeams(LuckPermsProvider.get(), messages), this);
        getServer().getPluginManager().registerEvents(buildMode, this);
        getServer().getPluginManager().registerEvents(new VanillaProgress(), this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = new CommandRegistry(event.registrar(), messages);
            commands.register(CoinsCommand.create(api.coins(), messages));
            commands.register(LevelCommand.create(xp, messages));
            commands.register(BuildCommand.create(buildMode, messages));
            commands.register(MapCommand.create(this, map, messages));
        });

        int heartbeatSeconds = number(config, "heartbeat-seconds", 5);
        heartbeat.start(heartbeatSeconds * TICKS_PER_SECOND);
    }

    /** The profile card's games: menu key to legacy stats table, as configured. */
    private Map<String, StatsRepository> profileGames(Map<String, Object> section) {
        var games = new LinkedHashMap<String, StatsRepository>();
        section.forEach((key, table) -> games.put(key, new StatsRepository(database, String.valueOf(table))));
        return games;
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
