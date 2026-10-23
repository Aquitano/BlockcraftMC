package me.blockcraft.bedwars.listener;

import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import org.bukkit.Tag;
import org.bukkit.block.data.Bisected;
import org.bukkit.entity.Hanging;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;

/**
 * What no BedWars world does, whatever the phase: blocks keep still unless they are halves of a bed or a tall plant,
 * nothing grows, burns, melts, flows or spreads, item frames and paintings stay, nobody crafts or sleeps.
 */
public final class WorldRules implements Listener {

    @EventHandler
    public void onPhysics(BlockPhysicsEvent event) {
        var block = event.getBlock();
        if (!Tag.BEDS.isTagged(block.getType()) && !(block.getBlockData() instanceof Bisected)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onGrow(BlockGrowEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onIgnite(BlockIgniteEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onFade(BlockFadeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onFlow(BlockFromToEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onSpread(BlockSpreadEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onHangingBreak(HangingBreakEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onHangingPlace(HangingPlaceEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onHangingDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Hanging) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemFrameChange(PlayerItemFrameChangeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onSleep(PlayerBedEnterEvent event) {
        event.setCancelled(true);
    }
}
