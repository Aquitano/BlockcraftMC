package me.blockcraft.ttt.arena;

import me.blockcraft.api.world.Point;
import org.bukkit.Material;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;

public class TownMapsTest {

    private static final String TESTER = """
            tester:
              zone: {x1: 1, y1: 75, z1: 0, x2: -1, y2: 77, z2: -2}
              button: {x: 0, y: 76, z: -2}
              entrance: {x1: -1, y1: 75, z1: 1, x2: 1, y2: 76, z2: 1}
              lamps:
                - {x: -2, y: 77, z: 1}
                - {x: 2, y: 77, z: 1}
              trap:
                button: {x: 0, y: 73, z: 12}
                floor: {x1: -1, y1: 74, z1: -2, x2: 1, y2: 74, z2: 0}
            """;

    private static final String PLAYABLE = """
            name: Vineyard
            builders: [ClayMC]
            icon: vine
            border: {x: 6.5, z: 8.5, size: 128}
            spectator: {x: 6.5, y: 105.0, z: 45.5, yaw: 180.0, pitch: 35.0}
            spawns:
              - {x: 0.5, y: 71.0, z: -12.5}
              - {x: 4.5, y: 72.0, z: -6.5, yaw: 33.7}
            """ + TESTER;

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void aMapReadsWhereARoundStartsAndItsTester() throws IOException {
        write("vineyard", PLAYABLE, true);

        var map = pool().maps().getFirst();

        assertEquals("Vineyard", map.name());
        assertEquals(List.of("ClayMC"), map.builders());
        assertEquals(Material.VINE, map.icon());
        assertEquals(new TownMap.Border(6.5, 8.5, 128), map.border());
        assertEquals(new Point(4.5, 72.0, -6.5, 33.7f, 0.0f), map.spawns().get(1));
        assertEquals(new TownMap.TesterParts(Box.of(-1, 75, -2, 1, 77, 0), new TownMap.Spot(0, 76, -2),
                Box.of(-1, 75, 1, 1, 76, 1), List.of(new TownMap.Spot(-2, 77, 1), new TownMap.Spot(2, 77, 1)),
                Optional.of(new TownMap.TrapParts(new TownMap.Spot(0, 73, 12), Box.of(-1, 74, -2, 1, 74, 0)))), map.tester());
    }

    @Test
    public void onlyMapsWithAWorldSpawnsAndATesterAreInThePool() throws IOException {
        write("ready", PLAYABLE, true);
        write("no_world", PLAYABLE, false);
        write("no_tester", PLAYABLE.replace(TESTER, ""), true);
        write("no_spawns", PLAYABLE.replaceAll("(?s)spawns:.*?tester:", "tester:"), true);
        write("Bad-Key", PLAYABLE, true);
        write("zero_border", PLAYABLE.replace("size: 128", "size: 0"), true);
        write("oversized_border", PLAYABLE.replace("size: 128", "size: 60000000"), true);
        write("nonfinite_border", PLAYABLE.replace("x: 6.5, z: 8.5, size: 128", "x: .nan, z: 8.5, size: 128"), true);

        assertEquals(List.of("ready"), pool().maps().stream().map(TownMap::key).toList());
    }

    private TownMaps pool() {
        return new TownMaps(temporary.getRoot().toPath(), Logger.getAnonymousLogger());
    }

    private void write(String key, String yaml, boolean world) throws IOException {
        var folder = temporary.getRoot().toPath().resolve(key);
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("map.yml"), yaml);
        if (world) {
            Files.createDirectories(folder.resolve("world").resolve("region"));
        }
    }
}
