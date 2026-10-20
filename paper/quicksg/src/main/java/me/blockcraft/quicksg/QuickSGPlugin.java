package me.blockcraft.quicksg;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.command.CommandRegistry;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.game.ArenaProtection;
import me.blockcraft.api.game.CompassTracker;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.LobbyProtection;
import me.blockcraft.api.game.SetupCommand;
import me.blockcraft.api.game.StartCommand;
import me.blockcraft.api.game.LootChests;
import me.blockcraft.api.game.ServerRestart;
import me.blockcraft.api.game.StatsCommand;
import me.blockcraft.api.game.StatsMenus;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.game.TopCommand;
import me.blockcraft.api.game.TopList;
import me.blockcraft.common.message.Messages;
import me.blockcraft.quicksg.command.DebugCommands;
import me.blockcraft.quicksg.config.LocationKey;
import me.blockcraft.quicksg.config.QuickSGConfig;
import me.blockcraft.quicksg.game.Game;
import me.blockcraft.quicksg.game.GameItems;
import me.blockcraft.quicksg.listener.ConnectionListener;
import me.blockcraft.quicksg.listener.DeathListener;
import me.blockcraft.quicksg.listener.ItemListener;
import me.blockcraft.quicksg.listener.ProtectionListener;
import me.blockcraft.quicksg.kit.Kit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

public final class QuickSGPlugin extends JavaPlugin {

    private Game game;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var gameConfig = GameConfig.from(getConfig());
        var config = QuickSGConfig.from(getConfig());
        var api = BlockcraftApi.get();
        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"), api.tags());
        var locations = Locations.load(api.map().file(this, "locations.yml"), LocationKey::key);

        var stats = new StatsRepository(api.database(), "qsg_stats");
        stats.createTable();
        api.unlocks().importLegacyTable("qsg_kits", Kit.LEGACY_COLUMNS);

        var items = new GameItems(messages);
        var topList = new TopList(this, stats, messages);
        var statsMenus = StatsMenus.of(messages, stats, topList, gameConfig.globalStatsHead());
        game = new Game(this, gameConfig, config, messages, locations, stats, statsMenus, api.unlocks(), api.map(), items);
        api.presence().maxPlayers(config.maxPlayers());
        game.applyLobbyWorldBorder();

        var lootChests = new LootChests(config.loot(), entry -> items.loot(entry.material(), entry.amount()));

        List.of(
                new ConnectionListener(game),
                new DeathListener(game),
                new ProtectionListener(game, api.buildMode()),
                new LobbyProtection(game::isLobby, api.buildMode(), () -> locations.get(LocationKey.LOBBY)),
                new ArenaProtection(game::frozen),
                game.lobbyHotbar(),
                new ItemListener(game, messages, lootChests, new CompassTracker(messages, config.tracker()))
        ).forEach(this::register);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = new CommandRegistry(event.registrar(), messages);
            commands.register(StartCommand.of(game.lobbyCountdown())
                    .then(DebugCommands.timer(game))
                    .then(DebugCommands.wins(this, stats))
                    .build());
            commands.register(StatsCommand.create(this, stats, messages));
            commands.register(SetupCommand.locations(messages, List.of(LocationKey.values()),
                    key -> key.key().toLowerCase(Locale.ROOT), locations::set));
            commands.register(TopCommand.create(topList));
        });

        ServerRestart.whenIdle(this, gameConfig);
    }

    @Override
    public void onDisable() {
        Bukkit.getOnlinePlayers().forEach(Player::kick);
        if (game != null) {
            game.shutdown();
        }
        Bukkit.getScheduler().cancelTasks(this);
    }

    private void register(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }
}
