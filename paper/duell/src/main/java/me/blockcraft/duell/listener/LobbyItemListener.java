package me.blockcraft.duell.listener;

import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.common.message.Messages;
import me.blockcraft.duell.game.Duel;
import me.blockcraft.duell.menu.ModeMenu;
import me.blockcraft.duell.menu.StatsMenus;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

public final class LobbyItemListener implements Listener {

    private final Plugin plugin;
    private final Duel duel;
    private final Messages messages;
    private final String hubServer;
    private final StatsMenus statsMenus;

    public LobbyItemListener(Plugin plugin, Duel duel, Messages messages, String hubServer, StatsMenus statsMenus) {
        this.plugin = plugin;
        this.duel = duel;
        this.messages = messages;
        this.hubServer = hubServer;
        this.statsMenus = statsMenus;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick() || event.getItem() == null) {
            return;
        }
        var player = event.getPlayer();
        switch (event.getItem().getType()) {
            case CHEST -> {
                event.setCancelled(true);
                new ModeMenu(duel, messages).open(player);
            }
            case SLIME_BALL -> {
                if (!duel.isIngame()) {
                    event.setCancelled(true);
                    ProxyMessaging.connect(plugin, player, hubServer);
                }
            }
            case PLAYER_HEAD -> {
                event.setCancelled(true);
                statsMenus.open(player);
            }
            default -> {
            }
        }
    }
}
