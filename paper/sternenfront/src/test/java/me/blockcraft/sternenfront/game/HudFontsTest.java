package me.blockcraft.sternenfront.game;

import me.blockcraft.api.pack.UiFont;
import me.blockcraft.sternenfront.PackFonts;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.*;

/** The client only shows a mismatch between the HUD panels' code and the pack's fonts as a garbled screen. */
public class HudFontsTest {

    @Test
    public void theMinimapAndTheKillFeedStartEachRunOfGlyphsWhereTheirFontDoes() throws IOException {
        assertEquals(PackFonts.first("minimap", "minimap_disc.png"), MinimapLayout.DISC);
        assertEquals(PackFonts.first("minimap", "objective_pips.png"), MinimapLayout.BADGE);
        assertEquals(PackFonts.first("minimap", "minimap_marks.png"), MinimapLayout.ARROW);
        assertEquals(PackFonts.first("minimap", "minimap_target.png"), MinimapLayout.TARGET);
        assertEquals(PackFonts.first("minimap", "minimap_classes.png"), MinimapLayout.CLASS_MARK);
        assertEquals(PackFonts.first("minimap", "minimap_feed.png"), KillFeed.SKULL);
    }

    @Test
    public void everyHudFontDrawsTheMenuLabelsLettersInTheirOrder() throws IOException {
        for (var font : new String[] {"label_wide_0", "minimap", "abilities"}) {
            var letters = PackFonts.providers(font).asList().stream().map(provider -> provider.getAsJsonObject())
                    .filter(provider -> provider.has("file") && provider.get("file").getAsString().matches(".*(labels|_letters)\\.png"))
                    .findFirst().orElseThrow();
            assertEquals(font, UiFont.LABEL_LETTERS, letters.getAsJsonArray("chars").get(0).getAsString());
        }
    }
}
