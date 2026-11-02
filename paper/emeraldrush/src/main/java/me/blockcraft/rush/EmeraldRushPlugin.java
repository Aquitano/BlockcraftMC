package me.blockcraft.rush;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.ArenaProtection;
import me.blockcraft.api.game.CompassTracker;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GameServices;
import me.blockcraft.api.game.RoundConnections;
import me.blockcraft.api.game.SetupCommand;
import me.blockcraft.api.game.StartCommand;
import me.blockcraft.api.world.WorldProtection;
import me.blockcraft.rush.command.ChestCommand;
import me.blockcraft.rush.command.RushCommand;
import me.blockcraft.rush.config.RushConfig;
import me.blockcraft.rush.config.SpawnPoint;
import me.blockcraft.rush.config.Spawns;
import me.blockcraft.rush.game.Game;
import me.blockcraft.rush.game.GamePhase;
import me.blockcraft.rush.listener.BlockListener;
import me.blockcraft.rush.listener.DeathListener;
import me.blockcraft.rush.listener.HotbarListener;
import me.blockcraft.rush.listener.ProtectionListener;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class EmeraldRushPlugin extends JavaPlugin {

    private Game game;

    @Override
    public void onEnable() {
        getConfig().options().copyDefaults(true);
        var gameConfig = GameConfig.from(getConfig());
        var config = RushConfig.from(getConfig());
        var api = BlockcraftApi.get();
        var spawns = Spawns.load(api.map().file(this, "locations.yml"), api.waitingLobby());
        var services = GameServices.load(this, gameConfig, "em_stats");
        var messages = services.messages();

        game = new Game(this, gameConfig, config, spawns, messages, services.stats(), services.menus(), api.unlocks(), api.map());
        api.presence().maxPlayers(config.maxPlayers());

        services.listen(
                new RoundConnections(game),
                new DeathListener(game),
                new ProtectionListener(game, api.buildMode()),
                WorldProtection.during(() -> game.phase() == GamePhase.LOBBY, api.buildMode())
                        .rescue(() -> spawns.get(SpawnPoint.LOBBY)),
                new ArenaProtection(() -> false),
                new BlockListener(this, game, config, api.buildMode()),
                game.lobbyHotbar(),
                new HotbarListener(game, game.menus(), new CompassTracker(messages, config.tracker())));

        services.commands(commands -> {
            commands.register(StartCommand.of(game.lobbyCountdown()).build());
            commands.register(SetupCommand.locations(messages, List.of(SpawnPoint.values()).stream()
                    .filter(point -> api.waitingLobby().isEmpty() || point != SpawnPoint.LOBBY).toList(), SpawnPoint::key, spawns::set));
            commands.register(ChestCommand.create(game, game.menus(), messages));
            commands.register(RushCommand.create(game, game.menus(), messages));
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
}
