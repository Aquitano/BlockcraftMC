package me.blockcraft.core;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.command.CommandRegistry;
import me.blockcraft.common.config.YamlFile;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.ModerationRepositories;
import me.blockcraft.core.command.AchievementsCommand;
import me.blockcraft.core.command.BackCommand;
import me.blockcraft.core.command.BackLocations;
import me.blockcraft.core.command.ClearCommand;
import me.blockcraft.core.command.ConnectCommand;
import me.blockcraft.core.command.DayTimeCommand;
import me.blockcraft.core.command.EloCommand;
import me.blockcraft.core.command.EnchantCommand;
import me.blockcraft.core.command.FreezeCommand;
import me.blockcraft.core.command.FlyCommand;
import me.blockcraft.core.command.FoodCommand;
import me.blockcraft.core.command.GamemodeCommand;
import me.blockcraft.core.command.HealCommand;
import me.blockcraft.core.command.InvseeCommand;
import me.blockcraft.core.command.KillCommand;
import me.blockcraft.core.command.NearCommand;
import me.blockcraft.core.command.NickCommand;
import me.blockcraft.core.command.PTimeCommand;
import me.blockcraft.core.command.PWeatherCommand;
import me.blockcraft.core.command.RainCommand;
import me.blockcraft.core.command.ReflashCommand;
import me.blockcraft.core.command.ReportCommand;
import me.blockcraft.core.command.ServerInfoCommand;
import me.blockcraft.core.command.SetHealthCommand;
import me.blockcraft.core.command.SkullCommand;
import me.blockcraft.core.command.SpeedCommand;
import me.blockcraft.core.command.StaffCommand;
import me.blockcraft.core.command.SudoCommand;
import me.blockcraft.core.command.SunCommand;
import me.blockcraft.core.command.TeamCommand;
import me.blockcraft.core.command.TpAllCommand;
import me.blockcraft.core.command.TpHereCommand;
import me.blockcraft.core.command.TpCommand;
import me.blockcraft.core.command.VanishCommand;
import me.blockcraft.core.command.XpCommand;
import me.blockcraft.core.listener.ChatCooldownListener;
import me.blockcraft.core.listener.ChatFilterConfig;
import me.blockcraft.core.listener.ChatFilterListener;
import me.blockcraft.core.listener.ChatFormatListener;
import me.blockcraft.core.listener.GlobalMuteListener;
import me.blockcraft.core.listener.MuteListener;
import me.blockcraft.core.staff.FreezeService;
import me.blockcraft.core.staff.InvseeGuard;
import me.blockcraft.core.staff.StaffInventoryRepository;
import me.blockcraft.core.staff.StaffMode;
import me.blockcraft.core.staff.VanishService;
import me.blockcraft.core.nick.NickListener;
import me.blockcraft.core.nick.Nicks;
import me.blockcraft.common.nick.NickRepository;
import me.blockcraft.common.report.ReportRepository;
import org.bukkit.GameMode;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class BlockcraftCorePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var config = YamlFile.loadWithDefaults(getDataPath().resolve("config.yml"), () -> getResource("config.yml"));
        Set<String> sudoProtected = YamlFile.section(config, "sudo").get("protected-players") instanceof List<?> names
                ? names.stream().map(String::valueOf).map(String::toLowerCase).collect(Collectors.toUnmodifiableSet())
                : Set.of();
        var api = BlockcraftApi.get();
        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"), api.tags());
        var moderation = ModerationRepositories.create(api.database());
        var chatFilter = ChatFilterConfig.fromYaml(config);
        var pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new MuteListener(this, moderation.punishments(), messages), this);
        pluginManager.registerEvents(new GlobalMuteListener(moderation.settings(), messages), this);
        pluginManager.registerEvents(new ChatCooldownListener(chatFilter.cooldown(), messages), this);
        pluginManager.registerEvents(new ChatFilterListener(this, chatFilter, api.server().name(), moderation.adLog(), messages), this);
        var nickRepository = new NickRepository(api.database());
        var nickSection = YamlFile.section(config, "nick");
        var nicks = new Nicks(this, nickRepository, stringList(nickSection, "names"), stringList(nickSection, "skins"),
                stringList(nickSection, "ranks"));
        pluginManager.registerEvents(new NickListener(nicks, messages), this);
        pluginManager.registerEvents(new ChatFormatListener(messages, nicks), this);
        var vanish = new VanishService(this, moderation.vanish());
        pluginManager.registerEvents(vanish, this);
        pluginManager.registerEvents(new InvseeGuard(), this);
        var freeze = new FreezeService(this, moderation.frozen(), messages);
        pluginManager.registerEvents(freeze, this);
        freeze.start();
        var staffInventories = new StaffInventoryRepository(api.database());
        staffInventories.createTable();
        var audit = api.audit();
        var staffMode = new StaffMode(this, messages, vanish, freeze, staffInventories, audit, new ReportRepository(api.database()),
                moderation.punishments());
        pluginManager.registerEvents(staffMode, this);
        var back = new BackLocations();
        pluginManager.registerEvents(back, this);
        var nearSection = YamlFile.section(config, "near");
        var nearRadius = new NearCommand.Radius(number(nearSection, "default-radius", 100), number(nearSection, "max-radius", 500));

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = new CommandRegistry(event.registrar(), messages);
            commands.register(TeamCommand.create(messages));
            commands.register(EnchantCommand.create(messages));
            commands.register(FlyCommand.create(audit, messages));
            commands.register(FoodCommand.create(audit, messages), "feed");
            commands.register(GamemodeCommand.create(audit, messages));
            commands.register(GamemodeCommand.shortcut("gms", GameMode.SURVIVAL, audit, messages));
            commands.register(GamemodeCommand.shortcut("gmc", GameMode.CREATIVE, audit, messages));
            commands.register(GamemodeCommand.shortcut("gma", GameMode.ADVENTURE, audit, messages));
            commands.register(GamemodeCommand.shortcut("gmsp", GameMode.SPECTATOR, audit, messages));
            commands.register(HealCommand.create(audit, messages));
            commands.register(InvseeCommand.create(audit, messages));
            commands.register(ServerInfoCommand.create(messages));
            commands.register(SetHealthCommand.create(messages));
            commands.register(SkullCommand.create(messages));
            commands.register(SpeedCommand.create(audit, messages));
            commands.register(SudoCommand.create(audit, messages, sudoProtected));
            commands.register(KillCommand.create(audit, messages));
            commands.register(RainCommand.create(messages));
            commands.register(SunCommand.create(messages));
            commands.register(DayTimeCommand.day(messages));
            commands.register(DayTimeCommand.night(messages));
            commands.register(TpCommand.create(back, audit, messages));
            commands.register(TpHereCommand.create(back, audit, messages));
            commands.register(TpAllCommand.create(back, audit, messages));
            commands.register(BackCommand.create(back, audit, messages));
            commands.register(NearCommand.create(nearRadius, messages));
            commands.register(PTimeCommand.create(messages));
            commands.register(PWeatherCommand.create(messages));
            commands.register(XpCommand.create(messages));
            commands.register(VanishCommand.create(vanish, audit, messages), "vanish");
            commands.register(FreezeCommand.create(freeze, audit, messages));
            commands.register(StaffCommand.create(staffMode));
            commands.register(ClearCommand.create(audit, messages));
            commands.register(ConnectCommand.create(this, messages));
            commands.register(ReflashCommand.create(this, messages));
            commands.register(NickCommand.create(nicks, audit, messages));
            commands.register(ReportCommand.create(this, stringList(YamlFile.section(config, "report"), "reasons"), messages));
            commands.register(AchievementsCommand.create());
            commands.register(EloCommand.create(this, api.elo(), messages));
        });
    }

    private static List<String> stringList(Map<String, Object> section, String key) {
        return section.get(key) instanceof List<?> values ? values.stream().map(String::valueOf).toList() : List.of();
    }

    private static int number(Map<String, Object> section, String key, int fallback) {
        return section.get(key) instanceof Number value ? value.intValue() : fallback;
    }
}
