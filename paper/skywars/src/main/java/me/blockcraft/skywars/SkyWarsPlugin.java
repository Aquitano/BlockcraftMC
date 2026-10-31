package me.blockcraft.skywars;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.command.CommandRegistry;
import me.blockcraft.api.game.ArenaProtection;
import me.blockcraft.api.game.ForceMapCommand;
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
import me.blockcraft.skywars.config.SkyWarsConfig;
import me.blockcraft.skywars.game.SkyWarsGame;
import me.blockcraft.skywars.listener.ConnectionListener;
import me.blockcraft.skywars.listener.RoundListener;
import me.blockcraft.skywars.map.ArenaBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.NamespacedKey;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public final class SkyWarsPlugin extends JavaPlugin {

    private SkyWarsGame game;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var gameConfig = GameConfig.from(getConfig());
        var config = SkyWarsConfig.from(getConfig());
        var api = BlockcraftApi.get();
        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"), api.tags());
        var stats = new StatsRepository(api.database(), "bc_skywars_stats");
        stats.createTable();

        // One round per start: both worlds begin empty, so nothing of the last round or the last map is left over.
        var lobbyKey = new NamespacedKey("skywars", "lobby");
        var arenaKey = new NamespacedKey("skywars", "inseln");
        Worlds.discard(lobbyKey);
        Worlds.discard(arenaKey);
        var lobbyWorld = Worlds.createArena(lobbyKey);
        var lobbySpawn = ArenaBuilder.lobby(lobbyWorld);
        lobbyWorld.setSpawnLocation(lobbySpawn);
        var arena = Worlds.createArena(arenaKey);
        // Hunger and natural regeneration are part of the fight; no mob spawns anyway.
        arena.setDifficulty(Difficulty.NORMAL);

        var topList = new TopList(this, stats, messages);
        var statsMenus = StatsMenus.of(messages, stats, topList, gameConfig.globalStatsHead());
        game = new SkyWarsGame(this, gameConfig, config, messages, arena, lobbySpawn, stats, statsMenus);
        api.presence().maxPlayers(config.maxPlayers());

        register(game.hotbar());
        register(new ConnectionListener(game));
        register(new LobbyProtection(() -> game.phase() != GamePhase.INGAME, api.buildMode(),
                () -> game.isLobby() ? Optional.of(lobbySpawn) : Optional.empty()));
        register(new ArenaProtection(() -> false));
        register(new RoundListener(game, messages));

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = new CommandRegistry(event.registrar(), messages);
            commands.register(StartCommand.of(game.countdown()).build());
            commands.register(ForceMapCommand.create(messages, game.vote()::keys, game::isLobby, game::forceMap));
            commands.register(StatsCommand.create(this, stats, messages));
            commands.register(TopCommand.create(topList));
        });

        Bukkit.getOnlinePlayers().forEach(game::join);
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
