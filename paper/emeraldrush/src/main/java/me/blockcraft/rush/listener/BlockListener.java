package me.blockcraft.rush.listener;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.api.world.BuildMode;
import me.blockcraft.rush.config.RushConfig;
import me.blockcraft.rush.game.Game;
import me.blockcraft.rush.game.GamePhase;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.Plugin;

public final class BlockListener implements Listener {

    private final Plugin plugin;
    private final Game game;
    private final RushConfig config;
    private final BuildMode buildMode;

    public BlockListener(Plugin plugin, Game game, RushConfig config, BuildMode buildMode) {
        this.plugin = plugin;
        this.game = game;
        this.config = config;
        this.buildMode = buildMode;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        event.setExpToDrop(0);
        event.setDropItems(false);
        var block = event.getBlock();
        var player = event.getPlayer();
        if (game.phase() == GamePhase.SEARCH && block.getType() == Material.EMERALD_ORE) {
            game.addEmeralds(player, config.emeraldsPerOre());
            BlockcraftApi.get().notifications().rewardSound(player, Sounds.COINS);
            Tasks.later(plugin, config.oreRespawnSeconds() * 20L, () -> block.setType(Material.EMERALD_ORE));
            return;
        }
        if (!buildMode.isEnabled(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (!buildMode.isEnabled(event.getPlayer())) {
            event.setCancelled(true);
        }
    }
}
