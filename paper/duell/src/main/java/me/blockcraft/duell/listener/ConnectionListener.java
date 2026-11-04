package me.blockcraft.duell.listener;

import me.blockcraft.api.game.DuelCancelledEvent;
import me.blockcraft.api.game.DuelSetupEvent;
import me.blockcraft.api.game.DuelAcceptedEvent;
import me.blockcraft.api.nick.NickChangeEvent;
import me.blockcraft.api.ranked.RankedMatchEvent;
import me.blockcraft.api.vanish.VanishChangeEvent;
import me.blockcraft.duell.game.Duel;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class ConnectionListener implements Listener {

    private final Duel duel;

    public ConnectionListener(Duel duel) {
        this.duel = duel;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.joinMessage(null);
        duel.join(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
        duel.quit(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        duel.respawn(event);
    }

    /** The proxy's ranked queue paired this player, which it says once both arrived. */
    @EventHandler
    public void onRankedMatch(RankedMatchEvent event) {
        duel.rankedMatch(event.getPlayer(), event.opponent());
    }

    @EventHandler
    public void onDuelSetup(DuelSetupEvent event) {
        duel.setupChallenge(event.getPlayer(), event.setup());
    }

    @EventHandler
    public void onDuelAccepted(DuelAcceptedEvent event) {
        duel.acceptChallenge(event.getPlayer(), event.ticket());
    }

    @EventHandler
    public void onDuelCancelled(DuelCancelledEvent event) {
        duel.cancelChallenge(event.ticket());
    }

    /** The tab list and the sidebar show the group a nick wears, not the account's. */
    @EventHandler
    public void onNickChange(NickChangeEvent event) {
        duel.nickChanged(event.getPlayer());
    }

    /** The proxy's word on the pack arrives after the join, and the sidebar's icons depend on it. */
    @EventHandler
    public void onResourcePack(PlayerResourcePackStatusEvent event) {
        duel.refreshScoreboards();
    }

    @EventHandler
    public void onVanishChange(VanishChangeEvent event) {
        duel.vanishChanged(event.getPlayer(), event.isVanished());
    }
}
