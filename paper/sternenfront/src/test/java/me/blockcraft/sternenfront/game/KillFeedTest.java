package me.blockcraft.sternenfront.game;

import me.blockcraft.sternenfront.PackFonts;
import me.blockcraft.sternenfront.game.HudPanel.Corner;
import me.blockcraft.sternenfront.game.HudPanel.Tint;
import me.blockcraft.sternenfront.game.KillFeed.Kind;
import me.blockcraft.sternenfront.game.KillFeed.Named;
import net.kyori.adventure.key.Key;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

public class KillFeedTest {

    private final UUID ann = UUID.randomUUID();

    @Test
    public void theNewestLineIsOnTopAndALineGreysOutBeforeItGoes() {
        var feed = new KillFeed(100);
        for (int kill = 0; kill < 5; kill++) {
            feed.add(ann, Kind.KILL, Optional.of(new Named("CT-" + kill, Tint.REPUBLIC)), kill * 10);
        }
        var live = feed.live(ann, 40);
        assertEquals(KillFeed.ROWS, live.size());
        assertEquals("CT-4", live.getFirst().other().orElseThrow().name());

        assertTrue(KillFeed.layout(feed.live(ann, 60), "Ann", 60).stream().noneMatch(glyph -> glyph.tint() == Tint.DIMMED));
        var fading = KillFeed.layout(feed.live(ann, 100), "Ann", 100);
        assertTrue(fading.stream().filter(glyph -> glyph.y() == 3 * KillFeed.ROW).allMatch(glyph -> glyph.tint() == Tint.DIMMED));
        assertTrue(fading.stream().filter(glyph -> glyph.y() == 0).noneMatch(glyph -> glyph.tint() == Tint.DIMMED));
        assertTrue(feed.live(ann, 140).isEmpty());
    }

    /** The panel starts in the boss bar line's middle and the shader hangs it from the corner, so it must take no room. */
    @Test
    public void everyLineEndsAtTheCornerAndThePanelTakesNoRoom() throws IOException {
        var feed = new KillFeed(100);
        var enemy = Optional.of(new Named("Ein_sehr_langer_Name", Tint.SEPARATISTS));
        feed.add(ann, Kind.DEATH, Optional.empty(), 0);
        feed.add(ann, Kind.DEATH, enemy, 0);
        feed.add(ann, Kind.ASSIST, enemy, 0);
        feed.add(ann, Kind.HEADSHOT, enemy, 0);
        var glyphs = KillFeed.layout(feed.live(ann, 0), "Ann", 0);

        var minimap = PackFonts.advances("minimap");
        var fonts = Map.of(Key.key("blockcraft", "ui"), PackFonts.advances("ui"), MinimapLayout.FONT, minimap);
        assertEquals(0, PackFonts.width(HudPanel.render(Corner.TOP_RIGHT, MinimapLayout.FONT, glyphs), fonts));
        for (var glyph : glyphs) {
            assertTrue(glyph.toString(), glyph.x() < 0 && glyph.y() >= 0 && glyph.y() <= 255);
            assertEquals(glyph.toString(), glyph.advance(), glyph.text().codePoints().map(minimap::get).sum());
        }
        for (int row = 0; row < KillFeed.ROWS; row++) {
            int y = row * KillFeed.ROW;
            var line = glyphs.stream().filter(glyph -> glyph.y() == y).toList();
            var last = line.getLast();
            assertEquals(line.toString(), 0, last.x() + last.advance());
        }
    }
}
