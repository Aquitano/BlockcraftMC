package me.blockcraft.system;

import com.google.inject.Inject;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.build.BuildConstants;
import me.blockcraft.common.calendar.ForcedOccasions;
import me.blockcraft.common.chat.ChatFilter;
import me.blockcraft.common.coins.CoinsRepository;
import me.blockcraft.common.db.Database;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.discord.DiscordLinkRepository;
import me.blockcraft.common.ignore.IgnoreRepository;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.ModerationRepositories;
import me.blockcraft.common.moderation.PlayerAddressRepository;
import me.blockcraft.common.moderation.PunishmentCatalogRepository;
import me.blockcraft.common.moderation.PunishmentType;
import me.blockcraft.common.moderation.StaffActionRepository;
import me.blockcraft.common.nick.NickRepository;
import me.blockcraft.common.network.GroupStateRepository;
import me.blockcraft.common.network.ServerRepository;
import me.blockcraft.common.network.operation.NetworkRequest;
import me.blockcraft.common.settings.PlayerSettingRepository;
import me.blockcraft.common.shop.Premium;
import me.blockcraft.common.welcome.GreetingRepository;
import me.blockcraft.system.alt.AddressHasher;
import me.blockcraft.system.alt.AltListener;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.command.moderation.CheckCommand;
import me.blockcraft.system.command.moderation.HistoryCommand;
import me.blockcraft.system.command.moderation.KickCommand;
import me.blockcraft.system.command.moderation.PunishmentCommands;
import me.blockcraft.system.command.moderation.StrafeCommand;
import me.blockcraft.system.command.moderation.StrikeCommands;
import me.blockcraft.system.command.moderation.WarnCommands;
import me.blockcraft.system.command.moderation.WerbungCommand;
import me.blockcraft.system.command.player.DiscordCommand;
import me.blockcraft.system.command.player.FriendCommand;
import me.blockcraft.system.command.player.HubCommand;
import me.blockcraft.system.command.player.IgnoreCommand;
import me.blockcraft.system.command.player.InfoCommand;
import me.blockcraft.system.command.player.MessageCommand;
import me.blockcraft.system.command.player.JoinMeCommand;
import me.blockcraft.system.command.player.ModCommand;
import me.blockcraft.system.command.player.OnlineCommand;
import me.blockcraft.system.command.player.PartyChatCommand;
import me.blockcraft.system.command.player.PartyCommand;
import me.blockcraft.system.command.player.PingCommand;
import me.blockcraft.system.command.player.PlayCommand;
import me.blockcraft.system.command.player.PlaytimeCommand;
import me.blockcraft.system.command.player.PremiumCommand;
import me.blockcraft.system.command.player.SeenCommand;
import me.blockcraft.system.command.player.SupportCommand;
import me.blockcraft.system.command.player.YouTuberCommand;
import me.blockcraft.system.command.staff.AltsCommand;
import me.blockcraft.system.command.staff.BroadcastCommand;
import me.blockcraft.system.command.staff.ChatSlowCommand;
import me.blockcraft.system.command.staff.ClearChatCommand;
import me.blockcraft.system.command.staff.EventCommand;
import me.blockcraft.system.command.staff.FindCommand;
import me.blockcraft.system.command.staff.GlobalMuteCommand;
import me.blockcraft.system.command.staff.GotoCommand;
import me.blockcraft.system.command.staff.ListCommand;
import me.blockcraft.system.command.staff.RankCommand;
import me.blockcraft.system.command.staff.MotdCommand;
import me.blockcraft.system.command.staff.RestartCommand;
import me.blockcraft.system.command.staff.ServerJoinCommand;
import me.blockcraft.system.command.staff.ServersCommand;
import me.blockcraft.system.command.staff.SetMaxCommand;
import me.blockcraft.system.command.staff.StaffLogCommand;
import me.blockcraft.system.command.staff.TeamChatCommand;
import me.blockcraft.system.command.staff.WartungenCommand;
import me.blockcraft.system.command.staff.WhoisCommand;
import me.blockcraft.system.friends.FriendListener;
import me.blockcraft.system.friends.FriendManager;
import me.blockcraft.system.friends.FriendRepository;
import me.blockcraft.system.friends.SpectateRequests;
import me.blockcraft.system.ignore.IgnoreList;
import me.blockcraft.system.listener.CommandListener;
import me.blockcraft.system.listener.FreezeQuitListener;
import me.blockcraft.system.listener.FreezeSwitchListener;
import me.blockcraft.system.listener.InitialServerListener;
import me.blockcraft.system.listener.KickListener;
import me.blockcraft.system.listener.LoginListener;
import me.blockcraft.system.listener.OperationListener;
import me.blockcraft.system.listener.ProxyMessageListener;
import me.blockcraft.system.listener.TabHeaderListener;
import me.blockcraft.system.listener.VanishSwitchListener;
import me.blockcraft.system.listener.WatchListener;
import me.blockcraft.system.moderation.ModerationActions;
import me.blockcraft.system.moderation.MuteGate;
import me.blockcraft.system.moderation.PrivateChatGate;
import me.blockcraft.system.moderation.PunishmentService;
import me.blockcraft.system.report.Reports;
import me.blockcraft.system.motd.MotdListener;
import me.blockcraft.system.motd.ReturningPlayers;
import me.blockcraft.system.network.LagAlert;
import me.blockcraft.system.network.LobbyAlert;
import me.blockcraft.system.network.OnDemand;
import me.blockcraft.system.network.OnDemandServers;
import me.blockcraft.system.network.ServerDirectory;
import me.blockcraft.system.network.ServerDiscovery;
import me.blockcraft.system.network.WakerClient;
import me.blockcraft.system.pack.NetworkPack;
import me.blockcraft.system.party.PartyListener;
import me.blockcraft.system.party.PartyManager;
import me.blockcraft.system.support.SupportListener;
import me.blockcraft.system.support.SupportSessions;
import me.blockcraft.system.task.AutoBroadcast;
import me.blockcraft.system.task.DailySchedule;
import me.blockcraft.system.welcome.Anniversaries;
import me.blockcraft.system.welcome.Newcomers;
import me.blockcraft.system.welcome.OgTag;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Plugin(id = "blockcraft-system", name = "BlockcraftSystem", version = BuildConstants.VERSION, authors = {"Aquitano"},
        dependencies = {@Dependency(id = "luckperms", optional = true)})
public final class SystemPlugin {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private Database database;
    private Optional<NetworkPack> pack = Optional.empty();

    @Inject
    public SystemPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent event) {
        try {
            enable();
        } catch (RuntimeException e) {
            logger.error("BlockcraftSystem failed to start; stopping the proxy so nobody joins without bans and mutes", e);
            proxy.shutdown();
        }
    }

    private void enable() {
        if (proxy.getPluginManager().getPlugin("blockcraft-report").isPresent()) {
            throw new IllegalStateException("Remove the old BlockcraftReport jar; reports are now part of BlockcraftSystem");
        }
        var config = SystemConfig.load(dataDirectory.resolve("config.yml"), () -> getClass().getResourceAsStream("/config.yml"));
        var loaded = Messages.load(dataDirectory, () -> getClass().getResourceAsStream("/messages.yml"));
        var messages = loaded.with(config.networkTags(), loaded.tagsOf("palette"));
        database = Database.connect(config.database(), "BlockcraftSystem");
        database.migrate();
        var repositories = ModerationRepositories.create(database);
        var servers = new ServerRepository(database);
        var discordLinks = new DiscordLinkRepository(database);
        var nicks = new NickRepository(database);
        var playerSettings = new PlayerSettingRepository(database);
        var friendships = new FriendRepository(database);
        var addresses = new PlayerAddressRepository(database);
        friendships.createTable();
        var catalog = new PunishmentCatalogRepository(database);
        catalog.replace(config.punishments().entrySet().stream()
                .map(preset -> new PunishmentCatalogRepository.Entry(preset.getKey(), preset.getValue().type(), preset.getValue().reason()))
                .toList());

        var settings = new ProxySettings(repositories.settings());
        var forcedOccasions = new ForcedOccasions(repositories.settings(), config.calendar());
        forcedOccasions.reload();
        var visibility = new PlayerVisibility(proxy, repositories.vanish(), nicks, logger);
        var choices = new PlayerChoices(playerSettings, logger);
        var ignores = new IgnoreList(new IgnoreRepository(database), logger);
        var afk = new AfkPlayers();
        var lookup = new PlayerLookup(proxy, visibility, repositories.players(), messages);
        var directory = new ServerDirectory(proxy, config.lobbyGroup(), Duration.ofSeconds(config.discovery().seatSeconds()),
                counts -> proxy.getScheduler().buildTask(this, () -> storeOccupancy(servers, counts)).schedule());
        var parties = new PartyManager(proxy, visibility, choices, ignores, directory, messages);
        var groupStates = new GroupStateRepository(database);
        var onDemand = config.onDemand().enabled()
                ? Optional.of(new OnDemandServers(proxy, new OnDemand(config.onDemand().settings()),
                        new WakerClient(config.onDemand().url(), config.onDemand().token(), logger), directory, groupStates,
                        messages, logger))
                : Optional.<OnDemandServers>empty();
        if (onDemand.isEmpty()) {
            // Off: no game is shown asleep or starting, whatever an earlier run left in the table.
            proxy.getScheduler().buildTask(this, () -> groupStates.retain(Set.of())).schedule();
        }
        var lobby = new LobbyConnector(directory, parties, config, messages, onDemand);
        onDemand.ifPresent(sleeping -> sleeping.connect(lobby));
        var punishments = new PunishmentService(proxy, repositories.punishments(), messages);
        var muteGate = new MuteGate(repositories.punishments(), punishments, messages, logger);
        var spectating = new SpectateRequests();
        var anniversaries = new Anniversaries(config.calendar(), database);
        var friends = new FriendManager(proxy, lookup, visibility, choices, ignores, directory, spectating, friendships, anniversaries,
                config, messages);
        var supportSessions = new SupportSessions();
        var playtime = new Playtime(repositories.players(), logger);
        var support = new CommandSupport(visibility, messages);
        var staffButtons = new StaffButtons(messages);
        var moderationActions = new ModerationActions(support, lookup, punishments, config, messages);
        var reports = Reports.load(this, proxy, logger, dataDirectory, database, lookup, visibility, choices, playerSettings,
                proxy.getPluginManager().fromInstance(this).orElseThrow().getDescription());
        register(reports.command().create());
        proxy.getEventManager().register(this, new OperationListener(proxy, (player, request) -> switch (request) {
            case NetworkRequest.SubmitReport report -> reports.service().submit(player, report.target(), report.reason());
            case NetworkRequest.TimedPunishment punishment -> moderationActions.timed(player, punishment.target(),
                    punishment.type(), Optional.of(Duration.ofSeconds(punishment.seconds())), punishment.reason());
            case NetworkRequest.PresetPunishment punishment -> moderationActions.preset(player, punishment.target(), punishment.number());
        }, messages, logger));

        var tabHeader = new TabHeaderListener(proxy, visibility, config, messages);

        var events = proxy.getEventManager();
        var newcomers = new Newcomers(this, proxy, directory, repositories.players(), new GreetingRepository(database),
                new OgTag(database), config.welcome(), messages, logger);
        events.register(this, new LoginListener(this, proxy, config, settings, repositories.players(), repositories.punishments(),
                punishments, lobby, newcomers, anniversaries, messages, logger));
        events.register(this, anniversaries);
        events.register(this, visibility);
        events.register(this, choices);
        events.register(this, ignores);
        events.register(this, afk);
        events.register(this, tabHeader);
        events.register(this, new CommandListener(config, messages));
        events.register(this, new KickListener(lobby, messages));
        events.register(this, new InitialServerListener(lobby));
        events.register(this, new ProxyMessageListener(proxy, lobby, visibility, choices, afk, friends, tabHeader, logger, () -> pack));
        var addressHasher = AddressHasher.load(dataDirectory);
        var returning = new ReturningPlayers(addressHasher, repositories.players(), choices, friends, config.serverListCache(), config.serverListFriends(), logger);
        events.register(this, new MotdListener(visibility, config, settings, returning, messages, dataDirectory, logger));
        events.register(this, directory);
        events.register(this, new PartyListener(parties));
        events.register(this, new FriendListener(friends, visibility, logger));
        events.register(this, spectating);
        events.register(this, new SupportListener(supportSessions, messages));
        events.register(this, new WatchListener(proxy, repositories.watches(), config.watchRecent(), config.alts().retention(),
                messages, staffButtons, logger));
        var alts = new AltListener(addresses, addressHasher, config.alts(), messages, logger);
        events.register(this, alts);
        events.register(this, new FreezeQuitListener(this, proxy, repositories.frozen(), punishments, moderationActions,
                config.freeze(), messages, staffButtons, logger));
        events.register(this, new FreezeSwitchListener(repositories.frozen(), messages, logger));
        events.register(this, new VanishSwitchListener(this, proxy, repositories.vanish(), visibility, logger));
        events.register(this, playtime);
        pack = NetworkPack.start(proxy, config.resourcePack(), dataDirectory, messages.get("resource-pack.prompt"), logger);
        pack.ifPresent(networkPack -> events.register(this, networkPack));

        var punishmentCommands = new PunishmentCommands(support, lookup, moderationActions, repositories.punishments(), messages);
        for (var type : PunishmentType.values()) {
            punishmentCommands.create(type).forEach(this::register);
        }
        register(new KickCommand(support, proxy, lookup, messages).create());
        register(new CheckCommand(support, lookup, punishments, messages).create());
        register(new HistoryCommand(support, lookup, punishments, config, messages).create());
        var staffActions = new StaffActionRepository(database);
        register(new StaffLogCommand(support, lookup, staffActions, config, messages, staffButtons).create());
        register(new StrafeCommand(support, moderationActions, config, messages).create());
        new StrikeCommands(support, lookup, repositories.strikes(), punishments, config, messages).create().forEach(this::register);
        new WarnCommands(support, proxy, lookup, repositories.warns(), config, messages).create().forEach(this::register);
        register(new WerbungCommand(support, lookup, repositories.adLog(), messages).create());

        register(new TeamChatCommand(support, proxy, messages).create());
        register(new FindCommand(support, lookup, messages).create());
        register(new GotoCommand(support, lookup, directory, messages).create());
        register(new ClearChatCommand(support, messages).create(), "clearchat");
        register(new GlobalMuteCommand(support, proxy, settings, messages).create());
        register(new ChatSlowCommand(support, proxy, settings, messages).create());
        register(new BroadcastCommand(support, proxy, messages).create());
        register(new WartungenCommand(this, support, proxy, settings, messages).create());
        register(new RestartCommand(this, support, proxy, messages).create());
        if (proxy.getPluginManager().isLoaded("luckperms")) {
            register(new RankCommand(support, lookup, staffActions, new Premium(database), config.calendar().zone(), messages,
                    logger).create());
        }
        register(new SetMaxCommand(support, config, messages).create());
        register(new MotdCommand(support, config, messages).create());
        register(new EventCommand(support, config.calendar(), forcedOccasions, staffActions, messages, logger).create());
        register(new ServersCommand(support, servers, config, messages).create());
        register(new ListCommand(support, visibility, messages).create());
        register(new WhoisCommand(support, lookup, repositories.players(), repositories.strikes(), repositories.warns(),
                punishments, playtime, new CoinsRepository(database), addresses, config.alts().retention(), messages).create());
        register(new AltsCommand(support, lookup, addresses, config.alts().retention(), messages).create());

        register(new HubCommand(support, lobby).create(), "h", "lobby", "l");
        register(new PlayCommand(support, lobby, directory).create());
        var privateChat = new PrivateChatGate(proxy, muteGate, ChatFilter.load(dataDirectory, config.network().addresses()), repositories.adLog(), staffButtons,
                config.privateChatCooldown(), messages, logger);
        events.register(this, privateChat);
        var privateMessages = new MessageCommand(support, proxy, visibility, lookup, choices, ignores, afk, privateChat, messages);
        events.register(this, privateMessages);
        register(privateMessages.msg(), "tell", "w");
        register(privateMessages.reply(), "reply");
        var ignoreCommands = new IgnoreCommand(support, lookup, visibility, ignores, config, messages);
        register(ignoreCommands.ignore());
        register(ignoreCommands.unignore());
        var friendCommands = new FriendCommand(support, friends, messages);
        register(friendCommands.create());
        register(friendCommands.list());
        register(new PartyCommand(support, lookup, parties, messages).create());
        register(new PartyChatCommand(support, parties, privateChat, messages).create());
        var joinMe = new JoinMeCommand(support, proxy, visibility, config, directory, messages);
        register(joinMe.create());
        register(new ServerJoinCommand(support, proxy, directory, joinMe, messages).create());
        register(new SupportCommand(support, proxy, supportSessions, muteGate, messages).create());
        register(new YouTuberCommand(support, messages).create(), "youtube", "yt");
        register(new PremiumCommand(support, messages).create(), "shop");
        register(new DiscordCommand(support, discordLinks, config, messages).create());
        register(new PingCommand(support, messages).create());
        register(new ModCommand(support, proxy, messages).create());
        register(new OnlineCommand(support, visibility, messages).create());
        register(new SeenCommand(support, visibility, repositories.players(), messages).create());
        register(new PlaytimeCommand(support, lookup, repositories.players(), playtime, messages).create());
        for (var info : config.infoCommands()) {
            if (!messages.has(InfoCommand.messageKey(info.name())) || proxy.getCommandManager().hasCommand(info.name())) {
                logger.warn("Skipping info command /{}: it has no {} lines or the name is taken", info.name(),
                        InfoCommand.messageKey(info.name()));
                continue;
            }
            register(InfoCommand.create(support, info.name(), messages), info.aliases().toArray(String[]::new));
        }

        var lobbyAlert = new LobbyAlert(proxy, directory, config, messages, logger);
        var lagAlert = new LagAlert(config, messages, logger);
        proxy.getScheduler().buildTask(this, new ServerDiscovery(proxy, servers, directory, lobbyAlert, lagAlert,
                        config.discovery().staleSeconds(), () -> onDemand.ifPresent(OnDemandServers::poll), logger))
                .repeat(config.discovery().pollSeconds(), TimeUnit.SECONDS)
                .schedule();
        proxy.getScheduler().buildTask(this, tabHeader)
                .repeat(config.tabRefreshSeconds(), TimeUnit.SECONDS)
                .schedule();
        proxy.getScheduler().buildTask(this, friends::purgeExpiredRequests)
                .repeat(config.friends().requestMinutes(), TimeUnit.MINUTES)
                .schedule();
        proxy.getScheduler().buildTask(this, () -> {
                    try {
                        friends.announceAnniversaries();
                    } catch (DatabaseException e) {
                        logger.warn("Could not announce friendship anniversaries", e);
                    }
                })
                .repeat(1, TimeUnit.MINUTES)
                .schedule();
        proxy.getScheduler().buildTask(this, () -> {
                    try {
                        forcedOccasions.reload();
                    } catch (DatabaseException e) {
                        logger.warn("Could not read the forced occasions", e);
                    }
                })
                .delay(config.calendarPollSeconds(), TimeUnit.SECONDS)
                .repeat(config.calendarPollSeconds(), TimeUnit.SECONDS)
                .schedule();
        proxy.getScheduler().buildTask(this, new AutoBroadcast(proxy, messages))
                .repeat(config.autoBroadcastMinutes(), TimeUnit.MINUTES)
                .schedule();
        DailySchedule.at(config.joinMe().resetHour(), config.calendar().zone(), proxy.getScheduler(), this, joinMe::resetDailyUses);
        DailySchedule.at(config.alts().purgeHour(), config.calendar().zone(), proxy.getScheduler(), this, alts::purgeExpired);
        logger.info("BlockcraftSystem enabled");
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        pack.ifPresent(NetworkPack::stop);
        if (database != null) {
            database.close();
        }
    }

    private void storeOccupancy(ServerRepository servers, Map<String, Integer> counts) {
        try {
            servers.occupancy(counts);
        } catch (DatabaseException e) {
            logger.warn("Could not store how many players the proxy counts on {}", counts.keySet(), e);
        }
    }

    private void register(BrigadierCommand command, String... aliases) {
        var commandManager = proxy.getCommandManager();
        commandManager.register(commandManager.metaBuilder(command).aliases(aliases).plugin(this).build(), command);
    }
}
