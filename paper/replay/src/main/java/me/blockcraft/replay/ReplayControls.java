package me.blockcraft.replay;

import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.common.message.Messages;
import me.blockcraft.replay.playback.PlaybackManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

final class ReplayControls implements Listener {

    private enum Control implements HotbarItem {
        PAUSE(0, MenuIcon.LOADING), BACK(3, MenuIcon.PREVIOUS), FORWARD(5, MenuIcon.NEXT),
        SPEED(7, MenuIcon.TOOL_SETTINGS), EXIT(8, MenuIcon.TOOL_CLOSE);

        private final int slot;
        private final MenuIcon icon;

        Control(int slot, MenuIcon icon) {
            this.slot = slot;
            this.icon = icon;
        }
    }

    private final ReplayService service;
    private final PlaybackManager playback;
    private final Messages messages;

    ReplayControls(ReplayService service, PlaybackManager playback, Messages messages) {
        this.service = service;
        this.playback = playback;
        this.messages = messages;
    }

    void give(Player player) {
        for (var control : Control.values()) {
            var key = control.name().toLowerCase(java.util.Locale.ROOT);
            player.getInventory().setItem(control.slot, control.build(control.icon.item()
                    .name(messages.get("controls." + key + ".name")).lore(messages.get("controls." + key + ".hint"))));
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        var player = event.getPlayer();
        if (!playback.active(player) || event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true);
        HotbarItem.of(event.getItem(), Control.class).ifPresent(control -> {
            switch (control) {
                case PAUSE -> service.pause(player);
                case BACK -> service.step(player, -10);
                case FORWARD -> service.step(player, 10);
                case SPEED -> service.cycleSpeed(player);
                case EXIT -> service.stop(player);
            }
        });
    }
}
