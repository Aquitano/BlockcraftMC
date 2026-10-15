package me.blockcraft.duell.game;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class RematchTest {

    private static final UUID LISA = UUID.randomUUID();
    private static final UUID KEVOS = UUID.randomUUID();
    private static final Instant END = Instant.parse("2026-10-12T18:00:00Z");

    @Test
    public void bothAskingWithinTheWindowAgreesOnce() {
        var requests = new Rematch.Requests(Duration.ofSeconds(15));

        assertEquals(Rematch.Answer.ASKED, requests.ask(KEVOS, LISA, END));
        assertEquals(Rematch.Answer.WAITING, requests.ask(KEVOS, LISA, END.plusSeconds(2)));
        assertEquals(Rematch.Answer.AGREED, requests.ask(LISA, KEVOS, END.plusSeconds(15)));
        assertEquals(Rematch.Answer.SETTLED, requests.ask(KEVOS, LISA, END.plusSeconds(16)));
    }

    @Test
    public void anAnswerAfterTheWindowIsANewRequest() {
        var requests = new Rematch.Requests(Duration.ofSeconds(15));

        requests.ask(KEVOS, LISA, END);

        assertEquals(Rematch.Answer.ASKED, requests.ask(LISA, KEVOS, END.plusSeconds(16)));
        assertEquals(Rematch.Answer.AGREED, requests.ask(KEVOS, LISA, END.plusSeconds(20)));
    }
}
