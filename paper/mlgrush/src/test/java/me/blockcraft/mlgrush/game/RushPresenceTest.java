package me.blockcraft.mlgrush.game;

import me.blockcraft.common.network.ServerIdentity;
import me.blockcraft.common.network.ServerRecord;
import me.blockcraft.common.network.ServerState;
import me.blockcraft.common.network.ServerStatus;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RushPresenceTest {

    @Test
    public void spectatorsLeaveRoomForAReplacementWithoutOpeningAThirdParticipantSeat() {
        assertFalse(server(2, 2, 2).joinable());
        var afterVanish = server(2, 1, 2);
        assertTrue(afterVanish.joinable());
        var replacementReserved = server(2, 1, 3);
        assertFalse(replacementReserved.joinable());
        var replacementArrived = server(3, 2, 3);
        assertFalse(replacementArrived.joinable());
        var spectatorDisconnected = server(2, 2, 2);
        assertFalse(spectatorDisconnected.joinable());
        var participantDisconnected = server(2, 1, 2);
        assertTrue(participantDisconnected.joinable());
    }

    private static ServerRecord server(int connected, int participants, int proxyOccupancy) {
        return new ServerRecord(new ServerIdentity("mlgrush", "mlgrush", "localhost", 25565),
                new ServerStatus(ServerState.LOBBY, connected, RushGame.advertisedCapacity(connected, participants), 20, 0),
                proxyOccupancy);
    }
}
