package me.blockcraft.system;

import com.velocitypowered.api.event.connection.DisconnectEvent;

/**
 * Velocity fires {@link DisconnectEvent} for every connection that is torn down, including logins that never went
 * live. A refused duplicate login carries the UUID of the session that is still connected, so state keyed by UUID
 * may only be dropped when the event ended a session that passed the post-login event.
 */
public final class LiveSession {

    private LiveSession() {
    }

    public static boolean endedBy(DisconnectEvent event) {
        return switch (event.getLoginStatus()) {
            case SUCCESSFUL_LOGIN, PRE_SERVER_JOIN -> true;
            case CONFLICTING_LOGIN, CANCELLED_BY_PROXY, CANCELLED_BY_USER, CANCELLED_BY_USER_BEFORE_COMPLETE -> false;
        };
    }
}
