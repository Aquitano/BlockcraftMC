package me.blockcraft.lobby.listener;

import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.hider.PlayerHider;
import me.blockcraft.lobby.item.LobbyItem;
import me.blockcraft.lobby.item.LobbyItems;
import me.blockcraft.lobby.menu.Menus;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class LobbyItemListener implements Listener {

    private final Messages messages;
    private final Menus menus;
    private final LobbyItems items;
    private final PlayerHider hider;

    public LobbyItemListener(Messages messages, Menus menus, LobbyItems items, PlayerHider hider) {
        this.messages = messages;
        this.menus = menus;
        this.items = items;
        this.hider = hider;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) {
            return;
        }
        Player player = event.getPlayer();
        LobbyItem.of(event.getItem()).ifPresent(item -> {
            switch (item) {
                case NAVIGATOR -> menus.openNavigator(player);
                case SECRETS -> menus.openSecrets(player);
                case EXTRAS -> menus.openExtras(player);
                case PLAYER_HIDER -> toggleHider(player);
                case FRIENDS, GADGET -> {
                }
            }
        });
    }

    private void toggleHider(Player player) {
        if (hider.onCooldown(player)) {
            player.sendMessage(messages.get("hider.wait"));
            return;
        }
        if (hider.isHiding(player)) {
            hider.showAll(player);
        } else {
            hider.hideAll(player);
        }
        player.getInventory().setItem(LobbyItem.PLAYER_HIDER.slot(), items.hiderItem(!hider.isHiding(player)));
    }
}
