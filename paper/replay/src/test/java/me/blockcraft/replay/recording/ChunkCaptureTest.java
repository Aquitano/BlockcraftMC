package me.blockcraft.replay.recording;

import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class ChunkCaptureTest {

    @Test
    public void paletteUsesSourceHeightAndYzxOrder() {
        var world = UUID.randomUUID();
        var snapshot = (ChunkSnapshot) Proxy.newProxyInstance(ChunkSnapshot.class.getClassLoader(),
                new Class<?>[]{ChunkSnapshot.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getBlockType")) { return Material.STONE; }
                    if (!method.getName().equals("getBlockData")) { throw new AssertionError(method); }
                    String data = arguments[0] + ":" + arguments[1] + ":" + arguments[2];
                    return Proxy.newProxyInstance(BlockData.class.getClassLoader(), new Class<?>[]{BlockData.class},
                            (block, accessor, values) -> {
                                if (accessor.getName().equals("getAsString")) { return data; }
                                throw new AssertionError(accessor);
                            });
                });

        var chunk = MatchRecorder.chunk(7, new MatchRecorder.ChunkKey(world, -2, 3), snapshot, -64, 2);

        assertEquals(world, chunk.world());
        assertEquals(-64, chunk.minY());
        assertEquals(512, chunk.blocks().length);
        assertEquals("0:-64:0", chunk.palette().get(chunk.blocks()[0]));
        assertEquals("15:-64:0", chunk.palette().get(chunk.blocks()[15]));
        assertEquals("0:-64:1", chunk.palette().get(chunk.blocks()[16]));
        assertEquals("0:-63:0", chunk.palette().get(chunk.blocks()[256]));
    }

    @Test
    public void airNeverConstructsBlockDataAndUsesOnePaletteEntry() {
        var snapshot = (ChunkSnapshot) Proxy.newProxyInstance(ChunkSnapshot.class.getClassLoader(),
                new Class<?>[]{ChunkSnapshot.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getBlockType")) { return Material.AIR; }
                    throw new AssertionError("Air capture must not request " + method.getName());
                });

        var chunk = MatchRecorder.chunk(0, new MatchRecorder.ChunkKey(UUID.randomUUID(), 0, 0), snapshot, -64, 384);

        assertEquals(java.util.List.of("minecraft:air"), chunk.palette());
        assertEquals(384 * 256, chunk.blocks().length);
        assertEquals(0, chunk.blocks()[chunk.blocks().length - 1]);
    }
}
