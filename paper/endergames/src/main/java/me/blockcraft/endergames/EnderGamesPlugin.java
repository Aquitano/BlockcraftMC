package me.blockcraft.endergames;

import me.blockcraft.api.config.BundledDefaults;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.CompassTracker;
import me.blockcraft.api.game.EnchantingLapis;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.GameServices;
import me.blockcraft.api.game.RoundConnections;
import me.blockcraft.api.game.RoundHost;
import me.blockcraft.api.game.StartCommand;
import me.blockcraft.api.world.WaitingLobby;
import me.blockcraft.api.world.WorldProtection;
import me.blockcraft.api.world.Worlds;
import me.blockcraft.endergames.config.EnderGamesConfig;
import me.blockcraft.endergames.game.EnderGamesGame;
import me.blockcraft.endergames.listener.RoundListener;
import me.blockcraft.endergames.world.LobbyPlatform;
import me.blockcraft.endergames.world.RoundWorlds;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public final class EnderGamesPlugin extends JavaPlugin {

    private RoundHost<EnderGamesGame> host;
    private RoundWorlds worlds;

    @Override
    public void onEnable() {
        BundledDefaults.apply(this);
        var gameConfig = GameConfig.from(getConfig());
        var config = EnderGamesConfig.from(getConfig());
        var api = BlockcraftApi.get();
        var services = GameServices.load(this, gameConfig, "bc_endergames_stats");
        var messages = services.messages();

        var lobbySpawn = api.waitingLobby().map(WaitingLobby::spawn)
                .orElseGet(() -> LobbyPlatform.build(Worlds.createArena(new NamespacedKey("endergames", "lobby"))));
        lobbySpawn.getWorld().setSpawnLocation(lobbySpawn);
        worlds = new RoundWorlds(this, config.world(), config.startIsland().chests());
        api.presence().maxPlayers(config.maxPlayers());
        services.listen(new EnchantingLapis());

        host = RoundHost.start(this, gameConfig, round -> {
            var world = worlds.take(round);
            var game = new EnderGamesGame(round, gameConfig, config, messages, world, worlds::generateNext, lobbySpawn,
                    services.stats(), services.menus());
            round.listen(
                    game.hotbar(),
                    game.abilities(),
                    new RoundConnections(game),
                    WorldProtection.during(() -> game.phase() != GamePhase.INGAME, api.buildMode())
                            .rescue(() -> game.isLobby() ? Optional.of(lobbySpawn) : Optional.empty()),
                    new RoundListener(game, new CompassTracker(messages, config.tracker())));
            return game;
        });

        services.commands(commands -> commands.register(StartCommand.of(host).build()));

        Bukkit.getOnlinePlayers().forEach(player -> host.current().join(player));
    }

    @Override
    public void onDisable() {
        if (host != null) {
            host.close();
        }
        Bukkit.getOnlinePlayers().forEach(player -> player.kick(Component.empty()));
        if (worlds != null) {
            worlds.discardNext();
        }
        Bukkit.getScheduler().cancelTasks(this);
    }
}
