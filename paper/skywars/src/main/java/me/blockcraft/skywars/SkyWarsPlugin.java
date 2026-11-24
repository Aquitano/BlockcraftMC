package me.blockcraft.skywars;

import me.blockcraft.api.config.BundledDefaults;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.ArenaProtection;
import me.blockcraft.api.game.EnchantingLapis;
import me.blockcraft.api.game.ForceMapCommand;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.GameServices;
import me.blockcraft.api.game.RoundConnections;
import me.blockcraft.api.game.RoundHost;
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
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public final class SkyWarsPlugin extends JavaPlugin {

    private RoundHost<SkyWarsGame> host;

    @Override
    public void onEnable() {
        BundledDefaults.apply(this);
        var gameConfig = GameConfig.from(getConfig());
        var config = SkyWarsConfig.from(getConfig());
        var api = BlockcraftApi.get();
        var services = GameServices.load(this, gameConfig, "bc_skywars_stats");
        var messages = services.messages();

        // The lobby is built anew at every start and the islands' world for every round, so nothing of the last
        // round or the last map is left over.
        var lobbyKey = new NamespacedKey("skywars", "lobby");
        var arenaKey = new NamespacedKey("skywars", "inseln");
        var lobbySpawn = api.waitingLobby().map(WaitingLobby::spawn).orElseGet(() -> {
            Worlds.discard(lobbyKey);
            return ArenaBuilder.lobby(Worlds.createArena(lobbyKey));
        });
        lobbySpawn.getWorld().setSpawnLocation(lobbySpawn);
        api.presence().maxPlayers(config.maxPlayers());
        services.listen(new ArenaProtection(() -> false), new EnchantingLapis());

        host = RoundHost.start(this, gameConfig, round -> {
            var arena = round.own(emptyArena(arenaKey), world -> Worlds.discard(world.getKey()));
            ArenaBuilder.preload(arena, round);
            var game = new SkyWarsGame(round, gameConfig, config, messages, arena, lobbySpawn, services.stats(), services.menus());
            round.listen(
                    game.hotbar(),
                    new RoundConnections(game),
                    WorldProtection.during(() -> game.phase() != GamePhase.INGAME, api.buildMode())
                            .rescue(() -> game.isLobby() ? Optional.of(lobbySpawn) : Optional.empty()),
                    game.voidLine(),
                    new RoundListener(game, messages));
            return game;
        });

        services.commands(commands -> {
            commands.register(StartCommand.of(host).build());
            commands.register(ForceMapCommand.create(messages, () -> host.current().vote().keys(),
                    () -> host.current().isLobby(), key -> host.current().forceMap(key)));
        });

        Bukkit.getOnlinePlayers().forEach(player -> host.current().join(player));
    }

    private static World emptyArena(NamespacedKey key) {
        Worlds.discard(key);
        var arena = Worlds.createArena(key);
        // Hunger and natural regeneration are part of the fight; no mob spawns anyway.
        arena.setDifficulty(Difficulty.NORMAL);
        return arena;
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
