package me.blockcraft.duell.listener;

import me.blockcraft.duell.game.Duel;
import me.blockcraft.duell.game.DuelMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class VsListener implements Listener {

    private final Duel duel;

    public VsListener(Duel duel) {
        this.duel = duel;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (duel.isPlaying(DuelMode.VS)) {
            duel.handleDeath(event);
        }
    }
}
