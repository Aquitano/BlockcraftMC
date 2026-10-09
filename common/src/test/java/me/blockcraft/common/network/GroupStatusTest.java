package me.blockcraft.common.network;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GroupStatusTest {

    @Test
    public void anOpenInstanceKeepsTheGameJoinableWhileOtherRoundsRun() {
        var status = GroupStatus.of(List.of(server("one", ServerState.INGAME, 8, 8),
                server("two", ServerState.LOBBY, 1, 8), server("three", ServerState.RESTART, 0, 8)));

        assertTrue(status.joinable());
        assertEquals(GroupStatus.Availability.AVAILABLE, status.availability());
        assertEquals(9, status.online());
        assertEquals(24, status.maxPlayers());
    }

    @Test
    public void aFullLobbyDoesNotAdvertiseAnAvailableRound() {
        var status = GroupStatus.of(List.of(server("one", ServerState.LOBBY, 8, 8),
                server("two", ServerState.INGAME, 2, 8)));

        assertFalse(status.joinable());
        assertEquals(GroupStatus.Availability.FULL, status.availability());
    }

    @Test
    public void reportsWhyNoRoundIsAvailable() {
        assertEquals(GroupStatus.Availability.OFFLINE, GroupStatus.of(List.of()).availability());
        assertEquals(GroupStatus.Availability.INGAME,
                GroupStatus.of(List.of(server("one", ServerState.INGAME, 1, 8))).availability());
        assertEquals(GroupStatus.Availability.RESTART,
                GroupStatus.of(List.of(server("one", ServerState.RESTART, 0, 8))).availability());
    }

    private static ServerRecord server(String name, ServerState state, int online, int max) {
        return new ServerRecord(new ServerIdentity(name, "quicksg", "localhost", 25565), new ServerStatus(state, online, max, 20, 5));
    }
}
