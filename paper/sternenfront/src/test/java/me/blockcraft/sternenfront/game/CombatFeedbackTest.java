package me.blockcraft.sternenfront.game;

import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.nio.file.Path;
import org.junit.Test;

import static org.junit.Assert.*;

public class CombatFeedbackTest {

    @Test
    public void lowerBarKeepsTheKillLabelAndCountAlongsideTheCombatHud() {
        var messages = Messages.load(Path.of("/nonexistent-combat-feedback-test"),
                () -> getClass().getResourceAsStream("/messages.yml"),
                Placeholder.parsed("error", "<red>"), Placeholder.parsed("success", "<green>"));
        var state = new CombatFeedback.State();
        state.earned(CombatFeedback.Reward.KILL, Component.text("Droide"), 25, 0, 30);
        assertEquals("1 Abschuss +25 KP", plain(state.reward(messages, false)));
        state.earned(CombatFeedback.Reward.KILL, Component.text("Droide"), 25, 1, 30);
        assertEquals("2 Abschüsse +50 KP", plain(state.reward(messages, false)));
    }

    @Test
    public void feedbackAndImmediateHitsRetainTheLatestHealthGuardDamageAndCooldowns() {
        var state = new CombatFeedback.State();
        var hud = Component.text("42 LP | Deckung | Schaden links | F 8s | Q 2s");
        var updated = Component.text("39 LP | Deckung | Schaden hinten | F 7s | Q 1s");
        var feedback = Component.text("+25 KP");
        var fallback = Component.text(" Hitze 100/600 KP");
        var separator = Component.text(" | ");

        assertEquals("42 LP | Deckung | Schaden links | F 8s | Q 2s | +25 KP",
                plain(state.compose(hud, feedback, fallback, separator)));
        assertEquals("42 LP | Deckung | Schaden links | F 8s | Q 2s | +25 KP",
                plain(state.compose(Component.empty(), feedback, fallback, separator)));
        state.compose(updated, feedback, fallback, separator);
        assertEquals(plain(updated), plain(state.compose(Component.empty(), Component.empty(), fallback, separator)));
    }

    @Test
    public void startupFeedbackUsesHeatAndProgressOnlyUntilTheFirstHudArrives() {
        var state = new CombatFeedback.State();
        var feedback = Component.text("Abschuss Droide +25 KP");
        var fallback = Component.text(" HEISS 100/600 KP");
        var separator = Component.text(" | ");
        assertEquals("Abschuss Droide +25 KP HEISS 100/600 KP",
                plain(state.compose(Component.empty(), feedback, fallback, separator)));
        assertEquals("40 LP | HEISS | +25 KP",
                plain(state.compose(Component.text("40 LP | HEISS"), Component.text("+25 KP"), fallback, separator)));
    }

    @Test
    public void killNotificationResolvesPaletteTagsWithoutDisplayingMarkup() {
        var messages = Messages.load(Path.of("/nonexistent-combat-feedback-test"),
                () -> getClass().getResourceAsStream("/messages.yml"),
                Placeholder.parsed("error", "<red>"), Placeholder.parsed("success", "<green>"));
        var notification = messages.get("feedback.kill", Placeholder.component("target", Component.text("Droide")),
                Formatter.number("points", 25));
        assertEquals("Abschuss Droide +25 KP", PlainTextComponentSerializer.plainText().serialize(notification));
    }

    @Test
    public void killAndHeadshotFeedbackSurviveWeakerHitsAndBurstSoundsDoNotStack() {
        var state = new CombatFeedback.State();
        assertTrue(state.hit(CombatFeedback.Marker.HEADSHOT, 0, 6));
        assertEquals(CombatFeedback.Marker.HEADSHOT, state.takeMarker(0).orElseThrow());
        assertFalse(state.hit(CombatFeedback.Marker.HIT, 0, 6));
        assertTrue(state.takeMarker(0).isEmpty());
        assertEquals(CombatFeedback.Marker.HEADSHOT, state.marker);
        assertTrue(state.hit(CombatFeedback.Marker.KILL, 0, 6));
        assertEquals(CombatFeedback.Marker.KILL, state.takeMarker(0).orElseThrow());
        state.hit(CombatFeedback.Marker.HIT, 1, 6);
        assertEquals(CombatFeedback.Marker.KILL, state.marker);
        assertTrue(state.takeMarker(1).isEmpty());
        state.hit(CombatFeedback.Marker.HIT, 6, 6);
        assertEquals(CombatFeedback.Marker.HIT, state.marker);
        assertEquals(CombatFeedback.Marker.HIT, state.takeMarker(6).orElseThrow());
        assertTrue(state.takeMarker(12).isEmpty());
    }

    @Test
    public void nearbyPointGainsCombineWithoutReplacingAKillWithASupportLabel() {
        var state = new CombatFeedback.State();
        var target = Component.text("Droid");
        state.earned(CombatFeedback.Reward.KILL, target, 100, 0, 30);
        state.earned(CombatFeedback.Reward.DEFENCE, Component.empty(), 25, 1, 30);
        assertEquals(125, state.points);
        assertEquals(CombatFeedback.Reward.KILL, state.reward);
        assertEquals(target, state.target);
        state.earned(CombatFeedback.Reward.KILL, target, 25, 2, 30);
        assertEquals(2, state.kills);
        assertEquals(150, state.points);
        state.earned(CombatFeedback.Reward.ASSIST, Component.empty(), 40, 32, 30);
        assertEquals(CombatFeedback.Reward.ASSIST, state.reward);
        assertEquals(40, state.points);
        assertEquals(0, state.kills);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
