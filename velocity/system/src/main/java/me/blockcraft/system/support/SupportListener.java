package me.blockcraft.system.support;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.SystemPermissions;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;

/** Keeps the queue honest on disconnects and tells arriving staff about requests still waiting. */
public final class SupportListener {

    private static final String LIST_COMMAND = "/support list";

    private final SupportSessions sessions;
    private final Messages messages;

    public SupportListener(SupportSessions sessions, Messages messages) {
        this.sessions = sessions;
        this.messages = messages;
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        sessions.onDisconnect(event.getPlayer().getUniqueId());
    }

    /** The first server connect is when the client can show chat; a login-time message would be lost. */
    @Subscribe
    public void onFirstServer(ServerPostConnectEvent event) {
        var staff = event.getPlayer();
        if (event.getPreviousServer() != null || !staff.hasPermission(SystemPermissions.SUPPORT_STAFF)) {
            return;
        }
        long waiting = sessions.waitingCount();
        if (waiting > 0) {
            staff.sendMessage(messages.get("support.waiting-on-join", Formatter.number("count", waiting))
                    .clickEvent(ClickEvent.runCommand(LIST_COMMAND)));
        }
    }
}
