package me.blockcraft.duell;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.command.CommandRegistry;
import me.blockcraft.api.game.ArenaProtection;
import me.blockcraft.api.game.LootChests;
import me.blockcraft.api.game.StatsMenus;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.game.TopList;
import me.blockcraft.common.message.Messages;
import me.blockcraft.duell.command.SetSpawnCommand;
import me.blockcraft.duell.config.DuellConfig;
import me.blockcraft.duell.config.Spawns;
import me.blockcraft.duell.game.Duel;
import me.blockcraft.duell.game.DuelRatings;
import me.blockcraft.duell.game.GameItems;
import me.blockcraft.duell.game.GameScoreboard;
import me.blockcraft.duell.listener.ConnectionListener;
import me.blockcraft.duell.listener.LobbyItemListener;
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
        var config = DuellConfig.from(getConfig());
        var api = BlockcraftApi.get();
        var spawns = Spawns.load(api.map().file(this, "locations.yml"));
        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"), api.tags());
        var stats = new StatsRepository(api.database(), "duell_stats");
        stats.createTable();

        var items = new GameItems(messages);
        duel = new Duel(this, config, messages, spawns, stats, new GameScoreboard(messages), items,
                new DuelRatings(this, messages, api.elo()));
        api.presence().maxPlayers(Duel.MAX_PARTICIPANTS);
        var topList = new TopList(this, stats, messages);
        var statsMenus = StatsMenus.ranked(messages, stats, topList, config.globalStatsHead());
        var lootChests = new LootChests(config.loot());

        List.of(
                new ConnectionListener(duel),
                new LobbyItemListener(this, duel, messages, statsMenus),
                new ProtectionListener(duel, api.buildMode()),
                new ArenaProtection(duel::frozen),
                new VsListener(this, duel, config, messages, items),
                new SgListener(duel, lootChests),
                new SuperJumpListener(duel)
        ).forEach(this::register);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                new CommandRegistry(event.registrar(), messages).register(SetSpawnCommand.create(spawns, config, messages)));
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
