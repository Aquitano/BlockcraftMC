package me.blockcraft.system.network;

import me.blockcraft.common.network.GroupState;
import me.blockcraft.system.network.OnDemand.Effect;
import me.blockcraft.system.network.OnDemand.Observation;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OnDemandTest {

    private static final Observation UP = new Observation(true, false);
    private static final Observation EMPTY = new Observation(true, true);

    private final OnDemand onDemand = new OnDemand(new OnDemand.Settings(Set.of("bedwars"),
            Duration.ofMinutes(10), Duration.ofMinutes(2), 0));
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private Instant now = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    public void theFirstPollPublishesEveryGroupAsItFindsIt() {
        assertEquals(List.of(new Effect.Publish("bedwars", GroupState.ASLEEP)), onDemand.observe(Map.of(), now));
        assertEquals(List.of(), onDemand.observe(Map.of(), now));
    }

    @Test
    public void aClickWakesASleepingGroupAndEveryoneWaitingGetsInOnceItRuns() {
        onDemand.observe(Map.of(), now);

        var woken = onDemand.request("bedwars", first, now).orElseThrow();
        var waiting = onDemand.request("bedwars", second, now).orElseThrow();

        assertEquals(OnDemand.Reply.STARTING, woken.reply());
        assertEquals(List.of(new Effect.Publish("bedwars", GroupState.STARTING), new Effect.Start("bedwars")), woken.effects());
        assertEquals(OnDemand.Reply.WAITING, waiting.reply());
        assertEquals(List.of(), waiting.effects());
        assertEquals(List.of(new Effect.Publish("bedwars", GroupState.AWAKE), new Effect.Admit("bedwars", List.of(first, second))),
                onDemand.observe(Map.of("bedwars", UP), later(30)));
    }

    @Test
    public void choosingAnotherGameReplacesTheEarlierWait() {
        var games = new OnDemand(new OnDemand.Settings(Set.of("bedwars", "ttt"),
                Duration.ofMinutes(10), Duration.ofMinutes(2), 0));
        games.request("bedwars", first, now);
        games.request("ttt", first, now);

        var admissions = games.observe(Map.of("bedwars", UP, "ttt", UP), later(30)).stream()
                .filter(Effect.Admit.class::isInstance).toList();

        assertEquals(List.of(new Effect.Admit("ttt", List.of(first))), admissions);
    }

    @Test
    public void choosingAnotherRematchReplacesTheEarlierWait() {
        var games = new OnDemand(new OnDemand.Settings(Set.of("bedwars", "ttt"),
                Duration.ofMinutes(10), Duration.ofMinutes(2), 0));
        games.observe(Map.of("bedwars", UP, "ttt", UP), now);
        games.rejoin("bedwars", first, now);
        games.rejoin("ttt", first, now);

        var admissions = games.observe(Map.of("bedwars", EMPTY, "ttt", EMPTY), later(30)).stream()
                .filter(Effect.Admit.class::isInstance).toList();

        assertEquals(List.of(new Effect.Admit("ttt", List.of(first))), admissions);
    }

    @Test
    public void cancellingAWaitLeavesTheOtherPlayersQueued() {
        onDemand.request("bedwars", first, now);
        onDemand.request("bedwars", second, now);

        onDemand.cancel(first);

        assertEquals(List.of(new Effect.Publish("bedwars", GroupState.AWAKE), new Effect.Admit("bedwars", List.of(second))),
                onDemand.observe(Map.of("bedwars", UP), later(30)));
    }

    @Test
    public void cancellingAfterThePollPreventsAdmissionWithoutCancellingOtherPlayers() {
        onDemand.request("bedwars", first, now);
        onDemand.request("bedwars", second, now);
        var admission = admission(onDemand.observe(Map.of("bedwars", UP), later(30)));
        var sent = new ArrayList<UUID>();

        onDemand.cancel(first);
        admission.players().forEach(player -> onDemand.admit(admission, player, () -> sent.add(player)));

        assertEquals(List.of(second), sent);
    }

    @Test
    public void choosingAnotherGameInvalidatesAnAdmissionThePollAlreadyReturned() {
        var games = new OnDemand(new OnDemand.Settings(Set.of("bedwars", "ttt"),
                Duration.ofMinutes(10), Duration.ofMinutes(2), 0));
        games.request("bedwars", first, now);
        var old = admission(games.observe(Map.of("bedwars", UP), later(30)));
        var sent = new ArrayList<String>();

        games.request("ttt", first, now);
        games.admit(old, first, () -> sent.add(old.group()));
        var current = admission(games.observe(Map.of("bedwars", UP, "ttt", UP), later(30)));
        games.admit(current, first, () -> sent.add(current.group()));

        assertEquals(List.of("ttt"), sent);
    }

    @Test
    public void anOldAdmissionCannotClaimANewWaitForTheSameGroup() {
        onDemand.request("bedwars", first, now);
        var old = admission(onDemand.observe(Map.of("bedwars", UP), later(30)));
        onDemand.cancel(first);
        onDemand.rejoin("bedwars", first, now);
        var current = admission(onDemand.observe(Map.of("bedwars", EMPTY), later(1)));
        var sent = new ArrayList<String>();

        assertEquals(old, current);
        onDemand.admit(old, first, () -> sent.add("old"));
        onDemand.admit(current, first, () -> sent.add("current"));
        onDemand.admit(current, first, () -> sent.add("duplicate"));

        assertEquals(List.of("current"), sent);
    }

    @Test
    public void aCancelledRematchDoesNotLeaveItsDeadlineForTheNextPlayer() {
        onDemand.observe(Map.of("bedwars", UP), now);
        onDemand.rejoin("bedwars", first, now);
        onDemand.cancel(first);
        onDemand.rejoin("bedwars", second, later(90));

        assertEquals(List.of(), onDemand.observe(Map.of("bedwars", UP), later(30)));
        assertEquals(List.of(new Effect.Fail("bedwars", List.of(second))), onDemand.observe(Map.of("bedwars", UP), later(90)));
    }

    @Test
    public void whoeverWaitsPastTheTimeoutHearsItFailed() {
        onDemand.observe(Map.of(), now);
        onDemand.request("bedwars", first, now);

        assertEquals(List.of(), onDemand.observe(Map.of(), later(60)));
        assertEquals(List.of(new Effect.Publish("bedwars", GroupState.ASLEEP), new Effect.Fail("bedwars", List.of(first))),
                onDemand.observe(Map.of(), later(60)));
    }

    @Test
    public void onlyAnUnbrokenIdleSpellStopsTheServer() {
        onDemand.observe(Map.of("bedwars", EMPTY), now);
        onDemand.observe(Map.of("bedwars", EMPTY), now);

        assertEquals(List.of(), onDemand.observe(Map.of("bedwars", EMPTY), later(9 * 60)));
        assertEquals(List.of(), onDemand.observe(Map.of("bedwars", UP), later(30)));
        assertEquals(List.of(), onDemand.observe(Map.of("bedwars", EMPTY), later(30)));
        assertEquals(List.of(new Effect.Publish("bedwars", GroupState.DRAINING), new Effect.Stop("bedwars")),
                onDemand.observe(Map.of("bedwars", EMPTY), later(10 * 60)));
    }

    @Test
    public void aClickWhileDrainingStartsTheServerAgainOnceItHasStopped() {
        drained();

        assertEquals(OnDemand.Reply.WAITING, onDemand.request("bedwars", first, now).orElseThrow().reply());
        assertEquals(List.of(new Effect.Publish("bedwars", GroupState.ASLEEP), new Effect.Publish("bedwars", GroupState.STARTING),
                new Effect.Start("bedwars")), onDemand.stopped("bedwars", true, now));
    }

    @Test
    public void aDrainingServerThatLeftTheDirectoryWaitsForItsStopToReturn() {
        drained();
        onDemand.request("bedwars", first, now);

        assertEquals(List.of(), onDemand.observe(Map.of(), later(5)));
    }

    @Test
    public void aFailedStopKeepsTheGroupAwakeForWhoeverWaited() {
        drained();
        onDemand.request("bedwars", first, now);

        assertEquals(List.of(new Effect.Publish("bedwars", GroupState.AWAKE), new Effect.Admit("bedwars", List.of(first))),
                onDemand.stopped("bedwars", false, now));
    }

    @Test
    public void aServerRestartingAfterItsRoundShowsAsStartingAndIsStartedOnceSomeoneWaits() {
        onDemand.observe(Map.of("bedwars", UP), now);

        assertEquals(List.of(new Effect.Publish("bedwars", GroupState.STARTING)), onDemand.observe(Map.of(), later(5)));
        assertEquals(List.of(new Effect.Start("bedwars")), onDemand.request("bedwars", first, now).orElseThrow().effects());
        assertEquals(List.of(), onDemand.request("bedwars", second, now).orElseThrow().effects());
    }

    @Test
    public void aRematchOnARunningGroupWaitsForItsServerToComeBackFromTheRestart() {
        onDemand.observe(Map.of("bedwars", UP), now);

        assertEquals(OnDemand.Reply.WAITING, onDemand.rejoin("bedwars", first, now).orElseThrow().reply());
        onDemand.rejoin("bedwars", second, now);
        onDemand.observe(Map.of(), later(5));

        assertEquals(List.of(new Effect.Publish("bedwars", GroupState.AWAKE), new Effect.Admit("bedwars", List.of(first, second))),
                onDemand.observe(Map.of("bedwars", UP), later(20)));
    }

    @Test
    public void aRematchIsAdmittedOnceTheGroupHasRoomEvenIfThePollMissedTheRestart() {
        onDemand.observe(Map.of("bedwars", UP), now);
        onDemand.rejoin("bedwars", first, now);

        assertEquals(List.of(new Effect.Admit("bedwars", List.of(first))), onDemand.observe(Map.of("bedwars", EMPTY), later(30)));
    }

    @Test
    public void aRematchWhoseServerNeverComesBackHearsItFailed() {
        onDemand.observe(Map.of("bedwars", UP), now);
        onDemand.rejoin("bedwars", first, now);

        assertEquals(List.of(), onDemand.observe(Map.of("bedwars", UP), later(60)));
        assertEquals(List.of(new Effect.Fail("bedwars", List.of(first))), onDemand.observe(Map.of("bedwars", UP), later(60)));
    }

    @Test
    public void aRunningGroupIsLeftToTheUsualMessages() {
        onDemand.observe(Map.of("bedwars", UP), now);

        assertEquals(Optional.empty(), onDemand.request("bedwars", first, now));
        assertEquals(Optional.empty(), onDemand.request("lobby", first, now));
    }

    @Test
    public void theCapKeepsAnotherGroupAsleep() {
        var capped = new OnDemand(new OnDemand.Settings(Set.of("bedwars", "ttt"), Duration.ofMinutes(10), Duration.ofMinutes(2), 1));
        capped.observe(Map.of("bedwars", UP), now);

        var outcome = capped.request("ttt", first, now).orElseThrow();

        assertEquals(OnDemand.Reply.BUSY, outcome.reply());
        assertTrue(outcome.effects().isEmpty());
    }

    /** Woken by the first poll, idle from the second, drained ten minutes later. */
    private void drained() {
        onDemand.observe(Map.of("bedwars", EMPTY), now);
        onDemand.observe(Map.of("bedwars", EMPTY), now);
        onDemand.observe(Map.of("bedwars", EMPTY), later(10 * 60));
    }

    private Instant later(long seconds) {
        now = now.plusSeconds(seconds);
        return now;
    }

    private static Effect.Admit admission(List<Effect> effects) {
        return effects.stream().filter(Effect.Admit.class::isInstance).map(Effect.Admit.class::cast).findFirst().orElseThrow();
    }
}
