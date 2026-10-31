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

    @Test
    public void persistentItemsOnlyRequestTheirOwnCompletedSeries() {
        var previous = new Rematch(null, null, Duration.ofSeconds(15), (player, opponent) -> true, (player, opponent) -> { });
        var current = new Rematch(null, null, Duration.ofSeconds(15), (player, opponent) -> true, (player, opponent) -> { });
        var lookups = new java.util.concurrent.atomic.AtomicInteger();
        var player = (org.bukkit.entity.Player) java.lang.reflect.Proxy.newProxyInstance(
                org.bukkit.entity.Player.class.getClassLoader(), new Class<?>[]{org.bukkit.entity.Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) {
                        lookups.incrementAndGet();
                        return LISA;
                    }
                    throw new AssertionError(method.getName());
                });
        current.request(player, previous.identity());
        current.request(player, null);
        assertEquals(0, lookups.get());
        current.request(player, current.identity());
        assertEquals(1, lookups.get());
    }
}
