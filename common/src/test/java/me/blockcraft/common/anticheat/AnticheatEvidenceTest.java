package me.blockcraft.common.anticheat;

import org.junit.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class AnticheatEvidenceTest {

    @Test
    public void externalFieldsHaveBoundedPlainTextWithoutSplittingUnicode() {
        var evidence = new AnticheatEvidence(UUID.randomUUID(), "Account\nName", "s".repeat(100), Instant.now(),
                "c".repeat(100), null, "v".repeat(100), "x".repeat(1023) + "\uD83D\uDE00", Optional.empty());

        assertEquals("Account Name", evidence.name());
        assertEquals(64, evidence.server().length());
        assertEquals(64, evidence.check().length());
        assertEquals("", evidence.description());
        assertEquals(32, evidence.violations().length());
        assertEquals("x".repeat(1023), evidence.verbose());
        assertThrows(IllegalArgumentException.class, () -> new AnticheatEvidence.Replay(UUID.randomUUID(), -1));
    }
}
