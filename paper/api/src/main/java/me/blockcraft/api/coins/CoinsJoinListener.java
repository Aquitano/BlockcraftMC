package me.blockcraft.api.coins;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.coins.CoinsRepository;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

public final class CoinsJoinListener implements Listener {

    private final Plugin plugin;
    private final CoinsRepository coins;
    private final int startingCoins;

    public CoinsJoinListener(Plugin plugin, CoinsRepository coins, int startingCoins) {
        this.plugin = plugin;
        this.coins = coins;
        this.startingCoins = startingCoins;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        var name = BlockcraftApi.get().realName(player);
        Tasks.async(plugin, () -> coins.register(player.getUniqueId(), name, startingCoins));
    }
}
