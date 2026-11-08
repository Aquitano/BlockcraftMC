package me.blockcraft.sternenfront.game;

import me.blockcraft.api.pack.UiFont;
import me.blockcraft.common.message.Messages;
import me.blockcraft.sternenfront.PackFonts;
import me.blockcraft.sternenfront.combat.DamageDirection;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.*;

public class CombatFeedbackTest {

    @Test
    public void eliteAvailabilityPingsOnceAndExpiresWithoutExtendingOnEachHudUpdate() {
        var elite = new CombatFeedback.Announcement();
        assertFalse(elite.update(false, 0, 60));
        assertTrue(elite.update(true, 10, 60));
        assertFalse(elite.update(true, 20, 60));
        assertTrue(elite.showing(69));
        assertFalse(elite.showing(70));
        assertFalse(elite.update(false, 100, 60));
        assertTrue(elite.update(true, 200, 60));
        assertTrue(elite.showing(259));
    }

    @Test
    public void lowerBarKeepsTheKillLabelAndCountAlongsideTheCombatHud() {
        var messages = Messages.load(Path.of("/nonexistent-combat-feedback-test"),
                () -> getClass().getResourceAsStream("/messages.yml"),
                Placeholder.parsed("error", "<red>"), Placeholder.parsed("success", "<green>"));
        var state = new CombatFeedback.PlayerFeedback();
        state.earned(CombatFeedback.Reward.KILL, 25, 0, 30);
        assertEquals("Abschuss +25 KP", plain(state.reward(messages)));
        state.earned(CombatFeedback.Reward.KILL, 25, 1, 30);
        assertEquals("2x +50 KP", plain(state.reward(messages)));
    }

    /** The client starts a line half its width left of the middle, where the shader expects the abilities panel. */
    @Test
    public void theAbilitiesPanelStartsInTheMiddleOfTheLineHoweverLongTheFeedback() throws IOException {
        var meter = Component.text("Deckung");
        var abilities = Component.text("\ue000").font(Key.key("blockcraft", "abilities"));
        var fonts = Map.of(UiFont.KEY, PackFonts.advances("ui"), UiFont.TEXT, PackFonts.advances("text"));
        var state = new CombatFeedback.PlayerFeedback();
        state.actionBar = new CombatFeedback.ActionBar(meter, UiFont.width(meter), abilities);
        for (var feedback : List.of(Component.empty(), Component.text("+25 KP"),
                Component.text("Kopfschuss Abschuss Verteidigt Abschuss +25 KP"))) {
            var parts = state.compose(feedback, Optional.empty()).children();
            assertEquals(abilities, parts.get(1));
            int rest = PackFonts.width(Component.empty().append(parts.get(2)).append(parts.get(3)), fonts) + PackFonts.width(parts.get(0), fonts);
            assertEquals(feedback.toString(), rest / 2, PackFonts.width(parts.get(0), fonts));
            assertTrue(plain(parts.get(3)).contains(plain(feedback) + " Deckung"));
        }
    }

    @Test
    public void activeCourseReplacesTheHudWithItsTimer() {
        var state = new CombatFeedback.PlayerFeedback();
        var timer = Optional.<Component>of(Component.text("Kraninspektion 2,00 s · Tor 2/4"));
        state.actionBar = new CombatFeedback.ActionBar(Component.text("Deckung"), 28, Component.text("Tiles"));

        assertTrue(plain(state.compose(Component.empty(), timer)).endsWith(" Kraninspektion 2,00 s · Tor 2/4"));
        assertTrue(plain(state.compose(Component.text("+25 KP"), timer)).endsWith("+25 KP Kraninspektion 2,00 s · Tor 2/4"));
        assertFalse(plain(state.compose(Component.empty(), timer)).contains("Tiles"));
        assertTrue(plain(state.compose(Component.empty(), Optional.empty())).contains(" Deckung"));
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
    public void crosshairRedrawsWhenTheMarkerOrTheDamageChanges() {
        var state = new CombatFeedback.PlayerFeedback();
        var left = Optional.of(DamageDirection.LEFT);
        assertTrue(state.crosshairChanged(0, left, 30, Optional.empty()));
        assertFalse(state.crosshairChanged(1, left, 30, Optional.empty()));
        assertTrue("a new hit from the same side extends the arc", state.crosshairChanged(2, left, 32, Optional.empty()));
        state.hit(CombatFeedback.HitMarker.HIT, 3, 6);
        assertTrue(state.crosshairChanged(3, left, 32, Optional.empty()));
        assertFalse(state.crosshairChanged(4, left, 32, Optional.empty()));
        assertTrue(state.crosshairChanged(9, left, 32, Optional.empty()));
        assertTrue(state.crosshairChanged(10, Optional.of(DamageDirection.BACK), 32, Optional.empty()));
        assertTrue(state.crosshairChanged(11, Optional.empty(), 0, Optional.empty()));
        assertFalse(state.crosshairChanged(12, Optional.empty(), 0, Optional.empty()));
    }

    @Test
    public void crosshairRedrawsWhenTheSpreadChangesSizeAndBeforeItsTitleRunsOut() {
        var state = new CombatFeedback.PlayerFeedback();
        assertTrue(state.crosshairChanged(0, Optional.empty(), 0, Optional.of(0)));
        assertFalse(state.crosshairChanged(1, Optional.empty(), 0, Optional.of(0)));
        assertTrue(state.crosshairChanged(2, Optional.empty(), 0, Optional.of(3)));
        assertFalse(state.crosshairChanged(21, Optional.empty(), 0, Optional.of(3)));
        assertTrue(state.crosshairChanged(22, Optional.empty(), 0, Optional.of(3)));
        assertTrue(state.crosshairChanged(23, Optional.empty(), 0, Optional.empty()));
        assertFalse(state.crosshairChanged(60, Optional.empty(), 0, Optional.empty()));
    }

    @Test
    public void killAndHeadshotFeedbackSurviveWeakerHitsAndBurstSoundsDoNotStack() {
        var state = new CombatFeedback.PlayerFeedback();
        assertTrue(state.hit(CombatFeedback.HitMarker.HEADSHOT, 0, 6));
        assertEquals(CombatFeedback.HitMarker.HEADSHOT, state.takeMarker(0).orElseThrow());
        assertFalse(state.hit(CombatFeedback.HitMarker.HIT, 0, 6));
        assertTrue(state.takeMarker(0).isEmpty());
        assertEquals(CombatFeedback.HitMarker.HEADSHOT, state.marker);
        assertTrue(state.hit(CombatFeedback.HitMarker.KILL, 0, 6));
        assertEquals(CombatFeedback.HitMarker.KILL, state.takeMarker(0).orElseThrow());
        state.hit(CombatFeedback.HitMarker.HIT, 1, 6);
        assertEquals(CombatFeedback.HitMarker.KILL, state.marker);
        assertTrue(state.takeMarker(1).isEmpty());
        state.hit(CombatFeedback.HitMarker.HIT, 6, 6);
        assertEquals(CombatFeedback.HitMarker.HIT, state.marker);
        assertEquals(CombatFeedback.HitMarker.HIT, state.takeMarker(6).orElseThrow());
        assertTrue(state.takeMarker(12).isEmpty());
    }

    @Test
    public void nearbyPointGainsCombineWithoutReplacingAKillWithASupportLabel() {
        var state = new CombatFeedback.PlayerFeedback();
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

    @Test
    public void killsChainWithinTheWindowAndStartOverAfterIt() {
        var state = new CombatFeedback.PlayerFeedback();
        assertEquals(1, state.chained(0, 80));
        assertEquals(2, state.chained(79, 80));
        assertEquals(1, state.chained(159, 80));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
