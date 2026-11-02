package me.blockcraft.skywars;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.ArenaProtection;
import me.blockcraft.api.game.EnchantingLapis;
import me.blockcraft.api.game.ForceMapCommand;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.GameServices;
import me.blockcraft.api.game.RoundConnections;
import me.blockcraft.api.game.StartCommand;
import me.blockcraft.api.world.WaitingLobby;
import me.blockcraft.api.world.WorldProtection;
import me.blockcraft.api.world.Worlds;
import me.blockcraft.skywars.config.SkyWarsConfig;
import me.blockcraft.skywars.game.SkyWarsGame;
import me.blockcraft.skywars.listener.RoundListener;
import me.blockcraft.skywars.map.ArenaBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public final class SkyWarsPlugin extends JavaPlugin {

    private SkyWarsGame game;

    @Override
    public void onEnable() {
        getConfig().options().copyDefaults(true);
        var gameConfig = GameConfig.from(getConfig());
        var config = SkyWarsConfig.from(getConfig());
        var api = BlockcraftApi.get();
        var services = GameServices.load(this, gameConfig, "bc_skywars_stats");
        var messages = services.messages();

        // One round per start: both worlds begin empty, so nothing of the last round or the last map is left over.
        var lobbyKey = new NamespacedKey("skywars", "lobby");
        var arenaKey = new NamespacedKey("skywars", "inseln");
        Worlds.discard(arenaKey);
        var lobbySpawn = api.waitingLobby().map(WaitingLobby::spawn).orElseGet(() -> {
            Worlds.discard(lobbyKey);
            return ArenaBuilder.lobby(Worlds.createArena(lobbyKey));
        });
        lobbySpawn.getWorld().setSpawnLocation(lobbySpawn);
        var arena = Worlds.createArena(arenaKey);
        // Hunger and natural regeneration are part of the fight; no mob spawns anyway.
        arena.setDifficulty(Difficulty.NORMAL);

        game = new SkyWarsGame(this, gameConfig, config, messages, arena, lobbySpawn, services.stats(), services.menus());
        api.presence().maxPlayers(config.maxPlayers());

        services.listen(
                game.hotbar(),
                new RoundConnections(game),
                WorldProtection.during(() -> game.phase() != GamePhase.INGAME, api.buildMode())
                        .rescue(() -> game.isLobby() ? Optional.of(lobbySpawn) : Optional.empty()),
                new ArenaProtection(() -> false),
                game.voidLine(),
                new EnchantingLapis(),
                new RoundListener(game, messages));

        services.commands(commands -> {
            commands.register(StartCommand.of(game.countdown()).build());
            commands.register(ForceMapCommand.create(messages, game.vote()::keys, game::isLobby, game::forceMap));
        });

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
