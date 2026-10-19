package me.blockcraft.duell;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.command.CommandRegistry;
import me.blockcraft.api.game.ArenaProtection;
import me.blockcraft.api.game.ForceMapCommand;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.LobbyProtection;
import me.blockcraft.api.game.LootChests;
import me.blockcraft.api.game.ServerRestart;
import me.blockcraft.api.game.SetupCommand;
import me.blockcraft.api.game.StartCommand;
import me.blockcraft.api.game.StatsCommand;
import me.blockcraft.api.game.StatsMenus;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.game.TopCommand;
import me.blockcraft.api.game.TopList;
import me.blockcraft.common.duel.DuelPairRepository;
import me.blockcraft.common.message.Messages;
import me.blockcraft.duell.config.DuellConfig;
import me.blockcraft.duell.config.Spawns;
import me.blockcraft.duell.game.Duel;
import me.blockcraft.duell.game.DuelRatings;
import me.blockcraft.duell.game.GameItems;
import me.blockcraft.duell.listener.ConnectionListener;
import me.blockcraft.duell.listener.ProtectionListener;
import me.blockcraft.duell.listener.SgListener;
import me.blockcraft.duell.listener.SuperJumpListener;
import me.blockcraft.duell.listener.VsListener;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class DuellPlugin extends JavaPlugin {

    private Duel duel;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var gameConfig = GameConfig.from(getConfig());
        var config = DuellConfig.from(getConfig());
        var api = BlockcraftApi.get();
        var spawns = Spawns.load(api.map().file(this, "locations.yml"));
        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"), api.tags());
        var stats = new StatsRepository(api.database(), "duell_stats");
        stats.createTable();
        var pairs = new DuelPairRepository(api.database());
        pairs.createTable();

        var items = new GameItems(messages);
        var topList = new TopList(this, stats, messages);
        var statsMenus = StatsMenus.ranked(messages, stats, topList, gameConfig.globalStatsHead());
        duel = new Duel(this, gameConfig, config, messages, spawns, stats, statsMenus, items,
                new DuelRatings(this, messages, api.elo()), pairs);
        api.presence().maxPlayers(Duel.MAX_PARTICIPANTS);
        var lootChests = new LootChests(config.loot());

        List.of(
                new ConnectionListener(duel),
                new ProtectionListener(duel, api.buildMode()),
                new LobbyProtection(duel::isLobby, api.buildMode(), duel::lobbySpawn),
                new ArenaProtection(duel::frozen),
                duel.lobbyHotbar(),
                new VsListener(this, duel, config, items),
                new SgListener(duel, lootChests),
                new SuperJumpListener(duel)
        ).forEach(this::register);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = new CommandRegistry(event.registrar(), messages);
            commands.register(StartCommand.of(duel.lobbyCountdown()).build());
            commands.register(SetupCommand.locations(messages, Spawns.places(config, Duel.MAX_PARTICIPANTS),
                    Spawns.Place::name, spawns::set));
            commands.register(ForceMapCommand.create(messages, duel::forcibleModes, duel::isLobby, duel::forceMode));
            commands.register(StatsCommand.create(this, stats, messages));
            commands.register(TopCommand.create(topList));
        });

        ServerRestart.whenIdle(this, gameConfig);
    }

    @Override
    public void onDisable() {
        if (duel != null) {
            duel.shutdown();
        }
        Bukkit.getScheduler().cancelTasks(this);
    }

    private void register(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }
}
