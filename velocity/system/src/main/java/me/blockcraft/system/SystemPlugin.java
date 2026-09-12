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
import me.blockcraft.common.db.Database;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.ModerationRepositories;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.command.moderation.BanCommand;
import me.blockcraft.system.command.moderation.BanListCommand;
import me.blockcraft.system.command.moderation.CheckCommand;
import me.blockcraft.system.command.moderation.DelStrikeCommand;
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
import me.blockcraft.system.command.player.HubCommand;
import me.blockcraft.system.command.player.JoinMeCommand;
import me.blockcraft.system.command.player.ModCommand;
import me.blockcraft.system.command.player.OnlineCommand;
import me.blockcraft.system.command.player.PartyChatCommand;
import me.blockcraft.system.command.player.PartyCommand;
import me.blockcraft.system.command.player.PingCommand;
import me.blockcraft.system.command.player.PremiumCommand;
import me.blockcraft.system.command.player.SupportCommand;
import me.blockcraft.system.command.player.YouTuberCommand;
import me.blockcraft.system.command.staff.AddPremiumCommand;
import me.blockcraft.system.command.staff.BroadcastCommand;
import me.blockcraft.system.command.staff.ClearChatCommand;
import me.blockcraft.system.command.staff.FindCommand;
import me.blockcraft.system.command.staff.GlobalMuteCommand;
import me.blockcraft.system.command.staff.GotoCommand;
import me.blockcraft.system.command.staff.ListCommand;
import me.blockcraft.system.command.staff.MotdCommand;
import me.blockcraft.system.command.staff.RestartCommand;
import me.blockcraft.system.command.staff.ServerJoinCommand;
import me.blockcraft.system.command.staff.SetMaxCommand;
import me.blockcraft.system.command.staff.TeamChatCommand;
import me.blockcraft.system.command.staff.WartungenCommand;
import me.blockcraft.system.listener.CommandListener;
import me.blockcraft.system.listener.KickListener;
import me.blockcraft.system.listener.LoginListener;
import me.blockcraft.system.listener.TabCompleteListener;
import me.blockcraft.system.listener.TabHeaderListener;
import me.blockcraft.system.moderation.PunishmentService;
import me.blockcraft.system.motd.MotdListener;
import me.blockcraft.system.party.PartyListener;
import me.blockcraft.system.party.PartyManager;
import me.blockcraft.system.support.SupportListener;
import me.blockcraft.system.support.SupportSessions;
import me.blockcraft.system.task.AutoBroadcast;
import me.blockcraft.system.task.DailySchedule;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Plugin(id = "blockcraft-system", name = "BlockcraftSystem", version = BuildConstants.VERSION, authors = {"Aquitano"},
        dependencies = {@Dependency(id = "luckperms", optional = true)})
public final class SystemPlugin {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private Database database;

    @Inject
    public SystemPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent event) {
        var config = SystemConfig.load(dataDirectory.resolve("config.yml"), () -> getClass().getResourceAsStream("/config.yml"));
        var messages = Messages.load(dataDirectory, () -> getClass().getResourceAsStream("/messages.yml"));
        database = Database.connect(config.database(), "BlockcraftSystem");
        var repositories = ModerationRepositories.create(database);
        repositories.createTables();

        var settings = new ProxySettings(repositories.settings());
        var lookup = new PlayerLookup(proxy, repositories.players());
        var lobby = new LobbyConnector(proxy, config, messages);
        var punishments = new PunishmentService(proxy, repositories.punishments(), messages, config.network());
        var parties = new PartyManager(proxy, messages);
        var supportSessions = new SupportSessions();
        var support = new CommandSupport(proxy, messages);

        var events = proxy.getEventManager();
        events.register(this, new LoginListener(this, proxy, config, settings, repositories.players(), repositories.punishments(),
                punishments, lobby, messages, logger));
        events.register(this, new TabHeaderListener(proxy, config, messages));
        events.register(this, new CommandListener(config, messages));
        events.register(this, new KickListener(lobby, messages));
        events.register(this, new TabCompleteListener());
        events.register(this, new MotdListener(proxy, config, settings, messages));
        events.register(this, new PartyListener(parties));
        events.register(this, new SupportListener(supportSessions));

        register(new BanCommand(support).create());
        register(new TempBanCommand(support, lookup, punishments, config, messages).create());
        register(new UnbanCommand(support, lookup, punishments, messages).create());
        register(new BanListCommand(support, lookup, repositories.punishments(), messages).create());
        register(new MuteCommand(support).create());
        register(new TempMuteCommand(support, lookup, punishments, messages).create());
        register(new UnmuteCommand(support, lookup, punishments, messages).create());
        register(new MuteListCommand(support, lookup, repositories.punishments(), messages).create());
        register(new KickCommand(support, proxy, config, messages).create());
        register(new CheckCommand(support, lookup, punishments, messages).create());
        register(new StrafeCommand(support, lookup, punishments, config, messages).create());
        register(new StrikeCommand(support, proxy, repositories.strikes(), punishments, config, messages).create());
        register(new StrikesCommand(support, repositories.strikes(), messages).create());
        register(new DelStrikeCommand(support, lookup, repositories.strikes(), messages).create());
        register(new ShowStrikesCommand(support, lookup, repositories.strikes(), messages).create());
        register(new WarnCommand(support, proxy, lookup, repositories.warns(), config, messages).create());
        register(new WarnsCommand(support, lookup, repositories.warns(), messages).create());
        register(new WerbungCommand(support, lookup, repositories.adLog(), messages).create());

        register(new TeamChatCommand(support, proxy, messages).create());
        register(new FindCommand(support, proxy, messages).create());
        register(new GotoCommand(support, proxy, messages).create());
        register(new ClearChatCommand(support, messages).create(), "clearchat");
        register(new GlobalMuteCommand(support, proxy, settings, messages).create());
        register(new BroadcastCommand(support, proxy, messages).create());
        register(new WartungenCommand(this, support, proxy, settings, config, messages).create());
        register(new RestartCommand(this, support, proxy, config, messages).create());
        register(new AddPremiumCommand(support, proxy, lookup, messages).create());
        register(new SetMaxCommand(support, config, messages).create());
        register(new MotdCommand(support, config, messages).create());
        register(new ServerJoinCommand(support, proxy, messages).create());
        register(new ListCommand(support, proxy, config, messages).create());

        register(new HubCommand(support, lobby).create(), "h", "lobby", "l");
        register(new PartyCommand(support, proxy, parties, messages).create());
        register(new PartyChatCommand(support, parties, messages).create());
        var joinMe = new JoinMeCommand(support, proxy, config, messages);
        register(joinMe.create());
        register(new SupportCommand(support, proxy, supportSessions, messages).create());
        register(new YouTuberCommand(support, config, messages).create(), "youtube", "yt");
        register(new PremiumCommand(support, config, messages).create(), "shop");
        register(new PingCommand(support, messages).create());
        register(new ModCommand(support, proxy, messages).create());
        register(new OnlineCommand(support, proxy, messages).create());

        proxy.getScheduler().buildTask(this, new AutoBroadcast(proxy, config, messages))
                .repeat(config.autoBroadcastMinutes(), TimeUnit.MINUTES)
                .schedule();
        DailySchedule.at(config.joinMe().resetHour(), proxy.getScheduler(), this, joinMe::resetDailyUses);
        logger.info("BlockcraftSystem enabled");
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        if (database != null) {
            database.close();
        }
    }

    private void register(BrigadierCommand command, String... aliases) {
        var commandManager = proxy.getCommandManager();
        commandManager.register(commandManager.metaBuilder(command).aliases(aliases).plugin(this).build(), command);
    }
}
