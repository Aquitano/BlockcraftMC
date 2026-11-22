import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Zombies' station screens, which the chest title draws instead of the shared menu panel: a panel of riveted olive iron
 * for each layout, with sunk cards where the layout puts them and a hazard-striped rail for the exit, and the fonts the
 * server writes each card's picture, words, price and state band from. A font draws at one height only, so each line a
 * card has in any layout gets one, {@code zombies/station/line_<top>}, holding every glyph. Zombies' StationScreen lays
 * the cards out from the same numbers, and StationScreenTest compares them.
 * <p>
 * Coordinates are the chest screen's own, from its top-left corner. A glyph's top lies at {@code 13 - ascent}.
 */
public final class StationArt {

    record Layout(String name, int rows, List<Integer> cards, boolean arrows) { }

    /** In the order of StationScreen.Layout: each card's top-left slot; the swap layout's arrows lead from its first card to the others. */
    static final List<Layout> LAYOUTS = List.of(
            new Layout("one", 3, List.of(2), false),
            new Layout("two", 3, List.of(0, 4), false),
            new Layout("three", 6, List.of(2, 27, 31), false),
            new Layout("four", 6, List.of(0, 4, 27, 31), false),
            new Layout("swap", 6, List.of(2, 27, 31), true));
    static final int CARD_COLUMNS = 4;
    static final int CARD_ROWS = 3;
    /** From a card's top: its picture, its three text lines and its band. */
    static final int ART_TOP = 0;
    static final List<Integer> TEXT_TOPS = List.of(22, 29, 36);
    static final int BAND_TOP = 43;
    /** The header's emblem and its line of text. */
    static final int EMBLEM_TOP = 3;
    static final int HEADER_TOP = 5;

    record Picture(String name, String[] rows, Map<Character, Integer> palette) { }

    /** The guns' side views HudArt draws for the weapon panel, in the order of Zombies' Weapon. */
    static final List<String> GUNS = List.of("pistol", "rifle", "shotgun", "sniper", "flamethrower");
    /** In the order of StationScreen.Art after the guns. */
    static final List<Picture> PICTURES = List.of(
            new Picture("ammo", new String[] {
                    "..........",
                    ".ee.ee.ee.",
                    ".cc.cc.cc.",
                    ".yy.yy.yy.",
                    ".YY.YY.YY.",
                    ".YY.YY.YY.",
                    ".YY.YY.YY.",
                    ".YY.YY.YY.",
                    ".dd.dd.dd.",
                    "kkkkkkkkkk"},
                    Map.of('e', 0xFFF0A27A, 'c', 0xFFC8734F, 'y', 0xFFF2D27A, 'Y', 0xFFD9A93A, 'd', 0xFF9A7224, 'k', 0xFF4A5040)),
            new Picture("extra_health", new String[] {
                    "..........",
                    ".RR....RR.",
                    "RHRR..RRRR",
                    "RHRRRRRRRR",
                    "RRRRRRRRRR",
                    ".RRRRRRRD.",
                    "..RRRRRD..",
                    "...RRRD...",
                    "....RD....",
                    ".........."},
                    Map.of('R', 0xFFD8443A, 'H', 0xFFF6A49A, 'D', 0xFF9A2A24)),
            new Picture("quick_reload", new String[] {
                    "....kk....",
                    "...kkkk...",
                    ".kkwwwwkk.",
                    ".kwwwkwwk.",
                    "kwwwwkwwwk",
                    "kwwwwkkkwk",
                    "kwwwwwwwwk",
                    ".kwwwwwwk.",
                    ".kkwwwwkk.",
                    "...kkkk..."},
                    Map.of('k', 0xFF3A3F2E, 'w', 0xFFE9E2C4)),
            new Picture("quick_revive", new String[] {
                    "...kkkk...",
                    "...kGGk...",
                    "...kGGk...",
                    "kkkkGGkkkk",
                    "kGGGGGGGGk",
                    "kGGGGGGGGk",
                    "kkkkGGkkkk",
                    "...kGGk...",
                    "...kGGk...",
                    "...kkkk..."},
                    Map.of('k', 0xFF1F3A12, 'G', 0xFF8FD64E)),
            new Picture("frozen_bullets", new String[] {
                    "....i.....",
                    ".i..i..i..",
                    "..i.i.i...",
                    "...iii....",
                    "iiiiIiiii.",
                    "...iii....",
                    "..i.i.i...",
                    ".i..i..i..",
                    "....i.....",
                    ".........."},
                    Map.of('i', 0xFF9CDBF5, 'I', 0xFFFFFFFF)),
            new Picture("armor_iron", chestplate(), Map.of('k', 0xFF2E3329, 'A', 0xFFC9CED6, 'H', 0xFFF4F6F8, 'D', 0xFF8C939E)),
            new Picture("armor_diamond", chestplate(), Map.of('k', 0xFF15403F, 'A', 0xFF4FD8D0, 'H', 0xFFC4FFF8, 'D', 0xFF2A9A94)),
            new Picture("power", new String[] {
                    ".....YYY..",
                    "....YYY...",
                    "...YYY....",
                    "..YYYYYY..",
                    ".....YYD..",
                    "....YYD...",
                    "...YYD....",
                    "..YD......",
                    ".YD.......",
                    ".........."},
                    Map.of('Y', 0xFFF6D44A, 'D', 0xFFB08A1A)),
            new Picture("upgrade", new String[] {
                    "....gg....",
                    "...gGGg...",
                    "..gGGGGg..",
                    ".gGGGGGGg.",
                    "gggGGGGggg",
                    "...gGGg...",
                    "...gGGg...",
                    "...gGGg...",
                    "...gggg...",
                    ".........."},
                    Map.of('g', 0xFF9A7224, 'G', 0xFFF2C230)),
            new Picture("mystery", new String[] {
                    "kkkkkkkkkk",
                    "kwwwwwwwwk",
                    "kwwyyyywwk",
                    "kwyywwyywk",
                    "kwwwwwyywk",
                    "kwwwwyywwk",
                    "kwwwyywwwk",
                    "kwwwwwwwwk",
                    "kwwwyywwwk",
                    "kkkkkkkkkk"},
                    Map.of('k', 0xFF3B2414, 'w', 0xFF7A5230, 'y', 0xFFB98BF0)),
            new Picture("team_revive", new String[] {
                    ".pp....pp.",
                    "pPPp..pPPp",
                    ".pp....pp.",
                    "....GG....",
                    "bbbbGGbbbb",
                    "bBGGGGGGBb",
                    "bBGGGGGGBb",
                    "bBbbGGbbBb",
                    "bBb.GG.bBb",
                    "bbb....bbb"},
                    Map.of('p', 0xFFB98A6A, 'P', 0xFFE0B494, 'b', 0xFF3A4A2A, 'B', 0xFF5E7A40, 'G', 0xFF8FD64E)));
    static final int PICTURE_WIDTH = 10;
    static final int PICTURE_HEIGHT = 12;

    /** The small marks a line spells with: a gold coin, a skull for a hit's damage and a round for a clip. */
    static final List<String[]> MARKS = List.of(
            new String[] {".###.", "##.##", "#.#.#", "##.##", ".###."},
            new String[] {".###.", "#.#.#", "#####", ".###.", ".#.#."},
            new String[] {"..#..", ".###.", ".###.", ".###.", ".###."});
    /**
     * Letters drawn otherwise than on the menu tiles: umlauts with their dots inside the capitals' five rows, so a line
     * seven pixels below another keeps a clear row between them, and a 1 with its stem in the middle, which reads as a
     * J beside the marks otherwise.
     */
    static final Map<Character, String> OWN_LETTERS = Map.of('Ä', "101010101111101", 'Ö', "101000111101111", 'Ü', "101000101101111",
            '1', "010110010010111");

    /** The first glyph of each run in a line font; StationScreen draws from the same numbers. */
    static final int MARK_GLYPHS = 0xE000;
    static final int ART_GLYPHS = 0xE010;
    static final int EMBLEM_GLYPHS = 0xE030;
    static final int BAND_GLYPH = 0xE050;
    static final int PANEL_GLYPHS = 0xE000;

    static final int SLOT = 18;
    static final int SLOT_LEFT = 8;
    static final int SLOT_TOP = 18;
    static final int PANEL_WIDTH = 176;
    /** A sheet's cell for the text lines: taller than any ascent a line font gives it. */
    static final int CELL_HEIGHT = 16;

    static final int OUTLINE = 0xFF070806;
    static final int RIM = 0xFF3B4130;
    static final int RIM_DARK = 0xFF0F120C;
    static final int BODY = 0xFF1E2219;
    static final int GRIME = 0xFF1A1E15;
    static final int SCUFF = 0xFF252A1E;
    static final int HEADER = 0xFF12150F;
    static final int HAZARD = 0xFFC9A227;
    static final int HAZARD_DARK = 0xFF17150D;
    static final int RIVET = 0xFF4E5641;
    static final int RIVET_SHINE = 0xFF79836A;
    static final int CARD_BORDER = 0xFF0A0C08;
    static final int PLATE = 0xFF2A3022;
    static final int PLATE_EDGE = 0xFF3A4230;
    static final int MOSS = 0xFF587D33;
    static final int WELL = 0xFF13160F;
    static final int WELL_SHADOW = 0xFF0B0D09;
    static final int SLOT_FILL = 0xFF161A12;
    static final int TOXIC = 0xFF7DB843;
    static final int TOXIC_DARK = 0xFF3F6420;

    private StationArt() {
    }

    public static void main(String[] args) throws IOException {
        write(Path.of(args[0]));
    }

    static void write(Path root) throws IOException {
        Path textures = root.resolve("assets/blockcraft/textures/font/zombies/station");
        Path fonts = root.resolve("assets/blockcraft/font/zombies/station");
        Files.createDirectories(textures);
        Files.createDirectories(fonts);
        var panels = new ArrayList<String>();
        for (int index = 0; index < LAYOUTS.size(); index++) {
            var layout = LAYOUTS.get(index);
            String file = "panel_" + layout.name() + ".png";
            var panel = panel(layout);
            PackArt.write(panel, textures.resolve(file));
            panels.add(DeployArt.bitmap("zombies/station/" + file, 0, panel.getHeight(), DeployArt.escape(PANEL_GLYPHS + index)));
        }
        Files.writeString(fonts.resolve("panel.json"), DeployArt.font(panels));
        PackArt.write(letters(), textures.resolve("letters.png"));
        PackArt.write(marks(), textures.resolve("marks.png"));
        PackArt.write(pictures(), textures.resolve("pictures.png"));
        PackArt.write(band(), textures.resolve("band.png"));
        for (int top : lines()) {
            var providers = new ArrayList<String>();
            providers.add("{\"type\":\"space\",\"advances\":{\" \":4}}");
            providers.add(bitmap("zombies/station/letters.png", top, CELL_HEIGHT, json(PackArt.TileArt.LETTERS)));
            providers.add(DeployArt.bitmap("zombies/station/marks.png", top, CELL_HEIGHT, DeployArt.run(MARK_GLYPHS, MARKS.size())));
            for (int gun = 0; gun < GUNS.size(); gun++) {
                providers.add(DeployArt.bitmap("zombies/gun_" + GUNS.get(gun) + ".png", top, 24, DeployArt.escape(ART_GLYPHS + gun)));
            }
            providers.add(DeployArt.bitmap("zombies/station/pictures.png", top, 2 * PICTURE_HEIGHT,
                    DeployArt.run(ART_GLYPHS + GUNS.size(), PICTURES.size())));
            providers.add(DeployArt.bitmap("zombies/station/pictures.png", top, PICTURE_HEIGHT, DeployArt.run(EMBLEM_GLYPHS, PICTURES.size())));
            providers.add(DeployArt.bitmap("zombies/station/band.png", top, CELL_HEIGHT, DeployArt.escape(BAND_GLYPH)));
            Files.writeString(fonts.resolve("line_" + top + ".json"), DeployArt.font(providers));
        }
    }

    /** Every top a line of text or a picture stands at, in the header or on a card of any layout. */
    static TreeSet<Integer> lines() {
        var tops = new TreeSet<>(List.of(EMBLEM_TOP, HEADER_TOP));
        for (var layout : LAYOUTS) {
            for (int slot : layout.cards()) {
                int card = slotY(slot);
                tops.add(card + ART_TOP);
                TEXT_TOPS.forEach(top -> tops.add(card + top));
                tops.add(card + BAND_TOP);
            }
        }
        return tops;
    }

    static String bitmap(String file, int top, int height, String chars) {
        return "{\"type\":\"bitmap\",\"file\":\"blockcraft:font/" + file + "\",\"ascent\":" + (13 - top)
                + ",\"height\":" + height + ",\"chars\":[\"" + chars + "\"]}";
    }

    static String json(String letters) {
        var escaped = new StringBuilder();
        letters.chars().forEach(letter -> escaped.append(letter < 128 ? String.valueOf((char) letter) : DeployArt.escape(letter)));
        return escaped.toString();
    }

    /**
     * The whole chest screen for {@code layout}: header with a hazard stripe, the cards' sunk plates, the rail whose last
     * slot holds the exit, and the player's inventory below; the swap layout leads an arrow from the offered card to each
     * carried one.
     */
    static BufferedImage panel(Layout layout) {
        int rows = layout.rows();
        int height = 114 + rows * SLOT;
        var image = new BufferedImage(PANEL_WIDTH, height, BufferedImage.TYPE_INT_ARGB);
        PackArt.fill(image, 0, 0, PANEL_WIDTH, height, BODY);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < PANEL_WIDTH; x++) {
                int noise = Math.floorMod(x * 7349 + y * 1931 + x * y * 17, 97);
                if (noise < 4) {
                    image.setRGB(x, y, GRIME);
                } else if (noise < 6) {
                    image.setRGB(x, y, SCUFF);
                }
            }
        }
        PackArt.fill(image, 0, 0, PANEL_WIDTH, 14, HEADER);
        hazard(image, 1, 14, PANEL_WIDTH - 2, 3);
        for (int slot : layout.cards()) {
            card(image, slotX(slot), slotY(slot));
        }
        int railX = slotX(8) - 1;
        PackArt.fill(image, railX, SLOT_TOP - 1, SLOT, rows * SLOT, RIM_DARK);
        PackArt.fill(image, railX + 1, SLOT_TOP, SLOT - 2, (rows - 1) * SLOT - 2, HEADER);
        hazard(image, railX + 5, SLOT_TOP + 3, SLOT - 10, (rows - 1) * SLOT - 8);
        slot(image, railX, SLOT_TOP - 1 + (rows - 1) * SLOT);
        if (layout.arrows()) {
            arrows(image, layout);
        }
        PackArt.fill(image, 7, rows * SLOT + 23, PANEL_WIDTH - 14, 1, RIM_DARK);
        PackArt.fill(image, 7, rows * SLOT + 24, PANEL_WIDTH - 14, 1, RIM);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slot(image, 7 + column * SLOT, rows * SLOT + 30 + row * SLOT);
            }
        }
        for (int column = 0; column < 9; column++) {
            slot(image, 7 + column * SLOT, rows * SLOT + 88);
        }
        frame(image, height);
        return image;
    }

    /** A plate sunk into the panel: a moss line along its top, a dark well for the picture, and room for the server's band. */
    static void card(BufferedImage image, int x, int y) {
        int width = span(CARD_COLUMNS);
        int height = span(CARD_ROWS);
        PackArt.fill(image, x - 1, y - 1, width + 2, height + 2, RIM_DARK);
        PackArt.fill(image, x, y, width, height, CARD_BORDER);
        PackArt.fill(image, x + 1, y + 1, width - 2, height - 2, PLATE);
        PackArt.fill(image, x + 1, y + 1, width - 2, 1, MOSS);
        PackArt.fill(image, x + 2, y + 2, width - 4, 20, WELL);
        PackArt.fill(image, x + 2, y + 2, width - 4, 1, WELL_SHADOW);
        PackArt.fill(image, x + 2, y + 22, width - 4, 1, PLATE_EDGE);
        PackArt.fill(image, x + 1, y + BAND_TOP, width - 2, 8, WELL);
    }

    /** From the offered card's sides, out over the carried cards and down onto them. */
    static void arrows(BufferedImage image, Layout layout) {
        int offered = layout.cards().getFirst();
        int left = slotX(offered) - 2;
        int right = slotX(offered) + span(CARD_COLUMNS) + 1;
        int middle = slotY(offered) + span(CARD_ROWS) / 2;
        int bottom = slotY(layout.cards().get(1)) - 2;
        for (int carried : layout.cards().subList(1, layout.cards().size())) {
            boolean leftward = slotX(carried) < slotX(offered);
            int x = leftward ? (SLOT_LEFT + left) / 2 : (right + slotX(8)) / 2;
            PackArt.fill(image, Math.min(x, leftward ? left : right), middle, Math.abs(x - (leftward ? left : right)) + 2, 2, TOXIC);
            PackArt.fill(image, x, middle, 2, bottom - 4 - middle, TOXIC);
            for (int step = 0; step < 4; step++) {
                PackArt.fill(image, x - 3 + step, bottom - 4 + step, 8 - 2 * step, 1, step == 3 ? TOXIC_DARK : TOXIC);
            }
        }
    }

    static void hazard(BufferedImage image, int x, int y, int width, int height) {
        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                image.setRGB(x + dx, y + dy, Math.floorMod(dx + dy, 6) < 3 ? HAZARD : HAZARD_DARK);
            }
        }
    }

    static void slot(BufferedImage image, int x, int y) {
        PackArt.fill(image, x, y, SLOT, SLOT, SLOT_FILL);
        PackArt.fill(image, x, y, SLOT - 1, 1, RIM_DARK);
        PackArt.fill(image, x, y, 1, SLOT - 1, RIM_DARK);
        PackArt.fill(image, x + 1, y + SLOT - 1, SLOT - 1, 1, RIM);
        PackArt.fill(image, x + SLOT - 1, y + 1, 1, SLOT - 1, RIM);
    }

    /** A dark outline with a lit bevel, its corners cut and riveted. */
    static void frame(BufferedImage image, int height) {
        int width = PANEL_WIDTH;
        PackArt.fill(image, 0, 0, width, 1, OUTLINE);
        PackArt.fill(image, 0, height - 1, width, 1, OUTLINE);
        PackArt.fill(image, 0, 0, 1, height, OUTLINE);
        PackArt.fill(image, width - 1, 0, 1, height, OUTLINE);
        PackArt.fill(image, 1, 1, width - 2, 1, RIM);
        PackArt.fill(image, 1, 1, 1, height - 2, RIM);
        PackArt.fill(image, 1, height - 2, width - 2, 1, RIM_DARK);
        PackArt.fill(image, width - 2, 1, 1, height - 2, RIM_DARK);
        for (int[] corner : new int[][] {{0, 0}, {width - 1, 0}, {0, height - 1}, {width - 1, height - 1}}) {
            image.setRGB(corner[0], corner[1], 0);
        }
        for (int[] rivet : new int[][] {{3, 3}, {width - 5, 3}, {3, height - 5}, {width - 5, height - 5}}) {
            PackArt.fill(image, rivet[0], rivet[1], 2, 2, RIVET);
            image.setRGB(rivet[0], rivet[1], RIVET_SHINE);
        }
    }

    /** The menu tiles' capitals in cells three wide, two rows below the cell's top, as the label fonts stand them. */
    static BufferedImage letters() {
        String letters = PackArt.TileArt.LETTERS;
        var image = new BufferedImage(letters.length() * 3, CELL_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        for (int index = 0; index < letters.length(); index++) {
            char letter = letters.charAt(index);
            var own = OWN_LETTERS.get(letter);
            if (own == null) {
                PackArt.TileArt.letter(image, letter, index * 3, 2, PackArt.WHITE);
            } else {
                for (int pixel = 0; pixel < own.length(); pixel++) {
                    if (own.charAt(pixel) == '1') {
                        image.setRGB(index * 3 + pixel % 3, 2 + pixel / 3, PackArt.WHITE);
                    }
                }
            }
            pad(image, index * 3 + 2);
        }
        return image;
    }

    static BufferedImage marks() {
        var image = new BufferedImage(MARKS.size() * 5, CELL_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        for (int index = 0; index < MARKS.size(); index++) {
            PackArt.paint(image, MARKS.get(index), index * 5, 2, Map.of('#', PackArt.WHITE));
            pad(image, index * 5 + 4);
        }
        return image;
    }

    /** Each picture in a cell a row taller on either side, so a line font can stand it in a card's well or the header. */
    static BufferedImage pictures() {
        var image = new BufferedImage(PICTURES.size() * PICTURE_WIDTH, PICTURE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        for (int index = 0; index < PICTURES.size(); index++) {
            var picture = PICTURES.get(index);
            for (int y = 0; y < picture.rows().length; y++) {
                for (int x = 0; x < PICTURE_WIDTH; x++) {
                    char pixel = picture.rows()[y].charAt(x);
                    if (pixel != '.') {
                        image.setRGB(index * PICTURE_WIDTH + x, 1 + y, picture.palette().get(pixel));
                    }
                }
            }
            int corner = index * PICTURE_WIDTH + PICTURE_WIDTH - 1;
            if (image.getRGB(corner, PICTURE_HEIGHT - 1) == 0) {
                image.setRGB(corner, PICTURE_HEIGHT - 1, 0x01000000);
            }
        }
        return image;
    }

    /** A card's state band, white so the server tints it with the state's colour, lit along its top. */
    static BufferedImage band() {
        int width = span(CARD_COLUMNS) - 2;
        var image = new BufferedImage(width, CELL_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        PackArt.fill(image, 0, 0, width, 8, 0xFFC8C8C8);
        PackArt.fill(image, 0, 0, width, 1, PackArt.WHITE);
        PackArt.fill(image, 0, 7, width, 1, 0xFF8C8C8C);
        return image;
    }

    static String[] chestplate() {
        return new String[] {
                ".kk....kk.",
                "kAAk..kAAk",
                "kAAAkkAAAk",
                "kAHAAAAAAk",
                ".kHAAAAADk",
                "..kAAAADk.",
                "..kHAAADk.",
                "..kAAAADk.",
                "..kAAAADk.",
                "...kkkkk.."};
    }

    /** A glyph's advance runs to its last inked column, so a cell whose ink stops short gets a nearly clear pixel there. */
    static void pad(BufferedImage image, int column) {
        for (int y = 0; y < image.getHeight(); y++) {
            if ((image.getRGB(column, y) >>> 24) != 0) {
                return;
            }
        }
        image.setRGB(column, image.getHeight() - 1, 0x01000000);
    }

    static int slotX(int slot) {
        return SLOT_LEFT + slot % 9 * SLOT;
    }

    static int slotY(int slot) {
        return SLOT_TOP + slot / 9 * SLOT;
    }

    /** How wide or tall {@code slots} slots are from the first's item to the last's. */
    static int span(int slots) {
        return slots * SLOT - 2;
    }
}
