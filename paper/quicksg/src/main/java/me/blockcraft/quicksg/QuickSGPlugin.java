package me.blockcraft.quicksg;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.game.ArenaProtection;
import me.blockcraft.api.game.CompassTracker;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GameServices;
import me.blockcraft.api.game.LootChests;
import me.blockcraft.api.game.RoundConnections;
import me.blockcraft.api.game.SetupCommand;
import me.blockcraft.api.game.StartCommand;
import me.blockcraft.api.world.WorldProtection;
import me.blockcraft.quicksg.command.DebugCommands;
import me.blockcraft.quicksg.config.LocationKey;
import me.blockcraft.quicksg.config.QuickSGConfig;
import me.blockcraft.quicksg.game.Game;
import me.blockcraft.quicksg.game.GameItems;
import me.blockcraft.quicksg.listener.DeathListener;
import me.blockcraft.quicksg.listener.ItemListener;
import me.blockcraft.quicksg.listener.ProtectionListener;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

public final class QuickSGPlugin extends JavaPlugin {

    private Game game;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var gameConfig = GameConfig.from(getConfig());
        var config = QuickSGConfig.from(getConfig());
        var api = BlockcraftApi.get();
        var services = GameServices.load(this, gameConfig, "qsg_stats");
        var messages = services.messages();
        var stats = services.stats();
        var locations = Locations.load(api.map().file(this, "locations.yml"), LocationKey::key);

        var items = new GameItems(messages);
        game = new Game(this, gameConfig, config, messages, locations, stats, services.menus(), api.map(), items);
        api.presence().maxPlayers(config.maxPlayers());
        game.applyLobbyWorldBorder();

        var lootChests = new LootChests(config.loot(), entry -> items.loot(entry.material(), entry.amount()));

        services.listen(
                new RoundConnections(game),
                new DeathListener(game),
                new ProtectionListener(game, api.buildMode()),
                WorldProtection.during(game::isLobby, api.buildMode()).rescue(game::lobbySpawn),
                new ArenaProtection(game::frozen),
                game.lobbyHotbar(),
                new ItemListener(game, messages, lootChests, new CompassTracker(messages, config.tracker())));

        services.commands(commands -> {
            commands.register(StartCommand.of(game.lobbyCountdown())
                    .then(DebugCommands.timer(game))
                    .then(DebugCommands.wins(this, stats))
                    .build());
            commands.register(SetupCommand.locations(messages, List.of(LocationKey.values()).stream()
                            .filter(key -> api.waitingLobby().isEmpty() || key != LocationKey.LOBBY && key != LocationKey.LOBBY_CENTER).toList(),
                    key -> key.key().toLowerCase(Locale.ROOT), locations::set));
        });
    }

    @Override
    public void onDisable() {
        Bukkit.getOnlinePlayers().forEach(Player::kick);
        if (game != null) {
            game.shutdown();
        }
        Bukkit.getScheduler().cancelTasks(this);
    }
}
