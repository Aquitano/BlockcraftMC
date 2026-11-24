package me.blockcraft.sternenfront.game;

import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

public class BattleRadioTest {
    @Test
    public void actWaitsForInfantryThenEachFactionHearsItsOwnCallWithoutOverlappingTheNext() {
        var fixture = new Fixture();
        var republic = fixture.listener(Faction.KOLONIEN);
        var separatists = fixture.listener(Faction.HEGEMONIE);
        var listeners = List.of(republic, separatists);
        fixture.radio.infantrySpoke(10);
        fixture.radio.act(BattleRadio.Event.ASSAULT, 1);
        fixture.radio.tick(59, listeners);
        assertTrue(fixture.played.isEmpty());
        assertFalse(fixture.radio.infantryReady(59));
        fixture.radio.tick(60, listeners);
        assertEquals(List.of("blockcraft:sternenfront.radio.kolonien.assault", "blockcraft:sternenfront.radio.hegemonie.assault"), fixture.played);
        assertEquals(List.of(1, 1), fixture.captions.stream().map(BattleRadio.Call::post).toList());
        fixture.radio.act(BattleRadio.Event.LAST_STAND, -1);
        fixture.radio.tick(139, listeners);
        assertEquals(2, fixture.played.size());
        fixture.radio.tick(140, listeners);
        assertEquals(4, fixture.played.size());
        assertEquals(2, fixture.stopped.size());
        assertFalse(fixture.radio.infantryReady(219));
        fixture.radio.tick(220, listeners);
        assertTrue(fixture.radio.infantryReady(220));
        assertEquals(fixture.played.stream().sorted().toList(), fixture.stopped.stream().sorted().toList());
    }

    @Test
    public void postReportsDuringFactionCooldownAreDroppedAndAnActReplacesPendingPostReports() {
        var fixture = new Fixture();
        var listeners = List.of(fixture.listener(Faction.KOLONIEN));
        fixture.radio.post(BattleRadio.Event.CAPTURED, Faction.KOLONIEN, 0, 0);
        fixture.radio.tick(1, listeners);
        fixture.radio.post(BattleRadio.Event.LOST, Faction.KOLONIEN, 0, 50);
        fixture.radio.post(BattleRadio.Event.CAPTURED, Faction.KOLONIEN, 1, 400);
        fixture.radio.tick(401, listeners);
        assertEquals(1, fixture.played.size());
        fixture.radio.post(BattleRadio.Event.LOST, Faction.KOLONIEN, 1, 401);
        fixture.radio.act(BattleRadio.Event.ASSAULT, 2);
        fixture.radio.tick(402, listeners);
        assertEquals(List.of(BattleRadio.Event.CAPTURED, BattleRadio.Event.ASSAULT),
                fixture.captions.stream().map(BattleRadio.Call::event).toList());
        fixture.radio.post(BattleRadio.Event.LOST, Faction.KOLONIEN, 1, 802);
        fixture.radio.tick(803, listeners);
        assertEquals(BattleRadio.Event.LOST, fixture.captions.getLast().event());
    }

    @Test
    public void capturingFactionHearsItsReportAfterTheOtherFactionsLoss() {
        var fixture = new Fixture();
        var listeners = List.of(fixture.listener(Faction.KOLONIEN), fixture.listener(Faction.HEGEMONIE));
        fixture.radio.post(BattleRadio.Event.LOST, Faction.HEGEMONIE, 0, 0);
        fixture.radio.tick(1, listeners);
        fixture.radio.post(BattleRadio.Event.CAPTURED, Faction.KOLONIEN, 0, 80);
        fixture.radio.tick(81, listeners);
        assertEquals(List.of(BattleRadio.Event.LOST, BattleRadio.Event.CAPTURED),
                fixture.captions.stream().map(BattleRadio.Call::event).toList());
        assertEquals(List.of(Faction.HEGEMONIE, Faction.KOLONIEN), fixture.captions.stream().map(BattleRadio.Call::faction).toList());
    }

    @Test
    public void latestPostReportWaitsForInfantryAndCaptionsDoNotNeedThePack() {
        var fixture = new Fixture();
        var listener = fixture.listener(Faction.KOLONIEN);
        var listeners = List.of(new BattleRadio.Listener(listener.player(), listener.faction(), false));
        fixture.radio.infantrySpoke(0);
        fixture.radio.post(BattleRadio.Event.CAPTURED, Faction.KOLONIEN, 0, 10);
        fixture.radio.post(BattleRadio.Event.LOST, Faction.KOLONIEN, 1, 20);
        fixture.radio.tick(49, listeners);
        assertTrue(fixture.captions.isEmpty());
        fixture.radio.tick(50, listeners);
        assertTrue(fixture.played.isEmpty());
        assertEquals(List.of(new BattleRadio.Call(BattleRadio.Event.LOST, Faction.KOLONIEN, 1)), fixture.captions);
    }

    @Test
    public void latestActReplacesAnUnspokenActAndUnavailableListenersDoNotLeaveAQueue() {
        var fixture = new Fixture();
        fixture.radio.infantrySpoke(0);
        fixture.radio.act(BattleRadio.Event.ASSAULT, 1);
        fixture.radio.act(BattleRadio.Event.LAST_STAND, -1);
        fixture.radio.tick(50, List.of(fixture.listener(Faction.HEGEMONIE)));
        assertEquals(List.of("blockcraft:sternenfront.radio.hegemonie.last-stand"), fixture.played);
        fixture.radio.tick(60, List.of());
        assertEquals(fixture.played.stream().sorted().toList(), fixture.stopped.stream().sorted().toList());
        fixture.radio.act(BattleRadio.Event.ASSAULT, 0);
        fixture.radio.tick(130, List.of());
        assertTrue(fixture.radio.infantryReady(130));
    }

    @Test
    public void speechCaptionsAndIndividualEventsCanBeDisabled() {
        var fixture = new Fixture();
        var listeners = List.of(fixture.listener(Faction.KOLONIEN));
        fixture.config.set("radio.speech", false);
        fixture.radio.act(BattleRadio.Event.ASSAULT, 2);
        fixture.radio.tick(0, listeners);
        assertTrue(fixture.played.isEmpty());
        assertEquals(1, fixture.captions.size());
        fixture.config.set("radio.speech", true);
        fixture.config.set("radio.captions", false);
        fixture.radio.act(BattleRadio.Event.LAST_STAND, -1);
        fixture.radio.tick(80, listeners);
        assertEquals(1, fixture.played.size());
        assertEquals(1, fixture.captions.size());
        fixture.config.set("radio.events.assault", false);
        fixture.radio.act(BattleRadio.Event.ASSAULT, 0);
        fixture.radio.tick(160, listeners);
        assertTrue(fixture.radio.infantryReady(160));
        fixture.config.set("radio.enabled", false);
        fixture.radio.act(BattleRadio.Event.LAST_STAND, -1);
        fixture.radio.tick(200, listeners);
        assertEquals(1, fixture.played.size());
        fixture.config.set("radio.enabled", true);
        fixture.config.set("radio.volume", Double.NaN);
        fixture.radio.act(BattleRadio.Event.LAST_STAND, -1);
        assertTrue(fixture.radio.infantryReady(200));
    }

    @Test
    public void leavingAndRoundCleanupStopOnlyOwnedSoundsAndDiscardPendingCalls() {
        var fixture = new Fixture();
        var listener = fixture.listener(Faction.KOLONIEN);
        fixture.radio.act(BattleRadio.Event.ASSAULT, 0);
        fixture.radio.tick(100, List.of(listener));
        fixture.radio.forget(listener.player());
        fixture.radio.forget(listener.player());
        assertEquals(fixture.played.stream().sorted().toList(), fixture.stopped.stream().sorted().toList());
        fixture.radio.act(BattleRadio.Event.LAST_STAND, -1);
        fixture.radio.close();
        fixture.radio.close();
        fixture.radio.tick(0, List.of(listener));
        assertTrue(fixture.radio.infantryReady(0));
        assertEquals(1, fixture.played.size());
        fixture.radio.act(BattleRadio.Event.ASSAULT, 1);
        fixture.radio.tick(0, List.of(listener));
        fixture.radio.close();
        assertEquals(fixture.played.stream().sorted().toList(), fixture.stopped.stream().sorted().toList());
    }

    private static final class Fixture {
        final YamlConfiguration config = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/config.yml").toFile());
        final List<String> played = new ArrayList<>();
        final List<String> stopped = new ArrayList<>();
        final List<BattleRadio.Call> captions = new ArrayList<>();
        final BattleRadio radio = new BattleRadio(fake(Plugin.class, (proxy, method, args) -> {
            throw new AssertionError(method.getName());
        }), config, (player, call) -> captions.add(call));

        BattleRadio.Listener listener(Faction faction) {
            var id = UUID.randomUUID();
            var player = fake(Player.class, (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "playSound" -> {
                    assertSame(proxy, args[0]);
                    assertEquals(SoundCategory.VOICE, args[2]);
                    played.add((String) args[1]);
                    yield null;
                }
                case "stopSound" -> {
                    assertEquals(SoundCategory.VOICE, args[1]);
                    stopped.add((String) args[0]);
                    yield null;
                }
                default -> throw new AssertionError(method.getName());
            });
            return new BattleRadio.Listener(player, faction, true);
        }
    }

    private static <T> T fake(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
