package me.blockcraft.ttt;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.command.CommandRegistry;
import me.blockcraft.api.game.ArenaProtection;
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
import me.blockcraft.ttt.arena.TownBuilder;
import me.blockcraft.ttt.command.TraitorChatCommand;
import me.blockcraft.ttt.config.TttConfig;
import me.blockcraft.ttt.game.KarmaRepository;
import me.blockcraft.ttt.game.TttGame;
import me.blockcraft.ttt.listener.ArenaListener;
import me.blockcraft.ttt.listener.ChatListener;
import me.blockcraft.ttt.listener.ConnectionListener;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.NamespacedKey;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public final class TttPlugin extends JavaPlugin {

    private static final int BORDER_SIZE = 89;

    private TttGame game;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var gameConfig = GameConfig.from(getConfig());
        var config = TttConfig.from(getConfig());
        var api = BlockcraftApi.get();
        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"), api.tags());
        var stats = new StatsRepository(api.database(), "bc_ttt_stats");
        stats.createTable();
        var karma = new KarmaRepository(api.database());
        karma.createTable();
        var world = Worlds.createArena(new NamespacedKey("ttt", "eichenhain"));
        // Peaceful would take the creeper arrows' creepers away at once.
        world.setDifficulty(Difficulty.NORMAL);
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, config.regeneration());
        var town = TownBuilder.build(world);
        world.setSpawnLocation(town.lobbySpawn());
        world.getWorldBorder().setCenter(0.5, 0.5);
        world.getWorldBorder().setSize(BORDER_SIZE);
        var topList = new TopList(this, stats, messages);
        var statsMenus = StatsMenus.of(messages, stats, topList, gameConfig.globalStatsHead());
        game = new TttGame(this, gameConfig, config, messages, town, stats, statsMenus, karma);
        api.presence().maxPlayers(config.capacity());

        register(game.hotbar());
        register(new ConnectionListener(game));
        register(new LobbyProtection(() -> game.phase() != GamePhase.INGAME, api.buildMode(), () -> Optional.of(town.lobbySpawn())));
        register(new ArenaProtection(() -> false));
        register(new ArenaListener(game, messages));
        register(new ChatListener(game, messages));

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = new CommandRegistry(event.registrar(), messages);
            commands.register(StartCommand.of(game.countdown()).build());
            commands.register(StatsCommand.create(this, stats, messages));
            commands.register(TopCommand.create(topList));
            commands.register(TraitorChatCommand.create(game, messages));
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
