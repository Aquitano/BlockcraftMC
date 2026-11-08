package me.blockcraft.sternenfront.menu;

import com.google.gson.JsonParser;
import me.blockcraft.api.pack.UiFont;
import me.blockcraft.common.message.Messages;
import me.blockcraft.sternenfront.game.AbilityIcon;
import me.blockcraft.sternenfront.game.Faction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DeployScreenTest {

    private static final Path PACK = Path.of("../../resourcepack");
    private final Messages messages = Messages.load(Path.of("/nonexistent-deploy-screen-test"),
            () -> getClass().getResourceAsStream("/messages.yml"));

    /** A font is drawn at one height only, so every line the screen writes on needs its own in the pack. */
    @Test
    public void everyLineTheScreenWritesOnHasAFontInThePack() {
        var fonts = new TreeSet<String>();
        for (int places = 1; places <= 7; places++) {
            fonts(DeployScreen.title(messages, view(places)), fonts);
        }
        for (var font : fonts) {
            var path = PACK.resolve("src/assets/" + font.replace(":", "/font/") + ".json");
            assertTrue(path.toString(), Files.isRegularFile(path));
        }
    }

    /**
     * Every part moves back to where the title started, by the width the pack's glyphs really take, so the title's
     * own text ends up at the window's top-left whatever the screen shows. The client's own letters keep its widths.
     */
    @Test
    public void everyPartReturnsTheCursorToTheTitlesStart() throws Exception {
        for (int places = 1; places <= 7; places++) {
            var title = DeployScreen.title(messages, view(places));
            assertEquals(-122, width(title, null, new HashMap<>()));
        }
    }

    @Test
    public void portraitsAreInTheOrderOfThePacksSheets() throws Exception {
        var matcher = Pattern.compile("UNITS = List\\.of\\(([^)]*)\\)", Pattern.DOTALL)
                .matcher(Files.readString(PACK.resolve("DeployArt.java")));
        assertTrue(matcher.find());
        var units = Arrays.stream(matcher.group(1).split(",")).map(unit -> unit.strip().replace("\"", "")).toList();
        var portraits = Arrays.stream(DeployScreen.Portrait.values()).map(portrait -> portrait.name().toLowerCase(Locale.ROOT)).toList();

        assertEquals(units.stream().map(unit -> unit.replaceFirst("^(reinforcement|hero)_", "")).toList(), portraits);
    }

    private DeployScreen.View view(int places) {
        var role = new DeployScreen.Role("Klasse", "Klonsoldat", DeployScreen.Portrait.KOLONIEN_SOLDIER,
                List.of(new DeployScreen.Stat("Gesundheit", "10", true), new DeployScreen.Stat("Tempo", "100 %", false),
                        new DeployScreen.Stat("Schaden", "2,5", true), new DeployScreen.Stat("Reichweite", "60 m", false)),
                "DC-15A-Blastergewehr", List.of(new DeployScreen.Ability("Thermaldetonator", "Taste F", AbilityIcon.GRENADE),
                        new DeployScreen.Ability("Adrenalin", "Taste Q", AbilityIcon.ADRENALINE)),
                List.of(new DeployScreen.Stat("Kosten", "Kostenlos", false), new DeployScreen.Stat("Guthaben", "340 KP", false)));
        var card = new DeployScreen.ClassCard("Soldat", DeployScreen.Portrait.KOLONIEN_SOLDIER, List.of(AbilityIcon.GRENADE, AbilityIcon.RALLY), true);
        var special = new DeployScreen.Special("ARC", DeployScreen.Portrait.ARC_TROOPER, 250, true, true);
        var spawns = new java.util.ArrayList<DeployScreen.Place>(List.of(new DeployScreen.Place.Headquarters("Hauptquartier")));
        for (int post = 0; post < places - 2; post++) {
            spawns.add(new DeployScreen.Place.Post(post, "Posten"));
        }
        spawns.add(new DeployScreen.Place.Landing("Landung bei A"));
        return new DeployScreen.View(Faction.HEGEMONIE, Component.empty(), role, List.of(card, card, card, card),
                List.of(special, special, special), 340, spawns.subList(0, places));
    }

    private static int width(Component component, String font, Map<String, Map<Integer, Integer>> fonts) throws Exception {
        String own = component.font() == null ? font : component.font().asString();
        int width = 0;
        if (component instanceof TextComponent text && !text.content().isEmpty()) {
            var advances = own == null ? Map.<Integer, Integer>of() : advances(own, fonts);
            for (int character : text.content().codePoints().toArray()) {
                width += advances.containsKey(character) ? advances.get(character) : UiFont.width(Component.text(Character.toString(character)));
            }
        }
        for (var child : component.children()) {
            width += width(child, own, fonts);
        }
        return width;
    }

    /** Each glyph's advance as the client measures it: its last column with any ink, plus one. */
    private static Map<Integer, Integer> advances(String font, Map<String, Map<Integer, Integer>> fonts) throws Exception {
        if (fonts.containsKey(font)) {
            return fonts.get(font);
        }
        var advances = new HashMap<Integer, Integer>();
        var json = JsonParser.parseString(Files.readString(PACK.resolve("src/assets/" + font.replace(":", "/font/") + ".json"))).getAsJsonObject();
        for (var element : json.getAsJsonArray("providers")) {
            var provider = element.getAsJsonObject();
            if (provider.get("type").getAsString().equals("space")) {
                provider.getAsJsonObject("advances").entrySet().forEach(entry ->
                        advances.putIfAbsent(entry.getKey().codePointAt(0), entry.getValue().getAsInt()));
                continue;
            }
            String file = provider.get("file").getAsString();
            if (file.startsWith("minecraft:")) {
                continue;
            }
            var image = ImageIO.read(PACK.resolve("src/assets/blockcraft/textures/" + file.substring("blockcraft:".length())).toFile());
            var rows = provider.getAsJsonArray("chars");
            int columns = rows.get(0).getAsString().codePointCount(0, rows.get(0).getAsString().length());
            int cellWidth = image.getWidth() / columns;
            int cellHeight = image.getHeight() / rows.size();
            for (int row = 0; row < rows.size(); row++) {
                int[] characters = rows.get(row).getAsString().codePoints().toArray();
                for (int column = 0; column < characters.length; column++) {
                    int ink = 0;
                    for (int x = 0; x < cellWidth; x++) {
                        for (int y = 0; y < cellHeight; y++) {
                            if (image.getRGB(column * cellWidth + x, row * cellHeight + y) >>> 24 != 0) ink = x + 1;
                        }
                    }
                    advances.putIfAbsent(characters[column], ink + 1);
                }
            }
        }
        fonts.put(font, advances);
        return advances;
    }

    private static void fonts(Component component, Set<String> fonts) {
        if (component.font() != null && component.font().namespace().equals("blockcraft")) {
            fonts.add(component.font().asString());
        }
        component.children().forEach(child -> fonts(child, fonts));
    }
}
