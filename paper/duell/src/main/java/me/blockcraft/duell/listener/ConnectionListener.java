package me.blockcraft.duell.listener;

import me.blockcraft.api.nick.NickChangeEvent;
import me.blockcraft.api.ranked.RankedMatchEvent;
import me.blockcraft.api.spectate.SpectateRequestEvent;
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

    /** The proxy names the friend a watching player came for, which reaches the backend after the join. */
    @EventHandler
    public void onSpectateRequest(SpectateRequestEvent event) {
        duel.follow(event.getPlayer(), event.target());
    }

    /** The proxy's ranked queue paired this player, which it says once both arrived. */
    @EventHandler
    public void onRankedMatch(RankedMatchEvent event) {
        duel.rankedMatch(event.getPlayer(), event.opponent());
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
