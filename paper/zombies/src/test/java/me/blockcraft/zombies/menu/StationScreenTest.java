package me.blockcraft.zombies.menu;

import com.google.gson.JsonParser;
import me.blockcraft.common.message.Messages;
import me.blockcraft.zombies.game.Perk;
import me.blockcraft.zombies.game.Weapon;
import me.blockcraft.zombies.menu.StationScreen.Art;
import me.blockcraft.zombies.menu.StationScreen.Card;
import me.blockcraft.zombies.menu.StationScreen.Layout;
import me.blockcraft.zombies.menu.StationScreen.Tone;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The client shows a screen whose numbers disagree with the pack only as glyphs in the wrong places or missing boxes. */
public class StationScreenTest {

    private static final Path PACK = Path.of("../../resourcepack");
    private final Messages messages = Messages.load(Path.of("/nonexistent-station-screen-test"),
            () -> getClass().getResourceAsStream("/messages.yml"));

    @Test
    public void theLayoutsAreThePanelsThePackDraws() throws IOException {
        var source = Files.readString(PACK.resolve("StationArt.java"));
        var matcher = Pattern.compile("new Layout\\(\"(\\w+)\", (\\d+), List\\.of\\(([\\d, ]+)\\)").matcher(source);
        var layouts = new ArrayList<String>();
        while (matcher.find()) {
            layouts.add(matcher.group(1) + " " + matcher.group(2) + " " + matcher.group(3).replace(" ", ""));
        }
        assertEquals(Arrays.stream(Layout.values()).map(layout -> layout.name().toLowerCase(Locale.ROOT) + " " + layout.rows + " "
                + String.join(",", layout.cards.stream().map(String::valueOf).toList())).toList(), layouts);
        var numbers = Map.of("CARD_COLUMNS", List.of(StationScreen.CARD_COLUMNS), "CARD_ROWS", List.of(StationScreen.CARD_ROWS),
                "ART_TOP", List.of(StationScreen.ART_TOP), "TEXT_TOPS", StationScreen.TEXT_TOPS, "BAND_TOP", List.of(StationScreen.BAND_TOP),
                "EMBLEM_TOP", List.of(StationScreen.EMBLEM_TOP), "HEADER_TOP", List.of(StationScreen.HEADER_TOP));
        numbers.forEach((name, value) -> assertEquals(name, value.stream().map(String::valueOf).toList(), generator(source, name)));
    }

    @Test
    public void theArtIsInTheOrderOfThePacksGlyphs() throws IOException {
        var source = Files.readString(PACK.resolve("StationArt.java"));
        var art = new ArrayList<>(generator(source, "GUNS").stream().map(gun -> gun.replace("\"", "")).toList());
        var pictures = Pattern.compile("new Picture\\(\"(\\w+)\"").matcher(source);
        while (pictures.find()) {
            art.add(pictures.group(1));
        }
        assertEquals(Arrays.stream(Art.values()).map(value -> value.name().toLowerCase(Locale.ROOT)).toList(), art);
    }

    /** Each part moves back by the width it says it takes, so a width the font does not give shifts every later part. */
    @Test
    public void everyPartIsDrawnByItsFontAsWideAsTheScreenCountsIt() throws Exception {
        var fonts = new HashMap<String, Map<Integer, Integer>>();
        var cards = List.of(
                new Card(Art.SNIPER, "SCHARFSCHÜTZENGEWEHR", StationScreen.stats(36, 5), OptionalInt.of(900), "ZU TEUER", Tone.EXPENSIVE),
                new Card(Art.AMMO, "MUNITION", "GEHALTENE WAFFE AUFFÜLLEN", OptionalInt.of(200), "KAUFEN", Tone.BUY),
                new Card(Art.QUICK_REVIVE, "SCHNELLE WIEDERBELEBUNG", "SCHNELLER RETTEN", OptionalInt.empty(), "AKTIV", Tone.SPENT),
                new Card(Art.TEAM_REVIVE, "TEAM-RETTUNG", "RETTET ALLE GEFALLENEN", OptionalInt.of(1200), "STROM AUS", Tone.POWER));
        for (var layout : Layout.values()) {
            for (var part : StationScreen.parts(layout, Art.MYSTERY, "WAFFEN & MUNITION", 12500, cards)) {
                var advances = advances(part.font().asString(), fonts);
                int width = part.text().codePoints().map(glyph -> {
                    assertTrue(part + " draws " + Character.toString(glyph), advances.containsKey(glyph));
                    return advances.get(glyph);
                }).sum();
                assertEquals(part.toString(), part.advance(), width);
            }
        }
    }

    @Test
    public void wordsBreakAfterAHyphenAWordTooLongGetsOneAndTooManyLinesEndInAnEllipsis() {
        assertEquals(List.of("SCHARFSCHÜTZEN-", "GEWEHR"), StationScreen.wrap("SCHARFSCHÜTZEN-GEWEHR", 2));
        assertEquals(List.of("ABCDEFGHIJKLMNO-", "PQRSTUVWXYZ"), StationScreen.wrap("ABCDEFGHIJKLMNOPQRSTUVWXYZ", 2));
        assertEquals(List.of("GEHALTENE WAFFE", "AUFFÜLLEN"), StationScreen.wrap("GEHALTENE WAFFE AUFFÜLLEN", 2));
        assertEquals(List.of("GEHALTENE WAFFE…"), StationScreen.wrap("GEHALTENE WAFFE AUFFÜLLEN", 1));
    }

    /** A card's words are written short for it; one that grows past its room is cut, which this catches first. */
    @Test
    public void everyCardsWordsFitWithoutBeingCut() {
        var hearts = Formatter.number("hearts", 10);
        var cards = new ArrayList<List<String>>();
        for (var key : List.of("ammo", "armor", "power", "upgrade", "mystery", "team-revive")) {
            cards.add(List.of(text("shop.card." + key + ".name", Formatter.number("tier", 2)), text("shop.card." + key + ".line")));
        }
        for (var weapon : Weapon.values()) {
            cards.add(List.of(StationScreen.name(messages, weapon), StationScreen.stats(36, 360)));
        }
        for (var perk : Perk.values()) {
            cards.add(List.of(text("perk." + perk.key()), text("perk.card." + perk.key(), hearts)));
        }
        for (var card : cards) {
            var names = StationScreen.wrap(card.getFirst(), 2);
            var lines = StationScreen.wrap(card.getLast(), 3 - names.size());
            assertFalse(card.toString(), String.join(" ", names).contains("…") || lines.isEmpty() || String.join(" ", lines).contains("…"));
        }
        for (var state : List.of("buy", "expensive", "power")) {
            assertEquals(state, text("shop.state." + state), StationScreen.fit(text("shop.state." + state), 64 - 6 - StationScreen.width("1500")));
        }
    }

    private String text(String key, TagResolver... values) {
        return StationScreen.capitals(messages.get(key, values));
    }

    /** The values of {@code name} in the standalone generator, which shares no code with the plugin. */
    private static List<String> generator(String source, String name) {
        var matcher = Pattern.compile("\\b" + name + " = (?:List\\.of\\()?([^);]*)\\)?;", Pattern.DOTALL).matcher(source);
        assertTrue(name, matcher.find());
        return Arrays.stream(matcher.group(1).split(",")).map(String::strip).toList();
    }

    /** Each glyph's advance as the client measures it: its last column with any ink, scaled to the glyph's height, plus one. */
    private static Map<Integer, Integer> advances(String font, Map<String, Map<Integer, Integer>> fonts) throws IOException {
        if (fonts.containsKey(font)) {
            return fonts.get(font);
        }
        var advances = new HashMap<Integer, Integer>();
        var path = PACK.resolve("src/assets/" + font.replace(":", "/font/") + ".json");
        assertTrue(path.toString(), Files.isRegularFile(path));
        for (var element : JsonParser.parseString(Files.readString(path)).getAsJsonObject().getAsJsonArray("providers")) {
            var provider = element.getAsJsonObject();
            if (provider.get("type").getAsString().equals("space")) {
                provider.getAsJsonObject("advances").entrySet().forEach(entry ->
                        advances.putIfAbsent(entry.getKey().codePointAt(0), entry.getValue().getAsInt()));
                continue;
            }
            var file = provider.get("file").getAsString();
            var image = ImageIO.read(PACK.resolve("src/assets/blockcraft/textures/" + file.substring("blockcraft:".length())).toFile());
            int[] characters = provider.getAsJsonArray("chars").get(0).getAsString().codePoints().toArray();
            int cellWidth = image.getWidth() / characters.length;
            double scale = provider.get("height").getAsInt() / (double) image.getHeight();
            for (int column = 0; column < characters.length; column++) {
                int ink = 0;
                for (int x = 0; x < cellWidth; x++) {
                    for (int y = 0; y < image.getHeight(); y++) {
                        if (image.getRGB(column * cellWidth + x, y) >>> 24 != 0) ink = x + 1;
                    }
                }
                advances.putIfAbsent(characters[column], (int) (0.5 + ink * scale) + 1);
            }
        }
        fonts.put(font, advances);
        return advances;
    }
}
