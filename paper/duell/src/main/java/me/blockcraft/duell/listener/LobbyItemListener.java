package me.blockcraft.duell.listener;

import me.blockcraft.api.game.StatsMenus;
import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.common.message.Messages;
import me.blockcraft.duell.game.Duel;
import me.blockcraft.duell.game.GameItem;
import me.blockcraft.duell.menu.ModeMenu;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

public final class LobbyItemListener implements Listener {

    private final Plugin plugin;
    private final Duel duel;
    private final Messages messages;
    private final StatsMenus statsMenus;

    public LobbyItemListener(Plugin plugin, Duel duel, Messages messages, StatsMenus statsMenus) {
        this.plugin = plugin;
        this.duel = duel;
        this.messages = messages;
        this.statsMenus = statsMenus;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) {
            return;
        }
        var player = event.getPlayer();
        HotbarItem.of(event.getItem(), GameItem.class).ifPresent(item -> {
            event.setCancelled(true);
            switch (item) {
                case MODE -> new ModeMenu(duel, messages).open(player);
                case HUB -> ProxyMessaging.hub(plugin, player);
                case STATS -> statsMenus.open(player);
            }
        });
    }
}
