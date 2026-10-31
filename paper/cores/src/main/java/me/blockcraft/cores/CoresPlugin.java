package me.blockcraft.cores;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.command.CommandRegistry;
import me.blockcraft.api.game.ArenaProtection;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.LobbyProtection;
import me.blockcraft.api.game.LootChests;
import me.blockcraft.api.game.ServerRestart;
import me.blockcraft.api.game.StartCommand;
import me.blockcraft.api.game.StatsCommand;
import me.blockcraft.api.game.StatsMenus;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.game.TopCommand;
import me.blockcraft.api.game.TopList;
import me.blockcraft.api.world.Worlds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.cores.arena.SkyMap;
import me.blockcraft.cores.config.CoresConfig;
import me.blockcraft.cores.game.CoresGame;
import me.blockcraft.cores.listener.ArenaListener;
import me.blockcraft.cores.listener.ChatListener;
import me.blockcraft.cores.listener.ConnectionListener;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.NamespacedKey;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public final class CoresPlugin extends JavaPlugin {

    private CoresGame game;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var gameConfig = GameConfig.from(getConfig());
        var config = CoresConfig.from(getConfig());
        var api = BlockcraftApi.get();
        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"), api.tags());
        var stats = new StatsRepository(api.database(), "bc_cores_stats");
        stats.createTable();
        var world = Worlds.createArena(new NamespacedKey("cores", "himmelsfeste"));
        // Hunger and natural regeneration are part of the fight; no mob spawns anyway.
        world.setDifficulty(Difficulty.NORMAL);
        var map = SkyMap.build(world, new LootChests(config.chests()));
        world.setSpawnLocation(map.lobbySpawn());
        var topList = new TopList(this, stats, messages);
        var statsMenus = StatsMenus.of(messages, stats, topList, gameConfig.globalStatsHead());
        game = new CoresGame(this, gameConfig, config, messages, map, stats, statsMenus);
        api.presence().maxPlayers(config.capacity());

        register(game.hotbar());
        register(new ConnectionListener(game));
        register(new LobbyProtection(() -> game.phase() != GamePhase.INGAME, api.buildMode(), () -> Optional.of(map.lobbySpawn())));
        register(new ArenaProtection(() -> false));
        register(new ArenaListener(game, messages));
        register(new ChatListener(game, messages));

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = new CommandRegistry(event.registrar(), messages);
            commands.register(StartCommand.of(game.countdown()).build());
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
