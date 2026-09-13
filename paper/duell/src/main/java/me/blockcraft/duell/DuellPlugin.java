package me.blockcraft.duell;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.world.Worlds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.duell.command.SetSpawnCommand;
import me.blockcraft.duell.config.DuellConfig;
import me.blockcraft.duell.config.Spawns;
import me.blockcraft.duell.game.Duel;
import me.blockcraft.duell.game.GameItems;
import me.blockcraft.duell.game.GameScoreboard;
import me.blockcraft.duell.game.LootChests;
import me.blockcraft.duell.game.TopList;
import me.blockcraft.duell.listener.ConnectionListener;
import me.blockcraft.duell.listener.LobbyItemListener;
import me.blockcraft.duell.listener.ProtectionListener;
import me.blockcraft.duell.listener.SgListener;
import me.blockcraft.duell.listener.SuperJumpListener;
import me.blockcraft.duell.listener.VsListener;
import me.blockcraft.duell.menu.StatsMenus;
import me.blockcraft.duell.storage.StatsRepository;
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
        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"));
        var spawns = Spawns.load(getDataPath());

        var api = BlockcraftApi.get();
        var stats = new StatsRepository(api.database());
        stats.createTable();

        var items = new GameItems(messages);
        duel = new Duel(this, config, messages, spawns, stats, api.coins(), new GameScoreboard(messages), items);
        api.presence().maxPlayers(Duel.MAX_PARTICIPANTS);
        var topList = new TopList(this, stats, messages);
        var statsMenus = new StatsMenus(this, messages, duel, stats, items, topList, config.globalStatsHead());
        var lootChests = new LootChests(config.loot());

        List.of(
                new ConnectionListener(duel),
                new LobbyItemListener(this, duel, messages, statsMenus),
                new ProtectionListener(duel),
                new VsListener(this, duel, messages, items),
                new SgListener(duel, lootChests),
                new SuperJumpListener(duel)
        ).forEach(this::register);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(SetSpawnCommand.create(spawns, config, messages), "Setzt die Duell Spawns", List.of()));

        clearArenaEntities(spawns);
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

    private void clearArenaEntities(Spawns spawns) {
        spawns.worlds().forEach(world -> Worlds.removeLeftovers(world, entity -> false));
    }
}
