package me.blockcraft.rush.Listener;

import me.blockcraft.rush.Main;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class WorldListener implements Listener {
    @EventHandler
    public void on(final BlockPlaceEvent e) {
        final Player p = e.getPlayer();
        e.setCancelled(!Main.builder.contains(p));
        if (Main.State_DM && e.getBlock().getType() == Material.WEB) {
            e.setBuild(false);
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> e.getBlock().setType(Material.AIR), 60L);
        }
    }

    @EventHandler
    public void noUproot(final PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL && event.getClickedBlock().getType() == Material.SOIL) {
            event.setCancelled(true);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @EventHandler
    public void onBuild(final BlockPlaceEvent event) {
        final Player p = event.getPlayer();
        if (event.getBlock().getType() == Material.TNT) {
            event.getBlock().setType(Material.AIR);
            final TNTPrimed tnt = (TNTPrimed) event.getBlock().getWorld().spawn(event.getBlock().getLocation(), (Class) TNTPrimed.class);
            tnt.setFuseTicks(40);
            ItemStack[] contents;
            for (int length = (contents = p.getInventory().getContents()).length, i = 0; i < length; ++i) {
                final ItemStack stack = contents[i];
                if (stack.getAmount() == 1) {
                    p.getInventory().remove(stack);
                } else {
                    stack.setAmount(stack.getAmount() - 1);
                }
            }
        }
    }

    @EventHandler
    public void onEntityExplore(final EntityExplodeEvent event) {
        event.blockList().clear();
    }

    @EventHandler
    public void preventLeavedecay(final LeavesDecayEvent e) {
        e.setCancelled(true);
    }
}
