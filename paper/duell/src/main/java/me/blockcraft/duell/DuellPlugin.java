package me.blockcraft.duell;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.ArenaProtection;
import me.blockcraft.api.game.ForceMapCommand;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GameServices;
import me.blockcraft.api.game.LootChests;
import me.blockcraft.api.game.SetupCommand;
import me.blockcraft.api.game.StartCommand;
import me.blockcraft.common.duel.DuelPairRepository;
import me.blockcraft.duell.config.DuellConfig;
import me.blockcraft.duell.config.Spawns;
import me.blockcraft.duell.arena.SumoArena;
import me.blockcraft.duell.game.Duel;
import me.blockcraft.duell.game.DuelGames;
import me.blockcraft.api.game.rush.MapPool;
import me.blockcraft.api.game.rush.RushArena;
import me.blockcraft.api.game.rush.RushConfig;
import me.blockcraft.api.world.WorldProtection;
import org.bukkit.NamespacedKey;
import me.blockcraft.duell.game.DuelRatings;
import me.blockcraft.duell.game.GameItems;
import me.blockcraft.duell.listener.ConnectionListener;
import me.blockcraft.duell.listener.ProtectionListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;


public final class DuellPlugin extends JavaPlugin {

    private Duel duel;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var gameConfig = GameConfig.from(getConfig());
        var config = DuellConfig.from(getConfig());
        var api = BlockcraftApi.get();
        var spawns = Spawns.load(api.map().file(this, "locations.yml"));
        var services = GameServices.ranked(this, gameConfig, "duell_stats");
        var messages = services.messages();
        var pairs = new DuelPairRepository(api.database());
        pairs.createTable();

        var items = new GameItems(messages);
        var rushConfig = RushConfig.from(getConfig().getConfigurationSection("mlgrush") == null
                ? new org.bukkit.configuration.file.YamlConfiguration() : getConfig().getConfigurationSection("mlgrush"));
        var rushMaps = MapPool.load(getDataPath().resolve("rush/maps.yml"), rushConfig.arena());
        var rushArena = new RushArena(rushMaps.maps().getFirst(), new NamespacedKey(this, "rush"));
        var lootChests = new LootChests(config.loot());
        var games = new DuelGames(this, config, messages, spawns, new SumoArena(config.sumo()),
                rushArena, rushConfig, rushMaps, items, lootChests);
        duel = new Duel(this, gameConfig, config, messages, spawns, games, services.stats(), services.menus(),
                new DuelRatings(this, messages, api.elo()), pairs);
        api.presence().maxPlayers(Duel.MAX_PARTICIPANTS);

        services.listen(
                new ConnectionListener(duel),
                new ProtectionListener(duel, api.buildMode()),
                WorldProtection.during(() -> !duel.isIngame(), api.buildMode()).rescue(duel::lobbySpawn),
                new ArenaProtection(duel::frozen),
                duel.lobbyHotbar());

        services.commands(commands -> {
            commands.register(StartCommand.of(duel.lobbyCountdown()).build());
            commands.register(SetupCommand.locations(messages, Spawns.places(config, Duel.MAX_PARTICIPANTS).stream()
                            .filter(place -> api.waitingLobby().isEmpty() || !place.name().equals("lobby")).toList(),
                    Spawns.Place::name, spawns::set));
            commands.register(ForceMapCommand.create(messages, duel::forcibleModes, duel::isLobby, duel::forceMode));
        });
    }

    @Override
    public void onDisable() {
        if (duel != null) {
            duel.shutdown();
        }
        Bukkit.getScheduler().cancelTasks(this);
    }
}
