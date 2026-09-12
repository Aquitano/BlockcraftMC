package me.blockcraft.system.support;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;

public final class SupportListener {

    private final SupportSessions sessions;

    public SupportListener(SupportSessions sessions) {
        this.sessions = sessions;
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        sessions.onDisconnect(event.getPlayer().getUniqueId());
    }
}
