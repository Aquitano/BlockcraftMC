package me.blockcraft.core.command;

import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class ReportReasonTest {

    private static final List<String> REASONS = List.of("Hacking", "Beleidigung");

    @Test
    public void typedReasonKeepsItsConfiguredSpellingAndAppendsDetails() {
        assertEquals(Optional.of("Hacking"), ReportCommand.reason("hacking", REASONS));
        assertEquals(Optional.of("Hacking - fliegt über die Map"), ReportCommand.reason(" HACKING  fliegt über die Map ", REASONS));
    }

    @Test
    public void unknownReasonIsRefusedUnlessNoneAreConfigured() {
        assertEquals(Optional.empty(), ReportCommand.reason("Spam bitte", REASONS));
        assertEquals(Optional.of("Spam bitte"), ReportCommand.reason("Spam bitte", List.of()));
        assertEquals(Optional.empty(), ReportCommand.reason(" ", List.of()));
    }
}
