package me.blockcraft.api.coins;

import me.blockcraft.api.player.RealNames;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.coins.CoinsRepository;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

public final class CoinsJoinListener implements Listener {

    private final Plugin plugin;
    private final CoinsRepository coins;
    private final RealNames realNames;
    private final int startingCoins;

    public CoinsJoinListener(Plugin plugin, CoinsRepository coins, RealNames realNames, int startingCoins) {
        this.plugin = plugin;
        this.coins = coins;
        this.realNames = realNames;
        this.startingCoins = startingCoins;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        var name = realNames.of(player);
        Tasks.async(plugin, () -> coins.register(player.getUniqueId(), name, startingCoins));
    }
}
