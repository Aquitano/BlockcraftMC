package me.blockcraft.api.scenery;

import me.blockcraft.api.world.Point;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class CreatureLayoutTest {

    @Test
    public void beesRestAtFlowersAndReturnWithoutJumpingAtTheLoopBoundary() {
        var hive = new Point(0, 2, 0, 0, 0);
        var flower = new Point(4, 3, 0, 0, 0);
        var flight = new CreatureLayout.BeeFlight("bee", List.of(hive, flower), 100, 40, 0, 0);

        assertEquals(0, flight.pose(39).x(), 0);
        assertEquals(2, flight.pose(90).x(), 1e-6);
        assertEquals(2.5, flight.pose(90).y(), 1e-6);
        assertEquals(4, flight.pose(140).x(), 0);
        assertEquals(4, flight.pose(179).x(), 0);
        assertEquals(0, flight.pose(280).x(), 0);
        assertEquals(flight.pose(0), flight.pose(280));
        assertEquals(flight.pose(90), new CreatureLayout.BeeFlight("other", List.of(hive, flower), 100, 40, 90, 0).pose(0));
    }

    @Test
    public void birdLooksAndHopsThenRestsOnTheSamePerch() {
        var perch = new Point(4, 52.5, 8, 135, 0);
        var bird = new CreatureLayout.PerchedBird("bird", perch, 320, 80, 16, 0.18, 35);

        assertEquals(170, bird.pose(20).yaw(), 1e-6);
        assertEquals(100, bird.pose(60).yaw(), 1e-6);
        assertEquals(52.68, bird.pose(88).y(), 1e-6);
        assertEquals(perch, bird.pose(96));
        assertEquals(perch, bird.pose(319));
        assertEquals(perch, bird.pose(320));
    }

    @Test
    public void invalidRoutesCannotSpawnAtAnAccidentalOrigin() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString("""
                step-ticks: 2
                creatures:
                  bee:
                    type: bee
                    travel-ticks: 120
                    rest-ticks: 60
                    phase-ticks: 0
                    hover-blocks: 0.04
                    route: [{x: 1, y: 2, z: 3}, {x: 4, y: 2}]
                """);
        assertThrows(IllegalArgumentException.class, () -> CreatureLayout.parse(yaml));
        yaml.set("creatures.bee.route", List.of(java.util.Map.of("x", 1, "y", 2, "z", 3),
                java.util.Map.of("x", 4, "y", 2, "z", 3)));
        assertEquals(1, CreatureLayout.parse(yaml).creatures().size());
        yaml.set("creatures.bee.travel-ticks", 0);
        assertThrows(IllegalArgumentException.class, () -> CreatureLayout.parse(yaml));
    }
}
