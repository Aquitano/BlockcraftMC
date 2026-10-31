package me.blockcraft.replay;

import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.replay.playback.PlaybackControl;
import me.blockcraft.replay.playback.PlaybackManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

final class ReplayControls implements Listener {

    private static final int JUMP_SECONDS = 10;

    private final ReplayService service;
    private final PlaybackManager playback;

    ReplayControls(ReplayService service, PlaybackManager playback) {
        this.service = service;
        this.playback = playback;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        var player = event.getPlayer();
        if (!playback.active(player) || event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true);
        HotbarItem.of(event.getItem(), PlaybackControl.class).ifPresent(control -> {
            Sounds.MENU_CLICK.play(player);
            switch (control) {
                case PLAYERS -> service.players(player);
                case BACK -> service.step(player, -JUMP_SECONDS);
                case PAUSE -> service.toggle(player);
                case FORWARD -> service.step(player, JUMP_SECONDS);
                case SPEED -> service.cycleSpeed(player);
                case EXIT -> service.stop(player);
            }
        });
    }
}
