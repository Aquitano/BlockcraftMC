package me.blockcraft.system.party;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import me.blockcraft.system.LiveSession;

public final class PartyListener {

    private final PartyManager parties;

    public PartyListener(PartyManager parties) {
        this.parties = parties;
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        parties.followLeader(event.getPlayer(), event.getServer());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        if (LiveSession.endedBy(event)) {
            parties.onDisconnect(event.getPlayer());
        }
    }
}
