package me.blockcraft.core;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.common.config.YamlFile;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.ModerationRepositories;
import me.blockcraft.core.command.ClearCommand;
import me.blockcraft.core.command.ConnectCommand;
import me.blockcraft.core.command.EnchantCommand;
import me.blockcraft.core.command.FlyCommand;
import me.blockcraft.core.command.FlySpeedCommand;
import me.blockcraft.core.command.FoodCommand;
import me.blockcraft.core.command.GamemodeCommand;
import me.blockcraft.core.command.HealCommand;
import me.blockcraft.core.command.InvseeCommand;
import me.blockcraft.core.command.KillCommand;
import me.blockcraft.core.command.MsgCommand;
import me.blockcraft.core.command.RainCommand;
import me.blockcraft.core.command.ReflashCommand;
import me.blockcraft.core.command.ServerInfoCommand;
import me.blockcraft.core.command.SetHealthCommand;
import me.blockcraft.core.command.SkullCommand;
import me.blockcraft.core.command.SpeedCommand;
import me.blockcraft.core.command.SudoCommand;
import me.blockcraft.core.command.SunCommand;
import me.blockcraft.core.command.TeamCommand;
import me.blockcraft.core.command.TimeCommand;
import me.blockcraft.core.command.TpCommand;
import me.blockcraft.core.command.VanishCommand;
import me.blockcraft.core.command.XpCommand;
import me.blockcraft.core.listener.ChatCooldownListener;
import me.blockcraft.core.listener.ChatFilterConfig;
import me.blockcraft.core.listener.ChatFilterListener;
import me.blockcraft.core.listener.GlobalMuteListener;
import me.blockcraft.core.listener.MuteListener;
import me.blockcraft.core.listener.VanishListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
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
        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"));

        var moderation = ModerationRepositories.create(BlockcraftApi.get().database());
        moderation.createTables();
        var chatFilter = ChatFilterConfig.fromYaml(config);
        var pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new MuteListener(moderation.punishments(), messages), this);
        pluginManager.registerEvents(new GlobalMuteListener(moderation.settings(), messages), this);
        pluginManager.registerEvents(new ChatCooldownListener(chatFilter.cooldown(), messages), this);
        pluginManager.registerEvents(new ChatFilterListener(chatFilter, moderation.adLog(), messages), this);
        var vanish = new VanishListener(this);
        pluginManager.registerEvents(vanish, this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var registrar = event.registrar();
            registrar.register(TeamCommand.create(messages), "Team-Hilfe");
            registrar.register(EnchantCommand.create(), "Öffnet einen Zaubertisch");
            registrar.register(FlyCommand.create(messages), "Flugmodus");
            registrar.register(FlySpeedCommand.create(messages), "Fluggeschwindigkeit");
            registrar.register(FoodCommand.create(messages), "Füllt den Hunger");
            registrar.register(GamemodeCommand.create(messages), "Ändert den Gamemode");
            registrar.register(HealCommand.create(messages), "Heilt dich");
            registrar.register(InvseeCommand.create(messages), "Öffnet das Inventar eines Spielers");
            registrar.register(ServerInfoCommand.create(messages), "Zeigt Serverinformationen");
            registrar.register(SetHealthCommand.create(messages), "Setzt die Herzen eines Spielers");
            registrar.register(SkullCommand.create(messages), "Gibt dir einen Spielerkopf");
            registrar.register(SpeedCommand.create(messages), "Speed-Effekt");
            registrar.register(SudoCommand.create(messages, sudoProtected), "Lässt einen Spieler etwas schreiben");
            registrar.register(KillCommand.create(messages), "Tötet einen Spieler");
            registrar.register(RainCommand.create(messages), "Aktiviert Regen");
            registrar.register(SunCommand.create(messages), "Deaktiviert Regen");
            registrar.register(TimeCommand.create(messages), "Ändert die Zeit");
            registrar.register(TpCommand.create(messages), "Teleportiert Spieler");
            registrar.register(XpCommand.create(messages), "Setzt das Level eines Spielers");
            registrar.register(MsgCommand.create(messages), "Private Nachricht");
            registrar.register(VanishCommand.create(vanish, messages), "Unsichtbarkeit", List.of("vanish"));
            registrar.register(ClearCommand.create(messages), "Leert das Inventar eines Spielers");
            registrar.register(ConnectCommand.create(this, messages), "Wechselt den Server");
            registrar.register(ReflashCommand.create(this, messages), "Aktualisiert die Spielerliste");
        });
    }
}
