package me.blockcraft.bedwars;

import me.blockcraft.api.config.BundledDefaults;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.ArenaProtection;
import me.blockcraft.api.game.ForceMapCommand;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.GameServices;
import me.blockcraft.api.game.RoundConnections;
import me.blockcraft.api.game.StartCommand;
import me.blockcraft.api.game.TeamChat;
import me.blockcraft.api.world.TemplateDimensions;
import me.blockcraft.api.world.WorldProtection;
import me.blockcraft.bedwars.command.BedWarsSetup;
import me.blockcraft.bedwars.config.BedWarsConfig;
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

    private Game game;

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
        game = new Game(this, gameConfig, config, messages, pool, arenas, layout, catalog, services.stats(), services.menus());
        api.presence().maxPlayers(config.capacity());

        game.listeners().forEach(services::listen);
        services.listen(
                new RoundConnections(game),
                // After the round everyone waits for the restart in the lobby, which keeps its rules then too.
                WorldProtection.during(() -> game.phase() != GamePhase.INGAME, api.buildMode()).rescue(game::lobbySpawn),
                new ArenaListener(this, game, messages, config),
                new WorldRules(),
                new ArenaProtection(() -> false),
                new ItemListener(this, game),
                new TeamChat(() -> game.phase() == GamePhase.INGAME, game::sameTeam, messages, config.playersPerTeam() == 1));

        services.commands(commands -> {
            commands.register(StartCommand.of(game.lobbyCountdown()).build());
            commands.register(ForceMapCommand.create(messages, game::playableMaps, game::mapForceable, game::forceMap));
            commands.register(BedWarsSetup.create(pool, arenas, layout, config.teams(), messages));
        });

        game.open();
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
