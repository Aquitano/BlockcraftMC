package me.blockcraft.quicksg.menu;

import me.blockcraft.api.game.Stats;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.game.TopList;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.quicksg.game.GameItems;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class StatsMenus {

    private final Plugin plugin;
    private final Messages messages;
    private final StatsRepository stats;
    private final GameItems items;
    private final TopList topList;
    private final String globalHead;

    public StatsMenus(Plugin plugin, Messages messages, StatsRepository stats, GameItems items, TopList topList, String globalHead) {
        this.plugin = plugin;
        this.messages = messages;
        this.stats = stats;
        this.items = items;
        this.topList = topList;
        this.globalHead = globalHead;
    }

    Messages messages() {
        return messages;
    }

    GameItems items() {
        return items;
    }

    TopList topList() {
        return topList;
    }

    String globalHead() {
        return globalHead;
    }

    public void openPlayers(Player viewer) {
        var online = Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList();
        Tasks.async(plugin, () -> {
            Map<UUID, Stats> loaded = new LinkedHashMap<>();
            online.forEach(uuid -> loaded.put(uuid, stats.get(uuid)));
            return loaded;
        }, loaded -> {
            if (viewer.isOnline()) {
                new StatsMenu(this, loaded).open(viewer);
            }
        });
    }

    void openGlobal(Player viewer) {
        new GlobalStatsMenu(this).open(viewer);
    }
}
