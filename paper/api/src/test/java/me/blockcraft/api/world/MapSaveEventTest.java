package me.blockcraft.api.world;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MapSaveEventTest {

    @Test
    public void failedPreparationStopsPublicationAndStillRestoresRuntimeState() {
        var event = new MapSaveEvent(null);
        var restored = new AtomicBoolean();
        var failure = new IllegalStateException("The seasonal journal could not be restored");
        event.afterSave(() -> restored.set(true));
        event.abort(failure);

        try {
            assertSame(failure, assertThrows(IllegalStateException.class, event::checkReady));
        } finally {
            event.complete();
        }
        assertTrue(restored.get());
    }

    @Test
    public void aFailedRestorationDoesNotSkipTheOthers() {
        var event = new MapSaveEvent(null);
        var restored = new AtomicBoolean();
        var failure = new IllegalStateException("Nexo refused a removal");
        event.afterSave(() -> {
            throw failure;
        });
        event.afterSave(() -> restored.set(true));

        assertEquals(List.of(failure), event.complete());
        assertTrue(restored.get());
    }
}
