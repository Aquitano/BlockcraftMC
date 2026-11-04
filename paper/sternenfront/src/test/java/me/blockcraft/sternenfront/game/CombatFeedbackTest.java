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
        assertFalse(state.hit(CombatFeedback.Marker.HIT, 0, 6));
        assertEquals(CombatFeedback.Marker.HEADSHOT, state.marker);
        assertTrue(state.hit(CombatFeedback.Marker.KILL, 0, 6));
        state.hit(CombatFeedback.Marker.HIT, 1, 6);
        assertEquals(CombatFeedback.Marker.KILL, state.marker);
        state.hit(CombatFeedback.Marker.HIT, 6, 6);
        assertEquals(CombatFeedback.Marker.HIT, state.marker);
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
}
