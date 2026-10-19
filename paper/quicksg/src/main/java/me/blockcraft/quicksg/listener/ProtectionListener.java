package me.blockcraft.quicksg.listener;

import me.blockcraft.api.world.BuildMode;
import me.blockcraft.quicksg.game.Game;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/** QuickSG's rules beyond the shared lobby's: nobody is hurt in the protection time, and only build mode builds. */
public final class ProtectionListener implements Listener {

    private final Game game;
    private final BuildMode buildMode;

    public ProtectionListener(Game game, BuildMode buildMode) {
        this.game = game;
        this.buildMode = buildMode;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (game.protection()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        event.setCancelled(!buildMode.isEnabled(event.getPlayer()));
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        event.setCancelled(!buildMode.isEnabled(event.getPlayer()));
    }
}
