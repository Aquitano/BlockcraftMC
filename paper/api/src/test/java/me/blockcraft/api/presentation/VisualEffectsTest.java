package me.blockcraft.api.presentation;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class VisualEffectsTest {
    @Test
    public void boundsOperatorSettingsAndReplacesNonFiniteDistances() {
        var settings = VisualEffects.Settings.from(Map.of("effects", Map.of("duration-ticks", 99999,
                "distance", Double.NaN, "radius", -5, "spin-degrees", 90)));
        assertEquals(1200, settings.durationTicks());
        assertEquals(4, settings.distance(), 0.001);
        assertEquals(0.1, settings.radius(), 0.001);
        assertEquals(20, settings.spinDegrees(), 0.001);
    }

    @Test
    public void encodesMaterialModesWithoutChangingTheirShaderMarkerBytes() {
        assertEquals(0xE901C5, VisualEffects.MaterialEffect.HOLOGRAM.color().asRGB());
        assertEquals(0xE902C5, VisualEffects.MaterialEffect.PLASMA.color().asRGB());
        assertEquals(0xE903C5, VisualEffects.MaterialEffect.PULSE.color().asRGB());
    }
}
