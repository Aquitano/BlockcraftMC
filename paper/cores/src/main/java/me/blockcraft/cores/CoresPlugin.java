package me.blockcraft.cores;

import me.blockcraft.api.config.BundledDefaults;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.ArenaProtection;
import me.blockcraft.api.game.EnchantingLapis;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.GameServices;
import me.blockcraft.api.game.RoundConnections;
import me.blockcraft.api.game.RoundHost;
import me.blockcraft.api.game.StartCommand;
import me.blockcraft.api.game.TeamChat;
import me.blockcraft.api.world.WaitingLobby;
import me.blockcraft.api.world.WorldProtection;
import me.blockcraft.cores.arena.SkyArena;
import me.blockcraft.cores.config.CoresConfig;
import me.blockcraft.cores.game.CoresGame;
import me.blockcraft.cores.listener.ArenaListener;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public final class CoresPlugin extends JavaPlugin {

    private RoundHost<CoresGame> host;

    @Override
    public void onEnable() {
        BundledDefaults.apply(this);
        var gameConfig = GameConfig.from(getConfig());
        var config = CoresConfig.from(getConfig());
        var api = BlockcraftApi.get();
        var services = GameServices.load(this, gameConfig, "bc_cores_stats");
        var messages = services.messages();
        var arena = SkyArena.build(this, config.chests(), api.waitingLobby().map(WaitingLobby::spawn));
        api.presence().maxPlayers(config.capacity());
        services.listen(new ArenaProtection(() -> false), new EnchantingLapis());

        host = RoundHost.start(this, gameConfig, round -> {
            var map = arena.open(round);
            var game = new CoresGame(round, gameConfig, config, messages, map, services.stats(), services.menus());
            round.listen(
                    game.hotbar(),
                    new RoundConnections(game),
                    WorldProtection.during(() -> game.phase() != GamePhase.INGAME, api.buildMode())
                            .rescue(() -> Optional.of(map.lobbySpawn())),
                    game.spawnProtection(),
                    game.voidLine(),
                    new ArenaListener(game, messages),
                    new TeamChat(() -> game.phase() == GamePhase.INGAME, game::sameTeam, messages, false));
            return game;
        });

        services.commands(commands -> commands.register(StartCommand.of(host).build()));

        Bukkit.getOnlinePlayers().forEach(host.current()::join);
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
