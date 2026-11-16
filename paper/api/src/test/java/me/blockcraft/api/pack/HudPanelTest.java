package me.blockcraft.api.pack;

import me.blockcraft.api.pack.HudPanel.Corner;
import me.blockcraft.api.pack.HudPanel.Tint;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The client only shows a mismatch between the code and the pack's text shader as a garbled screen. */
public class HudPanelTest {

    private static final Path SHADER = Path.of("../../resourcepack/src/assets/blockcraft/shaders/include/hud_panel.glsl");

    @Test
    public void theShaderReadsEachCornerAndTintWhereThePanelWritesIt() throws IOException {
        var shader = Files.readString(SHADER);
        for (var corner : Corner.values()) {
            assertTrue(corner.name(), shader.contains("const int BLOCKCRAFT_" + corner.name() + "_RED = " + corner.red + ";"));
        }
        assertTrue(shader.contains("const int BLOCKCRAFT_PANEL_FIRST_TINT = " + HudPanel.FIRST_TINT + ";"));
        var table = shader.substring(shader.indexOf("BLOCKCRAFT_PANEL_TINTS[BLOCKCRAFT_PANEL_TINT_COUNT]"));
        table = table.substring(0, table.indexOf(");"));
        var named = Pattern.compile("// (\\w+)").matcher(table).results().map(result -> result.group(1)).toList();
        assertEquals(Arrays.stream(Tint.values()).map(Tint::name).toList(), named);
        assertTrue(shader.contains("const int BLOCKCRAFT_PANEL_TINT_COUNT = " + Tint.values().length + ";"));
        assertTrue(shader.contains("const float BLOCKCRAFT_BOTTOM_RIGHT_HEIGHT = " + HudPanel.BOTTOM_RIGHT_HEIGHT + ".0;"));
    }
}
