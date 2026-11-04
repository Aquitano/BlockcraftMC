package me.blockcraft.knockbackffa;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.config.BundledDefaults;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.command.CommandRegistry;
import me.blockcraft.api.game.ArenaProtection;
import me.blockcraft.api.world.WorldProtection;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.ServerRestart;
import me.blockcraft.api.game.StatsCommand;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.game.TopCommand;
import me.blockcraft.api.game.TopList;
import me.blockcraft.common.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class KnockbackFfaPlugin extends JavaPlugin {

    private FfaGame game;

    @Override
    public void onEnable() {
        BundledDefaults.apply(this);
        var api = BlockcraftApi.get();
        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"), api.tags());
        var stats = new StatsRepository(api.database(), "knockbackffa_stats");
        stats.createTable();
        var arena = new FfaArena();
        game = new FfaGame(this, FfaConfig.from(getConfig()), messages, arena, stats);
        getServer().getPluginManager().registerEvents(new ArenaProtection(() -> false), this);
        getServer().getPluginManager().registerEvents(WorldProtection.in(arena.world(), api.buildMode()).withHeldItems().withDamage(), this);
        getServer().getPluginManager().registerEvents(game, this);
        getServer().getPluginManager().registerEvents(game.spawnProtection(), this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = new CommandRegistry(event.registrar(), messages);
            commands.register(game.command());
            commands.register(StatsCommand.create(this, stats, messages));
            commands.register(TopCommand.create(new TopList(this, stats, messages)));
        });
        Bukkit.getOnlinePlayers().forEach(game::join);
        game.start();
        ServerRestart.whenIdle(this, GameConfig.from(getConfig()));
    }

    @Override
    public void onDisable() {
        if (game != null) {
            game.close();
        }
        Bukkit.getScheduler().cancelTasks(this);
    }
}
