package me.blockcraft.signs.listener;

import me.blockcraft.signs.sign.StatusSigns;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public final class SignBreakListener implements Listener {

    private final StatusSigns signs;

    public SignBreakListener(StatusSigns signs) {
        this.signs = signs;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        signs.remove(event.getBlock().getLocation());
    }
}
