package me.blockcraft.rush;

import me.blockcraft.api.command.CommandRegistry;
import me.blockcraft.api.game.ArenaProtection;
import me.blockcraft.api.game.ServerRestart;
import me.blockcraft.api.game.StatsCommand;
import me.blockcraft.api.game.StatsMenus;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.game.TopCommand;
import me.blockcraft.api.game.TopList;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.common.message.Messages;
import me.blockcraft.rush.command.ChestCommand;
import me.blockcraft.rush.command.RushCoinsCommand;
import me.blockcraft.rush.command.RushCommand;
import me.blockcraft.rush.command.RushSetCommand;
import me.blockcraft.rush.command.StartCommand;
import me.blockcraft.rush.config.RushConfig;
import me.blockcraft.rush.config.Spawns;
import me.blockcraft.rush.game.Game;
import me.blockcraft.rush.listener.BlockListener;
import me.blockcraft.rush.listener.ConnectionListener;
import me.blockcraft.rush.listener.DeathListener;
import me.blockcraft.rush.listener.HotbarListener;
import me.blockcraft.rush.listener.ProtectionListener;
import me.blockcraft.rush.kit.Perk;
import me.blockcraft.rush.menu.RushMenus;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;


public final class EmeraldRushPlugin extends JavaPlugin {

    private Game game;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var config = RushConfig.from(getConfig());
        var api = BlockcraftApi.get();
        var spawns = Spawns.load(api.map().file(this, "locations.yml"));
        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"), api.tags());

        var stats = new StatsRepository(api.database(), "em_stats");
        stats.createTable();
        api.unlocks().importLegacyTable("em_kits", Perk.LEGACY_COLUMNS);

        game = new Game(this, config, spawns, messages, stats, api.map());
        var menus = new RushMenus(game, messages, spawns, api.unlocks(), api.shop());
        var topList = new TopList(this, stats, messages);
        var statsMenus = StatsMenus.of(messages, stats, topList, config.globalStatsHead());

        register(new ConnectionListener(game));
        register(new DeathListener(game));
        register(new ProtectionListener(game, api.buildMode()));
        register(new ArenaProtection(() -> false));
        register(new BlockListener(this, game, config, api.buildMode()));
        register(new HotbarListener(this, game, menus, statsMenus, config, messages));

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = new CommandRegistry(event.registrar(), messages);
            commands.register(StartCommand.create(this, game, api.coins(), messages));
            commands.register(StatsCommand.create(this, stats, messages));
            commands.register(RushSetCommand.create(spawns, messages));
            commands.register(TopCommand.create(topList));
            commands.register(ChestCommand.create(game, menus, messages));
            commands.register(RushCommand.create(this, game, menus, api.coins(), messages));
            commands.register(RushCoinsCommand.create(game, messages));
        });

        int idleSeconds = config.idleRestartMinutes() * 60;
        ServerRestart.whenIdle(this, idleSeconds, idleSeconds);
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
