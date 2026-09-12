package me.blockcraft.quicksg;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.quicksg.command.BuildCommand;
import me.blockcraft.quicksg.command.SetupCommand;
import me.blockcraft.quicksg.command.StartCommand;
import me.blockcraft.quicksg.command.StatsCommand;
import me.blockcraft.quicksg.command.TopCommand;
import me.blockcraft.quicksg.config.LocationKey;
import me.blockcraft.quicksg.config.QuickSGConfig;
import me.blockcraft.quicksg.game.Game;
import me.blockcraft.quicksg.game.GameItems;
import me.blockcraft.quicksg.game.GameScoreboard;
import me.blockcraft.quicksg.game.LootChests;
import me.blockcraft.quicksg.game.TopList;
import me.blockcraft.quicksg.listener.ConnectionListener;
import me.blockcraft.quicksg.listener.DeathListener;
import me.blockcraft.quicksg.listener.ItemListener;
import me.blockcraft.quicksg.listener.ProtectionListener;
import me.blockcraft.quicksg.menu.KitMenus;
import me.blockcraft.quicksg.menu.StatsMenus;
import me.blockcraft.quicksg.storage.KitRepository;
import me.blockcraft.quicksg.storage.StatsRepository;
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
        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"));
        var locations = Locations.load(getDataPath().resolve("locations.yml"), LocationKey::key);

        var api = BlockcraftApi.get();
        var stats = new StatsRepository(api.database());
        var kits = new KitRepository(api.database());
        stats.createTable();
        kits.createTable();

        var items = new GameItems(messages);
        game = new Game(this, config, messages, locations, stats, api.coins(), new GameScoreboard(messages, config), items);
        api.presence().maxPlayers(config.maxPlayers());
        game.applyLobbyWorldBorder();

        var lootChests = new LootChests(config.loot(), items);
        var topList = new TopList(this, stats, messages);
        var kitMenus = new KitMenus(this, messages, game, kits, api.coins(), config.kitPrice());
        var statsMenus = new StatsMenus(this, messages, stats, items, topList, config.globalStatsHead());

        List.of(
                new ConnectionListener(game, messages),
                new DeathListener(game),
                new ProtectionListener(game),
                new ItemListener(this, game, messages, lootChests, kitMenus, statsMenus)
        ).forEach(this::register);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var registrar = event.registrar();
            registrar.register(StartCommand.create(this, game, messages, api.coins(), stats), "Startet die Runde", List.of());
            registrar.register(StatsCommand.create(this, stats, messages), "Zeigt die Stats eines Spielers", List.of());
            registrar.register(BuildCommand.create(game, messages), "Schaltet den Build Modus um", List.of());
            registrar.register(SetupCommand.create(locations, messages), "Setzt die QuickSG Locations", List.of());
            registrar.register(TopCommand.create(topList, messages), "Zeigt die Top 10 einer Kategorie", List.of());
        });

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            var actionbar = messages.get("actionbar");
            Bukkit.getOnlinePlayers().forEach(player -> player.sendActionBar(actionbar));
        }, ACTIONBAR_INTERVAL_TICKS, ACTIONBAR_INTERVAL_TICKS);
        scheduleIdleRestart(config.countdowns().idleRestart(), config.countdowns().idleRestartRetry());
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

    /** Restarts an empty server after the idle period; with players online it checks again after the retry period. */
    private void scheduleIdleRestart(int seconds, int retrySeconds) {
        Tasks.later(this, seconds * 20L, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                Bukkit.shutdown();
            } else {
                scheduleIdleRestart(retrySeconds, retrySeconds);
            }
        });
    }
}
