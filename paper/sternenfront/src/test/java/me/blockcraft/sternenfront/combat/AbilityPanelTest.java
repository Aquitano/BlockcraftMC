package me.blockcraft.sternenfront.combat;

import me.blockcraft.sternenfront.PackFonts;
import me.blockcraft.sternenfront.combat.AbilityPanel.Binding;
import me.blockcraft.sternenfront.combat.AbilityPanel.Slot;
import me.blockcraft.sternenfront.combat.AbilityPanel.State;
import me.blockcraft.sternenfront.game.Ability;
import me.blockcraft.sternenfront.game.AbilityIcon;
import me.blockcraft.sternenfront.hero.HeroAbility;
import net.kyori.adventure.key.Key;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class AbilityPanelTest {

    /**
     * The client centres the action bar on its width and the shader expects the panel to start in the line's middle, so
     * the panel must take no room. Every glyph lies left of the corner and below the panel's top, inside a colour byte.
     */
    @Test
    public void everyStateOfEveryTileTakesNoRoomAndStaysInTheCorner() throws IOException {
        var states = List.of(new State.Ready(), new State.Cooling(1), new State.Cooling(99), new State.Charged(2, 2),
                new State.Charged(1, 2), new State.Armed());
        var bindings = Binding.values();
        var slots = new ArrayList<Slot>();
        for (var icon : AbilityIcon.values()) {
            slots.add(new Slot(bindings[icon.ordinal() % bindings.length], Optional.of(icon), states.get(icon.ordinal() % states.size())));
        }
        slots.add(new Slot(Binding.Q, Optional.empty(), new State.Ready()));

        var fonts = Map.of(Key.key("blockcraft", "ui"), PackFonts.advances("ui"), AbilityPanel.FONT, PackFonts.advances("abilities"));
        for (int first = 0; first < slots.size(); first += 4) {
            var four = slots.subList(first, Math.min(slots.size(), first + 4));
            assertEquals(four.toString(), 0, PackFonts.width(AbilityPanel.render(four), fonts));
            for (var glyph : AbilityPanel.layout(four)) {
                assertTrue(glyph.toString(), glyph.x() < 0 && glyph.y() >= 0 && glyph.y() <= 255);
                int advance = glyph.text().codePoints().map(codepoint -> fonts.get(AbilityPanel.FONT).get(codepoint)).sum();
                assertEquals(glyph.toString(), advance, glyph.advance());
            }
        }
        for (var provider : PackFonts.providers("abilities")) {
            assertEquals(provider.toString(), 7, provider.getAsJsonObject().get("ascent").getAsInt());
        }
    }

    @Test
    public void everyAbilityHasAPictureOfItsOwnInThePack() throws IOException {
        var icons = Stream.concat(Arrays.stream(Ability.values()).map(Ability::icon), Arrays.stream(HeroAbility.values()).map(HeroAbility::icon)).toList();
        assertEquals(icons.size(), icons.stream().distinct().count());
        var drawn = PackFonts.providers("abilities").asList().stream().map(provider -> provider.getAsJsonObject())
                .filter(provider -> provider.get("file").getAsString().endsWith("ability_icons.png")).findFirst().orElseThrow();
        assertEquals(AbilityIcon.values().length, drawn.getAsJsonArray("chars").get(0).getAsString().length());
    }
}
