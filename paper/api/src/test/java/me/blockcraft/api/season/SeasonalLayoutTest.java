package me.blockcraft.api.season;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SeasonalLayoutTest {

    @Test
    public void aPondSurfaceDoesNotIncludeTheWaterfallBelowOrAnotherChunk() {
        var surface = new SeasonalLayout.Box(new SeasonalLayout.Point(-967, 27, 1635),
                new SeasonalLayout.Point(-952, 27, 1647));

        assertTrue(surface.contains(new SeasonalLayout.Point(-967, 27, 1635)));
        assertTrue(surface.contains(new SeasonalLayout.Point(-952, 27, 1647)));
        assertFalse(surface.contains(new SeasonalLayout.Point(-960, 26, 1640)));
        assertFalse(surface.contains(new SeasonalLayout.Point(-951, 27, 1640)));
        assertTrue(surface.intersects(-61, 102));
        assertTrue(surface.intersects(-60, 102));
        assertFalse(surface.intersects(-59, 102));
    }
}
