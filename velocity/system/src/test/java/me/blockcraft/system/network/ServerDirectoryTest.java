package me.blockcraft.system.network;

import me.blockcraft.common.network.ServerIdentity;
import me.blockcraft.common.network.ServerRecord;
import me.blockcraft.common.network.ServerState;
import me.blockcraft.common.network.ServerStatus;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ServerDirectoryTest {

    @Test
    public void onlyAGameServerWithARunningRoundIsWatchable() {
        var directory = directoryOf(
                server("quicksg-1", "quicksg", ServerState.INGAME),
                server("quicksg-2", "quicksg", ServerState.LOBBY),
                server("quicksg-3", "quicksg", ServerState.RESTART),
                server("lobby-1", "lobby", ServerState.INGAME));

        assertTrue(directory.isWatchable("quicksg-1"));
        assertFalse(directory.isWatchable("quicksg-2"));
        assertFalse(directory.isWatchable("quicksg-3"));
        assertFalse(directory.isWatchable("lobby-1"));
        assertFalse(directory.isWatchable("unknown"));
    }

    @Test
    public void aRunningRoundStaysClosedToJoins() {
        var directory = directoryOf(server("quicksg-1", "quicksg", ServerState.INGAME));

        assertFalse(directory.isJoinable("quicksg-1"));
    }

    /** Both decisions read the mirrored rows alone, so the proxy behind the directory is never asked. */
    private static ServerDirectory directoryOf(ServerRecord... servers) {
        var directory = new ServerDirectory(null, "lobby");
        directory.update(List.of(servers));
        return directory;
    }

    private static ServerRecord server(String name, String group, ServerState state) {
        return new ServerRecord(new ServerIdentity(name, group, "127.0.0.1", 25566), new ServerStatus(state, 4, 8, 20, 5));
    }
}
