package de.Blockcraft.BungeeSystem.Util;

import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Manages support sessions between players and support staff.
 */
public class SupportManager {
    // Mapping of requester UUID to supporter UUID
    private static final ConcurrentMap<String, String> activeSupportSessions = new ConcurrentHashMap<>();

    /**
     * Starts a support session between a requester and a supporter.
     *
     * @param requester The player requesting support.
     * @param supporter The staff member providing support.
     */
    public static void startSupportSession(ProxiedPlayer requester, ProxiedPlayer supporter) {
        activeSupportSessions.put(requester.getUniqueId().toString(), supporter.getUniqueId().toString());
    }

    /**
     * Ends a support session between a requester and a supporter.
     *
     * @param requester The player requesting support.
     * @param supporter The staff member providing support.
     */
    public static void endSupportSession(ProxiedPlayer requester, ProxiedPlayer supporter) {
        activeSupportSessions.remove(requester.getUniqueId().toString());
    }

    /**
     * Retrieves the supporter UUID for a given requester UUID.
     *
     * @param requesterUUID The UUID of the requester.
     * @return The UUID of the supporter, or null if no session exists.
     */
    public static String getSupporterUUID(String requesterUUID) {
        return activeSupportSessions.get(requesterUUID);
    }

    /**
     * Retrieves the requester UUID for a given supporter UUID.
     *
     * @param supporterUUID The UUID of the supporter.
     * @return The UUID of the requester, or null if no session exists.
     */
    public static String getRequesterUUID(String supporterUUID) {
        for (Map.Entry<String, String> entry : activeSupportSessions.entrySet()) {
            if (entry.getValue().equals(supporterUUID)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Checks if a player has an active support session.
     *
     * @param playerUUID The UUID of the player.
     * @return True if the player is in an active support session, false otherwise.
     */
    public static boolean isInSupportSession(String playerUUID) {
        return activeSupportSessions.containsKey(playerUUID) || activeSupportSessions.containsValue(playerUUID);
    }
}
