package me.blockcraft.sternenfront.menu;

import me.blockcraft.sternenfront.game.BattleNavigation.Point;
import me.blockcraft.sternenfront.game.Spawn;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

public class TacticalLayoutTest {

    @Test
    public void equallySpacedPostsKeepTheirMiddlePostCentred() {
        var points = IntStream.range(0, 3).mapToObj(index -> new TacticalLayout.Marker(new Spawn.Post(index), new Point(0, index * 50))).toList();
        var slots = TacticalLayout.project(points);
        assertEquals(slots.get(new Spawn.Post(1)) - slots.get(new Spawn.Post(0)),
                slots.get(new Spawn.Post(2)) - slots.get(new Spawn.Post(1)));
    }

    @Test
    public void preservesNorthSouthAndEastWestAcrossMapOffsetsAndScale() {
        var points = List.of(new TacticalLayout.Marker(new Spawn.Headquarters(), new Point(-100, 0)),
                new TacticalLayout.Marker(new Spawn.Post(0), new Point(0, -50)),
                new TacticalLayout.Marker(new Spawn.Post(1), new Point(0, 50)),
                new TacticalLayout.Marker(new Spawn.Post(2), new Point(100, 0)));
        var slots = TacticalLayout.project(points);
        assertTrue(slots.get(new Spawn.Headquarters()) % 9 < slots.get(new Spawn.Post(2)) % 9);
        assertTrue(slots.get(new Spawn.Post(0)) / 9 < slots.get(new Spawn.Post(1)) / 9);
        var moved = points.stream().map(marker -> new TacticalLayout.Marker(marker.spawn(),
                new Point(marker.point().x() * 3 + 1000, marker.point().z() * 3 - 900))).toList();
        assertEquals(slots, TacticalLayout.project(moved));
        assertTrue(slots.values().stream().allMatch(slot -> slot >= 9 && slot < 45));
    }

    @Test
    public void overlappingMarkersRemainClickableAndDoNotCoverTheToolbarOrLanding() {
        var points = IntStream.range(0, 8).mapToObj(index -> new TacticalLayout.Marker(new Spawn.Post(index), new Point(1, 1))).toList();
        var slots = TacticalLayout.project(points);
        assertEquals(8, new HashSet<>(slots.values()).size());
        assertEquals(slots, TacticalLayout.project(points));
        assertTrue(slots.values().stream().allMatch(slot -> slot >= 9 && slot < 45));
        assertTrue(TacticalLayout.project(List.of()).isEmpty());
    }
}
