package me.blockcraft.replay.playback;

import me.blockcraft.replay.data.ReplayEvent;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class TerrainLogTest {

    private static final UUID WORLD = UUID.randomUUID();
    private static final TerrainLog.Position PLACED = new TerrainLog.Position(WORLD, -1, 64, -1);
    private static final TerrainLog.Position NEIGHBOUR = new TerrainLog.Position(WORLD, -1, 63, -1);

    @Test
    public void seekingRewritesOnlyTheBlocksThatDifferInEitherDirection() {
        var stone = new int[384 * 256];
        stone[(63 + 64) * 256 + 15 * 16 + 15] = 1;
        var events = List.<ReplayEvent>of(
                new ReplayEvent.Chunk(0, WORLD, -1, -1, -64, 384, List.of("minecraft:air", "minecraft:stone"), stone),
                new ReplayEvent.Blocks(10, List.of(block(PLACED, "minecraft:dirt"), block(NEIGHBOUR, "minecraft:stone"))),
                new ReplayEvent.Blocks(20, List.of(block(PLACED, "minecraft:air"))),
                new ReplayEvent.Blocks(30, List.of(block(PLACED, "minecraft:tnt"))));
        var log = new TerrainLog(TerrainLog.changes(events));

        assertEquals(Map.of(), log.seek(5));
        assertEquals(Map.of(PLACED, "minecraft:air"), log.seek(25));
        assertEquals(Map.of(PLACED, "minecraft:tnt"), log.seek(30));
        assertEquals(Map.of(PLACED, "minecraft:dirt"), log.seek(15));
        assertEquals(Map.of(PLACED, "minecraft:air"), log.seek(0));
        assertEquals(Map.of(), log.seek(0));
    }

    private static ReplayEvent.Block block(TerrainLog.Position position, String data) {
        return new ReplayEvent.Block(position.world(), position.x(), position.y(), position.z(), data);
    }
}
