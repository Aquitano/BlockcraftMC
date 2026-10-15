package me.blockcraft.core.staff;

import me.blockcraft.common.message.Messages;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * A pre-login read whose answer must be in place when the player arrives: a vanished staffer must not show up, a
 * frozen player must not walk free. When the database cannot answer, the login is turned away rather than let
 * through on a guess; after the first such failure the remaining reads are skipped, so a login waits for one
 * timeout at most.
 */
final class LoginLookup {

    private LoginLookup() {
    }

    static void run(Plugin plugin, Messages messages, AsyncPlayerPreLoginEvent event, String what, Runnable lookup) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        try {
            lookup.run();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not read the " + what + " of " + event.getName() + ", turning the login away", e);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, messages.get("login.retry"));
        }
    }
}
