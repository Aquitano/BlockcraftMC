package me.blockcraft.rush;

import me.blockcraft.api.command.CommandRegistry;
import me.blockcraft.api.game.ArenaProtection;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.LobbyProtection;
import me.blockcraft.api.game.ServerRestart;
import me.blockcraft.api.game.SetupCommand;
import me.blockcraft.api.game.StartCommand;
import me.blockcraft.api.game.StatsCommand;
import me.blockcraft.api.game.StatsMenus;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.game.TopCommand;
import me.blockcraft.api.game.TopList;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.common.message.Messages;
import me.blockcraft.rush.command.ChestCommand;
import me.blockcraft.rush.command.RushCommand;
import me.blockcraft.rush.config.RushConfig;
import me.blockcraft.rush.config.SpawnPoint;
import me.blockcraft.rush.config.Spawns;
import me.blockcraft.rush.game.Game;
import me.blockcraft.rush.game.GamePhase;
import me.blockcraft.rush.listener.BlockListener;
import me.blockcraft.rush.listener.ConnectionListener;
import me.blockcraft.rush.listener.DeathListener;
import me.blockcraft.rush.listener.HotbarListener;
import me.blockcraft.rush.listener.ProtectionListener;
import me.blockcraft.rush.kit.Perk;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class EmeraldRushPlugin extends JavaPlugin {

    private Game game;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var gameConfig = GameConfig.from(getConfig());
        var config = RushConfig.from(getConfig());
        var api = BlockcraftApi.get();
        var spawns = Spawns.load(api.map().file(this, "locations.yml"));
        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"), api.tags());

        var stats = new StatsRepository(api.database(), "em_stats");
        stats.createTable();
        api.unlocks().importLegacyTable("em_kits", Perk.LEGACY_COLUMNS);

        var topList = new TopList(this, stats, messages);
        var statsMenus = StatsMenus.of(messages, stats, topList, gameConfig.globalStatsHead());
        game = new Game(this, gameConfig, config, spawns, messages, stats, statsMenus, api.unlocks(), api.map());
        api.presence().maxPlayers(config.maxPlayers());

        register(new ConnectionListener(game));
        register(new DeathListener(game));
        register(new ProtectionListener(game, api.buildMode()));
        register(new LobbyProtection(() -> game.phase() == GamePhase.LOBBY, api.buildMode(), () -> spawns.get(SpawnPoint.LOBBY)));
        register(new ArenaProtection(() -> false));
        register(new BlockListener(this, game, config, api.buildMode()));
        register(game.lobbyHotbar());
        register(new HotbarListener(game, game.menus(), config, messages));

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = new CommandRegistry(event.registrar(), messages);
            commands.register(StartCommand.of(game.lobbyCountdown()).build());
            commands.register(StatsCommand.create(this, stats, messages));
            commands.register(SetupCommand.locations(messages, List.of(SpawnPoint.values()), SpawnPoint::key, spawns::set));
            commands.register(TopCommand.create(topList));
            commands.register(ChestCommand.create(game, game.menus(), messages));
            commands.register(RushCommand.create(game, game.menus(), messages));
        });

        ServerRestart.whenIdle(this, gameConfig);
    }

    @Override
    public void onDisable() {
        if (game != null) {
            game.stop();
        }
        Bukkit.getOnlinePlayers().forEach(player -> player.kick(Component.empty()));
        Bukkit.getScheduler().cancelTasks(this);
    }

    private void register(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }
}
