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
import me.blockcraft.common.coins.CoinBalanceRepository;
import me.blockcraft.common.db.Database;
import me.blockcraft.common.discord.DiscordLinkRepository;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.ModerationRepositories;
import me.blockcraft.common.moderation.PlayerAddressRepository;
import me.blockcraft.common.moderation.PunishmentCatalogRepository;
import me.blockcraft.common.nick.NickRepository;
import me.blockcraft.common.network.ServerRepository;
import me.blockcraft.common.network.operation.NetworkRequest;
import me.blockcraft.common.settings.PlayerSettingRepository;
import me.blockcraft.system.alt.AddressHasher;
import me.blockcraft.system.alt.AltListener;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.command.moderation.BanCommand;
import me.blockcraft.system.command.moderation.BanListCommand;
import me.blockcraft.system.command.moderation.CheckCommand;
import me.blockcraft.system.command.moderation.DelStrikeCommand;
import me.blockcraft.system.command.moderation.HistoryCommand;
import me.blockcraft.system.command.moderation.KickCommand;
import me.blockcraft.system.command.moderation.MuteCommand;
import me.blockcraft.system.command.moderation.MuteListCommand;
import me.blockcraft.system.command.moderation.ShowStrikesCommand;
import me.blockcraft.system.command.moderation.StrafeCommand;
import me.blockcraft.system.command.moderation.StrikeCommand;
import me.blockcraft.system.command.moderation.StrikesCommand;
import me.blockcraft.system.command.moderation.TempBanCommand;
import me.blockcraft.system.command.moderation.TempMuteCommand;
import me.blockcraft.system.command.moderation.UnbanCommand;
import me.blockcraft.system.command.moderation.UnmuteCommand;
import me.blockcraft.system.command.moderation.WarnCommand;
import me.blockcraft.system.command.moderation.WarnsCommand;
import me.blockcraft.system.command.moderation.WerbungCommand;
import me.blockcraft.system.command.player.DiscordCommand;
import me.blockcraft.system.command.player.FriendCommand;
import me.blockcraft.system.command.player.HubCommand;
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
import me.blockcraft.system.command.staff.AddPremiumCommand;
import me.blockcraft.system.command.staff.AltsCommand;
import me.blockcraft.system.command.staff.BroadcastCommand;
import me.blockcraft.system.command.staff.ClearChatCommand;
import me.blockcraft.system.command.staff.FindCommand;
import me.blockcraft.system.command.staff.GlobalMuteCommand;
import me.blockcraft.system.command.staff.GotoCommand;
import me.blockcraft.system.command.staff.ListCommand;
import me.blockcraft.system.command.staff.MotdCommand;
import me.blockcraft.system.command.staff.RestartCommand;
import me.blockcraft.system.command.staff.ServerJoinCommand;
import me.blockcraft.system.command.staff.ServersCommand;
import me.blockcraft.system.command.staff.SetMaxCommand;
import me.blockcraft.system.command.staff.TeamChatCommand;
import me.blockcraft.system.command.staff.WartungenCommand;
import me.blockcraft.system.command.staff.WhoisCommand;
import me.blockcraft.system.friends.FriendListener;
import me.blockcraft.system.friends.FriendManager;
import me.blockcraft.system.friends.FriendRepository;
import me.blockcraft.system.friends.SpectateRequests;
import me.blockcraft.system.listener.CommandListener;
import me.blockcraft.system.listener.FreezeQuitListener;
import me.blockcraft.system.listener.InitialServerListener;
import me.blockcraft.system.listener.KickListener;
import me.blockcraft.system.listener.LoginListener;
import me.blockcraft.system.listener.OperationListener;
import me.blockcraft.system.listener.ProxyMessageListener;
import me.blockcraft.system.listener.TabHeaderListener;
import me.blockcraft.system.listener.WatchListener;
import me.blockcraft.system.moderation.ModerationActions;
import me.blockcraft.system.moderation.MuteGate;
import me.blockcraft.system.moderation.PunishmentService;
import me.blockcraft.system.report.Reports;
import me.blockcraft.system.motd.MotdListener;
import me.blockcraft.system.network.LagAlert;
import me.blockcraft.system.network.LobbyAlert;
import me.blockcraft.system.network.ServerDirectory;
import me.blockcraft.system.network.ServerDiscovery;
import me.blockcraft.system.pack.NetworkPack;
import me.blockcraft.system.party.PartyListener;
import me.blockcraft.system.party.PartyManager;
import me.blockcraft.system.support.SupportListener;
import me.blockcraft.system.support.SupportSessions;
import me.blockcraft.system.task.AutoBroadcast;
import me.blockcraft.system.task.DailySchedule;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
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
        var visibility = new PlayerVisibility(proxy, repositories.vanish(), nicks, logger);
        var choices = new PlayerChoices(playerSettings, logger);
        var lookup = new PlayerLookup(proxy, visibility, repositories.players());
        var directory = new ServerDirectory(proxy, config.lobbyGroup());
        var lobby = new LobbyConnector(directory, config, messages);
        var punishments = new PunishmentService(proxy, repositories.punishments(), messages, config.network());
        var muteGate = new MuteGate(repositories.punishments(), punishments, messages, logger);
        var parties = new PartyManager(proxy, visibility, choices, messages);
        var spectating = new SpectateRequests();
        var friends = new FriendManager(proxy, lookup, visibility, choices, directory, spectating, friendships, config, messages);
        var supportSessions = new SupportSessions();
        var playtime = new Playtime(repositories.players(), logger);
        var support = new CommandSupport(visibility, messages);
        var moderationActions = new ModerationActions(support, lookup, punishments, config, messages);
        var reports = Reports.load(this, proxy, logger, dataDirectory, database, lookup, visibility,
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
        events.register(this, new LoginListener(this, proxy, config, settings, repositories.players(), repositories.punishments(),
                punishments, lobby, messages, logger));
        events.register(this, visibility);
        events.register(this, choices);
        events.register(this, tabHeader);
        events.register(this, new CommandListener(config, messages));
        events.register(this, new KickListener(lobby, messages));
        events.register(this, new InitialServerListener(lobby));
        events.register(this, new ProxyMessageListener(proxy, lobby, visibility, choices, friends, tabHeader, logger, () -> pack));
        events.register(this, new MotdListener(visibility, config, settings, messages, dataDirectory, logger));
        events.register(this, new PartyListener(parties));
        events.register(this, new FriendListener(friends, visibility, logger));
        events.register(this, spectating);
        events.register(this, new SupportListener(supportSessions, messages));
        events.register(this, new WatchListener(proxy, repositories.watches(), config.watchRecent(), config.alts().retention(),
                messages, logger));
        var alts = new AltListener(addresses, AddressHasher.load(dataDirectory), config.alts(), messages, logger);
        events.register(this, alts);
        events.register(this, new FreezeQuitListener(this, proxy, repositories.frozen(), punishments, moderationActions,
                config.freeze(), messages, logger));
        events.register(this, playtime);
        pack = NetworkPack.start(proxy, config.resourcePack(), dataDirectory, messages.get("resource-pack.prompt"), logger);
        pack.ifPresent(networkPack -> events.register(this, networkPack));

        register(new BanCommand(support).create());
        register(new TempBanCommand(support, moderationActions, messages).create());
        register(new UnbanCommand(support, lookup, punishments, messages).create());
        register(new BanListCommand(support, lookup, repositories.punishments(), messages).create());
        register(new MuteCommand(support).create());
        register(new TempMuteCommand(support, moderationActions, messages).create());
        register(new UnmuteCommand(support, lookup, punishments, messages).create());
        register(new MuteListCommand(support, lookup, repositories.punishments(), messages).create());
        register(new KickCommand(support, proxy, visibility, messages).create());
        register(new CheckCommand(support, lookup, punishments, messages).create());
        register(new HistoryCommand(support, lookup, punishments, config, messages).create());
        register(new StrafeCommand(support, moderationActions, messages).create());
        register(new StrikeCommand(support, visibility, repositories.strikes(), punishments, config, messages).create());
        register(new StrikesCommand(support, repositories.strikes(), messages).create());
        register(new DelStrikeCommand(support, lookup, repositories.strikes(), messages).create());
        register(new ShowStrikesCommand(support, lookup, repositories.strikes(), messages).create());
        register(new WarnCommand(support, proxy, lookup, repositories.warns(), config, messages).create());
        register(new WarnsCommand(support, lookup, repositories.warns(), messages).create());
        register(new WerbungCommand(support, lookup, repositories.adLog(), messages).create());

        register(new TeamChatCommand(support, proxy, messages).create());
        register(new FindCommand(support, visibility, messages).create());
        register(new GotoCommand(support, visibility, messages).create());
        register(new ClearChatCommand(support, messages).create(), "clearchat");
        register(new GlobalMuteCommand(support, proxy, settings, messages).create());
        register(new BroadcastCommand(support, proxy, messages).create());
        register(new WartungenCommand(this, support, proxy, settings, messages).create());
        register(new RestartCommand(this, support, proxy, messages).create());
        register(new AddPremiumCommand(support, proxy, lookup, messages).create());
        register(new SetMaxCommand(support, config, messages).create());
        register(new MotdCommand(support, config, messages).create());
        register(new ServerJoinCommand(support, proxy, messages).create());
        register(new ServersCommand(support, servers, config, messages).create());
        register(new ListCommand(support, visibility, messages).create());
        register(new WhoisCommand(support, lookup, repositories.players(), repositories.strikes(), repositories.warns(),
                punishments, playtime, new CoinBalanceRepository(database), addresses, config.alts().retention(), messages).create());
        register(new AltsCommand(support, lookup, addresses, config.alts().retention(), messages).create());

        register(new HubCommand(support, lobby).create(), "h", "lobby", "l");
        register(new PlayCommand(support, lobby, directory).create());
        var privateMessages = new MessageCommand(support, proxy, visibility, choices, muteGate, messages);
        events.register(this, privateMessages);
        register(privateMessages.msg(), "tell", "w");
        register(privateMessages.reply(), "reply");
        var friendCommands = new FriendCommand(support, friends, messages);
        register(friendCommands.create());
        register(friendCommands.list());
        register(new PartyCommand(support, visibility, parties, messages).create());
        register(new PartyChatCommand(support, parties, muteGate, messages).create());
        var joinMe = new JoinMeCommand(support, proxy, visibility, config, directory, messages);
        register(joinMe.create());
        register(new SupportCommand(support, proxy, supportSessions, muteGate, messages).create());
        register(new YouTuberCommand(support, messages).create(), "youtube", "yt");
        register(new PremiumCommand(support, messages).create(), "shop");
        register(new DiscordCommand(support, discordLinks, config, messages).create());
        register(new PingCommand(support, messages).create());
        register(new ModCommand(support, proxy, messages).create());
        register(new OnlineCommand(support, visibility, messages).create());
        register(new SeenCommand(support, visibility, repositories.players(), messages).create());
        register(new PlaytimeCommand(support, lookup, repositories.players(), playtime, messages).create());

        var lobbyAlert = new LobbyAlert(proxy, directory, config, messages, logger);
        var lagAlert = new LagAlert(config, messages, logger);
        proxy.getScheduler().buildTask(this, new ServerDiscovery(proxy, servers, directory, lobbyAlert, lagAlert,
                        config.discovery().staleSeconds(), logger))
                .repeat(config.discovery().pollSeconds(), TimeUnit.SECONDS)
                .schedule();
        proxy.getScheduler().buildTask(this, tabHeader)
                .repeat(config.tabRefreshSeconds(), TimeUnit.SECONDS)
                .schedule();
        proxy.getScheduler().buildTask(this, friends::purgeExpiredRequests)
                .repeat(config.friends().requestMinutes(), TimeUnit.MINUTES)
                .schedule();
        proxy.getScheduler().buildTask(this, new AutoBroadcast(proxy, messages))
                .repeat(config.autoBroadcastMinutes(), TimeUnit.MINUTES)
                .schedule();
        DailySchedule.at(config.joinMe().resetHour(), proxy.getScheduler(), this, joinMe::resetDailyUses);
        DailySchedule.at(config.alts().purgeHour(), proxy.getScheduler(), this, alts::purgeExpired);
        logger.info("BlockcraftSystem enabled");
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        pack.ifPresent(NetworkPack::stop);
        if (database != null) {
            database.close();
        }
    }

    private void register(BrigadierCommand command, String... aliases) {
        var commandManager = proxy.getCommandManager();
        commandManager.register(commandManager.metaBuilder(command).aliases(aliases).plugin(this).build(), command);
    }
}
