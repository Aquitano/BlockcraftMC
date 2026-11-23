package me.blockcraft.rush;

import me.blockcraft.api.config.BundledDefaults;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.game.ArenaProtection;
import me.blockcraft.api.game.CompassTracker;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GameServices;
import me.blockcraft.api.game.RoundConnections;
import me.blockcraft.api.game.RoundHost;
import me.blockcraft.api.game.SetupCommand;
import me.blockcraft.api.game.StartCommand;
import me.blockcraft.api.world.WorldProtection;
import me.blockcraft.rush.command.ChestCommand;
import me.blockcraft.rush.command.RushCommand;
import me.blockcraft.rush.config.RushConfig;
import me.blockcraft.rush.config.SpawnPoint;
import me.blockcraft.rush.config.Spawns;
import me.blockcraft.rush.game.ArenaCopies;
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

    private RoundHost<Game> host;

    @Override
    public void onEnable() {
        BundledDefaults.apply(this);
        var gameConfig = GameConfig.from(getConfig());
        var config = RushConfig.from(getConfig());
        var api = BlockcraftApi.get();
        var locations = Locations.load(api.map().file(this, "locations.yml"), SpawnPoint::key);
        var services = GameServices.load(this, gameConfig, "em_stats");
        var messages = services.messages();
        var arenas = new ArenaCopies(this);

        api.presence().maxPlayers(config.maxPlayers());
        services.listen(new ArenaProtection(() -> false));
        host = RoundHost.start(this, gameConfig, round -> {
            var spawns = new Spawns(locations, api.waitingLobby());
            var game = new Game(round, gameConfig, config, spawns, arenas, messages, services.stats(), services.menus(), api.unlocks(), api.map());
            round.listen(
                    new RoundConnections(game),
                    new DeathListener(game),
                    new ProtectionListener(game, api.buildMode()),
                    game.spawnProtection(),
                    WorldProtection.during(() -> game.phase() == GamePhase.LOBBY, api.buildMode())
                            .rescue(() -> spawns.get(SpawnPoint.LOBBY)),
                    new BlockListener(round, game, config, api.buildMode()),
                    game.lobbyHotbar(),
                    new HotbarListener(game, game.menus(), new CompassTracker(messages, config.tracker())));
            return game;
        });

        services.commands(commands -> {
            commands.register(StartCommand.of(host).build());
            commands.register(SetupCommand.locations(messages, List.of(SpawnPoint.values()).stream()
                    .filter(point -> api.waitingLobby().isEmpty() || point != SpawnPoint.LOBBY).toList(), SpawnPoint::key, locations::set));
            commands.register(ChestCommand.create(host::current, messages));
            commands.register(RushCommand.create(host::current, messages));
        });
    }

    @Override
    public void onDisable() {
        if (host != null) {
            host.close();
        }
        Bukkit.getOnlinePlayers().forEach(player -> player.kick(Component.empty()));
        Bukkit.getScheduler().cancelTasks(this);
    }
}
