package me.blockcraft.quicksg;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.command.CommandRegistry;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.game.ArenaProtection;
import me.blockcraft.api.game.LootChests;
import me.blockcraft.api.game.ServerRestart;
import me.blockcraft.api.game.StatsCommand;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.game.TopList;
import me.blockcraft.common.message.Messages;
import me.blockcraft.quicksg.command.SetupCommand;
import me.blockcraft.quicksg.command.StartCommand;
import me.blockcraft.quicksg.command.TopCommand;
import me.blockcraft.quicksg.config.LocationKey;
import me.blockcraft.quicksg.config.QuickSGConfig;
import me.blockcraft.quicksg.game.Game;
import me.blockcraft.quicksg.game.GameItems;
import me.blockcraft.quicksg.game.GameScoreboard;
import me.blockcraft.quicksg.listener.ConnectionListener;
import me.blockcraft.quicksg.listener.DeathListener;
import me.blockcraft.quicksg.listener.ItemListener;
import me.blockcraft.quicksg.listener.ProtectionListener;
import me.blockcraft.quicksg.kit.Kit;
import me.blockcraft.quicksg.menu.KitMenus;
import me.blockcraft.quicksg.menu.StatsMenus;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class QuickSGPlugin extends JavaPlugin {

    private static final long ACTIONBAR_INTERVAL_TICKS = 40L;

    private Game game;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var config = QuickSGConfig.from(getConfig());
        var api = BlockcraftApi.get();
        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"), api.tags());
        var locations = Locations.load(api.map().file(this, "locations.yml"), LocationKey::key);

        var stats = new StatsRepository(api.database(), "qsg_stats");
        stats.createTable();
        api.unlocks().importLegacyTable("qsg_kits", Kit.LEGACY_COLUMNS);

        var items = new GameItems(messages);
        game = new Game(this, config, messages, locations, stats, new GameScoreboard(messages, config, api.map()), items);
        api.presence().maxPlayers(config.maxPlayers());
        game.applyLobbyWorldBorder();

        var lootChests = new LootChests(config.loot(), entry -> items.loot(entry.material(), entry.amount()));
        var topList = new TopList(this, stats, messages);
        var kitMenus = new KitMenus(this, messages, game, api.unlocks(), api.shop(), config.kitPrice());
        var statsMenus = new StatsMenus(this, messages, stats, items, topList, config.globalStatsHead());

        List.of(
                new ConnectionListener(game),
                new DeathListener(game),
                new ProtectionListener(game, api.buildMode()),
                new ArenaProtection(game::frozen),
                new ItemListener(this, game, messages, lootChests, kitMenus, statsMenus)
        ).forEach(this::register);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = new CommandRegistry(event.registrar(), messages);
            commands.register(StartCommand.create(this, game, messages, api.coins(), stats));
            commands.register(StatsCommand.create(this, stats, messages));
            commands.register(SetupCommand.create(locations, messages));
            commands.register(TopCommand.create(topList, messages));
        });

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            var actionbar = messages.get("actionbar");
            Bukkit.getOnlinePlayers().forEach(player -> player.sendActionBar(actionbar));
        }, ACTIONBAR_INTERVAL_TICKS, ACTIONBAR_INTERVAL_TICKS);
        ServerRestart.whenIdle(this, config.countdowns().idleRestart(), config.countdowns().idleRestartRetry());
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
