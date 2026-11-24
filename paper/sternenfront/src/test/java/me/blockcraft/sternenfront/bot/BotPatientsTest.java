package me.blockcraft.sternenfront.bot;

import org.junit.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BotPatientsTest {

    private final BotConfig.Medic config = new BotConfig.Medic(80, 100);
    private final UUID blocked = UUID.randomUUID();
    private final UUID nearby = UUID.randomUUID();

    @Test
    public void immediatelyHealableAllyReplacesRetainedPatientBehindAWall() {
        var patients = new BotPatients();
        var wounded = new BotPatients.Patient(blocked, 0.1, false);
        assertEquals(blocked, patients.choose(List.of(wounded), 0, config, id -> true).orElseThrow());
        assertEquals(nearby, patients.choose(List.of(wounded, new BotPatients.Patient(nearby, 0.7, true)),
                4, config, id -> { throw new AssertionError("Healable allies need no path search"); }).orElseThrow());
    }

    @Test
    public void failedPathsTryAnotherAllyAndSuppressTheFailedPatientUntilRetry() {
        var patients = new BotPatients();
        var candidates = List.of(new BotPatients.Patient(blocked, 0.1, false), new BotPatients.Patient(nearby, 0.7, false));
        var checks = new AtomicInteger();
        assertEquals(nearby, patients.choose(candidates, 0, config, id -> {
            checks.incrementAndGet();
            return id.equals(nearby);
        }).orElseThrow());
        assertEquals(2, checks.get());
        assertTrue(patients.choose(candidates.subList(0, 1), 20, config, id -> true).isEmpty());
        assertEquals(blocked, patients.choose(candidates.subList(0, 1), 100, config, id -> true).orElseThrow());
    }

    @Test
    public void pursuitExpiresDespiteAcceptedPathsAndDeploymentClearsExclusions() {
        var patients = new BotPatients();
        var candidates = List.of(new BotPatients.Patient(blocked, 0.1, false));
        assertEquals(blocked, patients.choose(candidates, 0, config, id -> true).orElseThrow());
        assertEquals(blocked, patients.choose(candidates, 79, config, id -> true).orElseThrow());
        assertTrue(patients.choose(candidates, 80, config, id -> true).isEmpty());
        assertTrue(patients.choose(candidates, 179, config, id -> true).isEmpty());
        patients.clear();
        assertEquals(blocked, patients.choose(candidates, 180, config, id -> true).orElseThrow());
        assertTrue(patients.choose(candidates, 260, config, id -> true).isEmpty());
        assertEquals(blocked, patients.choose(candidates, 360, config, id -> true).orElseThrow());
    }
}
