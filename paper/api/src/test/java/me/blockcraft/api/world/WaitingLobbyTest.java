package me.blockcraft.api.world;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class WaitingLobbyTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void invalidSpawnFailsBeforeLoadingOrReplacingAnyWorld() throws IOException {
        var template = folder.newFolder("waiting-lobby").toPath();
        var region = Files.createDirectories(template.resolve("world/region")).resolve("r.0.0.mca");
        Files.writeString(region, "saved terrain");
        for (String spawn : new String[]{"spawn: {x: 1, y: 47}", "spawn: {x: missing, y: 47, z: 2}",
                "spawn: {x: .NaN, y: 47, z: 2}"}) {
            Files.writeString(template.resolve("lobby.yml"), spawn);
            assertThrows(IllegalArgumentException.class, () -> WaitingLobby.load(template));
            assertEquals("saved terrain", Files.readString(region));
        }
    }
}
