package me.blockcraft.rush;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.rush.command.BuildCommand;
import me.blockcraft.rush.command.ChestCommand;
import me.blockcraft.rush.command.RushCoinsCommand;
import me.blockcraft.rush.command.RushCommand;
import me.blockcraft.rush.command.RushSetCommand;
import me.blockcraft.rush.command.StartCommand;
import me.blockcraft.rush.command.StatsCommand;
import me.blockcraft.rush.command.TopCommand;
import me.blockcraft.rush.config.RushConfig;
import me.blockcraft.rush.config.Spawns;
import me.blockcraft.rush.game.Game;
import me.blockcraft.rush.listener.BlockListener;
import me.blockcraft.rush.listener.ConnectionListener;
import me.blockcraft.rush.listener.DeathListener;
import me.blockcraft.rush.listener.HotbarListener;
import me.blockcraft.rush.listener.MotdListener;
import me.blockcraft.rush.listener.ProtectionListener;
import me.blockcraft.rush.menu.RushMenus;
import me.blockcraft.rush.storage.KitRepository;
import me.blockcraft.rush.storage.StatsRepository;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class EmeraldRushPlugin extends JavaPlugin {

    private Game game;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var config = RushConfig.from(getConfig());
        var spawns = new Spawns(this);
        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"));

        var api = BlockcraftApi.get();
        var stats = new StatsRepository(api.database());
        var kits = new KitRepository(api.database());
        stats.createTable();
        kits.createTable();

        game = new Game(this, config, spawns, messages, stats, api.coins());
        var menus = new RushMenus(this, game, messages, spawns, kits, stats, api.coins());

        register(new ConnectionListener(game, messages));
        register(new DeathListener(game));
        register(new ProtectionListener(game));
        register(new BlockListener(this, game, config));
        register(new HotbarListener(this, game, menus, config, messages));
        register(new MotdListener(game, messages));

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var registrar = event.registrar();
            registrar.register(StartCommand.create(this, game, api.coins(), messages), "Startet die Runde", List.of());
            registrar.register(StatsCommand.create(this, stats, messages), "Zeigt die Stats eines Spielers", List.of());
            registrar.register(BuildCommand.create(game, messages), "Schaltet den Build-Modus um", List.of());
            registrar.register(RushSetCommand.create(spawns, messages), "Setzt eine Spawn-Location", List.of());
            registrar.register(TopCommand.create(menus), "Zeigt die Top 10 einer Kategorie", List.of());
            registrar.register(ChestCommand.create(game, menus, messages), "Öffnet den Shop", List.of());
            registrar.register(RushCommand.create(this, game, menus, api.coins(), messages), "Admin-Befehle", List.of());
            registrar.register(RushCoinsCommand.create(game, messages), "Setzt die Emeralds eines Spielers", List.of());
        });

        long idleRestartTicks = config.idleRestartMinutes() * 60L * 20L;
        Tasks.later(this, idleRestartTicks, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                Bukkit.shutdown();
            }
        });
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
