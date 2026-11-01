package me.blockcraft.endergames;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.CompassTracker;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.GameServices;
import me.blockcraft.api.game.RoundConnections;
import me.blockcraft.api.game.StartCommand;
import me.blockcraft.api.world.WaitingLobby;
import me.blockcraft.api.world.WorldProtection;
import me.blockcraft.api.world.Worlds;
import me.blockcraft.endergames.config.EnderGamesConfig;
import me.blockcraft.endergames.game.EnderGamesGame;
import me.blockcraft.endergames.listener.RoundListener;
import me.blockcraft.endergames.world.LobbyPlatform;
import me.blockcraft.endergames.world.RoundWorld;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public final class EnderGamesPlugin extends JavaPlugin {

    private EnderGamesGame game;
    private RoundWorld world;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var gameConfig = GameConfig.from(getConfig());
        var config = EnderGamesConfig.from(getConfig());
        var api = BlockcraftApi.get();
        var services = GameServices.load(this, gameConfig, "bc_endergames_stats");
        var messages = services.messages();

        var lobbySpawn = api.waitingLobby().map(WaitingLobby::spawn)
                .orElseGet(() -> LobbyPlatform.build(Worlds.createArena(new NamespacedKey("endergames", "lobby"))));
        lobbySpawn.getWorld().setSpawnLocation(lobbySpawn);
        world = RoundWorld.create(this, config.world());

        game = new EnderGamesGame(this, gameConfig, config, messages, world, lobbySpawn, services.stats(), services.menus());
        api.presence().maxPlayers(config.maxPlayers());

        services.listen(
                game.hotbar(),
                game.abilities(),
                new RoundConnections(game),
                WorldProtection.during(() -> game.phase() != GamePhase.INGAME, api.buildMode())
                        .rescue(() -> game.isLobby() ? Optional.of(lobbySpawn) : Optional.empty()),
                new RoundListener(game, new CompassTracker(messages, config.tracker())));

        services.commands(commands -> commands.register(StartCommand.of(game.countdown()).build()));

        Bukkit.getOnlinePlayers().forEach(game::join);
        world.prepare(config.startIsland().chests(), game::worldReady);
    }

    @Override
    public void onDisable() {
        if (game != null) {
            game.stop();
        }
        Bukkit.getOnlinePlayers().forEach(player -> player.kick(Component.empty()));
        if (world != null) {
            world.discard();
        }
        Bukkit.getScheduler().cancelTasks(this);
    }
}
