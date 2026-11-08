package me.blockcraft.sternenfront.game;

import me.blockcraft.sternenfront.PackFonts;
import me.blockcraft.sternenfront.game.HudPanel.Corner;
import me.blockcraft.sternenfront.game.HudPanel.Tint;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/** The client only shows a mismatch between the code, the pack's fonts and its text shader as a garbled screen. */
public class HudPanelTest {

    @Test
    public void theShaderReadsEachCornerAndTintWhereThePanelWritesIt() throws IOException {
        var shader = Files.readString(PackFonts.FONTS.resolveSibling("shaders/include/hud_panel.glsl"));
        for (var corner : Corner.values()) {
            assertTrue(corner.name(), shader.contains("const int BLOCKCRAFT_" + corner.name() + "_RED = " + corner.red + ";"));
        }
        assertTrue(shader.contains("const int BLOCKCRAFT_PANEL_FIRST_TINT = " + HudPanel.FIRST_TINT + ";"));
        var table = shader.substring(shader.indexOf("BLOCKCRAFT_PANEL_TINTS[BLOCKCRAFT_PANEL_TINT_COUNT]"));
        table = table.substring(0, table.indexOf(");"));
        var named = Pattern.compile("// (\\w+)").matcher(table).results().map(result -> result.group(1)).toList();
        assertEquals(Arrays.stream(Tint.values()).map(Tint::name).toList(), named);
        assertTrue(shader.contains("const int BLOCKCRAFT_PANEL_TINT_COUNT = " + Tint.values().length + ";"));
    }

    @Test
    public void theMinimapAndTheKillFeedStartEachRunOfGlyphsWhereTheirFontDoes() throws IOException {
        assertEquals(PackFonts.first("minimap", "minimap_disc.png"), MinimapLayout.DISC);
        assertEquals(PackFonts.first("minimap", "objective_pips.png"), MinimapLayout.BADGE);
        assertEquals(PackFonts.first("minimap", "minimap_marks.png"), MinimapLayout.ARROW);
        assertEquals(PackFonts.first("minimap", "minimap_target.png"), MinimapLayout.TARGET);
        assertEquals(PackFonts.first("minimap", "minimap_classes.png"), MinimapLayout.CLASS_MARK);
        assertEquals(PackFonts.first("minimap", "minimap_feed.png"), KillFeed.SKULL);
    }
}
