package me.blockcraft.sternenfront.menu;

import com.google.gson.JsonParser;
import me.blockcraft.common.message.Messages;
import me.blockcraft.sternenfront.game.AbilityIcon;
import me.blockcraft.sternenfront.game.CombatClass;
import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.game.Spawn;
import me.blockcraft.sternenfront.hero.Hero;
import me.blockcraft.sternenfront.reinforcement.Reinforcement;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.UncheckedIOException;
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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class DeployScreenTest {

    private static final Path PACK = Path.of("../../resourcepack");
    private final Messages messages = Messages.load(Path.of("/nonexistent-deploy-screen-test"),
            () -> getClass().getResourceAsStream("/messages.yml"));

    /** A font is drawn at one height only, so every line the screen writes on needs its own in the pack. */
    @Test
    public void everyLineTheScreenWritesOnHasAFontInThePack() {
        var fonts = new TreeSet<String>();
        views().forEach(view -> fonts(DeployScreen.title(messages, view), fonts));
        for (var font : fonts) {
            var path = PACK.resolve("src/assets/" + font.replace(":", "/font/") + ".json");
            assertTrue(path.toString(), Files.isRegularFile(path));
        }
    }

    /**
     * Every part moves back to where the title started, by the width the pack's glyphs really take, so the title's
     * own text ends up at the window's top-left whatever the screen shows. Every letter is the pack's own, so a player's
     * pack cannot change a width the server counted.
     */
    @Test
    public void everyPartReturnsTheCursorToTheTitlesStart() throws Exception {
        for (var view : views()) {
            assertEquals(-122, width(DeployScreen.title(messages, view), null, new HashMap<>()));
        }
    }

    @Test
    public void portraitsAreInTheOrderOfThePacksSheets() {
        var units = generator("UNITS").stream().map(unit -> unit.replace("\"", "")).toList();
        var portraits = Arrays.stream(DeployScreen.Portrait.values()).map(portrait -> portrait.name().toLowerCase(Locale.ROOT)).toList();

        assertEquals(units.stream().map(unit -> unit.replaceFirst("^(reinforcement|hero)_", "")).toList(), portraits);
    }

    @Test
    public void everyRoleTheMenuOffersHasAPortrait() {
        for (var faction : Faction.values()) {
            Arrays.stream(CombatClass.values()).forEach(combatClass -> DeployScreen.Portrait.of(faction, combatClass));
            DeployScreen.Portrait.of(Reinforcement.of(faction));
            Hero.of(faction).forEach(DeployScreen.Portrait::of);
        }
    }

    /** The pack's window has its buttons, cards and map where the menu's slots are, and the screen draws over them. */
    @Test
    public void thePacksWindowHasItsButtonsCardsAndMapWhereTheSlotsAre() {
        assertEquals(DeployScreen.CLASS_CARDS.stream().map(String::valueOf).toList(), generator("CLASS_CARDS"));
        assertEquals(DeployScreen.SPECIALS.stream().map(String::valueOf).toList(), generator("SPECIALS"));
        assertEquals(DeployScreen.BUTTONS.stream().map(String::valueOf).toList(), generator("BUTTONS"));
        var sizes = Map.of("CLASS_CARD_COLUMNS", DeployScreen.CLASS_CARD_COLUMNS, "CLASS_CARD_ROWS", DeployScreen.CLASS_CARD_ROWS,
                "SPECIAL_COLUMNS", DeployScreen.SPECIAL_COLUMNS, "MAP_COLUMNS", TacticalLayout.COLUMNS, "MAP_ROWS", TacticalLayout.ROWS);
        sizes.forEach((name, size) -> assertEquals(name, List.of(String.valueOf(size)), generator(name)));
    }

    /** The icons are numbered by their order, so both sides must list them alike. */
    @Test
    public void iconsAreInTheOrderOfThePacksSheet() {
        var matcher = Pattern.compile("new Icon\\(\"(\\w+)\"").matcher(source());
        var icons = new java.util.ArrayList<String>();
        while (matcher.find()) {
            icons.add(matcher.group(1));
        }

        assertEquals(Arrays.stream(DeployScreen.Icon.values()).map(icon -> icon.name().toLowerCase(Locale.ROOT)).toList(), icons);
    }

    @Test
    public void everyAbilityIconHasAGlyphOnEveryLine() throws Exception {
        var line = JsonParser.parseString(Files.readString(PACK.resolve("src/assets/" + DeployScreen.line(144).asString()
                .replace(":", "/font/") + ".json"))).getAsJsonObject();
        var abilities = line.getAsJsonArray("providers").asList().stream().map(provider -> provider.getAsJsonObject())
                .filter(provider -> provider.has("file") && provider.get("file").getAsString().endsWith("/ability_icons.png"))
                .findFirst().orElseThrow();

        assertEquals(AbilityIcon.values().length, abilities.getAsJsonArray("chars").get(0).getAsString().length());
    }

    /** The values of {@code name} in the standalone generator, which shares no code with the plugin. */
    private static List<String> generator(String name) {
        var matcher = Pattern.compile("\\b" + name + " = (?:List\\.of\\()?([^);]*)\\)?;", Pattern.DOTALL).matcher(source());
        assertTrue(name, matcher.find());
        return Arrays.stream(matcher.group(1).split(",")).map(String::strip).toList();
    }

    /** The battle locks and recommends spawns on its own, so only those marks wait to settle; the player's choice does not. */
    @Test
    public void onlyTheBattlesSpawnMarksAreLeftOutOfTheSettledView() {
        var view = view(3, true);
        var places = new java.util.ArrayList<>(view.places());
        var post = places.get(1);
        places.set(1, new DeployScreen.Place(post.spawn(), post.name(), post.colour(), !post.open(), !post.recommended(), post.selection()));
        var flipped = new DeployScreen.View(view.faction(), view.title(), view.buttons(), view.role(), view.classes(), view.specials(),
                List.copyOf(places));
        places.set(1, new DeployScreen.Place(post.spawn(), post.name(), post.colour(), post.open(), post.recommended(), DeployScreen.Selection.PENDING));
        var chosen = new DeployScreen.View(view.faction(), view.title(), view.buttons(), view.role(), view.classes(), view.specials(),
                places);

        assertEquals(view.withoutSpawnMarks(), flipped.withoutSpawnMarks());
        assertNotEquals(view.withoutSpawnMarks(), chosen.withoutSpawnMarks());
    }

    /** The headquarters with up to five posts, with and without the dropship, so every line of the list is drawn on. */
    private List<DeployScreen.View> views() {
        var views = new java.util.ArrayList<DeployScreen.View>();
        for (int posts = 0; posts <= 5; posts++) {
            views.add(view(posts, false));
            views.add(view(posts, true));
        }
        return views;
    }

    private DeployScreen.View view(int posts, boolean dropship) {
        var role = new DeployScreen.Role("Klasse", "Klonsoldat", DeployScreen.Portrait.KOLONIEN_SOLDIER,
                List.of(new DeployScreen.Stat("Gesundheit", "10", true), new DeployScreen.Stat("Tempo", "100 %", false),
                        new DeployScreen.Stat("Schaden", "2,5", true), new DeployScreen.Stat("Reichweite", "60 m", false)),
                "Großer DC-15A-Blaster", List.of(new DeployScreen.Ability("Thermaldetonator", "Taste F", AbilityIcon.GRENADE),
                        new DeployScreen.Ability("Adrenalin", "Taste Q", AbilityIcon.ADRENALINE)),
                List.of(new DeployScreen.Stat("Kosten", "Kostenlos", false), new DeployScreen.Stat("Deine Kampfpunkte", "1340 KP", false)));
        var card = new DeployScreen.ClassCard("Soldat", DeployScreen.Portrait.KOLONIEN_SOLDIER, List.of(AbilityIcon.GRENADE, AbilityIcon.RALLY), true);
        var special = new DeployScreen.Special("ARC", DeployScreen.Portrait.ARC_TROOPER, 1250, true, true);
        var spawns = new java.util.ArrayList<>(List.of(new DeployScreen.Place(new Spawn.Headquarters(), "Hauptquartier", 0xFF3434,
                true, false, DeployScreen.Selection.NONE)));
        for (int post = 0; post < posts; post++) {
            boolean open = post % 2 == 0;
            spawns.add(new DeployScreen.Place(new Spawn.Post(post), "Raffinerie", 0xFFFFFF, open, open,
                    open ? DeployScreen.Selection.values()[post % 3] : DeployScreen.Selection.NONE));
        }
        if (dropship) {
            spawns.add(new DeployScreen.Place(new Spawn.Dropship(), "Landung bei A", 0xFFFFFF, true, true, DeployScreen.Selection.CONFIRMED));
        }
        return new DeployScreen.View(Faction.HEGEMONIE, Component.empty(), List.of("Schließen", "Einsetzen"), role,
                List.of(card, card, card, card), List.of(special, special, special), spawns);
    }

    private static String source() {
        try {
            return Files.readString(PACK.resolve("DeployArt.java"));
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    private static int width(Component component, String font, Map<String, Map<Integer, Integer>> fonts) throws Exception {
        String own = component.font() == null ? font : component.font().asString();
        int width = 0;
        if (component instanceof TextComponent text && !text.content().isEmpty()) {
            var advances = own == null ? Map.<Integer, Integer>of() : advances(own, fonts);
            for (int character : text.content().codePoints().toArray()) {
                assertTrue(own + " draws " + Character.toString(character), advances.containsKey(character));
                width += advances.get(character);
            }
        }
        for (var child : component.children()) {
            width += width(child, own, fonts);
        }
        return width;
    }

    /** Each glyph's advance as the client measures it: its last column with any ink, scaled to the glyph's height, plus one. */
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
            if (!provider.has("file")) {
                continue;
            }
            String file = provider.get("file").getAsString();
            var image = ImageIO.read(PACK.resolve("src/assets/blockcraft/textures/" + file.substring("blockcraft:".length())).toFile());
            var rows = provider.getAsJsonArray("chars");
            int columns = rows.get(0).getAsString().codePointCount(0, rows.get(0).getAsString().length());
            int cellWidth = image.getWidth() / columns;
            int cellHeight = image.getHeight() / rows.size();
            double scale = (provider.has("height") ? provider.get("height").getAsInt() : 8) / (double) cellHeight;
            for (int row = 0; row < rows.size(); row++) {
                int[] characters = rows.get(row).getAsString().codePoints().toArray();
                for (int column = 0; column < characters.length; column++) {
                    if (characters[column] == 0) {
                        continue;
                    }
                    int ink = 0;
                    for (int x = 0; x < cellWidth; x++) {
                        for (int y = 0; y < cellHeight; y++) {
                            if (image.getRGB(column * cellWidth + x, row * cellHeight + y) >>> 24 != 0) ink = x + 1;
                        }
                    }
                    advances.putIfAbsent(characters[column], (int) (0.5 + ink * scale) + 1);
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
