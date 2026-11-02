package me.blockcraft.mlgrush;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.ArenaProtection;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GameServices;
import me.blockcraft.api.game.StartCommand;
import me.blockcraft.api.game.rush.RushArena;
import me.blockcraft.api.game.rush.MapPool;
import me.blockcraft.api.game.rush.RushConfig;
import me.blockcraft.api.world.WorldProtection;
import me.blockcraft.mlgrush.game.StandaloneRushGame;
import me.blockcraft.mlgrush.listener.ConnectionListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.util.concurrent.ThreadLocalRandom;

public final class MlgRushPlugin extends JavaPlugin {

    private StandaloneRushGame game;

    @Override
    public void onEnable() {
        getConfig().options().copyDefaults(true);
        var config = RushConfig.from(getConfig());
        var gameConfig = GameConfig.from(getConfig());
        var mapsFile = getDataPath().resolve("maps.yml");
        if (Files.notExists(mapsFile)) {
            saveResource("maps.yml", false);
        }
        var maps = MapPool.load(mapsFile, config.arena());
        var map = maps.choose(ThreadLocalRandom.current());
        getSLF4JLogger().info("MLG Rush map: {} ({})", map.key(), map.name());
        var api = BlockcraftApi.get();
        var services = GameServices.load(this, gameConfig, "mlgrush_stats");
        var arena = new RushArena(map);
        game = new StandaloneRushGame(this, gameConfig, config, services.messages(), arena, maps, services.stats(), services.menus());
        services.listen(new ConnectionListener(game),
                new ArenaProtection(() -> false),
                WorldProtection.during(() -> !game.isIngame(), api.buildMode()).rescue(game::lobbySpawn),
                game.hotbar());
        services.commands(commands -> commands.register(StartCommand.of(game.countdown()).build()));
        Bukkit.getOnlinePlayers().forEach(game::join);
        game.refreshPresence();
    }

    @Override
    public void onDisable() {
        if (game != null) {
            game.shutdown();
        }
        Bukkit.getScheduler().cancelTasks(this);
    }
}
