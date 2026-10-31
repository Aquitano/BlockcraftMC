package me.blockcraft.lobby.listener;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.hider.PlayerHider;
import me.blockcraft.lobby.item.LobbyItem;
import me.blockcraft.lobby.menu.Menus;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class LobbyItemListener implements Listener {

    private final Messages messages;
    private final Menus menus;
    private final PlayerHider hider;

    public LobbyItemListener(Messages messages, Menus menus, PlayerHider hider) {
        this.messages = messages;
        this.menus = menus;
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
                case PROFILE -> menus.openProfile(player);
                case SETTINGS -> menus.openSettings(player);
                case FRIENDS -> menus.openFriends(player);
                case GADGET, POND_ROD, THROW -> {
                }
            }
        });
    }

    private void toggleHider(Player player) {
        if (hider.onCooldown(player)) {
            BlockcraftApi.get().notifications().feedback(player, messages.get("hider.wait"));
            return;
        }
        if (hider.isHiding(player)) {
            hider.showAll(player);
        } else {
            hider.hideAll(player);
        }
        player.setCooldown(Material.LIME_DYE, hider.cooldownTicks());
        player.setCooldown(Material.GRAY_DYE, hider.cooldownTicks());
        Sounds.MENU_CLICK.play(player);
        BlockcraftApi.get().notifications().feedback(player, messages.get(hider.isHiding(player) ? "hider.hidden" : "hider.visible"));
    }
}
