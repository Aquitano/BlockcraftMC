package me.blockcraft.mapimport;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RegionFileTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void oversizedChunksRoundTripIntoANewRegionDirectory() throws IOException {
        var file = folder.getRoot().toPath().resolve("region/r.-1.2.mca");
        var payload = new byte[255 * 4096];
        var entry = new RegionFile.Entry(123, (byte) 3, payload);

        RegionFile.write(file, Map.of(33, entry));

        var restored = RegionFile.read(file).get(33);
        assertTrue(Files.isRegularFile(file.resolveSibling("c.-31.65.mcc")));
        assertEquals(entry.timestamp(), restored.timestamp());
        assertEquals(entry.compression(), restored.compression());
        assertArrayEquals(payload, restored.payload());
    }

    @Test
    public void corruptSectorOffsetsAreSkipped() throws IOException {
        var file = folder.getRoot().toPath().resolve("r.0.0.mca");
        var header = ByteBuffer.allocate(8192).putInt(0, 0xffffff01);
        Files.write(file, header.array());

        assertTrue(RegionFile.read(file).isEmpty());
    }
}
