import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sternenfront's deploy screen: a window wider and taller than the chest it is drawn behind, with a loadout column left
 * of the slots and a spawn column right of them. The window is three glyphs, each under the client's 256-pixel limit
 * for a font texture, and each faction has its own colours. Everything that changes is drawn by the server from the
 * fonts written here: one font per text line, because a font places its glyphs at one height only.
 * <p>
 * Coordinates are the chest screen's own, from its top-left corner, where the title line starts at y 6. A glyph's top
 * lies at {@code 13 - ascent}, so its ascent follows from where it is drawn.
 */
public final class DeployArt {

    /** The window fits the client's smallest automatic screen, 426 by 240 GUI pixels, as its default window gets. */
    static final int LEFT = -124;
    static final int RIGHT = 300;
    static final int TOP = -8;
    static final int BOTTOM = 230;
    static final int CHEST_WIDTH = 176;

    /**
     * Chest slots, as Sternenfront's DeployScreen and TacticalLayout number them; DeployScreenTest keeps the two in step.
     * A card covers its slot and the ones right of and below it.
     */
    static final List<Integer> CLASS_CARDS = List.of(9, 14, 27, 32);
    static final int CLASS_CARD_COLUMNS = 4;
    static final int CLASS_CARD_ROWS = 2;
    static final List<Integer> SPECIALS = List.of(45, 48, 51);
    static final int SPECIAL_COLUMNS = 3;
    /** The toolbar's buttons from these slots, each up to the next or the row's end: close, then deploy. */
    static final List<Integer> BUTTONS = List.of(0, 4);
    static final int MAP_COLUMNS = 9;
    static final int MAP_ROWS = 3;

    static final int SLOT = 18;
    static final int COLUMNS = 9;
    /** Where the chest's first slot draws its item. */
    static final int SLOT_LEFT = 8;
    static final int SLOT_TOP = 18;
    /** Where the player's inventory below the six-row chest draws its first item. */
    static final int INVENTORY_TOP = 139;

    /** Tops of the server's text lines; Sternenfront's DeployScreen draws on no others. */
    static final int[] LINES = {-3, 18, 21, 24, 32, 34, 35, 38, 39, 40, 47, 48, 49, 50, 52, 54, 55, 62, 64, 65, 70, 71, 72, 74, 75, 76, 77, 79, 80, 86, 87, 92, 94, 95, 99, 107, 109, 110, 111, 112, 114, 115, 118, 122, 124, 125, 130, 134, 144, 153, 154, 155, 163, 165, 168, 173, 175, 178, 183, 185, 193, 195, 196, 202, 204, 205, 206, 214};

    static final String[] FACTIONS = {"kolonien", "hegemonie"};
    static final int[] ACCENTS = {0xFF3D8BFF, 0xFFE8473F};

    static final int BORDER = 0xFF07080B;
    static final int EDGE = 0xFF3A4050;
    static final int BODY = 0xFF1B1E26;
    static final int HEADER = 0xFF11131A;
    static final int WELL = 0xFF13161D;
    static final int PANEL = 0xFF171A22;
    static final int CARD = 0xFF222836;
    static final int CARD_EDGE = 0xFF343C4E;
    static final int MAP = 0xFF0C161D;
    static final int MAP_LINE = 0xFF142530;
    static final int MAP_CROSS = 0xFF1F3946;
    /** The colour the client prints the player-inventory label in; the label vanishes on it. */
    static final int LABEL_GREY = 0xFF404040;

    /** The portraits in one sheet each, in this order; the server picks a cell by its index. */
    static final List<String> UNITS = List.of("kolonien_soldier", "kolonien_heavy", "kolonien_scout", "kolonien_medic",
            "hegemonie_soldier", "hegemonie_heavy", "hegemonie_scout", "hegemonie_medic",
            "reinforcement_arc_trooper", "reinforcement_commando_droid", "hero_jedi", "hero_commander", "hero_sith", "hero_bounty_hunter");

    static final String LABELS = PackArt.TileArt.LETTERS + "%,()·";
    /** Letters the menu tiles lack, and a 1 with its stem in the middle, since theirs reads as a ] beside KP. */
    static final Map<Character, String> EXTRA_LABELS = Map.of(
            '%', "101001010100101", ',', "000000000001010", '(', "001010010010001", ')', "100010010010100",
            '·', "000000010000000", '1', "010110010010111");
    static final int PORTRAIT_WELL = 30;

    /** The first glyph of each run the art font and the line fonts hold; DeployScreen draws from the same numbers. */
    static final int WINDOW = 0xE000;
    static final int DOSSIERS = 0xE010;
    static final int CARD_PORTRAITS = 0xE030;
    static final int MINIS = 0xE050;
    static final int CLASS_SELECTION = 0xE060;
    static final int SPECIAL_SELECTION = 0xE062;
    static final int LABEL_GLYPHS = 0xE000;
    static final int ICON_GLYPHS = 0xE100;
    static final int ABILITY_GLYPHS = 0xE120;
    static final int BADGE_GLYPHS = 0xE140;
    static final int TOOLBAR_GLYPHS = 0xE150;
    static final int ICON_SIZE = 9;
    static final int ABILITY_SIZE = 16;
    static final int BADGE_SIZE = 13;
    static final int TOOLBAR_SIZE = 16;
    /** The menu icons the buttons wore as items, now drawn beside their labels: close, then deploy. */
    static final List<String> TOOLBAR_ICONS = List.of("tool_close", "deploy");

    /** A small picture for the text lines, nine pixels square. */
    record Icon(String name, String... rows) { }

    /** In the order of their glyphs from U+E100, which DeployScreen's own Icon names in the same order. */
    static final List<Icon> ICONS = List.of(
            new Icon("heart", ".........", ".##...##.", "####.####", "#########", "#########", ".#######.", "..#####..", "...###...", "....#...."),
            new Icon("recommended", "....#....", "....#....", "#########", ".#######.", "..#####..", ".###.###.", "##.....##", ".........", "........."),
            new Icon("pending", "###...###", "#.......#", "#.......#", ".........", ".........", ".........", "#.......#", "#.......#", "###...###"),
            new Icon("confirmed", "#########", "#.......#", "#.......#", "#.......#", "#.......#", "#.......#", "#.......#", "#.......#", "#########"),
            new Icon("locked", "...###...", "..#...#..", "..#...#..", ".#######.", ".#######.", ".###.###.", ".###.###.", ".#######.", "........."),
            new Icon("contested", "#.#.#.#.#", ".........", "#.......#", ".........", "#.......#", ".........", "#.......#", ".........", "#.#.#.#.#"),
            new Icon("headquarters", "#####....", "#####....", "#####....", "#........", "#........", "#........", "#........", "#........", "###......"),
            new Icon("dropship", "....#....", "...###...", "...###...", "..#####..", "..#####..", "..##.##..", ".##...##.", ".#.###.#.", "...#.#..."));

    private DeployArt() {
    }

    public static void main(String[] args) throws IOException {
        write(Path.of(args[0]));
    }

    static void write(Path root) throws IOException {
        Path textures = root.resolve("assets/blockcraft/textures/font/deploy");
        Path fonts = root.resolve("assets/blockcraft/font/deploy");
        Path art = root.toAbsolutePath().resolveSibling("art/sternenfront");
        Files.createDirectories(textures);
        Files.createDirectories(fonts);
        var artGlyphs = new ArrayList<String>();
        for (int faction = 0; faction < FACTIONS.length; faction++) {
            var window = window(ACCENTS[faction]);
            int[][] pieces = {{LEFT, 0}, {0, CHEST_WIDTH}, {CHEST_WIDTH, RIGHT}};
            String[] names = {"left", "center", "right"};
            for (int piece = 0; piece < pieces.length; piece++) {
                String file = "frame_" + names[piece] + "_" + FACTIONS[faction] + ".png";
                var image = window.getSubimage(pieces[piece][0] - LEFT, 0, pieces[piece][1] - pieces[piece][0], BOTTOM - TOP);
                var copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
                copy.createGraphics().drawImage(image, 0, 0, null);
                PackArt.write(copy, textures.resolve(file));
                artGlyphs.add(bitmap("deploy/" + file, TOP, BOTTOM - TOP, escape(WINDOW + faction * pieces.length + piece)));
            }
        }
        PackArt.write(sheet(art, "_dossier.png", 64, 64), textures.resolve("dossiers.png"));
        artGlyphs.add(bitmap("deploy/dossiers.png", 31, 64, run(DOSSIERS, UNITS.size())));
        PackArt.write(sheet(art, "_card.png", PORTRAIT_WELL, span(CLASS_CARD_ROWS) - 2), textures.resolve("cards.png"));
        var cardRows = cardRows();
        for (int row = 0; row < cardRows.size(); row++) {
            artGlyphs.add(bitmap("deploy/cards.png", cardRows.get(row) + 1, span(CLASS_CARD_ROWS) - 2, run(CARD_PORTRAITS + row * 0x10, UNITS.size())));
        }
        int specialTop = slotY(SPECIALS.getFirst());
        PackArt.write(sheet(art, "_mini.png", span(1) - 2, span(1) - 2), textures.resolve("minis.png"));
        artGlyphs.add(bitmap("deploy/minis.png", specialTop + 1, span(1) - 2, run(MINIS, UNITS.size())));
        PackArt.write(selection(span(CLASS_CARD_COLUMNS) + 2, span(CLASS_CARD_ROWS) + 2), textures.resolve("selected_class.png"));
        for (int row = 0; row < cardRows.size(); row++) {
            artGlyphs.add(bitmap("deploy/selected_class.png", cardRows.get(row) - 1, span(CLASS_CARD_ROWS) + 2, escape(CLASS_SELECTION + row)));
        }
        PackArt.write(selection(span(SPECIAL_COLUMNS) + 2, span(1) + 2), textures.resolve("selected_special.png"));
        artGlyphs.add(bitmap("deploy/selected_special.png", specialTop - 1, span(1) + 2, escape(SPECIAL_SELECTION)));
        Files.writeString(fonts.resolve("art.json"), font(artGlyphs));

        PackArt.write(labels(), textures.resolve("labels.png"));
        PackArt.write(icons(), textures.resolve("icons.png"));
        PackArt.write(toolbar(root.resolve("assets/blockcraft/textures/item")), textures.resolve("toolbar.png"));
        int abilities = squares(textures.resolveSibling("ability_icons.png"));
        int badges = squares(textures.resolveSibling("objective_pips.png"));
        for (int top : LINES) {
            var providers = new ArrayList<String>();
            providers.add("{\"type\":\"space\",\"advances\":{\" \":4}}");
            // The letter sheet is eight pixels tall, so its text cannot rise above the title line's top.
            if (top >= 5) {
                providers.addAll(PackArt.textProviders(13 - top));
            }
            providers.add(bitmap("deploy/labels.png", top - 2, 32, run(LABEL_GLYPHS, LABELS.length())));
            if (top >= 4) {
                providers.add(bitmap("deploy/icons.png", top, ICON_SIZE, run(ICON_GLYPHS, ICONS.size())));
                providers.add(bitmap("ability_icons.png", top, ABILITY_SIZE, run(ABILITY_GLYPHS, abilities)));
                providers.add(bitmap("objective_pips.png", top, BADGE_SIZE, run(BADGE_GLYPHS, badges)));
                providers.add(bitmap("deploy/toolbar.png", top, TOOLBAR_SIZE, run(TOOLBAR_GLYPHS, TOOLBAR_ICONS.size())));
            }
            Files.writeString(fonts.resolve("line_" + top + ".json"), font(providers));
        }
    }

    /** The whole window, faction-coloured, before it is cut into its three glyphs. */
    static BufferedImage window(int accent) {
        var image = new BufferedImage(RIGHT - LEFT, BOTTOM - TOP, BufferedImage.TYPE_INT_ARGB);
        var canvas = new Canvas(image);
        canvas.fill(LEFT, TOP, RIGHT - LEFT, BOTTOM - TOP, BODY);
        canvas.fill(LEFT, TOP, RIGHT - LEFT, 15 - TOP, HEADER);
        canvas.fill(LEFT + 1, 15, RIGHT - LEFT - 2, 1, mix(accent, BORDER, .55));
        canvas.fill(LEFT + 1, 16, RIGHT - LEFT - 2, 1, accent);
        canvas.fill(LEFT + 4, -4, 2, 17, accent);
        outline(canvas);

        canvas.panel(-120, 17, 117, 209);
        canvas.fill(-119, 18, 115, 10, HEADER);
        canvas.fill(-119, 28, 115, 1, EDGE);
        canvas.fill(-118, 30, 66, 66, BORDER);
        canvas.fill(-117, 31, 64, 64, WELL);
        for (int divider : new int[] {109, 130, 190}) {
            canvas.fill(-118, divider, 113, 1, EDGE);
        }
        for (int key = 0; key < 2; key++) {
            canvas.fill(-118, 142 + key * 24, 20, 20, BORDER);
            canvas.fill(-117, 143 + key * 24, 18, 18, CARD);
        }

        canvas.panel(179, 17, 117, 209);
        canvas.fill(180, 18, 115, 10, HEADER);
        canvas.fill(180, 28, 115, 1, EDGE);
        canvas.fill(181, 139, 113, 1, EDGE);

        for (int button = 0; button < BUTTONS.size(); button++) {
            int last = button + 1 < BUTTONS.size() ? BUTTONS.get(button + 1) : COLUMNS;
            PackArt.button(image, slotX(BUTTONS.get(button)) - LEFT, slotY(BUTTONS.get(button)) - TOP,
                    span(last - BUTTONS.get(button)), PackArt.BUTTONS[button]);
        }

        for (int slot : CLASS_CARDS) {
            card(canvas, slotX(slot), slotY(slot), accent);
        }
        canvas.fill(87, 38, 2, 66, 0xFF20242D);
        for (int slot : SPECIALS) {
            int x = slotX(slot);
            int y = slotY(slot);
            canvas.fill(x, y, span(SPECIAL_COLUMNS), span(1), BORDER);
            canvas.fill(x + 1, y + 1, span(SPECIAL_COLUMNS) - 2, span(1) - 2, CARD);
            canvas.fill(x + 1, y + 1, span(SPECIAL_COLUMNS) - 2, 1, CARD_EDGE);
            canvas.fill(x + 1, y + 1, span(1) - 2, span(1) - 2, WELL);
        }

        canvas.fill(7, 126, 162, 1, 0xFF2A2A2A);
        canvas.fill(7, 127, 162, 10, LABEL_GREY);
        canvas.fill(7, 137, 162, 1, 0xFF262626);
        canvas.fill(122, 127, 3, 10, accent);

        int mapLeft = SLOT_LEFT - 1;
        int mapTop = INVENTORY_TOP - 1;
        int mapWidth = MAP_COLUMNS * SLOT;
        int mapHeight = MAP_ROWS * SLOT;
        canvas.fill(mapLeft - 1, mapTop - 1, mapWidth + 2, mapHeight + 2, BORDER);
        canvas.fill(mapLeft, mapTop, mapWidth, mapHeight, MAP);
        for (int column = 1; column < MAP_COLUMNS; column++) {
            canvas.fill(mapLeft + column * SLOT, mapTop, 1, mapHeight, MAP_LINE);
        }
        for (int row = 1; row < MAP_ROWS; row++) {
            canvas.fill(mapLeft, mapTop + row * SLOT, mapWidth, 1, MAP_LINE);
        }
        for (int column = 1; column < MAP_COLUMNS; column++) {
            for (int row = 1; row < MAP_ROWS; row++) {
                int x = mapLeft + column * SLOT;
                int y = mapTop + row * SLOT;
                canvas.fill(x - 1, y, 3, 1, MAP_CROSS);
                canvas.fill(x, y - 1, 1, 3, MAP_CROSS);
            }
        }
        int mapRight = mapLeft + mapWidth - 1;
        int mapBottom = mapTop + mapHeight - 1;
        for (int[] corner : new int[][] {{mapLeft, mapTop, 1, 1}, {mapRight, mapTop, -1, 1}, {mapLeft, mapBottom, 1, -1}, {mapRight, mapBottom, -1, -1}}) {
            canvas.fill(Math.min(corner[0], corner[0] + corner[2] * 5), corner[1], 6, 1, accent);
            canvas.fill(corner[0], Math.min(corner[1], corner[1] + corner[3] * 5), 1, 6, accent);
        }
        canvas.fill(mapLeft, mapBottom + 4, mapWidth, 1, EDGE);
        return image;
    }

    /** A class card: a sunk well for the portrait on the left, the name and abilities on the right. */
    static void card(Canvas canvas, int x, int y, int accent) {
        int width = span(CLASS_CARD_COLUMNS);
        int height = span(CLASS_CARD_ROWS);
        canvas.fill(x, y, width, height, BORDER);
        canvas.fill(x + 1, y + 1, width - 2, height - 2, CARD);
        canvas.fill(x + 1, y + 1, PORTRAIT_WELL, height - 2, WELL);
        canvas.fill(x + 1 + PORTRAIT_WELL, y + 1, width - 2 - PORTRAIT_WELL, 1, mix(accent, CARD, .45));
        canvas.fill(x + 1 + PORTRAIT_WELL, y + height - 2, width - 2 - PORTRAIT_WELL, 1, 0xFF1A1F2A);
    }

    static int slotX(int slot) {
        return SLOT_LEFT + slot % COLUMNS * SLOT;
    }

    static int slotY(int slot) {
        return SLOT_TOP + slot / COLUMNS * SLOT;
    }

    /** How wide or tall {@code slots} slots are from the first's item to the last's, without the borders outside them. */
    static int span(int slots) {
        return slots * SLOT - 2;
    }

    /** The tops of the class cards' rows, in order. */
    static List<Integer> cardRows() {
        return CLASS_CARDS.stream().map(DeployArt::slotY).distinct().toList();
    }

    static void outline(Canvas canvas) {
        int width = RIGHT - LEFT;
        int height = BOTTOM - TOP;
        canvas.fill(LEFT, TOP, width, 1, BORDER);
        canvas.fill(LEFT, BOTTOM - 1, width, 1, BORDER);
        canvas.fill(LEFT, TOP, 1, height, BORDER);
        canvas.fill(RIGHT - 1, TOP, 1, height, BORDER);
        canvas.fill(LEFT + 1, TOP + 1, width - 2, 1, EDGE);
        canvas.fill(LEFT + 1, BOTTOM - 2, width - 2, 1, EDGE);
        canvas.fill(LEFT + 1, TOP + 1, 1, height - 2, EDGE);
        canvas.fill(RIGHT - 2, TOP + 1, 1, height - 2, EDGE);
        for (int[] corner : new int[][] {{LEFT, TOP}, {RIGHT - 1, TOP}, {LEFT, BOTTOM - 1}, {RIGHT - 1, BOTTOM - 1}}) {
            canvas.set(corner[0], corner[1], 0);
        }
        for (int[] corner : new int[][] {{LEFT + 1, TOP + 1}, {RIGHT - 2, TOP + 1}, {LEFT + 1, BOTTOM - 2}, {RIGHT - 2, BOTTOM - 2}}) {
            canvas.set(corner[0], corner[1], BORDER);
        }
    }

    /** A two-pixel white frame with a soft outer ring, laid around the chosen card. */
    static BufferedImage selection(int width, int height) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int ring = Math.min(Math.min(x, y), Math.min(width - 1 - x, height - 1 - y));
                if (ring < 2) {
                    image.setRGB(x, y, ring == 0 ? 0xFFF4F7FF : 0xFFB9C8E8);
                }
            }
        }
        image.setRGB(0, 0, 0);
        image.setRGB(width - 1, 0, 0);
        image.setRGB(0, height - 1, 0);
        image.setRGB(width - 1, height - 1, 0);
        return image;
    }

    static BufferedImage sheet(Path art, String suffix, int width, int height) throws IOException {
        var image = new BufferedImage(width * UNITS.size(), height, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        for (int index = 0; index < UNITS.size(); index++) {
            graphics.drawImage(ImageIO.read(art.resolve(UNITS.get(index) + suffix).toFile()), index * width, 0, null);
        }
        graphics.dispose();
        return image;
    }

    /** The menu tiles' letters, each in the top of a tall cell so a line may sit high above the title. */
    static BufferedImage labels() {
        var image = new BufferedImage(LABELS.length() * 3, 32, BufferedImage.TYPE_INT_ARGB);
        for (int index = 0; index < LABELS.length(); index++) {
            char letter = LABELS.charAt(index);
            String pixels = EXTRA_LABELS.get(letter);
            if (pixels == null) {
                PackArt.TileArt.letter(image, letter, index * 3, 2, PackArt.WHITE);
                continue;
            }
            for (int y = 0; y < 5; y++) {
                for (int x = 0; x < 3; x++) {
                    if (pixels.charAt(y * 3 + x) == '1') {
                        image.setRGB(index * 3 + x, 2 + y, PackArt.WHITE);
                    }
                }
            }
            image.setRGB(index * 3 + 2, 31, 0x01000000);
        }
        return image;
    }

    static BufferedImage icons() {
        var image = new BufferedImage(ICON_SIZE * ICONS.size(), ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int index = 0; index < ICONS.size(); index++) {
            var rows = ICONS.get(index).rows();
            for (int y = 0; y < rows.length; y++) {
                for (int x = 0; x < rows[y].length(); x++) {
                    char pixel = rows[y].charAt(x);
                    if (pixel != '.') {
                        image.setRGB(index * ICON_SIZE + x, y, PackArt.WHITE);
                    }
                }
            }
            int corner = index * ICON_SIZE + ICON_SIZE - 1;
            if (image.getRGB(corner, ICON_SIZE - 1) == 0) {
                image.setRGB(corner, ICON_SIZE - 1, 0x01000000);
            }
        }
        return image;
    }

    static BufferedImage toolbar(Path items) throws IOException {
        var image = new BufferedImage(TOOLBAR_SIZE * TOOLBAR_ICONS.size(), TOOLBAR_SIZE, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        for (int index = 0; index < TOOLBAR_ICONS.size(); index++) {
            graphics.drawImage(ImageIO.read(items.resolve(TOOLBAR_ICONS.get(index) + ".png").toFile()), index * TOOLBAR_SIZE, 0, null);
            int corner = index * TOOLBAR_SIZE + TOOLBAR_SIZE - 1;
            if (image.getRGB(corner, TOOLBAR_SIZE - 1) == 0) {
                image.setRGB(corner, TOOLBAR_SIZE - 1, 0x01000000);
            }
        }
        graphics.dispose();
        return image;
    }

    /** How many square cells a sheet another generator writes holds side by side. */
    static int squares(Path sheet) throws IOException {
        var image = ImageIO.read(sheet.toFile());
        return image.getWidth() / image.getHeight();
    }

    static String bitmap(String file, int top, int height, String chars) {
        return "{\"type\":\"bitmap\",\"file\":\"blockcraft:font/" + file + "\",\"ascent\":" + (13 - top)
                + ",\"height\":" + height + ",\"chars\":[\"" + chars + "\"]}";
    }

    static String font(List<String> providers) {
        return "{\n  \"providers\": [\n    " + String.join(",\n    ", providers) + "\n  ]\n}\n";
    }

    static String run(int first, int count) {
        var chars = new StringBuilder();
        for (int index = 0; index < count; index++) {
            chars.append(escape(first + index));
        }
        return chars.toString();
    }

    static String escape(int character) {
        return String.format(Locale.ROOT, "\\u%04x", character);
    }

    static int mix(int a, int b, double weight) {
        return PackArt.TileArt.mix(a, b, weight);
    }

    /** Draws in the chest screen's coordinates onto the window image. */
    record Canvas(BufferedImage image) {
        void fill(int x, int y, int width, int height, int argb) {
            PackArt.fill(image, x - LEFT, y - TOP, width, height, argb);
        }

        void set(int x, int y, int argb) {
            image.setRGB(x - LEFT, y - TOP, argb);
        }

        void panel(int x, int y, int width, int height) {
            fill(x, y, width, height, EDGE);
            fill(x + 1, y + 1, width - 2, height - 2, PANEL);
        }
    }
}
