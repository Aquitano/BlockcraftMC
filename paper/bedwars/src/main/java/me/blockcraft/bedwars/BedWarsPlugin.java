package me.blockcraft.bedwars;

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
import me.blockcraft.api.world.TemplateDimensions;
import me.blockcraft.bedwars.command.BedWarsSetup;
import me.blockcraft.bedwars.config.BedWarsConfig;
import me.blockcraft.bedwars.game.Game;
import me.blockcraft.bedwars.listener.ArenaListener;
import me.blockcraft.bedwars.listener.ChatListener;
import me.blockcraft.bedwars.listener.ConnectionListener;
import me.blockcraft.bedwars.listener.ItemListener;
import me.blockcraft.bedwars.listener.WorldRules;
import me.blockcraft.bedwars.lobby.LobbyLayout;
import me.blockcraft.bedwars.map.MapPool;
import me.blockcraft.bedwars.shop.ShopCatalog;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class BedWarsPlugin extends JavaPlugin {

    private Game game;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var gameConfig = GameConfig.from(getConfig());
        var config = BedWarsConfig.from(getConfig());
        var api = BlockcraftApi.get();
        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"), api.tags());
        var catalog = ShopCatalog.parse(getConfig().getMapList("shop"));
        var stats = new StatsRepository(api.database(), "bw_stats");
        stats.createTable();

        WorldRules.settle(Bukkit.getWorlds().getFirst());
        var layout = LobbyLayout.load(api.map().file(this, "lobby.yml"));
        var pool = new MapPool(api.map().file(this, "maps"), getLogger());
        var arenas = new TemplateDimensions("bedwars", WorldRules::settle);
        var topList = new TopList(this, stats, messages);
        var statsMenus = StatsMenus.of(messages, stats, topList, gameConfig.globalStatsHead());
        game = new Game(this, gameConfig, config, messages, pool, arenas, layout, catalog, stats, statsMenus);
        api.presence().maxPlayers(config.capacity());

        game.listeners().forEach(this::register);
        register(new ConnectionListener(game));
        // After the round everyone waits for the restart in the lobby, which keeps its rules then too.
        register(new LobbyProtection(() -> game.phase() != GamePhase.INGAME, api.buildMode(), game::lobbySpawn));
        register(new ArenaListener(this, game, messages, config));
        register(new WorldRules());
        register(new ArenaProtection(() -> false));
        register(new ItemListener(this, game));
        register(new ChatListener(game, messages, config.playersPerTeam() == 1));

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = new CommandRegistry(event.registrar(), messages);
            commands.register(StartCommand.of(game.lobbyCountdown()).build());
            commands.register(ForceMapCommand.create(messages, game::playableMaps, game::mapForceable, game::forceMap));
            commands.register(StatsCommand.create(this, stats, messages));
            commands.register(TopCommand.create(topList));
            commands.register(BedWarsSetup.create(pool, arenas, layout, config.teams(), messages));
        });

        game.open();
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
