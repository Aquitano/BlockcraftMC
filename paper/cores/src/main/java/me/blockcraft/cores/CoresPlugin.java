package me.blockcraft.cores;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.ArenaProtection;
import me.blockcraft.api.game.EnchantingLapis;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.GameServices;
import me.blockcraft.api.game.LootChests;
import me.blockcraft.api.game.RoundConnections;
import me.blockcraft.api.game.StartCommand;
import me.blockcraft.api.game.TeamChat;
import me.blockcraft.api.world.WaitingLobby;
import me.blockcraft.api.world.WorldProtection;
import me.blockcraft.api.world.Worlds;
import me.blockcraft.cores.arena.SkyMap;
import me.blockcraft.cores.config.CoresConfig;
import me.blockcraft.cores.game.CoresGame;
import me.blockcraft.cores.listener.ArenaListener;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public final class CoresPlugin extends JavaPlugin {

    private CoresGame game;

    @Override
    public void onEnable() {
        getConfig().options().copyDefaults(true);
        var gameConfig = GameConfig.from(getConfig());
        var config = CoresConfig.from(getConfig());
        var api = BlockcraftApi.get();
        var services = GameServices.load(this, gameConfig, "bc_cores_stats");
        var messages = services.messages();
        var world = Worlds.createArena(new NamespacedKey("cores", "himmelsfeste"));
        // Hunger and natural regeneration are part of the fight; no mob spawns anyway.
        world.setDifficulty(Difficulty.NORMAL);
        var map = SkyMap.build(world, new LootChests(config.chests()), api.waitingLobby().map(WaitingLobby::spawn));
        map.lobbySpawn().getWorld().setSpawnLocation(map.lobbySpawn());
        game = new CoresGame(this, gameConfig, config, messages, map, services.stats(), services.menus());
        api.presence().maxPlayers(config.capacity());

        services.listen(
                game.hotbar(),
                new RoundConnections(game),
                WorldProtection.during(() -> game.phase() != GamePhase.INGAME, api.buildMode())
                        .rescue(() -> Optional.of(map.lobbySpawn())),
                new ArenaProtection(() -> false),
                game.spawnProtection(),
                game.voidLine(),
                new EnchantingLapis(),
                new ArenaListener(game, messages),
                new TeamChat(() -> game.phase() == GamePhase.INGAME, game::sameTeam, messages, false));

        services.commands(commands -> commands.register(StartCommand.of(game.countdown()).build()));

        Bukkit.getOnlinePlayers().forEach(game::join);
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
