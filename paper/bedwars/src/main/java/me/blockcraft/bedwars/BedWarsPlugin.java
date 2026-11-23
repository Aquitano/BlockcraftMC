package me.blockcraft.bedwars;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.config.BundledDefaults;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.ArenaProtection;
import me.blockcraft.api.game.ForceMapCommand;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.GameServices;
import me.blockcraft.api.game.RoundConnections;
import me.blockcraft.api.game.RoundHost;
import me.blockcraft.api.game.StartCommand;
import me.blockcraft.api.game.TeamChat;
import me.blockcraft.api.world.TemplateDimensions;
import me.blockcraft.api.world.WorldProtection;
import me.blockcraft.bedwars.command.BedWarsSetup;
import me.blockcraft.bedwars.config.BedWarsConfig;
import me.blockcraft.bedwars.game.ArenaCopies;
import me.blockcraft.bedwars.game.Game;
import me.blockcraft.bedwars.listener.ArenaListener;
import me.blockcraft.bedwars.listener.ItemListener;
import me.blockcraft.bedwars.listener.WorldRules;
import me.blockcraft.bedwars.lobby.LobbyLayout;
import me.blockcraft.bedwars.map.MapPool;
import me.blockcraft.bedwars.shop.ShopCatalog;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class BedWarsPlugin extends JavaPlugin {

    private RoundHost<Game> host;

    @Override
    public void onEnable() {
        BundledDefaults.apply(this);
        var gameConfig = GameConfig.from(getConfig());
        var config = BedWarsConfig.from(getConfig());
        var api = BlockcraftApi.get();
        var services = GameServices.load(this, gameConfig, "bw_stats");
        var messages = services.messages();
        var catalog = ShopCatalog.parse(getConfig().getMapList("shop"));

        WorldRules.settle(Bukkit.getWorlds().getFirst());
        var layout = LobbyLayout.load(api.map().file(this, "lobby.yml"), api.waitingLobby());
        var pool = new MapPool(api.map().file(this, "maps"), getSLF4JLogger());
        var arenas = new TemplateDimensions("bedwars", WorldRules::settle);
        var copies = new ArenaCopies(this, pool, arenas);
        api.presence().maxPlayers(config.capacity());
        services.listen(new WorldRules(), new ArenaProtection(() -> false));

        host = RoundHost.start(this, gameConfig, round -> {
            var game = new Game(round, gameConfig, config, messages, pool, copies, layout, catalog, services.stats(), services.menus());
            game.listeners().forEach(round::listen);
            round.listen(
                    new RoundConnections(game),
                    // After the round everyone waits in the lobby, which keeps its rules then too.
                    WorldProtection.during(() -> game.phase() != GamePhase.INGAME, api.buildMode()).rescue(game::lobbySpawn),
                    new ArenaListener(round, game, messages, config),
                    new ItemListener(round, game),
                    new TeamChat(() -> game.phase() == GamePhase.INGAME, game::sameTeam, messages, config.playersPerTeam() == 1));
            game.open();
            return game;
        });

        services.commands(commands -> {
            commands.register(StartCommand.of(host)
                    .then(Commands.literal("solo").executes(context -> {
                        host.current().lobbyCountdown().forceStart(context.getSource().getPlayerOrThrow(), 1);
                        return Command.SINGLE_SUCCESS;
                    }))
                    .build());
            commands.register(ForceMapCommand.create(messages, () -> host.current().playableMaps(),
                    () -> host.current().mapForceable(), key -> host.current().forceMap(key)));
            commands.register(BedWarsSetup.create(pool, arenas, layout, config.teams(), messages, key -> host.current().playsOn(key)));
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
