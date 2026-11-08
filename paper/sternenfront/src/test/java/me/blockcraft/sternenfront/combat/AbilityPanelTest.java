package me.blockcraft.sternenfront.combat;

import me.blockcraft.sternenfront.PackFonts;
import me.blockcraft.sternenfront.combat.AbilityPanel.Binding;
import me.blockcraft.sternenfront.combat.AbilityPanel.Charges;
import me.blockcraft.sternenfront.combat.AbilityPanel.Slot;
import me.blockcraft.sternenfront.combat.AbilityPanel.State;
import me.blockcraft.sternenfront.game.Ability;
import me.blockcraft.sternenfront.game.AbilityIcon;
import me.blockcraft.sternenfront.game.HudPanel.Tint;
import me.blockcraft.sternenfront.hero.HeroAbility;
import net.kyori.adventure.key.Key;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class AbilityPanelTest {

    /**
     * The client centres the action bar on its width and the shader expects the panel to start in the line's middle, so
     * the panel must take no room. Every glyph lies left of the corner and inside the column the shader makes room for.
     */
    @Test
    public void everyStateOfEveryTileTakesNoRoomAndStaysInTheCorner() throws IOException {
        var states = List.of(new State.Ready(), new State.Cooling(1, 0), new State.Cooling(99, 0.5), new State.Cooling(7, 1),
                new State.Armed());
        var charges = List.of(Charges.ONE, new Charges(2, 2), new Charges(0, 3), new Charges(12, 12));
        var bindings = Binding.values();
        var slots = new ArrayList<Slot>();
        for (var icon : AbilityIcon.values()) {
            slots.add(new Slot(bindings[icon.ordinal() % bindings.length], icon, "Belagerungsmodus Ä", states.get(icon.ordinal() % states.size()),
                    charges.get(icon.ordinal() % charges.size())));
        }

        var abilities = PackFonts.advances("abilities");
        var fonts = Map.of(Key.key("blockcraft", "ui"), PackFonts.advances("ui"), AbilityPanel.FONT, abilities);
        var heights = new HashMap<Integer, Integer>();
        for (var provider : PackFonts.providers("abilities")) {
            var json = provider.getAsJsonObject();
            if (json.has("chars")) {
                json.getAsJsonArray("chars").forEach(row -> row.getAsString().codePoints()
                        .forEach(codepoint -> heights.put(codepoint, json.get("height").getAsInt())));
            }
        }
        for (int first = 0; first < slots.size(); first += AbilityPanel.MAX_SLOTS) {
            var column = slots.subList(first, Math.min(slots.size(), first + AbilityPanel.MAX_SLOTS));
            for (var glyph : AbilityPanel.layout(column, Tint.REPUBLIC)) {
                int height = glyph.text().codePoints().map(codepoint -> heights.getOrDefault(codepoint, 0)).max().orElseThrow();
                assertTrue(glyph.toString(), glyph.x() < 0 && glyph.y() >= 0 && glyph.y() + height <= AbilityPanel.HEIGHT);
                assertEquals(glyph.toString(), glyph.advance(), glyph.text().codePoints().map(abilities::get).sum());
            }
            assertEquals(column.toString(), 0, PackFonts.width(AbilityPanel.render(column, Tint.REPUBLIC), fonts));
        }
        for (var provider : PackFonts.providers("abilities")) {
            var json = provider.getAsJsonObject();
            if (json.has("ascent")) {
                assertEquals(json.toString(), 7, json.get("ascent").getAsInt());
                assertTrue("the client drops a font with a glyph shorter than its ascent: " + json, json.get("height").getAsInt() >= 7);
            }
        }
    }

    /** The shader hangs the panel from its top, which must stand a full column above the corner. */
    @Test
    public void theShaderMakesRoomForAFullColumn() throws IOException {
        var shader = Files.readString(PackFonts.FONTS.resolveSibling("shaders/include/hud_panel.glsl"));
        assertTrue(shader.contains("const float BLOCKCRAFT_ABILITY_COLUMN = " + AbilityPanel.HEIGHT + ".0;"));
    }

    @Test
    public void eachRunOfGlyphsStartsWhereTheFontDoes() throws IOException {
        assertEquals(PackFonts.first("abilities", "ability_tile.png"), AbilityPanel.BACKING);
        assertEquals(PackFonts.first("abilities", "ability_frame.png"), AbilityPanel.FRAME);
        assertEquals(PackFonts.first("abilities", "ability_icons.png"), AbilityPanel.FIRST_ICON);
        assertEquals(PackFonts.first("abilities", "ability_icons_cooling.png"), AbilityPanel.FIRST_COOLING_ICON);
        assertEquals(PackFonts.first("abilities", "ability_keys.png"), AbilityPanel.FIRST_BADGE);
        assertEquals(PackFonts.first("abilities", "ability_fill.png"), AbilityPanel.FIRST_FILL);
        assertEquals(PackFonts.first("abilities", "ability_digits.png"), AbilityPanel.FIRST_DIGIT);
        assertEquals(PackFonts.first("abilities", "ability_count.png"), AbilityPanel.FIRST_COUNT);
    }

    @Test
    public void aTileCountsItsStoredUsesOnlyWhileItHoldsSeveralAndHasOneLeft() {
        var counted = List.of(new Charges(2, 2), new Charges(1, 2), new Charges(12, 12));
        for (var charges : counted) {
            var glyphs = AbilityPanel.layout(List.of(new Slot(Binding.F, AbilityIcon.GRENADE, "Granate", new State.Ready(), charges)), Tint.REPUBLIC);
            var count = glyphs.stream().map(glyph -> glyph.text().charAt(0)).filter(glyph -> glyph >= AbilityPanel.FIRST_COUNT
                    && glyph < AbilityPanel.FIRST_COUNT + 10).map(glyph -> String.valueOf(glyph - AbilityPanel.FIRST_COUNT)).toList();
            assertEquals(charges.toString(), String.valueOf(charges.stored()), String.join("", count));
        }
        for (var charges : List.of(Charges.ONE, new Charges(0, 2))) {
            var state = charges.stored() > 0 ? new State.Ready() : new State.Cooling(5, 0.5);
            var glyphs = AbilityPanel.layout(List.of(new Slot(Binding.F, AbilityIcon.GRENADE, "Granate", state, charges)), Tint.REPUBLIC);
            assertTrue(charges.toString(), glyphs.stream().map(glyph -> glyph.text().charAt(0))
                    .noneMatch(glyph -> glyph >= AbilityPanel.FIRST_COUNT && glyph < AbilityPanel.FIRST_COUNT + 10));
        }
    }

    @Test
    public void everyAbilityHasAPictureOfItsOwnInThePackReadyAndRecharging() throws IOException {
        var icons = Stream.concat(Arrays.stream(Ability.values()).map(Ability::icon), Arrays.stream(HeroAbility.values()).map(HeroAbility::icon)).toList();
        assertEquals(icons.size(), icons.stream().distinct().count());
        var drawn = PackFonts.providers("abilities").asList().stream().map(provider -> provider.getAsJsonObject())
                .filter(provider -> provider.has("file") && provider.get("file").getAsString().contains("ability_icons")).toList();
        assertEquals(2, drawn.size());
        drawn.forEach(provider -> assertEquals(AbilityIcon.values().length, provider.getAsJsonArray("chars").get(0).getAsString().length()));
    }
}
