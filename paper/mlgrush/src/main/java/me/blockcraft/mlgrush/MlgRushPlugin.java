package me.blockcraft.mlgrush;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.command.CommandRegistry;
import me.blockcraft.api.game.ArenaProtection;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.LobbyProtection;
import me.blockcraft.api.game.ServerRestart;
import me.blockcraft.api.game.StartCommand;
import me.blockcraft.api.game.StatsCommand;
import me.blockcraft.api.game.StatsMenus;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.game.TopCommand;
import me.blockcraft.api.game.TopList;
import me.blockcraft.common.message.Messages;
import me.blockcraft.api.game.rush.RushArena;
import me.blockcraft.api.game.rush.MapPool;
import me.blockcraft.api.game.rush.RushConfig;
import me.blockcraft.mlgrush.game.StandaloneRushGame;
import me.blockcraft.mlgrush.listener.ConnectionListener;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class MlgRushPlugin extends JavaPlugin {

    private StandaloneRushGame game;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var config = RushConfig.from(getConfig());
        var gameConfig = GameConfig.from(getConfig());
        var mapsFile = getDataPath().resolve("maps.yml");
        if (Files.notExists(mapsFile)) {
            saveResource("maps.yml", false);
        }
        var maps = MapPool.load(mapsFile, config.arena());
        var map = maps.choose(ThreadLocalRandom.current());
        getLogger().info("MLG Rush map: " + map.key() + " (" + map.name() + ")");
        var api = BlockcraftApi.get();
        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"), api.tags());
        var stats = new StatsRepository(api.database(), "mlgrush_stats");
        stats.createTable();
        var topList = new TopList(this, stats, messages);
        var statsMenus = StatsMenus.of(messages, stats, topList, gameConfig.globalStatsHead());
        var arena = new RushArena(map);
        game = new StandaloneRushGame(this, gameConfig, config, messages, arena, maps, stats, statsMenus);
        List.of(new ConnectionListener(game),
                new ArenaProtection(() -> false),
                new LobbyProtection(() -> !game.isIngame(), api.buildMode(), java.util.Optional::empty),
                game.hotbar()).forEach(this::register);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = new CommandRegistry(event.registrar(), messages);
            commands.register(StartCommand.of(game.countdown()).build());
            commands.register(StatsCommand.create(this, stats, messages));
            commands.register(TopCommand.create(topList));
        });
        Bukkit.getOnlinePlayers().forEach(game::join);
        game.refreshPresence();
        ServerRestart.whenIdle(this, gameConfig);
    }

    @Override
    public void onDisable() {
        if (game != null) {
            game.shutdown();
        }
        Bukkit.getScheduler().cancelTasks(this);
    }

    private void register(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }
}
