package me.blockcraft.sternenfront.game;

import me.blockcraft.common.message.Messages;
import me.blockcraft.sternenfront.combat.DamageDirection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.nio.file.Path;
import java.util.Optional;

import org.junit.Test;

import static org.junit.Assert.*;

public class CombatFeedbackTest {

    @Test
    public void lowerBarKeepsTheKillLabelAndCountAlongsideTheCombatHud() {
        var messages = Messages.load(Path.of("/nonexistent-combat-feedback-test"),
                () -> getClass().getResourceAsStream("/messages.yml"),
                Placeholder.parsed("error", "<red>"), Placeholder.parsed("success", "<green>"));
        var state = new CombatFeedback.State();
        state.earned(CombatFeedback.Reward.KILL, 25, 0, 30);
        assertEquals("Abschuss +25 KP", plain(state.reward(messages)));
        state.earned(CombatFeedback.Reward.KILL, 25, 1, 30);
        assertEquals("2x +50 KP", plain(state.reward(messages)));
    }

    @Test
    public void feedbackKeepsTheLatestHudAndLeavesItInPlace() {
        var state = new CombatFeedback.State();
        var hud = Component.text("Deckung | F 8s | Q 2s");
        var updated = Component.text("Deckung | F 7s | Q 1s");
        var feedback = Component.text("+25 KP");

        assertTrue(plain(state.compose(hud, feedback)).endsWith("+25 KP Deckung | F 8s | Q 2s"));
        assertTrue(plain(state.compose(Component.empty(), feedback)).endsWith("+25 KP Deckung | F 8s | Q 2s"));
        state.compose(updated, feedback);
        assertTrue(plain(state.compose(Component.empty(), Component.empty())).endsWith(" Deckung | F 7s | Q 1s"));
    }

    @Test
    public void killNotificationResolvesPaletteTagsWithoutDisplayingMarkup() {
        var messages = Messages.load(Path.of("/nonexistent-combat-feedback-test"),
                () -> getClass().getResourceAsStream("/messages.yml"),
                Placeholder.parsed("error", "<red>"), Placeholder.parsed("success", "<green>"));
        var notification = messages.get("feedback.kill", Formatter.number("points", 25));
        assertEquals("Abschuss +25 KP", PlainTextComponentSerializer.plainText().serialize(notification));
    }

    @Test
    public void crosshairRedrawsWhenTheMarkerOrTheDamageDirectionChanges() {
        var state = new CombatFeedback.State();
        var left = Optional.of(DamageDirection.LEFT);
        assertTrue(state.crosshairChanged(0, left));
        assertFalse(state.crosshairChanged(1, left));
        state.hit(CombatFeedback.Marker.HIT, 2, 6);
        assertTrue(state.crosshairChanged(2, left));
        assertFalse(state.crosshairChanged(3, left));
        assertTrue(state.crosshairChanged(8, left));
        assertTrue(state.crosshairChanged(9, Optional.of(DamageDirection.BACK)));
        assertTrue(state.crosshairChanged(10, Optional.empty()));
        assertFalse(state.crosshairChanged(11, Optional.empty()));
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
        state.earned(CombatFeedback.Reward.KILL, 100, 0, 30);
        state.earned(CombatFeedback.Reward.DEFENCE, 25, 1, 30);
        assertEquals(125, state.points);
        assertEquals(CombatFeedback.Reward.KILL, state.reward);
        state.earned(CombatFeedback.Reward.KILL, 25, 2, 30);
        assertEquals(2, state.kills);
        assertEquals(150, state.points);
        state.earned(CombatFeedback.Reward.ASSIST, 40, 32, 30);
        assertEquals(CombatFeedback.Reward.ASSIST, state.reward);
        assertEquals(40, state.points);
        assertEquals(0, state.kills);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
