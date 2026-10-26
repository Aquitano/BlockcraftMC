package me.blockcraft.endergames;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.command.CommandRegistry;
import me.blockcraft.api.game.CompassTracker;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.LobbyProtection;
import me.blockcraft.api.game.ServerRestart;
import me.blockcraft.api.game.StartCommand;
import me.blockcraft.api.game.StatsCommand;
import me.blockcraft.api.game.StatsMenus;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.game.TopCommand;
import me.blockcraft.api.game.TopList;
import me.blockcraft.api.world.Worlds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.endergames.config.EnderGamesConfig;
import me.blockcraft.endergames.game.EnderGamesGame;
import me.blockcraft.endergames.listener.ConnectionListener;
import me.blockcraft.endergames.listener.RoundListener;
import me.blockcraft.endergames.world.LobbyPlatform;
import me.blockcraft.endergames.world.RoundWorld;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public final class EnderGamesPlugin extends JavaPlugin {

    private EnderGamesGame game;
    private RoundWorld world;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var gameConfig = GameConfig.from(getConfig());
        var config = EnderGamesConfig.from(getConfig());
        var api = BlockcraftApi.get();
        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"), api.tags());
        var stats = new StatsRepository(api.database(), "bc_endergames_stats");
        stats.createTable();

        var lobbyWorld = Worlds.createArena(new NamespacedKey("endergames", "lobby"));
        var lobbySpawn = LobbyPlatform.build(lobbyWorld);
        lobbyWorld.setSpawnLocation(lobbySpawn);
        world = RoundWorld.create(this, config.world());

        var topList = new TopList(this, stats, messages);
        var statsMenus = StatsMenus.of(messages, stats, topList, gameConfig.globalStatsHead());
        game = new EnderGamesGame(this, gameConfig, config, messages, world, lobbySpawn, stats, statsMenus);
        api.presence().maxPlayers(config.maxPlayers());

        register(game.hotbar());
        register(game.abilities());
        register(new ConnectionListener(game));
        register(new LobbyProtection(() -> game.phase() != GamePhase.INGAME, api.buildMode(),
                () -> game.isLobby() ? Optional.of(lobbySpawn) : Optional.empty()));
        register(new RoundListener(game, new CompassTracker(messages, config.tracker())));

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = new CommandRegistry(event.registrar(), messages);
            commands.register(StartCommand.of(game.countdown()).build());
            commands.register(StatsCommand.create(this, stats, messages));
            commands.register(TopCommand.create(topList));
        });

        Bukkit.getOnlinePlayers().forEach(game::join);
        world.prepare(game::worldReady);
        ServerRestart.whenIdle(this, gameConfig);
    }

    @Override
    public void onDisable() {
        if (game != null) {
            game.stop();
        }
        Bukkit.getOnlinePlayers().forEach(player -> player.kick(Component.empty()));
        if (world != null) {
            world.discard();
        }
        Bukkit.getScheduler().cancelTasks(this);
    }

    private void register(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }
}
