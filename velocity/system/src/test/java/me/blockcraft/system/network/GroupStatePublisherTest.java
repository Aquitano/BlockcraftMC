package me.blockcraft.system.network;

import me.blockcraft.common.network.GroupState;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GroupStatePublisherTest {

    private final ArrayDeque<Runnable> queued = new ArrayDeque<>();

    @Test
    public void aFailedWriteRetriesWithoutAnotherStateTransition() {
        var fail = new AtomicBoolean(true);
        var stored = new HashMap<String, GroupState>();
        var publisher = publisher((group, state) -> {
            if (fail.getAndSet(false)) {
                throw new IllegalStateException("database unavailable");
            }
            stored.put(group, state);
        });
        drain();
        publisher.publish("bedwars", GroupState.AWAKE);
        drain();
        assertTrue(stored.isEmpty());

        publisher.retry();
        drain();

        assertEquals(GroupState.AWAKE, stored.get("bedwars"));
        assertTrue(queued.isEmpty());
    }

    @Test
    public void queuedTransitionsCoalesceToTheNewestState() {
        var written = new ArrayList<GroupState>();
        var publisher = publisher((group, state) -> written.add(state));
        drain();

        publisher.publish("bedwars", GroupState.ASLEEP);
        publisher.publish("bedwars", GroupState.STARTING);
        publisher.publish("bedwars", GroupState.AWAKE);
        assertEquals(1, queued.size());
        drain();

        assertEquals(java.util.List.of(GroupState.AWAKE), written);
    }

    @Test
    public void aNewTransitionDuringAFailedWriteIsNotLostOrReplacedByTheOldRetry() {
        var stored = new HashMap<String, GroupState>();
        var current = new GroupStatePublisher[1];
        current[0] = publisher((group, state) -> {
            if (state == GroupState.ASLEEP) {
                current[0].publish(group, GroupState.AWAKE);
                throw new IllegalStateException("database unavailable");
            }
            stored.put(group, state);
        });
        drain();

        current[0].publish("bedwars", GroupState.ASLEEP);
        drain();
        current[0].retry();
        drain();

        assertEquals(GroupState.AWAKE, stored.get("bedwars"));
    }

    private GroupStatePublisher publisher(BiConsumer<String, GroupState> write) {
        return new GroupStatePublisher(Set.of("bedwars"), write, () -> { }, queued::add,
                LoggerFactory.getLogger(GroupStatePublisherTest.class));
    }

    private void drain() {
        while (!queued.isEmpty()) {
            queued.remove().run();
        }
    }
}
