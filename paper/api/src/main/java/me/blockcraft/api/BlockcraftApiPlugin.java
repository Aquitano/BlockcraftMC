package me.blockcraft.api;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.achievement.Achievements;
import me.blockcraft.api.afk.Afk;
import me.blockcraft.api.afk.AfkCommand;
import me.blockcraft.api.audit.StaffAudit;
import me.blockcraft.api.coins.CoinsJoinListener;
import me.blockcraft.api.command.BuildCommand;
import me.blockcraft.api.command.CoinsCommand;
import me.blockcraft.api.command.CommandRegistry;
import me.blockcraft.api.command.LevelCommand;
import me.blockcraft.api.command.MapCommand;
import me.blockcraft.api.cosmetic.Effects;
import me.blockcraft.api.dialog.ServerMenu;
import me.blockcraft.api.elo.Elo;
import me.blockcraft.api.elo.SkillGroup;
import me.blockcraft.api.game.RoundConfig;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.game.Streaks;
import me.blockcraft.api.ignore.Ignores;
import me.blockcraft.api.menu.MenuListener;
import me.blockcraft.api.menu.Profiles;
import me.blockcraft.api.network.ServerGroups;
import me.blockcraft.api.network.ServerHeartbeat;
import me.blockcraft.api.network.ServerPresence;
import me.blockcraft.api.nick.NickStats;
import me.blockcraft.api.pack.ResourcePacks;
import me.blockcraft.api.player.RealNames;
import me.blockcraft.api.proxy.ProxyMessageListener;
import me.blockcraft.api.rank.NickRanks;
import me.blockcraft.api.player.VanillaProgress;
import me.blockcraft.api.proxy.NetworkOperations;
import me.blockcraft.api.rank.TabTeams;
import me.blockcraft.api.settings.PlayerSettings;
import me.blockcraft.api.shop.Unlocks;
import me.blockcraft.api.task.PlayerTasks;
import me.blockcraft.api.vanish.VanishedPlayers;
import me.blockcraft.api.world.BuildMode;
import me.blockcraft.api.world.MapBundle;
import me.blockcraft.api.world.WorldTemplate;
import me.blockcraft.api.xp.Levels;
import me.blockcraft.api.xp.Xp;
import me.blockcraft.api.notification.Notifications;
import me.blockcraft.api.xp.XpReward;
import me.blockcraft.common.coins.CoinsRepository;
import me.blockcraft.common.calendar.NetworkCalendar;
import me.blockcraft.common.config.YamlFile;
import me.blockcraft.common.db.Database;
import me.blockcraft.common.db.DatabaseConfig;
import me.blockcraft.common.elo.EloFormula;
import me.blockcraft.common.elo.EloRepository;
import me.blockcraft.common.ignore.IgnoreRepository;
import me.blockcraft.common.moderation.VanishRepository;
import me.blockcraft.common.moderation.StaffActionRepository;
import me.blockcraft.common.network.ProxyMessageCodec;
import me.blockcraft.common.network.ServerRepository;
import me.blockcraft.common.round.RoundRecapRepository;
import me.blockcraft.common.settings.PlayerSettingRepository;
import me.blockcraft.common.shop.Shop;
import me.blockcraft.common.xp.XpRepository;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.GameRules;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BlockcraftApiPlugin extends JavaPlugin {

    private Notifications notifications;
    private Database database;
    private ServerHeartbeat heartbeat;
    private ServerGroups groups;

    @Override
    public void onEnable() {
        try {
            enable();
        } catch (RuntimeException e) {
            getSLF4JLogger().error("BlockcraftAPI failed to start; stopping the server so it restarts instead of running without plugins", e);
            getServer().shutdown();
        }
    }

    private void enable() {
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
        var groupStatus = YamlFile.section(config, "group-status");
        groups = new ServerGroups(this, servers, number(groupStatus, "stale-seconds", 20));

        var messages = resources.messages();
        notifications = new Notifications(this, messages, Notifications.Timing.from(YamlFile.section(config, "notifications")));
        var settingRepository = new PlayerSettingRepository(database);
        var settings = new PlayerSettings(this, settingRepository, messages);
        var realNames = new RealNames();
        var nickRanks = new NickRanks();
        var xpRepository = new XpRepository(database);
        var xp = new Xp(this, xpRepository, new Levels(number(YamlFile.section(config, "xp"), "level-step", 100)),
                xpRewards(YamlFile.section(YamlFile.section(config, "xp"), "rewards")), notifications);
        var map = MapBundle.load(getServer().getWorlds().getFirst(), WorldTemplate.configured(config));
        // The locator bar would take the level bar's place and point at hidden or hunted players. No server lets mobs
        // spawn by themselves (the games' ArenaProtection, the lobby's ProtectionListener), so none spends ticks trying.
        getServer().getWorlds().forEach(world -> {
            world.setGameRule(GameRules.LOCATOR_BAR, false);
            world.setGameRule(GameRules.SPAWN_MOBS, false);
        });
        var buildMode = new BuildMode();
        var resourcePack = new ResourcePacks(messages);
        var eloSection = YamlFile.section(config, "elo");
        var elo = new Elo(new EloRepository(database,
                new EloFormula(number(eloSection, "start-rating", 1000), number(eloSection, "k-factor", 32))),
                SkillGroup.ladder(YamlFile.section(eloSection, "groups")), messages);
        var afk = new Afk(this, notifications, messages,
                Duration.ofMinutes(Math.max(1, number(YamlFile.section(config, "afk"), "idle-minutes", 5))));
        var ignores = new Ignores(this, new IgnoreRepository(database));
        var vanished = new VanishedPlayers(this, new VanishRepository(database), messages);
        var playerTasks = new PlayerTasks(this, messages);
        var coins = new CoinsRepository(database);
        var unlocks = new Unlocks(database);
        var profileGames = profileGames(YamlFile.section(YamlFile.section(config, "profile"), "games"));
        var achievements = new Achievements(this, Achievements.parse(YamlFile.section(config, "achievements")),
                profileGames, unlocks, xp, messages, playerTasks, notifications);
        var profiles = new Profiles(this, coins, profileGames, playerTasks, unlocks, achievements, xp, messages, realNames);
        var nickStats = new NickStats(nickRanks, NickStats.Ranges.from(YamlFile.section(config, "nick-stats")));
        var audit = new StaffAudit(this, new StaffActionRepository(database), realNames, self.name(),
                StaffAudit.Notices.from(YamlFile.section(config, "audit")), messages);
        var tabTeams = new TabTeams(LuckPermsProvider.get(), messages, nickRanks, afk);
        var api = new BlockcraftApi(notifications, new NetworkOperations(this, messages), playerTasks, database, coins,
                unlocks, new Shop(database), new RoundRecapRepository(database), streaks(YamlFile.section(config, "streaks")),
                settings, resourcePack, profiles, new Effects(settings), messages,
                servers, self, groups, text(config, "lobby-group", "lobby"), presence, resources.tags(), xp, map,
                buildMode, realNames, nickRanks, nickStats, achievements, elo, afk, ignores, vanished, audit, tabTeams,
                NetworkCalendar.fromYaml(YamlFile.section(config, "calendar")), RoundConfig.from(config));
        BlockcraftApi.install(api);
        getLogger().info("Running map " + map.name() + map.template().map(template -> " from " + template.directory()).orElse(""));
        int startingCoins = number(config, "starting-coins", 10);
        getServer().getPluginManager().registerEvents(realNames, this);
        getServer().getPluginManager().registerEvents(nickRanks, this);
        getServer().getPluginManager().registerEvents(vanished, this);
        getServer().getPluginManager().registerEvents(xp, this);
        getServer().getPluginManager().registerEvents(achievements, this);
        getServer().getPluginManager().registerEvents(new CoinsJoinListener(this, coins, realNames, startingCoins), this);
        getServer().getPluginManager().registerEvents(settings, this);
        getServer().getPluginManager().registerEvents(resourcePack, this);
        getServer().getPluginManager().registerEvents(ignores, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, ProxyMessageCodec.CHANNEL,
                new ProxyMessageListener(this, resourcePack, ignores, settings));
        getServer().getPluginManager().registerEvents(afk, this);
        afk.start();
        getServer().getPluginManager().registerEvents(new MenuListener(playerTasks), this);
        getServer().getPluginManager().registerEvents(new ServerMenu(this, profiles), this);
        getServer().getPluginManager().registerEvents(tabTeams, this);
        getServer().getPluginManager().registerEvents(buildMode, this);
        getServer().getPluginManager().registerEvents(new VanillaProgress(), this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = new CommandRegistry(event.registrar(), messages);
            commands.register(CoinsCommand.create(this, coins, audit, messages));
            commands.register(LevelCommand.create(xp, messages));
            commands.register(BuildCommand.create(buildMode, audit, messages));
            commands.register(MapCommand.create(this, map, messages));
            commands.register(AfkCommand.create(afk));
        });

        int heartbeatSeconds = number(config, "heartbeat-seconds", 5);
        heartbeat.start(Duration.ofSeconds(heartbeatSeconds));
        groups.start(number(groupStatus, "refresh-ticks", 50));
    }

    /**
     * The profile card's games: menu key to legacy stats table, as configured. The table of a game the network does
     * not run yet is created here, so it reads as zeroes instead of failing the whole card.
     */
    private Map<String, StatsRepository> profileGames(Map<String, Object> section) {
        var games = new LinkedHashMap<String, StatsRepository>();
        section.forEach((key, table) -> {
            var repository = new StatsRepository(database, String.valueOf(table));
            repository.createTable();
            games.put(key, repository);
        });
        return games;
    }

    private static Map<XpReward, Integer> xpRewards(Map<String, Object> section) {
        var rewards = new EnumMap<XpReward, Integer>(XpReward.class);
        for (var reward : XpReward.values()) {
            rewards.put(reward, number(section, reward.key(), 0));
        }
        return rewards;
    }

    private static Streaks streaks(Map<String, Object> section) {
        return new Streaks(section.get("announce") instanceof List<?> values
                ? values.stream().filter(Number.class::isInstance).map(value -> ((Number) value).intValue()).toList()
                : List.of());
    }

    private static int number(Map<String, Object> section, String key, int fallback) {
        return section.get(key) instanceof Number value ? value.intValue() : fallback;
    }

    private static String text(Map<String, Object> section, String key, String fallback) {
        return section.get(key) instanceof String value ? value : fallback;
    }

    @Override
    public void onDisable() {
        if (notifications != null) {
            notifications.close();
        }
        BlockcraftApi.uninstall();
        if (groups != null) {
            groups.stop();
        }
        if (heartbeat != null) {
            heartbeat.stop();
        }
        if (database != null) {
            database.close();
        }
    }
}
