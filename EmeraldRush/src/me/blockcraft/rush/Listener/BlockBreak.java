package me.blockcraft.rush.Listener;

import me.blockcraft.rush.Main;
import me.blockcraft.rush.RushManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.HashMap;

public class BlockBreak implements Listener {
    public static HashMap<Integer, String> broken;
    public static int current;
    public static int currentBlock;

    static {
        BlockBreak.broken = new HashMap<>();
        BlockBreak.current = 0;
        BlockBreak.currentBlock = 0;
    }

    @SuppressWarnings({"unused", "deprecation"})
    @EventHandler
    public void onBlockBreak(final BlockBreakEvent e) {
        final Block b = e.getBlock();
        final Player p = e.getPlayer();
        e.getBlock().getDrops().clear();
        e.setExpToDrop(0);
        if (Main.State_SuchZeit) {
            if (e.getBlock().getType() == Material.EMERALD_ORE) {
                e.setCancelled(false);
                e.getBlock().getDrops().clear();
                e.getBlock().setType(Material.AIR);
                final String blockString = e.getBlock().getTypeId() + ":" + e.getBlock().getData() + ":" + e.getBlock().getWorld().getName() + ":" + e.getBlock().getX() + ":" + e.getBlock().getY() + ":" + e.getBlock().getZ();
                BlockBreak.broken.put(BlockBreak.current, b.getLocation().getBlockX() + ";" + b.getLocation().getBlockY() + ";" + b.getLocation().getBlockZ() + ";" + b.getTypeId() + ";" + b.getData());
                ++BlockBreak.current;
                RushManager.setCoins(p, RushManager.getCoins(p) + 12);
                p.playSound(p.getLocation(), Sound.CLICK, 4.0f, 4.0f);
                Bukkit.getScheduler().scheduleSyncDelayedTask(Main.getInstance(), () -> {
                    final String[] array = BlockBreak.broken.get(BlockBreak.currentBlock).split(";");
                    final int x = Integer.parseInt(array[0]);
                    final int y = Integer.parseInt(array[1]);
                    final int z = Integer.parseInt(array[2]);
                    final int id = Integer.parseInt(array[3]);
                    final byte data = Byte.parseByte(array[4]);
                    Bukkit.getWorld("world").getBlockAt(x, y, z).setType(Material.EMERALD_ORE);
                    ++BlockBreak.currentBlock;
                }, 240L);
            } else e.setCancelled(!Main.builder.contains(p));
        } else e.setCancelled(!Main.builder.contains(p));
    }
}
