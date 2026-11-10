package me.blockcraft.api.game;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.command.CommandRegistry;
import me.blockcraft.common.message.Messages;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Consumer;

/**
 * What every game plugin sets up the same way when it enables: its messages, its stats table with the top list,
 * the stats screens and the board on the shared waiting island, {@code /stats} and {@code /top}, and the restart
 * of an idle server.
 */
public final class GameServices {

    private final JavaPlugin plugin;
    private final Messages messages;
    private final StatsRepository stats;
    private final TopList topList;
    private final StatsMenus menus;

    private GameServices(JavaPlugin plugin, GameConfig config, String statsTable, boolean ranked) {
        var api = BlockcraftApi.get();
        this.plugin = plugin;
        this.messages = Messages.load(plugin.getDataPath(), () -> plugin.getResource("messages.yml"), api.tags());
        this.stats = new StatsRepository(api.database(), statsTable);
        stats.createTable();
        this.topList = new TopList(plugin, stats, messages);
        this.menus = ranked
                ? StatsMenus.ranked(messages, stats, topList, config.globalStatsHead())
                : StatsMenus.of(messages, stats, topList, config.globalStatsHead());
        WaitingBoard.show(plugin, stats);
        ServerRestart.whenIdle(plugin, config);
    }

    public static GameServices load(JavaPlugin plugin, GameConfig config, String statsTable) {
        return new GameServices(plugin, config, statsTable, false);
    }

    /** As {@link #load}, with stats screens that show each player's rating of the season. */
    public static GameServices ranked(JavaPlugin plugin, GameConfig config, String statsTable) {
        return new GameServices(plugin, config, statsTable, true);
    }

    public Messages messages() {
        return messages;
    }

    public StatsRepository stats() {
        return stats;
    }

    public StatsMenus menus() {
        return menus;
    }

    public void listen(Listener... listeners) {
        for (var listener : listeners) {
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }
    }

    /** Registers {@code /stats} and {@code /top} next to the game's own commands. */
    public void commands(Consumer<CommandRegistry> game) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = new CommandRegistry(event.registrar(), messages);
            commands.register(StatsCommand.create(plugin, stats, messages));
            commands.register(TopCommand.create(topList));
            game.accept(commands);
        });
    }
}
