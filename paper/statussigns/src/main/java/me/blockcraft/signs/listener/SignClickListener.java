package me.blockcraft.signs.listener;

import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.signs.sign.StatusSigns;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

public final class SignClickListener implements Listener {

    private final Plugin plugin;
    private final StatusSigns signs;

    public SignClickListener(Plugin plugin, StatusSigns signs) {
        this.plugin = plugin;
        this.signs = signs;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        signs.find(event.getClickedBlock().getLocation()).ifPresent(sign -> {
            event.setCancelled(true);
            (signs.status(sign).joinable() ? Sounds.MENU_CLICK : Sounds.MENU_DENY).play(event.getPlayer());
            ProxyMessaging.play(plugin, event.getPlayer(), sign.group());
        });
    }
}
