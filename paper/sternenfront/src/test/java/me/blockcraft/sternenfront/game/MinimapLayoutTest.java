package me.blockcraft.sternenfront.game;

import me.blockcraft.sternenfront.PackFonts;
import me.blockcraft.sternenfront.game.HudPanel.Glyph;
import me.blockcraft.sternenfront.game.HudPanel.Tint;
import me.blockcraft.sternenfront.game.MinimapLayout.Mark;
import me.blockcraft.sternenfront.game.MinimapLayout.Member;
import me.blockcraft.sternenfront.game.MinimapLayout.Pixel;
import me.blockcraft.sternenfront.game.MinimapLayout.Role;
import me.blockcraft.sternenfront.game.MinimapLayout.View;
import net.kyori.adventure.key.Key;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.*;

public class MinimapLayoutTest {

    @Test
    public void northIsUpAndEastIsRightAroundThePlayer() {
        var view = new View(100, 200, 48);
        assertEquals(Optional.of(new Pixel(MinimapLayout.MIDDLE, MinimapLayout.MIDDLE - 18)), view.inside(100, 168));
        assertEquals(Optional.of(new Pixel(MinimapLayout.MIDDLE + 18, MinimapLayout.MIDDLE)), view.inside(132, 200));
        assertEquals(Optional.empty(), view.inside(100, 249));
    }

    @Test
    public void aPostBeyondTheRadiusKeepsItsBearingOnTheRim() {
        var view = new View(0, 0, 48);
        assertEquals(new Pixel(MinimapLayout.MIDDLE, MinimapLayout.MIDDLE + MinimapLayout.BADGE_REACH), view.clamped(0, 500, MinimapLayout.BADGE_REACH));
        var corner = view.clamped(-300, -300, MinimapLayout.BADGE_REACH);
        assertEquals(corner.x(), corner.y());
        assertEquals(MinimapLayout.BADGE_REACH, Math.hypot(corner.x() - MinimapLayout.MIDDLE, corner.y() - MinimapLayout.MIDDLE), 1);
    }

    @Test
    public void theArrowPointsWhereThePlayerLooks() {
        assertEquals(0, MinimapLayout.heading(180));
        assertEquals(4, MinimapLayout.heading(-90));
        assertEquals(8, MinimapLayout.heading(0));
        assertEquals(12, MinimapLayout.heading(90));
        assertEquals(0, MinimapLayout.heading(-175));
        assertEquals(1, MinimapLayout.heading(-157.5f));
    }

    @Test
    public void namesUseTheTileCapitalsAndFitUnderTheMap() {
        assertEquals("ITS_AQUITANO", MinimapLayout.label("Its_Aquitano"));
        assertEquals("CT-12", MinimapLayout.label("CT-12"));
        assertEquals("?UKASZ-LANGER…", MinimapLayout.label("Łukasz-Langername"));
    }

    @Test
    public void deadOrRedeployingMembersAreDimmed() {
        var line = MinimapLayout.member(1, Role.MEDIC, Tint.SQUAD, "CT-4", false);
        assertTrue(line.stream().allMatch(glyph -> glyph.tint() == Tint.DIMMED));
        assertEquals(Tint.SQUAD, MinimapLayout.member(1, Role.MEDIC, Tint.SQUAD, "CT-4", true).getFirst().tint());
    }

    /**
     * The client centres a boss bar line on its width and the shader expects the line to start in the screen's middle,
     * so a full panel must take no room. Its rows must fit a colour byte, and the shader finds a glyph's line only while
     * it and its shadow stay within 17 pixels of the line's top.
     */
    @Test
    public void aFullPanelTakesNoRoomAndEveryGlyphHangsFromItsLine() throws IOException {
        var glyphs = new ArrayList<>(MinimapLayout.disc());
        var view = new View(0, 0, 48);
        for (int post = 0; post < 5; post++) {
            double angle = post * Math.PI * 2 / 5;
            var rim = view.clamped(Math.sin(angle) * 900, -Math.cos(angle) * 900, MinimapLayout.BADGE_REACH);
            glyphs.add(MinimapLayout.badge(post, post % 2 == 0, rim, Tint.REPUBLIC));
            glyphs.add(MinimapLayout.target(rim));
        }
        for (var mark : Mark.values()) {
            glyphs.add(MinimapLayout.mark(mark, view.inside(0, -48).orElseThrow(), Tint.SQUAD));
            glyphs.add(MinimapLayout.mark(mark, view.inside(-48, 0).orElseThrow(), Tint.SEPARATISTS));
        }
        for (int heading = 0; heading < 16; heading++) {
            glyphs.add(MinimapLayout.arrow(heading * 22.5f));
        }
        var members = new ArrayList<Member>();
        for (var role : Role.values()) {
            members.add(new Member("Ein_sehr_langer_Name", role, Tint.SQUAD, role.ordinal() % 2 == 0));
        }
        members.add(new Member("ÄÖÜ-.:!?/+& 0123456789", Role.HERO, Tint.WHITE, true));
        glyphs.addAll(MinimapLayout.list(members));

        var fonts = Map.of(Key.key("blockcraft", "ui"), PackFonts.advances("ui"), MinimapLayout.FONT, PackFonts.advances("minimap"));
        assertEquals(0, PackFonts.width(MinimapLayout.render(glyphs), fonts));
        for (Glyph glyph : glyphs) {
            assertTrue(glyph.toString(), glyph.y() >= 0 && glyph.y() <= 255);
            glyph.text().codePoints().forEach(codepoint -> assertEquals(glyph.toString(),
                    (int) fonts.get(MinimapLayout.FONT).get(codepoint), glyph.advance() / glyph.text().codePointCount(0, glyph.text().length())));
        }
        for (var provider : PackFonts.providers("minimap")) {
            var json = provider.getAsJsonObject();
            if (json.has("ascent")) {
                assertEquals(json.toString(), 7, json.get("ascent").getAsInt());
                assertTrue(json.toString(), json.get("height").getAsInt() <= 16);
            }
        }
    }

    @Test
    public void theNamesReadTheMenuLabelsLettersInTheirOrder() throws IOException {
        var labels = PackFonts.providers("label_wide_0").get(1).getAsJsonObject();
        var minimap = PackFonts.providers("minimap");
        var ours = minimap.asList().stream().map(provider -> provider.getAsJsonObject())
                .filter(provider -> provider.has("file") && provider.get("file").getAsString().equals(labels.get("file").getAsString()))
                .findFirst().orElseThrow();
        assertEquals(labels.get("chars"), ours.get("chars"));
    }
}
