package me.blockcraft.ttt.listener;

import org.bukkit.Location;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ArenaListenerTest {

    @Test
    public void decorativePortalsCannotTakePlayersOutOfTtt() {
        var listener = new ArenaListener(null, null);
        for (var cause : new TeleportCause[]{TeleportCause.NETHER_PORTAL, TeleportCause.END_PORTAL}) {
            var event = new PlayerPortalEvent(null, new Location(null, 25, 87, -23), new Location(null, 0, 64, 0), cause);
            listener.onPortal(event);
            assertTrue(event.isCancelled());
        }
    }
}
