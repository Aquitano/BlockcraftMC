package me.blockcraft.rush.listener;

import me.blockcraft.api.task.Tasks;
import me.blockcraft.rush.config.RushConfig;
import me.blockcraft.rush.game.Game;
import me.blockcraft.rush.game.GamePhase;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.Plugin;

public final class BlockListener implements Listener {

    private static final int TNT_FUSE_TICKS = 40;
    private static final long COBWEB_LIFETIME_TICKS = 60L;

    private final Plugin plugin;
    private final Game game;
    private final RushConfig config;

    public BlockListener(Plugin plugin, Game game, RushConfig config) {
        this.plugin = plugin;
        this.game = game;
        this.config = config;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        event.setExpToDrop(0);
        event.setDropItems(false);
        var block = event.getBlock();
        var player = event.getPlayer();
        if (game.phase() == GamePhase.SEARCH && block.getType() == Material.EMERALD_ORE) {
            game.addEmeralds(player, config.emeraldsPerOre());
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 4.0f, 4.0f);
            Tasks.later(plugin, config.oreRespawnSeconds() * 20L, () -> block.setType(Material.EMERALD_ORE));
            return;
        }
        event.setCancelled(!game.isBuilder(player));
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        event.setCancelled(!game.isBuilder(event.getPlayer()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlaceSpecial(BlockPlaceEvent event) {
        var block = event.getBlock();
        if (block.getType() == Material.TNT) {
            block.setType(Material.AIR);
            block.getWorld().spawn(block.getLocation().toCenterLocation(), TNTPrimed.class, tnt -> tnt.setFuseTicks(TNT_FUSE_TICKS));
        } else if (block.getType() == Material.COBWEB && game.phase() == GamePhase.DEATHMATCH) {
            Tasks.later(plugin, COBWEB_LIFETIME_TICKS, () -> block.setType(Material.AIR));
        }
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent event) {
        event.blockList().clear();
    }
}
