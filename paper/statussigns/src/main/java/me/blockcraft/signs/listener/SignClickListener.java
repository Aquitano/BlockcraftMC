package me.blockcraft.signs.listener;

import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.common.message.Messages;
import me.blockcraft.signs.sign.StatusSigns;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

public final class SignClickListener implements Listener {

    private final Plugin plugin;
    private final StatusSigns signs;
    private final Messages messages;

    public SignClickListener(Plugin plugin, StatusSigns signs, Messages messages) {
        this.plugin = plugin;
        this.signs = signs;
        this.messages = messages;
    }

    @EventHandler
    public void onClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        signs.find(event.getClickedBlock().getLocation()).ifPresent(sign -> {
            event.setCancelled(true);
            var player = event.getPlayer();
            if (!signs.isOnline(sign)) {
                player.sendMessage(messages.get("click.offline"));
                return;
            }
            ProxyMessaging.connect(plugin, player, sign.name());
        });
    }
}
