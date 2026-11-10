package me.blockcraft.replay.recording;

import me.blockcraft.replay.data.ReplayEvent;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ActorFramesTest {

    @Test
    public void unchangedEquipmentComparesByBytesAndRemovedActorsChangeTheFrame() {
        var id = UUID.randomUUID();
        var world = UUID.randomUUID();
        var first = actor(id, world, new byte[]{1, 2});
        var identical = actor(id, world, new byte[]{1, 2});
        var equipped = actor(id, world, new byte[]{1, 3});

        assertTrue(ActorFrames.same(List.of(first), List.of(identical)));
        assertFalse(ActorFrames.same(List.of(first), List.of(equipped)));
        assertFalse(ActorFrames.same(List.of(first), List.of()));
        assertFalse(ActorFrames.same(List.of(first), List.of(actor(id, UUID.randomUUID(), new byte[]{1, 2}))));
    }

    private static ReplayEvent.Actor actor(UUID id, UUID world, byte[] equipment) {
        return new ReplayEvent.Actor(id, world, 1, 2, 3, 4, 5, "STANDING", List.of(equipment));
    }
}
