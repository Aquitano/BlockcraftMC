import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

/**
 * Draws the resource pack's textures 1:1 in GUI pixels: menu panels per chest row count (plain and with a toolbar
 * row of three buttons), the tooltip behind a menu item's name, 8x8 HUD glyphs and 16x16 item icons. Icons are
 * text grids: '.' is transparent, 'o' the outline, every other letter a colour of the icon's palette.
 */
public final class PackArt {

    static final int PANEL_WIDTH = 176;
    static final int BORDER = 0xFF07080B;
    static final int EDGE = 0xFF3A4050;
    static final int BODY = 0xFF1B1E26;
    static final int HEADER = 0xFF11131A;
    static final int ACCENT = 0xFF4C6EF5;
    static final int ACCENT_DARK = 0xFF2D46B0;
    static final int SLOT_FILL = 0xFF101217;
    static final int SLOT_EDGE = 0xFF2C303B;
    static final int SLOT_SHINE = 0xFF3D4250;
    static final int DIVIDER = 0xFF2C303B;
    static final int WHITE = 0xFFFFFFFF;
    static final int OUTLINE = 0xFF17212D;
    static final int TOOLTIP_SIZE = 100;
    static final int TOOLTIP_MARGIN = 8;
    static final int TOOLTIP_BODY = 0xF2141720;

    static final int[][] BUTTONS = {
            {0xFFE04E4E, 0xFF9E2B2B, 0xFFF08A8A},
            {0xFFF2B632, 0xFFA97A12, 0xFFFAD57A},
            {0xFF3E8EF7, 0xFF1F55A8, 0xFF8BBCFA},
    };

    public static void main(String[] args) throws IOException {
        Path root = Path.of(args[0]);
        Path font = root.resolve("assets/blockcraft/textures/font");
        Path item = root.resolve("assets/blockcraft/textures/item");
        Path tooltip = root.resolve("assets/blockcraft/textures/gui/sprites/tooltip");
        if (args.length > 1) {
            var icon = ICONS.get(args[1]);
            write(pixels(icon.rows(), icon.palette()), item.resolve(args[1] + ".png"));
            return;
        }
        Files.createDirectories(font);
        Files.createDirectories(item);
        Files.createDirectories(tooltip);
        for (int rows = 1; rows <= 6; rows++) {
            write(panel(rows, false), font.resolve("menu_" + rows + ".png"));
        }
        for (int rows = 2; rows <= 6; rows++) {
            write(panel(rows, true), font.resolve("toolbar_" + rows + ".png"));
        }
        write(tooltipBackground(), tooltip.resolve("menu_background.png"));
        write(tooltipFrame(), tooltip.resolve("menu_frame.png"));
        write(glyphSheet(ICON_PROFILE, ICON_COINS, ICON_LEVEL, ICON_CHAT, ICON_SERVER, ICON_PLAYERS, ICON_MAP, ICON_KIT,
                ICON_SPECTATOR, ICON_TIME, ICON_EMERALD, ICON_WINNER, ICON_MODE, ICON_OPPONENT, ICON_KILLS), font.resolve("icons.png"));
        write(badges(), font.resolve("badges.png"));
        write(glyph(ICON_ERROR, ERROR), font.resolve("error.png"));
        write(display(), font.resolve("display.png"));
        for (var icon : ICONS.entrySet()) {
            write(pixels(icon.getValue().rows(), icon.getValue().palette()), item.resolve(icon.getKey() + ".png"));
        }
        TileArt.write(root);
    }

    static BufferedImage panel(int rows, boolean toolbar) {
        int height = 114 + rows * 18;
        var image = new BufferedImage(PANEL_WIDTH, height, BufferedImage.TYPE_INT_ARGB);
        fill(image, 0, 0, PANEL_WIDTH, height, BODY);
        fill(image, 0, 0, PANEL_WIDTH, 17, HEADER);
        fill(image, 1, 15, PANEL_WIDTH - 2, 1, ACCENT_DARK);
        fill(image, 1, 16, PANEL_WIDTH - 2, 1, ACCENT);
        for (int row = 0; row < rows; row++) {
            if (toolbar && row == 0) {
                for (int button = 0; button < 3; button++) {
                    button(image, 8 + button * 54, 18, BUTTONS[button]);
                }
                continue;
            }
            for (int col = 0; col < 9; col++) {
                slot(image, 7 + col * 18, 17 + row * 18);
            }
        }
        int inventoryTop = rows * 18 + 30;
        fill(image, 7, rows * 18 + 23, PANEL_WIDTH - 14, 1, DIVIDER);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slot(image, 7 + col * 18, inventoryTop + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            slot(image, 7 + col * 18, rows * 18 + 88);
        }
        outline(image, PANEL_WIDTH, height);
        return image;
    }

    /**
     * The tooltip sprites keep vanilla's shape: a 100x100 nine-slice whose outer {@link #TOOLTIP_MARGIN} pixels are
     * empty, because the client draws them that far outside the text, and whose corners are cut by one pixel.
     */
    static BufferedImage tooltipBackground() {
        var image = new BufferedImage(TOOLTIP_SIZE, TOOLTIP_SIZE, BufferedImage.TYPE_INT_ARGB);
        int inner = TOOLTIP_SIZE - 2 * TOOLTIP_MARGIN;
        fill(image, TOOLTIP_MARGIN, TOOLTIP_MARGIN, inner, inner, TOOLTIP_BODY);
        for (int[] corner : tooltipCorners(TOOLTIP_MARGIN)) {
            image.setRGB(corner[0], corner[1], 0);
        }
        return image;
    }

    static BufferedImage tooltipFrame() {
        var image = new BufferedImage(TOOLTIP_SIZE, TOOLTIP_SIZE, BufferedImage.TYPE_INT_ARGB);
        int edge = TOOLTIP_MARGIN + 1;
        int inner = TOOLTIP_SIZE - 2 * edge;
        fill(image, edge, edge, inner, 1, ACCENT);
        fill(image, edge, TOOLTIP_SIZE - edge - 1, inner, 1, EDGE);
        fill(image, edge, edge, 1, inner, EDGE);
        fill(image, TOOLTIP_SIZE - edge - 1, edge, 1, inner, EDGE);
        for (int[] corner : tooltipCorners(edge)) {
            image.setRGB(corner[0], corner[1], 0);
        }
        return image;
    }

    static int[][] tooltipCorners(int inset) {
        int far = TOOLTIP_SIZE - inset - 1;
        return new int[][] {{inset, inset}, {far, inset}, {inset, far}, {far, far}};
    }

    static void slot(BufferedImage image, int x, int y) {
        fill(image, x, y, 18, 18, SLOT_EDGE);
        fill(image, x + 1, y + 1, 16, 16, SLOT_FILL);
        fill(image, x + 1, y + 17, 16, 1, SLOT_SHINE);
        fill(image, x + 17, y + 1, 1, 17, SLOT_SHINE);
    }

    /** Three slots wide and one high: a bevelled bar the middle slot's icon item sits on. */
    static void button(BufferedImage image, int x, int y, int[] colours) {
        int fill = colours[0];
        int shade = colours[1];
        int shine = colours[2];
        fill(image, x, y, 52, 16, shade);
        fill(image, x + 1, y + 1, 50, 14, fill);
        fill(image, x + 1, y + 1, 50, 1, shine);
        fill(image, x + 1, y + 1, 1, 13, shine);
        fill(image, x + 1, y + 14, 50, 1, shade);
        for (int[] corner : new int[][] {{x, y}, {x + 51, y}, {x, y + 15}, {x + 51, y + 15}}) {
            image.setRGB(corner[0], corner[1], BODY);
        }
    }

    /** A one pixel border with a lighter line inside and the corners cut, so the panel reads as a rounded card. */
    static void outline(BufferedImage image, int width, int height) {
        fill(image, 1, 1, width - 2, 1, EDGE);
        fill(image, 1, height - 2, width - 2, 1, EDGE);
        fill(image, 1, 1, 1, height - 2, EDGE);
        fill(image, width - 2, 1, 1, height - 2, EDGE);
        fill(image, 1, 17, width - 2, 1, EDGE);
        fill(image, 0, 0, width, 1, BORDER);
        fill(image, 0, height - 1, width, 1, BORDER);
        fill(image, 0, 0, 1, height, BORDER);
        fill(image, width - 1, 0, 1, height, BORDER);
        for (int[] corner : new int[][] {{0, 0}, {width - 1, 0}, {0, height - 1}, {width - 1, height - 1}}) {
            image.setRGB(corner[0], corner[1], 0);
        }
        for (int[] corner : new int[][] {{1, 1}, {width - 2, 1}, {1, height - 2}, {width - 2, height - 2}}) {
            image.setRGB(corner[0], corner[1], BORDER);
        }
    }

    static void fill(BufferedImage image, int x, int y, int width, int height, int argb) {
        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                image.setRGB(x + dx, y + dy, argb);
            }
        }
    }

    /** 8x8 glyphs side by side; the font provider reads them left to right. White, so the text colour tints them. */
    static BufferedImage glyphSheet(String[]... glyphs) {
        var image = new BufferedImage(8 * glyphs.length, 8, BufferedImage.TYPE_INT_ARGB);
        for (int index = 0; index < glyphs.length; index++) {
            paint(image, glyphs[index], index * 8, 0, Map.of('#', WHITE));
        }
        return image;
    }

    /** The badges side by side in cells as wide as the widest; the client trims each glyph to its own width. */
    static BufferedImage badges() {
        int cell = BADGES.stream().mapToInt(badge -> badgeWidth(badge.name())).max().orElseThrow();
        var image = new BufferedImage(cell * BADGES.size(), BADGE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        for (int index = 0; index < BADGES.size(); index++) {
            badge(image, BADGES.get(index), index * cell);
        }
        return image;
    }

    /** Two pixels before the first letter, each letter followed by the column its shade falls on, one pixel after. */
    static int badgeWidth(String name) {
        return 2 + name.chars().map(letter -> BADGE_LETTERS.get((char) letter)[0].length() + 1).sum() + 1;
    }

    /** As tall as a capital letter: a bevelled bar with cut corners, the name in white with a shade to its right. */
    static void badge(BufferedImage image, Badge badge, int x) {
        int width = badgeWidth(badge.name());
        fill(image, x, 0, width, 1, badge.shine());
        fill(image, x, 1, width, BADGE_HEIGHT - 2, badge.fill());
        fill(image, x, BADGE_HEIGHT - 1, width, 1, badge.shade());
        for (int[] corner : new int[][] {{x, 0}, {x + width - 1, 0}, {x, BADGE_HEIGHT - 1}, {x + width - 1, BADGE_HEIGHT - 1}}) {
            image.setRGB(corner[0], corner[1], 0);
        }
        int left = x + 2;
        for (char letter : badge.name().toCharArray()) {
            var rows = BADGE_LETTERS.get(letter);
            for (int dy = 0; dy < rows.length; dy++) {
                for (int dx = 0; dx < rows[dy].length(); dx++) {
                    if (rows[dy].charAt(dx) != '#') {
                        continue;
                    }
                    image.setRGB(left + dx, 1 + dy, WHITE);
                    if (dx + 1 == rows[dy].length() || rows[dy].charAt(dx + 1) != '#') {
                        image.setRGB(left + dx + 1, 1 + dy, badge.shade());
                    }
                }
            }
            left += rows[0].length() + 1;
        }
    }

    /**
     * The title lettering, laid out as font/display.json reads it: letters with two-pixel strokes, white over a
     * lighter shade so the text colour tints a two-tone face, inside a dark outline that the tint only darkens.
     */
    static BufferedImage display() {
        var image = new BufferedImage(DISPLAY_CELL_WIDTH * DISPLAY_COLUMNS, DISPLAY_CELL_HEIGHT * DISPLAY_SHEET.length,
                BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < DISPLAY_SHEET.length; row++) {
            for (int column = 0; column < DISPLAY_SHEET[row].length(); column++) {
                displayGlyph(image, DISPLAY_SHEET[row].charAt(column), column * DISPLAY_CELL_WIDTH, row * DISPLAY_CELL_HEIGHT);
            }
        }
        return image;
    }

    /** The letter stands on the cell's last row but one, the outline below it; an umlaut's dots sit two rows above. */
    static void displayGlyph(BufferedImage image, char glyph, int x, int y) {
        char letter = UMLAUTS.getOrDefault(glyph, glyph);
        var rows = DISPLAY_LETTERS.get(letter);
        int top = DISPLAY_CELL_HEIGHT - 1 - rows.length;
        var ink = new int[DISPLAY_CELL_HEIGHT][DISPLAY_CELL_WIDTH];
        for (int dy = 0; dy < rows.length; dy++) {
            for (int dx = 0; dx < rows[dy].length(); dx++) {
                if (rows[dy].charAt(dx) == '#') {
                    ink[top + dy][1 + dx] = dy < rows.length / 2 ? WHITE : DISPLAY_SHADE;
                }
            }
        }
        if (UMLAUTS.containsKey(glyph)) {
            for (int dx = 0; dx < UMLAUT_DOTS.length(); dx++) {
                if (UMLAUT_DOTS.charAt(dx) == '#') {
                    ink[top - 2][1 + dx] = WHITE;
                }
            }
        }
        for (int dy = 0; dy < DISPLAY_CELL_HEIGHT; dy++) {
            for (int dx = 0; dx < DISPLAY_CELL_WIDTH; dx++) {
                if (ink[dy][dx] != 0) {
                    image.setRGB(x + dx, y + dy, ink[dy][dx]);
                } else if (touchesInk(ink, dx, dy)) {
                    image.setRGB(x + dx, y + dy, DISPLAY_OUTLINE);
                }
            }
        }
    }

    static boolean touchesInk(int[][] ink, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int row = y + dy;
                int column = x + dx;
                if (row >= 0 && row < ink.length && column >= 0 && column < ink[row].length && ink[row][column] != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A glyph in its own colours rather than white, so it is shown untinted. */
    static BufferedImage glyph(String[] rows, Map<Character, Integer> palette) {
        var image = new BufferedImage(rows[0].length(), rows.length, BufferedImage.TYPE_INT_ARGB);
        paint(image, rows, 0, 0, palette);
        return image;
    }

    static BufferedImage pixels(String[] rows, Map<Character, Integer> palette) {
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        paint(image, rows, 0, 0, palette);
        return image;
    }

    static void paint(BufferedImage image, String[] rows, int x, int y, Map<Character, Integer> palette) {
        for (int dy = 0; dy < rows.length; dy++) {
            for (int dx = 0; dx < rows[dy].length(); dx++) {
                char pixel = rows[dy].charAt(dx);
                if (pixel == 'o') {
                    image.setRGB(x + dx, y + dy, OUTLINE);
                } else if (pixel != '.') {
                    image.setRGB(x + dx, y + dy, palette.get(pixel));
                }
            }
        }
    }

    static void write(BufferedImage image, Path file) throws IOException {
        ImageIO.write(image, "png", new File(file.toString()));
        System.out.println("wrote " + file);
    }

    record Icon(String[] rows, Map<Character, Integer> palette) {
    }

    record Badge(String name, int fill, int shade, int shine) {
    }

    static final int BADGE_HEIGHT = 7;

    /** In the order of their glyphs from U+E300, which luckperms/ranks.json hands each group as its badge. */
    static final List<Badge> BADGES = List.of(
            new Badge("SPIELER", 0xFF7D8DA1, 0xFF52697F, 0xFF9DADC0),
            new Badge("PREMIUM", 0xFFE8891C, 0xFFA0560C, 0xFFF6B060),
            new Badge("YT", 0xFFA347BA, 0xFF6B2A80, 0xFFC273D6),
            new Badge("SUP", 0xFFD9B814, 0xFF8F7608, 0xFFF0D84E),
            new Badge("MOD", 0xFFE04E4E, 0xFF9E2B2B, 0xFFF08A8A),
            new Badge("DEV", 0xFF2FA9C4, 0xFF1B6E82, 0xFF6CCBE0),
            new Badge("ADMIN", 0xFFB3262B, 0xFF70161A, 0xFFD3555A),
            new Badge("OWNER", 0xFF3E8EF7, 0xFF1F55A8, 0xFF8BBCFA)
    );

    static final Map<Character, String[]> BADGE_LETTERS = Map.ofEntries(
            Map.entry('A', new String[] {".##.", "#..#", "####", "#..#", "#..#"}),
            Map.entry('D', new String[] {"###.", "#..#", "#..#", "#..#", "###."}),
            Map.entry('E', new String[] {"####", "#...", "###.", "#...", "####"}),
            Map.entry('I', new String[] {"###", ".#.", ".#.", ".#.", "###"}),
            Map.entry('L', new String[] {"#...", "#...", "#...", "#...", "####"}),
            Map.entry('M', new String[] {"#...#", "##.##", "#.#.#", "#...#", "#...#"}),
            Map.entry('N', new String[] {"#..#", "##.#", "#.##", "#..#", "#..#"}),
            Map.entry('O', new String[] {".##.", "#..#", "#..#", "#..#", ".##."}),
            Map.entry('P', new String[] {"###.", "#..#", "###.", "#...", "#..."}),
            Map.entry('R', new String[] {"###.", "#..#", "###.", "#.#.", "#..#"}),
            Map.entry('S', new String[] {".###", "#...", ".##.", "...#", "###."}),
            Map.entry('T', new String[] {"###", ".#.", ".#.", ".#.", ".#."}),
            Map.entry('U', new String[] {"#..#", "#..#", "#..#", "#..#", ".##."}),
            Map.entry('V', new String[] {"#...#", "#...#", ".#.#.", ".#.#.", "..#.."}),
            Map.entry('W', new String[] {"#...#", "#...#", "#.#.#", "##.##", "#...#"}),
            Map.entry('Y', new String[] {"#...#", ".#.#.", "..#..", "..#..", "..#.."})
    );

    /** The widest letter and its outline across; an umlaut's dots, the letter and both outlines down. */
    static final int DISPLAY_CELL_WIDTH = 9;
    static final int DISPLAY_CELL_HEIGHT = 12;
    static final int DISPLAY_COLUMNS = 16;
    static final int DISPLAY_SHADE = 0xFFC4C4C4;
    static final int DISPLAY_OUTLINE = 0xFF262626;
    static final String[] DISPLAY_SHEET = {"ABCDEFGHIJKLMNOP", "QRSTUVWXYZÄÖÜ012", "3456789!?.:-"};
    static final Map<Character, Character> UMLAUTS = Map.of('Ä', 'A', 'Ö', 'O', 'Ü', 'U');
    static final String UMLAUT_DOTS = "##..##";

    static final Map<Character, String[]> DISPLAY_LETTERS = Map.ofEntries(
            Map.entry('A', new String[] {".####.", "##..##", "##..##", "##..##", "######", "##..##", "##..##", "##..##"}),
            Map.entry('B', new String[] {"#####.", "##..##", "##..##", "#####.", "##..##", "##..##", "##..##", "#####."}),
            Map.entry('C', new String[] {".####.", "##..##", "##....", "##....", "##....", "##....", "##..##", ".####."}),
            Map.entry('D', new String[] {"#####.", "##..##", "##..##", "##..##", "##..##", "##..##", "##..##", "#####."}),
            Map.entry('E', new String[] {"#####", "##...", "##...", "####.", "##...", "##...", "##...", "#####"}),
            Map.entry('F', new String[] {"#####", "##...", "##...", "####.", "##...", "##...", "##...", "##..."}),
            Map.entry('G', new String[] {".####.", "##..##", "##....", "##....", "##.###", "##..##", "##..##", ".####."}),
            Map.entry('H', new String[] {"##..##", "##..##", "##..##", "######", "##..##", "##..##", "##..##", "##..##"}),
            Map.entry('I', new String[] {"##", "##", "##", "##", "##", "##", "##", "##"}),
            Map.entry('J', new String[] {"....##", "....##", "....##", "....##", "....##", "##..##", "##..##", ".####."}),
            Map.entry('K', new String[] {"##..##", "##.##.", "####..", "###...", "####..", "##.##.", "##..##", "##..##"}),
            Map.entry('L', new String[] {"##...", "##...", "##...", "##...", "##...", "##...", "##...", "#####"}),
            Map.entry('M', new String[] {"##...##", "###.###", "#######", "##.#.##", "##...##", "##...##", "##...##", "##...##"}),
            Map.entry('N', new String[] {"##..##", "###.##", "######", "##.###", "##..##", "##..##", "##..##", "##..##"}),
            Map.entry('O', new String[] {".####.", "##..##", "##..##", "##..##", "##..##", "##..##", "##..##", ".####."}),
            Map.entry('P', new String[] {"#####.", "##..##", "##..##", "#####.", "##....", "##....", "##....", "##...."}),
            Map.entry('Q', new String[] {".####.", "##..##", "##..##", "##..##", "##..##", "##.###", ".####.", "....##"}),
            Map.entry('R', new String[] {"#####.", "##..##", "##..##", "#####.", "####..", "##.##.", "##..##", "##..##"}),
            Map.entry('S', new String[] {".####.", "##..##", "##....", ".####.", "....##", "....##", "##..##", ".####."}),
            Map.entry('T', new String[] {"######", "..##..", "..##..", "..##..", "..##..", "..##..", "..##..", "..##.."}),
            Map.entry('U', new String[] {"##..##", "##..##", "##..##", "##..##", "##..##", "##..##", "##..##", ".####."}),
            Map.entry('V', new String[] {"##..##", "##..##", "##..##", "##..##", "##..##", ".####.", ".####.", "..##.."}),
            Map.entry('W', new String[] {"##...##", "##...##", "##...##", "##.#.##", "##.#.##", "#######", "###.###", "##...##"}),
            Map.entry('X', new String[] {"##..##", "##..##", ".####.", "..##..", "..##..", ".####.", "##..##", "##..##"}),
            Map.entry('Y', new String[] {"##..##", "##..##", "##..##", ".####.", "..##..", "..##..", "..##..", "..##.."}),
            Map.entry('Z', new String[] {"######", "....##", "...##.", "..##..", ".##...", "##....", "##....", "######"}),
            Map.entry('0', new String[] {".####.", "##..##", "##.###", "######", "###.##", "##..##", "##..##", ".####."}),
            Map.entry('1', new String[] {"..##", ".###", "####", "..##", "..##", "..##", "..##", "..##"}),
            Map.entry('2', new String[] {".####.", "##..##", "....##", "...##.", "..##..", ".##...", "##....", "######"}),
            Map.entry('3', new String[] {".####.", "##..##", "....##", "..###.", "....##", "....##", "##..##", ".####."}),
            Map.entry('4', new String[] {"...###", "..####", ".##.##", "##..##", "######", "....##", "....##", "....##"}),
            Map.entry('5', new String[] {"######", "##....", "##....", "#####.", "....##", "....##", "##..##", ".####."}),
            Map.entry('6', new String[] {".####.", "##..##", "##....", "#####.", "##..##", "##..##", "##..##", ".####."}),
            Map.entry('7', new String[] {"######", "....##", "....##", "...##.", "..##..", "..##..", "..##..", "..##.."}),
            Map.entry('8', new String[] {".####.", "##..##", "##..##", ".####.", "##..##", "##..##", "##..##", ".####."}),
            Map.entry('9', new String[] {".####.", "##..##", "##..##", "##..##", ".#####", "....##", "##..##", ".####."}),
            Map.entry('!', new String[] {"##", "##", "##", "##", "##", "..", "##", "##"}),
            Map.entry('?', new String[] {".####.", "##..##", "....##", "...##.", "..##..", "..##..", "......", "..##.."}),
            Map.entry('.', new String[] {"..", "..", "..", "..", "..", "..", "##", "##"}),
            Map.entry(':', new String[] {"..", "##", "##", "..", "..", "##", "##", ".."}),
            Map.entry('-', new String[] {"....", "....", "....", "####", "####", "....", "....", "...."})
    );

    static final Map<Character, Integer> LIGHT = Map.of('H', 0xFFF4FAFC, 'W', 0xFFCCDCE5, 'w', 0xFF8CA3B4, 'd', 0xFF52697F);
    static final Map<Character, Integer> RED = Map.of('H', 0xFFFFB6A4, 'R', 0xFFEB655E, 'r', 0xFFB83F48, 'd', 0xFF743349);
    static final Map<Character, Integer> GOLD = Map.of('H', 0xFFFFF0AE, 'G', 0xFFEAC05B, 'g', 0xFFB67C38, 'd', 0xFF795033, 'R', 0xFFE66E68, 'C', 0xFF72D9CE);
    static final Map<Character, Integer> GREEN = Map.of('H', 0xFFCCFAD4, 'L', 0xFF85E4AC, 'G', 0xFF42BF83, 'g', 0xFF26895F, 'd', 0xFF205749);
    static final Map<Character, Integer> STEEL = Map.of('H', 0xFFF2FAFC, 'W', 0xFFCCDCE5, 'w', 0xFF8CA3B4, 'G', 0xFFEAC05B, 'g', 0xFFB67C38, 'B', 0xFF936344, 'b', 0xFF553E37);
    static final Map<Character, Integer> CYAN = Map.of('H', 0xFFD3FAF6, 'C', 0xFF80D9DB, 'c', 0xFF42A4B4, 'd', 0xFF2A657F);
    static final Map<Character, Integer> GEAR = Map.of('H', 0xFFF2FAFC, 'L', 0xFFCCDCE5, 'M', 0xFF8CA3B4, 'D', 0xFF52697F);
    static final Map<Character, Integer> STAR = Map.of('H', 0xFFFFD5EC, 'L', 0xFFF0ACD1, 'M', 0xFFCC6DAA, 'D', 0xFF894777);
    static final Map<Character, Integer> HOUSE = Map.of('H', 0xFFFFB6A4, 'R', 0xFFEB655E, 'r', 0xFFB83F48, 'W', 0xFFF0E5D2, 'w', 0xFFC5B49D, 'e', 0xFF80BED3, 'B', 0xFF936344, 'b', 0xFF553E37);
    static final Map<Character, Integer> CHEST = Map.of('H', 0xFFE7B378, 'B', 0xFFBB8958, 'b', 0xFF936344, 'd', 0xFF553E37, 'G', 0xFFFFE5A0, 'g', 0xFFC4964B);
    static final Map<Character, Integer> PERSON = Map.of('H', 0xFF916444, 'h', 0xFF503A32, 'S', 0xFFEAC29A, 's', 0xFFC48E6B, 'E', 0xFFF2FAFC, 'e', 0xFF426E87, 'T', 0xFF74CBC6, 't', 0xFF3C929C, 'd', 0xFF285F74);
    static final Map<Character, Integer> SHIRT = Map.of('H', 0xFFF2FAFC, 'W', 0xFFD4E0E5, 'w', 0xFF8CA3B4, 'R', 0xFFCC6DAA, 'r', 0xFF894777);
    static final Map<Character, Integer> COMPASS = Map.of('G', 0xFFEAC05B, 'g', 0xFFB67C38, 'H', 0xFFF2FAFC, 'W', 0xFFCCDCE5, 'R', 0xFFEB655E, 'r', 0xFFB83F48, 'D', 0xFF52697F, 'd', 0xFF354758);
    static final Map<Character, Integer> ICE = Map.of('H', 0xFFF2FAFC, 'W', 0xFFD3FAF6, 'C', 0xFF80D9DB, 'c', 0xFF42A4B4, 'd', 0xFF2A657F);
    static final Map<Character, Integer> PEEK = Map.of('H', 0xFFE7B378, 'B', 0xFFBB8958, 'b', 0xFF936344, 'd', 0xFF553E37, 'W', 0xFFF2FAFC, 'e', 0xFF426E87);
    static final Map<Character, Integer> BOOK = Map.of('H', 0xFFFFB6A4, 'R', 0xFFEB655E, 'r', 0xFFB83F48, 'd', 0xFF743349, 'W', 0xFFF0E5D2, 'w', 0xFFC5B49D, 'G', 0xFFEAC05B);
    static final Map<Character, Integer> LENS = Map.of('G', 0xFFCCDCE5, 'g', 0xFF52697F, 'C', 0xFF80D9DB, 'c', 0xFF42A4B4, 'H', 0xFFF2FAFC, 'B', 0xFFBB8958, 'b', 0xFF553E37);
    static final Map<Character, Integer> EYE = Map.of('H', 0xFFF2FAFC, 'W', 0xFFCCDCE5, 'w', 0xFF8CA3B4, 'e', 0xFF74CBC6, 'E', 0xFF285F74);
    static final Map<Character, Integer> BALLOT = Map.of('H', 0xFFF2FAFC, 'W', 0xFFD4E0E5, 'w', 0xFF8CA3B4, 'G', 0xFF42BF83, 'g', 0xFF26895F);
    static final Map<Character, Integer> HOURGLASS = Map.of('H', 0xFFF2FAFC, 'W', 0xFFCCDCE5, 'w', 0xFF8CA3B4, 'G', 0xFFEAC05B, 'g', 0xFFB67C38, 'B', 0xFFBB8958, 'b', 0xFF553E37);
    static final Map<Character, Integer> FRIENDS = Map.of('H', 0xFF916444, 'h', 0xFF503A32, 'S', 0xFFEAC29A, 's', 0xFFC48E6B, 'e', 0xFF426E87, 'T', 0xFF74CBC6, 't', 0xFF3C929C, 'R', 0xFFCC6DAA, 'r', 0xFF894777);
    static final Map<Character, Integer> ADD_FRIEND = Map.of('H', 0xFF916444, 'h', 0xFF503A32, 'S', 0xFFEAC29A, 's', 0xFFC48E6B, 'e', 0xFF426E87, 'T', 0xFF74CBC6, 't', 0xFF3C929C, 'G', 0xFF42BF83, 'g', 0xFF26895F);
    static final Map<Character, Integer> MAIL = Map.of('H', 0xFFF2FAFC, 'W', 0xFFD4E0E5, 'w', 0xFF8CA3B4, 'd', 0xFF52697F, 'R', 0xFFEB655E, 'r', 0xFFB83F48);
    static final Map<Character, Integer> HOOK = Map.of('H', 0xFFF2FAFC, 'W', 0xFFCCDCE5, 'w', 0xFF8CA3B4, 'd', 0xFF52697F, 'B', 0xFFE7B378, 'b', 0xFFBB8958);
    static final Map<Character, Integer> ROCKET = Map.of('Y', 0xFFFFF0AE, 'G', 0xFFEAC05B, 'g', 0xFFB67C38, 'H', 0xFFFFB6A4, 'R', 0xFFEB655E, 'r', 0xFFB83F48, 'W', 0xFFF2FAFC, 'B', 0xFF936344, 'b', 0xFF553E37, 'C', 0xFF80D9DB);
    static final Map<Character, Integer> TRAIL = Map.of('H', 0xFFFFF0AE, 'G', 0xFFEAC05B, 'g', 0xFFB67C38, 'L', 0xFFF0ACD1, 'M', 0xFFCC6DAA, 'C', 0xFF80D9DB, 'c', 0xFF42A4B4);
    static final Map<Character, Integer> TAG = Map.of('H', 0xFF8BBCFA, 'B', 0xFF3E8EF7, 'b', 0xFF1F55A8, 'W', 0xFFF2FAFC);
    static final Map<Character, Integer> RACK = Map.of('H', 0xFFF2FAFC, 'L', 0xFFCCDCE5, 'M', 0xFF8CA3B4, 'D', 0xFF52697F, 'G', 0xFF85E4AC);
    static final Map<Character, Integer> CORE = Map.of('H', 0xFFF2FAFC, 'W', 0xFFD3FAF6, 'w', 0xFF8CA3B4, 'C', 0xFF80D9DB, 'c', 0xFF42A4B4, 'd', 0xFF3B2A4D);
    static final Map<Character, Integer> TEAMS = Map.of('W', 0xFFBB8958, 'w', 0xFF936344, 'R', 0xFFEB655E, 'r', 0xFFB83F48, 'B', 0xFF3E8EF7, 'b', 0xFF1F55A8);
    static final Map<Character, Integer> PEARL = Map.of('H', 0xFFD3FAF6, 'C', 0xFF4FB7A8, 'c', 0xFF2A7A6E, 'd', 0xFF1C4A44, 'P', 0xFFB06BD6, 'p', 0xFF6B3A8C);
    static final Map<Character, Integer> KNIFE = Map.of('H', 0xFFF2FAFC, 'W', 0xFFCCDCE5, 'w', 0xFF8CA3B4, 'R', 0xFFEB655E, 'r', 0xFFB83F48, 'B', 0xFFBB8958, 'b', 0xFF936344);
    static final Map<Character, Integer> ISLAND = Map.of('H', 0xFFCCFAD4, 'G', 0xFF42BF83, 'g', 0xFF26895F, 'B', 0xFFBB8958, 'b', 0xFF936344, 'd', 0xFF553E37, 'S', 0xFF8CA3B4, 's', 0xFF52697F);
    static final Map<Character, Integer> GIFT = Map.of('H', 0xFFFFB6A4, 'R', 0xFFEB655E, 'r', 0xFFB83F48, 'd', 0xFF743349, 'Y', 0xFFFFF0AE, 'G', 0xFFEAC05B, 'g', 0xFFB67C38);
    static final Map<Character, Integer> CLIPBOARD = Map.of('H', 0xFFF2FAFC, 'W', 0xFFD4E0E5, 'w', 0xFF8CA3B4, 'D', 0xFF52697F, 'B', 0xFFBB8958, 'b', 0xFF936344, 'd', 0xFF553E37, 'G', 0xFF42BF83);
    static final Map<Character, Integer> MEDAL = Map.of('H', 0xFFFFF0AE, 'G', 0xFFEAC05B, 'g', 0xFFB67C38, 'd', 0xFF795033, 'R', 0xFFEB655E, 'r', 0xFFB83F48, 'P', 0xFFFFB6A4);
    static final Map<Character, Integer> PET = Map.of('H', 0xFFFFF0AE, 'G', 0xFFEAC05B, 'g', 0xFFB67C38, 'd', 0xFF795033, 'W', 0xFFF2FAFC, 'P', 0xFFF0ACD1);
    static final Map<Character, Integer> PODIUM = Map.of('Y', 0xFFFFF0AE, 'G', 0xFFEAC05B, 'g', 0xFFB67C38, 'H', 0xFFF2FAFC, 'W', 0xFFCCDCE5, 'w', 0xFF8CA3B4, 'E', 0xFFE7B378, 'B', 0xFFBB8958, 'b', 0xFF936344);
    static final Map<Character, Integer> ENCHANT = Map.of('H', 0xFFFFD5EC, 'L', 0xFFF0ACD1, 'M', 0xFFCC6DAA, 'D', 0xFF894777, 'W', 0xFFF0E5D2, 'w', 0xFFC5B49D, 'C', 0xFF80D9DB, 'c', 0xFF42A4B4);

    static final Map<String, Icon> ICONS = Map.ofEntries(
            Map.entry("tool_settings", new Icon(new String[] {
                    "................",
                    "......oooo......",
                    "...oo.oHLo.oo...",
                    "..oHLooLMooHMo..",
                    "..oLMLLMMMLMDo..",
                    "...oLMooooMDo...",
                    ".oooLMo..oMDooo.",
                    ".oHLMo....oMMMo.",
                    ".oLMMo....oMMDo.",
                    ".oooMMo..oMDooo.",
                    "...oMMooooMDo...",
                    "..oLMMMMMMMMDo..",
                    "..oLMooMMooMDo..",
                    "...oo.oMDo.oo...",
                    "......oooo......",
                    "................",
            }, GEAR)),
            Map.entry("tool_cosmetics", new Icon(new String[] {
                    "................",
                    ".......o........",
                    "......oHo.......",
                    "......oLo.......",
                    ".....oLLMo......",
                    "..ooooLMMoooo...",
                    ".oHLLLLMMMMMDo..",
                    "..oLLLMMMMMDo...",
                    "...oLLMMMMDo....",
                    "....oLMMMDo.....",
                    "....oMMMMMo.....",
                    "...oLLMoMMDo....",
                    "...oLMo.oMDo....",
                    "...ooo...ooo....",
                    "................",
                    "................",
            }, STAR)),
            Map.entry("tool_profile", new Icon(new String[] {
                    "................",
                    ".....oooooo.....",
                    "....oHHHHhho....",
                    "....oHhhhhho....",
                    "....oSSSSSso....",
                    "....oSeSSeso....",
                    "....oSSSSsso....",
                    ".....oSSSso.....",
                    "......oSso......",
                    "....ooTSstoo....",
                    "...oHTTTtttto...",
                    "..oTTTTTttttdo..",
                    "..oTTTTtttttdo..",
                    "..ottttttddddo..",
                    "...oooooooooo...",
                    "................",
            }, PERSON)),
            Map.entry("tool_heads", new Icon(new String[] {
                    "................",
                    "................",
                    "...oooooooooo...",
                    "..oHHHHHHhhhho..",
                    "..oHHhhhhhhhho..",
                    "..oHSSSSSSssho..",
                    "..oSSSSSSsssso..",
                    "..oSEeSSSSeESo..",
                    "..oSSSSSSsssso..",
                    "..oSSSSssSSsso..",
                    "..oSSSShhSSsso..",
                    "..oSSSSssSSsso..",
                    "..osssssssssso..",
                    "...oooooooooo...",
                    "................",
                    "................",
            }, PERSON)),
            Map.entry("tool_hats", new Icon(new String[] {
                    "................",
                    "................",
                    "................",
                    ".oo....oo....oo.",
                    ".oHo..oHGo..oGo.",
                    ".oHGooGGGgooGgo.",
                    ".oHGGGGRGGGGggo.",
                    ".oHGGGRRRGGGggo.",
                    ".oHGGGGRGGGgggo.",
                    ".oGGGGGGGGGgggo.",
                    ".oGgCggGggCgggo.",
                    ".oggggggggggggo.",
                    ".oddddddddddddo.",
                    "..oooooooooooo..",
                    "................",
                    "................",
            }, GOLD)),
            Map.entry("tool_shirts", new Icon(new String[] {
                    "................",
                    "................",
                    "...ooo....ooo...",
                    "..oHWWooooWWwo..",
                    ".oHHHWWWWWWWwwo.",
                    ".oHHHWWWWWWWwwo.",
                    "..oHWWHHWWWwwo..",
                    "...oWHRRHWWwo...",
                    "...oWWrrWWWwo...",
                    "...oWWWWWWWwo...",
                    "...oWWWWWWWwo...",
                    "...oWWWWWWWwo...",
                    "...owwwwwwwwo...",
                    "....oooooooo....",
                    "................",
                    "................",
            }, SHIRT)),
            Map.entry("tool_shop", new Icon(new String[] {
                    "................",
                    "................",
                    "...oooooooooo...",
                    "..oHHBBBBBBBbo..",
                    "..oHBBBBBBBBbo..",
                    "..oBBBBBBBBbbo..",
                    "..obbbbbbbbbdo..",
                    "..oooooGGooooo..",
                    "..oBBBbGgbbbdo..",
                    "..oBBBboobbbdo..",
                    "..oBBBbbbbbbdo..",
                    "..obbbbbbbbbdo..",
                    "..oddddddddddo..",
                    "...oooooooooo...",
                    "................",
                    "................",
            }, CHEST)),
            Map.entry("tool_home", new Icon(new String[] {
                    "................",
                    ".......oo.......",
                    "......oHHo......",
                    ".....oHRRro.....",
                    "....oHRRRrro....",
                    "...oHRRRRrrro...",
                    "..oHRRRRRrrrro..",
                    "..oooooooooooo..",
                    "...oWWWWWWWwo...",
                    "...oWeeWWBBwo...",
                    "...oWeeWWBbwo...",
                    "...oWWWWWBbwo...",
                    "...owwwwwbbwo...",
                    "....oooooooo....",
                    "................",
                    "................",
            }, HOUSE)),
            Map.entry("back", new Icon(new String[] {
                    "................",
                    "................",
                    "................",
                    "......oo........",
                    ".....oHWo.......",
                    "....oHWwo.......",
                    "...oHWwooooooo..",
                    "..oHWWWHHHWWWwo.",
                    "..oWWWWWWWWwwdo.",
                    "...owWwooooooo..",
                    "....owWdo.......",
                    ".....owdo.......",
                    "......oo........",
                    "................",
                    "................",
                    "................",
            }, LIGHT)),
            Map.entry("close", new Icon(new String[] {
                    "................",
                    "................",
                    "...oo......oo...",
                    "..oHRo....oHRo..",
                    "..oRRro..oRRro..",
                    "...oRRrooRRro...",
                    "....oRRRRRro....",
                    ".....oRRRro.....",
                    ".....oRRRro.....",
                    "....oRRRRRro....",
                    "...oRRrooRRro...",
                    "..oRRro..oRRro..",
                    "..orro....orro..",
                    "...oo......oo...",
                    "................",
                    "................",
            }, RED)),
            Map.entry("tool_close", new Icon(new String[] {
                    "................",
                    "................",
                    "...oo......oo...",
                    "..oHWo....oHWo..",
                    "..oWWwo..oWWwo..",
                    "...oWWwooWWwo...",
                    "....oWWWWWwo....",
                    ".....oWWWwo.....",
                    ".....oWWWwo.....",
                    "....oWWWWWwo....",
                    "...oWWwooWWwo...",
                    "..oWWwo..oWWwo..",
                    "..owdo....owdo..",
                    "...oo......oo...",
                    "................",
                    "................",
            }, LIGHT)),
            Map.entry("quicksg", new Icon(new String[] {
                    "................",
                    "............oo..",
                    "...........oHWo.",
                    "..........oHWwo.",
                    ".........oHWwo..",
                    "........oHWwo...",
                    ".......oHWwo....",
                    "..oo..oHWwo.....",
                    "..oGooHWwo......",
                    "...oGGWwo.......",
                    "....oGGo........",
                    "....oGgGo.......",
                    "...oBoooGo......",
                    "..oBbo..oo......",
                    "...oo...........",
                    "................",
            }, STEEL)),
            Map.entry("duell", new Icon(new String[] {
                    "................",
                    "..oo........oo..",
                    ".oHWo......oHWo.",
                    ".oHWwo....oHWwo.",
                    "..oHWwo..oHWwo..",
                    "...oHWwooHWwo...",
                    "....oHWWWWwo....",
                    ".....oWWWwo.....",
                    ".....oWWWwo.....",
                    "....oGWoWGo.....",
                    "...oGGo.oGGo....",
                    "..oBbo...oBbo...",
                    ".oBbo.....oBbo..",
                    "..oo.......oo...",
                    "................",
                    "................",
            }, STEEL)),
            Map.entry("emeraldrush", new Icon(new String[] {
                    "................",
                    ".....oooooo.....",
                    "....oHHLLLGo....",
                    "...oHLLLLLGgo...",
                    "..oHLLGGGGGgdo..",
                    "..oLLGGGGgggdo..",
                    "..oLGGGLGgggdo..",
                    "..oLGGLGGgggdo..",
                    "..oGGGGGgggddo..",
                    "..oGGGGgggdddo..",
                    "...oGGgggdddo...",
                    "....oGggdddo....",
                    ".....ogdddo.....",
                    "......oooo......",
                    "................",
                    "................",
            }, GREEN)),
            Map.entry("mlgrush", new Icon(new String[] {
                    "................",
                    "............oo..",
                    "...........oHBo.",
                    "..........oHBbo.",
                    ".........oHBbo..",
                    "........oHBbo...",
                    ".......oHBbo....",
                    "......oHBbo.....",
                    ".....oHBbo......",
                    "....oHBbo.......",
                    "...oHBbo........",
                    "..oHBbo.........",
                    ".oHBbo..........",
                    ".oBbo...........",
                    "..oo............",
                    "................",
            }, CHEST)),
            Map.entry("zombies", new Icon(new String[] {
                    "................",
                    "...oooooooooo...",
                    "..oHLLLLLLLGgo..",
                    "..oHLLGGGGGGgo..",
                    "..oLLGGGGGGggo..",
                    "..oLGooGGooGgo..",
                    "..oLGooGGooGgo..",
                    "..oLGGGggGGggo..",
                    "..oLGGgooggGgo..",
                    "..oLGGGggGGggo..",
                    "..oLGooooooGgo..",
                    "..oGGGgGGGgggo..",
                    "...oggggggggo...",
                    "....oooooooo....",
                    "................",
                    "................",
            }, GREEN)),
            Map.entry("endergames", new Icon(new String[] {
                    "................",
                    "................",
                    ".....oooooo.....",
                    "....oHHCCCco....",
                    "...oHCCCCCcco...",
                    "..oHCCPPPPCcco..",
                    "..oCCPppppPcco..",
                    "..oCCPpddpPcco..",
                    "..oCCPpddpPcco..",
                    "..oCCPppppPcco..",
                    "..oCcCPPPPccco..",
                    "...oCccccccco...",
                    "....occccccdo...",
                    ".....oooooo.....",
                    "................",
                    "................",
            }, PEARL)),
            Map.entry("ttt", new Icon(new String[] {
                    "................",
                    "............ooo.",
                    "...........oHWo.",
                    "..........oHWwo.",
                    ".........oHWwo..",
                    "........oHWwo...",
                    ".......oHWwo....",
                    "......oRWwo.....",
                    "..oo.oRrwo......",
                    "..oBoRrro.......",
                    "...oBBoo........",
                    "...obBo.........",
                    "..obobBo........",
                    ".obo..oo........",
                    ".oo.............",
                    "................",
            }, KNIFE)),
            Map.entry("ttt_shop", new Icon(new String[] {
                    "................",
                    "................",
                    "......oooo......",
                    ".....oHHLGo.....",
                    "....oHLLGGgo....",
                    "...oHLLGGGggo...",
                    "...oLLGGGGggo...",
                    "...oLGGGGgggo...",
                    "...oGGGGggggo...",
                    "...oGGGggggdo...",
                    "....oGgggggo....",
                    ".....oggddo.....",
                    "......oooo......",
                    "................",
                    "................",
                    "................",
            }, GREEN)),
            Map.entry("skywars", new Icon(new String[] {
                    "................",
                    "......oooo......",
                    ".....oHGGGo.....",
                    "....oHGGGggo....",
                    "....oGGGGggo....",
                    ".....oobboo.....",
                    "......obo.......",
                    "..oooooooooooo..",
                    ".oHHGGGGGGGGGgo.",
                    ".oGGGGGGGGGGggo.",
                    "..oBBBBbbbbbbo..",
                    "...oBBbbbbbdo...",
                    "....oSSssssso...",
                    ".....oSssso.....",
                    "......osso......",
                    ".......oo.......",
            }, ISLAND)),
            Map.entry("archive", new Icon(new String[] {
                    "................",
                    ".......oo.......",
                    "......oGgo......",
                    ".....oooooo.....",
                    "...ooGGGGggoo...",
                    "..oGGHWWWWwggo..",
                    "..oGHWWoWWWwgo..",
                    ".oGHWWWoWWWWwgo.",
                    ".oGWWWWoWWWWwgo.",
                    ".oGWWWWoooWWwgo.",
                    ".oGWWWWWWWWWwgo.",
                    "..oGWWWWWWWwgo..",
                    "..ogGwwwwwwggo..",
                    "...ooggggggoo...",
                    ".....oooooo.....",
                    "................",
            }, HOURGLASS)),
            Map.entry("archive_entrance", new Icon(new String[] {
                    "................",
                    "......oooo......",
                    "....ooWWWWoo....",
                    "...oWWooooWwo...",
                    "..oWWoBBBboWwo..",
                    "..oWoBBBBbboWo..",
                    "..oWoBBBBbboWo..",
                    "..oWoBBBBbboWo..",
                    "..oWoBBBBWboWo..",
                    "..oWoBBBBbboWo..",
                    "..oWoBBBBbboWo..",
                    "..oWoBBBBbboWo..",
                    "..owoBBBBbbowo..",
                    ".oooooooooooooo.",
                    ".oWWWWWWWWWWwwo.",
                    ".oooooooooooooo.",
            }, HOUSE)),
            Map.entry("archive_point", new Icon(new String[] {
                    "................",
                    "......oooo......",
                    "....ooHHRRoo....",
                    "...oHHRRRRrro...",
                    "...oHRRooRrro...",
                    "...oRRo..orro...",
                    "...oRRo..orro...",
                    "...oRRRooRrro...",
                    "....oRRRRrro....",
                    "....oRRRrrro....",
                    ".....oRRrro.....",
                    ".....oRrrdo.....",
                    "......oRdo......",
                    "......oddo......",
                    ".......oo.......",
                    "................",
            }, RED)),
            Map.entry("archive_note", new Icon(new String[] {
                    "................",
                    "..ooooooooo.....",
                    "..oHWWWWWWoo....",
                    "..oWWWWWWWowo...",
                    "..oWDDDDWWoooo..",
                    "..oWWWWWWWWWwo..",
                    "..oWDDDDDDDWwo..",
                    "..oWWWWWWWWWwo..",
                    "..oWDDDDDDDWwo..",
                    "..oWWWWWWWWWwo..",
                    "..oWDDDDDWWWwo..",
                    "..oWWWWWWWWWwo..",
                    "..owwwwwwwwwwo..",
                    "..oooooooooooo..",
                    "................",
                    "................",
            }, CLIPBOARD)),
            Map.entry("cores", new Icon(new String[] {
                    "................",
                    ".......oo.......",
                    "......oHCo......",
                    "......oHCo......",
                    "...oooooooooo...",
                    "...oHWWWWWWwo...",
                    "...oWWoooowwo...",
                    "...oWoHCCcowo...",
                    "...oWoCCccowo...",
                    "...oWoCcccowo...",
                    "...oWwoooowwo...",
                    "...owwwwwwwwo...",
                    "..oddddddddddo..",
                    "..oddddddddddo..",
                    "...oooooooooo...",
                    "................",
            }, CORE)),
            Map.entry("team", new Icon(new String[] {
                    "................",
                    ".oo.....oo......",
                    ".oWoooo.oWoooo..",
                    ".oWRRRo.oWBBBo..",
                    ".oWRRro.oWBBbo..",
                    ".oWRrro.oWBbbo..",
                    ".oWoooo.oWoooo..",
                    ".oWo....oWo.....",
                    ".oWo....oWo.....",
                    ".oWo....oWo.....",
                    ".oWo....oWo.....",
                    ".oWo....oWo.....",
                    ".owo....owo.....",
                    ".ooo....ooo.....",
                    "................",
                    "................",
            }, TEAMS)),
            Map.entry("bedwars", new Icon(new String[] {
                    "................",
                    "................",
                    "................",
                    "..oo............",
                    "..oBo...........",
                    "..oBo...........",
                    "..oBoooooooooo..",
                    "..oBWWWWHRRRrro.",
                    "..oBWWWwRRRrrro.",
                    "..oBwwwrrrrrrro.",
                    "..oBBBBBBBBBBbo.",
                    "..obbbbbbbbbbbo.",
                    "..obooooooooobo.",
                    "..oo.........oo.",
                    "................",
                    "................",
            }, HOUSE)),
            Map.entry("jump", new Icon(new String[] {
                    "................",
                    "................",
                    "....ooooo.......",
                    "...oHHCCco......",
                    "...oHCCCco......",
                    "...oHCCCco......",
                    "...oHCCCco......",
                    "...oCCCccoooo...",
                    "...oCCCcHCCCco..",
                    "...oCCCHCCCcco..",
                    "..oCCCCCCCccco..",
                    "..oCCCCCCcccdo..",
                    "..oddddddddddo..",
                    "...oooooooooo...",
                    "................",
                    "................",
            }, CYAN)),
            Map.entry("premium", new Icon(new String[] {
                    "................",
                    "................",
                    "..oo...oo...oo..",
                    ".oHGo.oHGo.oHGo.",
                    ".oGGGoGGGooGGgo.",
                    ".oGGGGGGGGGGggo.",
                    "..oGGGGGGGGggo..",
                    "..oGGGGGGGgggo..",
                    "..oHGGRGGCGggo..",
                    "..oGGGGGGGgggo..",
                    "..oggggggggggo..",
                    "..oHHGGGGGGggo..",
                    "..ogggggggdddo..",
                    "...oooooooooo...",
                    "................",
                    "................",
            }, GOLD)),
            Map.entry("start", new Icon(new String[] {
                    "................",
                    "................",
                    "....oo..........",
                    "....oHoo........",
                    "....oHLGoo......",
                    "....oHLLGGoo....",
                    "....oLLGGGGgo...",
                    "....oLGGGgggdo..",
                    "....oGGGgggddo..",
                    "....oGGggddoo...",
                    "....oGgddoo.....",
                    "....ogdoo.......",
                    "....odoo........",
                    "....oo..........",
                    "................",
                    "................",
            }, GREEN)),
            Map.entry("vote", new Icon(new String[] {
                    "................",
                    "....ooooooo.....",
                    "...oHHHHWWwo....",
                    "...oHHWWWwWWo...",
                    "...oHWWWWWwWo...",
                    "...oWwwwWWWwo...",
                    "...oWWWWWWWwo...",
                    "...oWwwwwWWwo...",
                    "...oWWWWWWGwo...",
                    "...oWGWWWGgwo...",
                    "...oWgGWGgWwo...",
                    "...oWWgGgWWwo...",
                    "...oWWWgWWWwo...",
                    "....oooooooo....",
                    "................",
                    "................",
            }, BALLOT)),
            Map.entry("staff_random", new Icon(new String[] {
                    "................",
                    ".....oooooo.....",
                    "...ooHHGGGgoo...",
                    "..oHHWWWWWWggo..",
                    "..oHWWWRWWWggo..",
                    ".oHWWWRRrWWWggo.",
                    ".oGWWWRRrWWWggo.",
                    ".oGWWWRrrWWWggo.",
                    ".oGWWWDDdWWWggo.",
                    ".oGWWWDDWWWWggo.",
                    ".ogWWWWDWWWWggo.",
                    "..ogWWWWWWWggo..",
                    "..ogggggggggdo..",
                    "...ooggggddoo...",
                    ".....oooooo.....",
                    "................",
            }, COMPASS)),
            Map.entry("staff_freeze", new Icon(new String[] {
                    "................",
                    "................",
                    "...oooooooooo...",
                    "..oHHHWWCCCCco..",
                    "..oHHWWCCCCcco..",
                    "..oHWCCWCCCcco..",
                    "..oWCCCWWCCcco..",
                    "..oCCCCWCCCcco..",
                    "..oCCCCCWCCcco..",
                    "..oCCCCWWCCcco..",
                    "..oCCCWCCCCcco..",
                    "..oCCCCCCCWcco..",
                    "..occccccccddo..",
                    "...oooooooooo...",
                    "................",
                    "................",
            }, ICE)),
            Map.entry("staff_invsee", new Icon(new String[] {
                    "................",
                    "................",
                    "...oooooooooo...",
                    "..oHHBBBBBBBbo..",
                    "..oHBBBBBBBBbo..",
                    "..obbbbbbbbbdo..",
                    "..oooooooooooo..",
                    "..oBBboooobbdo..",
                    "..oBboWWeWobdo..",
                    "..oBboWeeWobdo..",
                    "..oBBboooobbdo..",
                    "..oBbbbbbbbbdo..",
                    "..oddddddddddo..",
                    "...oooooooooo...",
                    "................",
                    "................",
            }, PEEK)),
            Map.entry("staff_reports", new Icon(new String[] {
                    "................",
                    "....oooooooo....",
                    "...oHRRRRrWwo...",
                    "...oHRRRRrWwo...",
                    "...oRWWWRrWwo...",
                    "...oRRRRRrWwo...",
                    "...oRWWRRrWwo...",
                    "...oRRRRRrWwo...",
                    "...oRRGRRrWwo...",
                    "...oRRGRRrWwo...",
                    "...oRRRRRrWwo...",
                    "...oRRGRRrWwo...",
                    "...orrrrrrWwo...",
                    "....oooooooo....",
                    "................",
                    "................",
            }, BOOK)),
            Map.entry("staff_info", new Icon(new String[] {
                    "................",
                    "....ooooo.......",
                    "...oHHGGGo......",
                    "..oHHHCCCGo.....",
                    "..oHHCCCCGo.....",
                    "..oHCCCCcGo.....",
                    "..oCCCCccGo.....",
                    "..oCCCcccgo.....",
                    "...oGGgggBo.....",
                    "....ooooBBBo....",
                    "........oBBbo...",
                    ".........oBBbo..",
                    "..........oBbo..",
                    "...........oo...",
                    "................",
                    "................",
            }, LENS)),
            Map.entry("staff_spectate", new Icon(new String[] {
                    "................",
                    "................",
                    "................",
                    "................",
                    ".....oooooo.....",
                    "...ooHHHHWWoo...",
                    "..oHHHHeeWWWwo..",
                    ".oHHHHeEEeWWwwo.",
                    ".oHWWWeEEeWwwwo.",
                    "..oWWWWEEWwwwo..",
                    "...oowwwwwwoo...",
                    ".....oooooo.....",
                    "................",
                    "................",
                    "................",
                    "................",
            }, EYE)),
            Map.entry("spectate_players", new Icon(new String[] {
                    "................",
                    ".....oooooo.....",
                    "....oHHHHhho....",
                    "....oHhhhhho....",
                    "....oSSSSSso....",
                    "....oSeSSeso....",
                    "....oSSSSsso....",
                    ".....oSSSso.....",
                    "......oSso......",
                    "....ooTTttoo....",
                    "...oHTTTtttto...",
                    "..oTTTTTttttdo..",
                    "..oTTTTtttttdo..",
                    "..ottttttddddo..",
                    "...oooooooooo...",
                    "................",
            }, PERSON)),
            Map.entry("spectate_previous", new Icon(new String[] {
                    "................",
                    "................",
                    "................",
                    ".....oo.....oo..",
                    "....oHWo...oHWo.",
                    "...oHWdo..oHWdo.",
                    "..oHWdo..oHWdo..",
                    ".oHWdo..oHWdo...",
                    ".oWWdo..oWWdo...",
                    "..oWWdo..oWWdo..",
                    "...oWWdo..oWWdo.",
                    "....owdo...owdo.",
                    ".....oo.....oo..",
                    "................",
                    "................",
                    "................",
            }, LIGHT)),
            Map.entry("spectate_next", new Icon(new String[] {
                    "................",
                    "................",
                    "................",
                    "..oo.....oo.....",
                    ".oWHo...oWHo....",
                    ".odWHo..odWHo...",
                    "..odWHo..odWHo..",
                    "...odWHo..odWHo.",
                    "...odWWo..odWWo.",
                    "..odWWo..odWWo..",
                    ".odWWo..odWWo...",
                    ".odwo...odwo....",
                    "..oo.....oo.....",
                    "................",
                    "................",
                    "................",
            }, LIGHT)),
            Map.entry("previous", new Icon(new String[] {
                    "................",
                    "................",
                    "................",
                    ".........oo.....",
                    "........oHWo....",
                    ".......oHWdo....",
                    "......oHWdo.....",
                    ".....oHWdo......",
                    ".....oWWdo......",
                    "......oWWdo.....",
                    ".......oWWdo....",
                    "........owdo....",
                    ".........oo.....",
                    "................",
                    "................",
                    "................",
            }, LIGHT)),
            Map.entry("next", new Icon(new String[] {
                    "................",
                    "................",
                    "................",
                    "......oo........",
                    ".....oWHo.......",
                    ".....odWHo......",
                    "......odWHo.....",
                    ".......odWHo....",
                    ".......odWWo....",
                    "......odWWo.....",
                    ".....odWWo......",
                    ".....odwo.......",
                    "......oo........",
                    "................",
                    "................",
                    "................",
            }, LIGHT)),
            Map.entry("page", new Icon(new String[] {
                    "................",
                    "...oooooooooo...",
                    "...oHHHHHHHWo...",
                    "...oHddddddWo...",
                    "...oHWWWWWWWo...",
                    "...oHddddddWo...",
                    "...oHWWWWWWWo...",
                    "...oHddddWWWo...",
                    "...oHWWWWWWWo...",
                    "...oHddddddWo...",
                    "...oHWWWWWWWo...",
                    "...oHdddddWWo...",
                    "...oWWWWWWWwo...",
                    "...owwwwwwwwo...",
                    "...oooooooooo...",
                    "................",
            }, LIGHT)),
            Map.entry("refresh", new Icon(new String[] {
                    "................",
                    ".....ooooo......",
                    "...ooHLLLLoo....",
                    "..oHLGgggGLLo...",
                    "..oLGgoooLGgo...",
                    ".oHGgo...oLGLo..",
                    ".oLgo....ooLgoo.",
                    ".oLgo...oHLGGLLo",
                    ".oLgo....oLGGgo.",
                    ".oLGLo....oLgo..",
                    "..oLGLooo..oo...",
                    "..oLgGLLLo......",
                    "...ooLgggo......",
                    ".....oooo.......",
                    "................",
                    "................",
            }, GREEN)),
            Map.entry("loading", new Icon(new String[] {
                    "................",
                    "...oooooooooo...",
                    "...oBBBBBBBbo...",
                    "...oooooooooo...",
                    "....oHWWWWwo....",
                    "....oWGGGGwo....",
                    ".....oWGGwo.....",
                    "......oGgo......",
                    "......oWgo......",
                    ".....oHWGwo.....",
                    "....oHWGGGwo....",
                    "....oWGGGggo....",
                    "...oooooooooo...",
                    "...oBBBBBBBbo...",
                    "...oooooooooo...",
                    "................",
            }, HOURGLASS)),
            Map.entry("empty_list", new Icon(new String[] {
                    "................",
                    "......oooo......",
                    "....ooHWWWoo....",
                    "...oHWwwwwWWo...",
                    "..oHWwooooWWWo..",
                    "..oWwo...oWwwo..",
                    ".oHwo...oHwoWWo.",
                    ".oWwo..oHwooWwo.",
                    ".oWwo.oHwo.oWwo.",
                    ".oWwooHwo..oWwo.",
                    "..oWWWwo..oHwo..",
                    "..oWWwooooHWwo..",
                    "...oWwWWWWwwo...",
                    "....ooWwwwoo....",
                    "......oooo......",
                    "................",
            }, LIGHT)),
            Map.entry("tool_friends", new Icon(new String[] {
                    "...oooo.........",
                    "..oHHHho........",
                    ".oHHHhhho.......",
                    ".oSSSSssooooo...",
                    ".oSeSSesoHHHho..",
                    ".oSSSSsoHHHhhho.",
                    "..oSSssoSSSSsso.",
                    "..ooSsooSeSSeso.",
                    ".oRRRRroSSSSsso.",
                    "oRRRRRrroSSsso..",
                    "oRRRRrrrooSsoo..",
                    "oRRRrrroTTTTtto.",
                    ".ooooooTTTTTttto",
                    "......oTTTTtttto",
                    "......oTTTttttto",
                    ".......oooooooo.",
            }, FRIENDS)),
            Map.entry("tool_requests", new Icon(new String[] {
                    "................",
                    "................",
                    "................",
                    ".oooooooooooooo.",
                    "odWWWWWWWWWWWWdo",
                    "oWdWWWWWWWWWWdwo",
                    "oWWdWWWWWWWWdWwo",
                    "oWWWdWWWWWWdWWwo",
                    "oWWWWdWWWWdWWWwo",
                    "oWWWWWdRRdWWWWwo",
                    "oWWWWWWRrWWWWWwo",
                    "oWWWWWWWWWWWWWwo",
                    "oWwwwwwwwwwwwwwo",
                    ".oooooooooooooo.",
                    "................",
                    "................",
            }, MAIL)),
            Map.entry("tool_add_friend", new Icon(new String[] {
                    "...........oo...",
                    "..........oGGo..",
                    "...oooo..ooGgoo.",
                    "..oHHHhooGGGGggo",
                    ".oHHHhhhoGgggggo",
                    ".oSSSSssoooGgoo.",
                    ".oSeSSeso.oggo..",
                    ".oSSSSsso..oo...",
                    "..oSSsso........",
                    "..ooSsoo........",
                    ".oTTTTtto.......",
                    "oTTTTTttto......",
                    "oTTTTtttto......",
                    "oTTTttttto......",
                    ".oooooooo.......",
                    "................",
            }, ADD_FRIEND)),
            Map.entry("gadgets", new Icon(new String[] {
                    "..oo............",
                    ".oBbo...........",
                    "..oBbooooo......",
                    "...oBbHWWwo.....",
                    "....ooWoowo.....",
                    ".....oWWwwo.....",
                    "......oWwo......",
                    "..o...oWwo...o..",
                    ".oHo..oWwo..owo.",
                    ".oHWo.oWwo.oWwo.",
                    "..oHWooWwooWwo..",
                    "..oHWWoWwoWwwo..",
                    "...oHWWWwwwwo...",
                    "....ooWwddoo....",
                    "......oddo......",
                    ".......oo.......",
            }, HOOK)),
            Map.entry("kill_effects", new Icon(new String[] {
                    "................",
                    "................",
                    ".....ooooo......",
                    "....oHHHWWoo....",
                    "...oHHWWWWWwo...",
                    "..oHHWWWWWWwwo..",
                    "..oHWWWWWWWwwo..",
                    "..oHWooWWoowwo..",
                    "..oWWooWWoowwo..",
                    "..oWWWWWWWWwwo..",
                    "...oWWWooWwwo...",
                    "....oWoWWoWo....",
                    "....owwwwddo....",
                    ".....oooooo.....",
                    "................",
                    "................",
            }, MAIL)),
            Map.entry("win_effects", new Icon(new String[] {
                    ".....o......o...",
                    "....oGo....oYo..",
                    "...ooGoo..oYGGo.",
                    "..oGGYGGooHGGgo.",
                    "...ooGoooHRRgo..",
                    "....oGooHRRRro..",
                    ".....ooHWWRro...",
                    ".....oHRRWWro...",
                    "....oHRRRroo....",
                    "...oHRRRro......",
                    "....oRroo..o....",
                    "...oBbo...oCo...",
                    "..oBbo...oCCCo..",
                    ".oBbo.....oCo...",
                    "..oo.......o....",
                    "................",
            }, ROCKET)),
            Map.entry("trails", new Icon(new String[] {
                    "................",
                    "..........o.....",
                    ".........oHo....",
                    ".........oGo....",
                    ".......ooHGGoo..",
                    ".....ooHHGGGGgo.",
                    "....oCCooGGgoo..",
                    "...oCcoHGogo....",
                    "..occoHGoogo....",
                    "...ooGGoLLo.....",
                    "...oGgoLMo......",
                    "..oggoLMo.......",
                    "...ooMMo........",
                    ".....oo.........",
                    "................",
                    "................",
            }, TRAIL)),
            Map.entry("chat_tags", new Icon(new String[] {
                    "................",
                    "................",
                    "................",
                    ".....ooooooooo..",
                    "....oHHHHHHHHBo.",
                    "...oHBBBBBBBBbo.",
                    "..oHBBWWWWWWBbo.",
                    ".oHBoBBBBBBBBbo.",
                    ".oBBoBBBBBBBBbo.",
                    "..oBBBWWWWBBBbo.",
                    "...obbbbbbbbbbo.",
                    "....obbbbbbbbbo.",
                    ".....ooooooooo..",
                    "................",
                    "................",
                    "................",
            }, TAG)),
            Map.entry("lobbies", new Icon(new String[] {
                    "................",
                    "................",
                    "...oooooooooo...",
                    "..oHLLLLLLLLMo..",
                    "..oLGLLDDDDLMo..",
                    "..oMMMMMMMMMDo..",
                    "...oooooooooo...",
                    "..oHLLLLLLLLMo..",
                    "..oLGLLDDDDLMo..",
                    "..oMMMMMMMMMDo..",
                    "...oooooooooo...",
                    "..oHLLLLLLLLMo..",
                    "..oLGLLDDDDLMo..",
                    "..oMMMMMMMMMDo..",
                    "...oooooooooo...",
                    "................",
            }, RACK)),
            Map.entry("advent", new Icon(new String[] {
                    "................",
                    "....ooo..ooo....",
                    "...oYGGooGGgo...",
                    "...oYoGGGGogo...",
                    "..oooGgGGggooo..",
                    ".oHHRRRYGRRRrro.",
                    ".oHRRRRYGRRRrro.",
                    ".orrrrrGgrrrrdo.",
                    "..oooooooooooo..",
                    "..oHRRRYGRRro...",
                    "..oHRRRYGRRro...",
                    "..oRRRRYGRrro...",
                    "..orrrrGgrrdo...",
                    "...ooooooooo....",
                    "................",
                    "................",
            }, GIFT)),
            Map.entry("recap", new Icon(new String[] {
                    "......oooo......",
                    "..ooooHWwwoooo..",
                    ".oBBBHWWWwwBBbo.",
                    ".oBHHHHHHHHHWbo.",
                    ".oBHGWGWWWWWwbo.",
                    ".oBHWGWWDDDDwbo.",
                    ".oBHWWWWWWWWwbo.",
                    ".oBHGWGWWWWWwbo.",
                    ".oBHWGWWDDDDwbo.",
                    ".oBHWWWWWWWWwbo.",
                    ".oBHGWGWWWWWwbo.",
                    ".oBHWGWWDDDDwbo.",
                    ".oBwwwwwwwwwwbo.",
                    ".oddddddddddddo.",
                    "..oooooooooooo..",
                    "................",
            }, CLIPBOARD)),
            Map.entry("achievements", new Icon(new String[] {
                    "...ooo...ooo....",
                    "..oPRro.oRRro...",
                    "...oPRroRRro....",
                    "....oPRRRro.....",
                    ".....oRRro......",
                    "....oHHGGgo.....",
                    "...oHGGGGGgo....",
                    "..oHGGGHGGGgo...",
                    "..oHGHHHHHGgo...",
                    "..oGGGHHHGggo...",
                    "..oGGGHGHggdo...",
                    "..oGGGGGgggdo...",
                    "...ogggggddo....",
                    "....odddddo.....",
                    ".....ooooo......",
                    "................",
            }, MEDAL)),
            Map.entry("premium_area", new Icon(new String[] {
                    "................",
                    ".....ooooo......",
                    "....oHHRRroo....",
                    "...oHRRRRRRro...",
                    "..oHRRRRRRRRro..",
                    "..oHRYRGGRGRro..",
                    "..oHRYGGGGgRro..",
                    "..oRRYGGGGgRro..",
                    "..oRRggggggRro..",
                    "..oRRRRRRRRrdo..",
                    "...oRRRRRrrdo...",
                    "....oRRRrdoo....",
                    ".....orrrdo.....",
                    "......ordo......",
                    ".......oo.......",
                    "................",
            }, GIFT)),
            Map.entry("pets", new Icon(new String[] {
                    "................",
                    "...o........o...",
                    "..oHo......ogo..",
                    "..oHGo....oGgo..",
                    "..oHPGooooGPgo..",
                    "..oHGGGGGGGGgo..",
                    ".oHGGGGGGGGGGgo.",
                    ".oHGGoGGGGoGGgo.",
                    ".oGGGoGGGGoGggo.",
                    ".oGGGGGPPGGGggo.",
                    ".oGGGGWWWWGGggo.",
                    "..ogGGWWWWGgdo..",
                    "...oggggggddo...",
                    "....oooooooo....",
                    "................",
                    "................",
            }, PET)),
            Map.entry("global_stats", new Icon(new String[] {
                    "................",
                    "................",
                    ".....oooooo.....",
                    "....oYYYYYGo....",
                    "....oYGGgGgo....",
                    "....oYGggGgo....",
                    ".ooooYGGgGgo....",
                    "oHHHWYGGgGgo....",
                    "oHWWwYGGgGgoooo.",
                    "oHWWwYGGGGgEEEBo",
                    "oHWWwYGGGGgEBBbo",
                    "oHWWwYGGGGgEBBbo",
                    "oHWWwYGGGGgEBBbo",
                    "owwwwggggggbbbbo",
                    ".oooooooooooooo.",
                    "................",
            }, PODIUM)),
            Map.entry("enchanting", new Icon(new String[] {
                    ".......o....o...",
                    "......oHo..oCo..",
                    ".....ooLoooCCco.",
                    "..o.oHLHLMooco..",
                    ".oCo.ooMoo..o...",
                    "..o...oMo.......",
                    "..oooo.o..oooo..",
                    ".oHHWWo..oWWWwo.",
                    "oMHWWWWooWWWWwDo",
                    "oMHwwwWwwWwwwwDo",
                    "oMHWWWWwwWWWWwDo",
                    "oMHwwwWwwWwwwwDo",
                    "oLMMMMMDDMMMMMDo",
                    ".oDDDDDooDDDDDo.",
                    "..ooooo..ooooo..",
                    "................",
            }, ENCHANT))
    );

    static final String[] ICON_PROFILE = {
            "..###...",
            "..###...",
            "..###...",
            "...#....",
            ".#####..",
            "#######.",
            "#######.",
            "........",
    };

    static final String[] ICON_COINS = {
            "..###...",
            ".#####..",
            "##.#.##.",
            "##.#.##.",
            "##.#.##.",
            ".#####..",
            "..###...",
            "........",
    };

    static final String[] ICON_LEVEL = {
            "...#....",
            "...#....",
            ".#####..",
            "#######.",
            ".#####..",
            "..#.#...",
            ".#...#..",
            "........",
    };

    static final String[] ICON_CHAT = {
            ".#####..",
            "#.....#.",
            "#.###.#.",
            "#.....#.",
            ".#####..",
            "..##....",
            "..#.....",
            "........",
    };

    static final String[] ICON_SERVER = {
            "#######.",
            "#....##.",
            "#######.",
            "........",
            "#######.",
            "#....##.",
            "#######.",
            "........",
    };

    static final String[] ICON_PLAYERS = {
            ".##.##..",
            ".##.##..",
            "........",
            "###.###.",
            "###.###.",
            "###.###.",
            "###.###.",
            "........",
    };

    static final String[] ICON_MAP = {
            "..###...",
            ".#...#..",
            "#..#..#.",
            "#.....#.",
            ".#...#..",
            "..###...",
            "...#....",
            "........",
    };

    static final String[] ICON_KIT = {
            ".#####..",
            "#######.",
            "#.....#.",
            "#######.",
            "#..#..#.",
            "#.....#.",
            "#######.",
            "........",
    };

    static final String[] ICON_SPECTATOR = {
            "........",
            "..###...",
            ".#.#.#..",
            "#.###.#.",
            ".#.#.#..",
            "..###...",
            "........",
            "........",
    };

    static final String[] ICON_TIME = {
            "..###...",
            ".#...#..",
            "#..#..#.",
            "#..##.#.",
            "#.....#.",
            ".#...#..",
            "..###...",
            "........",
    };

    static final String[] ICON_EMERALD = {
            "..###...",
            ".#.###..",
            "#..####.",
            "#..####.",
            "#######.",
            ".#####..",
            "..###...",
            "........",
    };

    static final String[] ICON_WINNER = {
            "#######.",
            "#.###.#.",
            "#.###.#.",
            ".#####..",
            "..###...",
            "...#....",
            ".#####..",
            "........",
    };

    static final String[] ICON_MODE = {
            "#.......",
            "######..",
            "#######.",
            "######..",
            "#.......",
            "#.......",
            "#.......",
            "........",
    };

    static final String[] ICON_OPPONENT = {
            "##...##.",
            ".##.##..",
            "..###...",
            "...#....",
            "..###...",
            ".##.##..",
            "##...##.",
            "........",
    };

    static final Map<Character, Integer> ERROR = Map.of('h', 0xFFF08A8A, 'R', 0xFFE04E4E, 's', 0xFF9E2B2B, 'W', WHITE);

    static final String[] ICON_ERROR = {
            ".hhhhhh.",
            "RRRWWsRR",
            "RRRWWsRR",
            "RRRWWsRR",
            "RRRRRRRR",
            "RRRWWsRR",
            "RRRRRRRR",
            ".ssssss.",
    };

    static final String[] ICON_KILLS = {
            ".#####..",
            "#######.",
            "#..#..#.",
            "#..#..#.",
            "#######.",
            ".#.#.#..",
            "..###...",
            "........",
    };
    static final class TileArt {
        record Card(String key, String icon, int accent, Scene scene) { }
        record State(String key, int colour, String[] pixels) { }
        enum Scene { FOREST, MINE, ARENA, ISLAND, CITY, END, CRYPT, STUDIO, TREASURE }

        static final List<State> STATES = List.of(
                new State("available", 0xFF8AD9A7, new String[] {"....#", "...##", "#.#..", ".#...", "....."}),
                new State("locked", 0xFFE5A478, new String[] {".###.", ".#.#.", "#####", "##.##", "#####"}),
                new State("owned", 0xFF8AD9C5, new String[] {".###.", "#...#", "#.#.#", "#...#", ".###."}),
                new State("active", 0xFF9DBDF6, new String[] {"..#..", ".###.", "#####", ".###.", "..#.."}),
                new State("pending", 0xFFE8C582, new String[] {"#####", ".#.#.", "..#..", ".#.#.", "#####"}),
                new State("offline", 0xFF9CA6B8, new String[] {".###.", "#...#", "#...#", "#...#", ".###."}));

        static final List<Card> CARDS = List.of(
                new Card("quicksg", "quicksg", 0xFF69B982, Scene.FOREST),
                new Card("emeraldrush", "emeraldrush", 0xFF5ED7AE, Scene.MINE),
                new Card("duell", "duell", 0xFFE7A66A, Scene.ARENA),
                new Card("bedwars", "bedwars", 0xFFED7B83, Scene.ISLAND),
                new Card("skywars", "skywars", 0xFF8ABEF0, Scene.ISLAND),
                new Card("ttt", "ttt", 0xFFDFB574, Scene.CITY),
                new Card("mlgrush", "mlgrush", 0xFF7FC5EA, Scene.ARENA),
                new Card("cores", "cores", 0xFF9BDADC, Scene.MINE),
                new Card("endergames", "endergames", 0xFFBD99E8, Scene.END),
                new Card("zombies", "zombies", 0xFF9EC175, Scene.CRYPT),
                new Card("heads", "tool_heads", 0xFF85BBD5, Scene.STUDIO),
                new Card("hats", "tool_hats", 0xFFE2BB78, Scene.STUDIO),
                new Card("shirts", "tool_shirts", 0xFF8EAFEB, Scene.STUDIO),
                new Card("kill_effects", "kill_effects", 0xFFDC8D8C, Scene.ARENA),
                new Card("win_effects", "win_effects", 0xFFE3BF78, Scene.TREASURE),
                new Card("premium", "premium", 0xFFF0C375, Scene.TREASURE),
                new Card("level", "achievements", 0xFF9FC7F6, Scene.TREASURE),
                new Card("coins", "tool_shop", 0xFFE8CB7E, Scene.TREASURE),
                new Card("recap", "recap", 0xFF8BC8CC, Scene.STUDIO),
                new Card("achievements", "achievements", 0xFFE1BE78, Scene.TREASURE),
                new Card("gadgets", "gadgets", 0xFF8CCCD0, Scene.STUDIO),
                new Card("trails", "trails", 0xFFCCAFE8, Scene.END),
                new Card("tags", "chat_tags", 0xFF9AB8F0, Scene.STUDIO),
                new Card("lobbies", "lobbies", 0xFF9AB8F0, Scene.CITY));

        static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789ÄÖÜ-.:!?…/+&";
        static final Map<Character, String> LETTER_PIXELS = Map.ofEntries(
                Map.entry('A', "010101111101101"), Map.entry('B', "110101110101110"),
                Map.entry('C', "011100100100011"), Map.entry('D', "110101101101110"),
                Map.entry('E', "111100110100111"), Map.entry('F', "111100110100100"),
                Map.entry('G', "011100101101011"), Map.entry('H', "101101111101101"),
                Map.entry('I', "111010010010111"), Map.entry('J', "001001001101111"),
                Map.entry('K', "101101110101101"), Map.entry('L', "100100100100111"),
                Map.entry('M', "101111111101101"), Map.entry('N', "111101101101101"),
                Map.entry('O', "111101101101111"), Map.entry('P', "110101110100100"),
                Map.entry('Q', "111101101111001"), Map.entry('R', "110101110101101"),
                Map.entry('S', "011100111001110"), Map.entry('T', "111010010010010"),
                Map.entry('U', "101101101101111"), Map.entry('V', "101101101101010"),
                Map.entry('W', "101101111111101"), Map.entry('X', "101101010101101"),
                Map.entry('Y', "101101010010010"), Map.entry('Z', "111001010100111"),
                Map.entry('0', "111101101101111"), Map.entry('1', "011001001001111"),
                Map.entry('2', "110001010100111"), Map.entry('3', "110001010001110"),
                Map.entry('4', "101101111001001"), Map.entry('5', "111100110001110"),
                Map.entry('6', "011100111101111"), Map.entry('7', "111001010010010"),
                Map.entry('8', "111101111101111"), Map.entry('9', "111101111001110"),
                Map.entry('-', "000000111000000"), Map.entry('.', "000000000000001"),
                Map.entry(':', "000001000001000"), Map.entry('!', "001001001000001"),
                Map.entry('?', "111001010000010"), Map.entry('…', "000000000000101"),
                Map.entry('/', "001001010100100"), Map.entry('+', "000010111010000"),
                Map.entry('&', "010101010101011"));

        static void write(Path root) throws IOException {
            Path textures = root.resolve("assets/blockcraft/textures/font");
            Path fonts = root.resolve("assets/blockcraft/font");
            Files.createDirectories(textures.resolve("tiles"));
            var providers = new ArrayList<String>();
            for (var state : STATES) {
                var sprite = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                PackArt.paint(sprite, state.pixels(), 9, 9, Map.of('#', state.colour()));
                String name = "state_" + state.key();
                PackArt.write(sprite, root.resolve("assets/blockcraft/textures/item/" + name + ".png"));
                Files.writeString(root.resolve("assets/blockcraft/items/" + name + ".json"),
                        "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"blockcraft:item/" + name + "\"}}\n");
                Files.writeString(root.resolve("assets/blockcraft/models/item/" + name + ".json"),
                        "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"blockcraft:item/" + name + "\"}}\n");
                var frame = PackArt.tooltipFrame();
                for (int y = 0; y < frame.getHeight(); y++) {
                    for (int x = 0; x < frame.getWidth(); x++) {
                        if (frame.getRGB(x, y) == PackArt.ACCENT) frame.setRGB(x, y, state.colour());
                    }
                }
                Path tooltip = root.resolve("assets/blockcraft/textures/gui/sprites/tooltip");
                PackArt.write(frame, tooltip.resolve(name + "_frame.png"));
                PackArt.write(PackArt.tooltipBackground(), tooltip.resolve(name + "_background.png"));
                for (String part : new String[] {"frame", "background"}) {
                    Files.copy(tooltip.resolve("menu_" + part + ".png.mcmeta"),
                            tooltip.resolve(name + "_" + part + ".png.mcmeta"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            for (int index = 0; index < CARDS.size(); index++) {
                var card = CARDS.get(index);
                for (boolean large : new boolean[] {false, true}) {
                    String shape = large ? "large" : "wide";
                    String file = "tiles/" + card.key() + "_" + shape + ".png";
                    PackArt.write(card(card, large), textures.resolve(file));
                    for (int row = 0; row < (large ? 5 : 6); row++) {
                        providers.add(bitmap(file, 13 - 18 * (row + 1), large ? 34 : 16,
                                String.valueOf((char) ((large ? 0xE600 : 0xE400) + index * 8 + row))));
                    }
                }
            }
            Files.writeString(fonts.resolve("tiles.json"), "{\n  \"providers\": [\n    " + String.join(",\n    ", providers) + "\n  ]\n}\n");
            PackArt.write(letters(), textures.resolve("labels.png"));
            for (boolean large : new boolean[] {false, true}) {
                for (int row = 0; row < (large ? 5 : 6); row++) {
                    String shape = large ? "large" : "wide";
                    Files.writeString(fonts.resolve("label_" + shape + "_" + row + ".json"),
                            "{\n  \"providers\": [\n    {\"type\":\"space\",\"advances\":{\" \":4}},\n    "
                                    + bitmap("labels.png", 13 - (18 * (row + 1) + (large ? 25 : 2)), 7, LETTERS)
                                    + "\n  ]\n}\n");
                }
            }
            for (int rows = 2; rows <= 6; rows++) {
                var panel = PackArt.panel(rows, true);
                PackArt.fill(panel, 7, 35, 162, (rows - 1) * 18, PackArt.BODY);
                PackArt.write(panel, textures.resolve("cards_" + rows + ".png"));
            }
        }

        static String bitmap(String file, int ascent, int height, String chars) {
            var encoded = new StringBuilder();
            chars.chars().forEach(glyph -> encoded.append(String.format(Locale.ROOT, "\\u%04x", glyph)));
            return "{\"type\":\"bitmap\",\"file\":\"blockcraft:font/" + file + "\",\"ascent\":" + ascent
                    + ",\"height\":" + height + ",\"chars\":[\"" + encoded + "\"]}";
        }

        static BufferedImage letters() {
            var sheet = new BufferedImage(LETTERS.length() * 3, 7, BufferedImage.TYPE_INT_ARGB);
            for (int index = 0; index < LETTERS.length(); index++) {
                char letter = LETTERS.charAt(index);
                String pixels = LETTER_PIXELS.get(PackArt.UMLAUTS.getOrDefault(letter, letter));
                for (int y = 0; y < 5; y++) {
                    for (int x = 0; x < 3; x++) {
                        if (pixels.charAt(y * 3 + x) == '1') {
                            sheet.setRGB(index * 3 + x, y + 2, PackArt.WHITE);
                        }
                    }
                }
                if (PackArt.UMLAUTS.containsKey(letter)) {
                    sheet.setRGB(index * 3, 0, PackArt.WHITE);
                    sheet.setRGB(index * 3 + 2, 0, PackArt.WHITE);
                }
            }
            return sheet;
        }

        static BufferedImage card(Card card, boolean large) {
            int width = large ? 52 : 70;
            int height = large ? 34 : 16;
            var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            PackArt.fill(image, 0, 0, width, height, PackArt.BORDER);
            PackArt.fill(image, 1, 1, width - 2, height - 2, 0xFF252E40);
            int artWidth = large ? width - 2 : 23;
            int artHeight = large ? 22 : height - 2;
            scene(image, 1, 1, artWidth, artHeight, card);
            PackArt.fill(image, large ? 1 : 24, large ? 24 : 1, large ? width - 2 : width - 25,
                    large ? 9 : height - 2, 0xFF252E40);
            PackArt.fill(image, 1, height - 2, width - 2, 1, mix(card.accent(), 0xFF252E40, 0.3));
            PackArt.fill(image, 1, 1, large ? width - 2 : 23, 1, mix(card.accent(), 0xFFFFFFFF, 0.7));
            var icon = PackArt.ICONS.get(card.icon());
            var sprite = PackArt.pixels(icon.rows(), icon.palette());
            int iconX = large ? (width - 16) / 2 : 4;
            int iconY = large ? 4 : 0;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int pixel = sprite.getRGB(x, y);
                    if ((pixel >>> 24) != 0 && iconY + y < height - (large ? 10 : 1)) {
                        image.setRGB(iconX + x, iconY + y, pixel);
                    }
                }
            }
            return image;
        }

        static void scene(BufferedImage image, int x, int y, int width, int height, Card card) {
            for (int row = 0; row < height; row++) {
                int sky = mix(card.accent(), 0xFF111C31, 0.18 + row / (double) height * 0.14);
                PackArt.fill(image, x, y + row, width, 1, sky);
            }
            for (int column = 0; column < width; column++) {
                int ridge = height / 2 + Math.floorMod(column / 3 + column / 7, Math.max(1, height / 3));
                PackArt.fill(image, x + column, y + ridge, 1, height - ridge, mix(card.accent(), 0xFF101929, 0.3));
            }
            switch (card.scene()) {
                case FOREST -> {
                    for (int tree = 2; tree < width - 2; tree += 10) {
                        int top = 2 + tree % 3;
                        PackArt.fill(image, x + tree + 2, y + top + 3, 1, height - top - 3, 0xFF3E3F39);
                        PackArt.fill(image, x + tree, y + top, 5, 5, 0xFF285648);
                        PackArt.fill(image, x + tree + 1, y + top, 3, 2, 0xFF3D7758);
                    }
                }
                case ISLAND -> {
                    for (int island = 1; island < width - 6; island += 15) {
                        int top = height / 2 + island % 4;
                        PackArt.fill(image, x + island, y + top, 9, 2, 0xFF608966);
                        PackArt.fill(image, x + island + 1, y + top + 2, 7, 2, 0xFF66513F);
                        if (top + 5 < height) PackArt.fill(image, x + island + 3, y + top + 4, 3, 2, 0xFF3D3840);
                    }
                    PackArt.fill(image, x + width - 8, y + 3, 5, 1, 0xFFB1CFD8);
                }
                case ARENA, CITY -> {
                    for (int wall = 1; wall < width - 4; wall += 6) {
                        int top = 3 + wall % 5;
                        PackArt.fill(image, x + wall, y + top, 4, height - top, 0xFF364357);
                        PackArt.fill(image, x + wall, y + top, 4, 1, 0xFF566176);
                        PackArt.fill(image, x + wall + 1, y + top + 2, 1, 2, 0xFFD4A869);
                    }
                }
                case MINE, CRYPT -> {
                    for (int rock = 0; rock < width - 3; rock += 5) {
                        int drop = 2 + rock % 4;
                        PackArt.fill(image, x + rock, y, 4, drop, 0xFF3F4855);
                        PackArt.fill(image, x + rock + 1, y + height - drop, 3, drop, 0xFF3F4855);
                        PackArt.fill(image, x + rock + 1, y + height - drop, 1, 1, card.accent());
                    }
                }
                case END, TREASURE -> {
                    for (int star = 0; star < width / 4; star++) {
                        int left = Math.floorMod(star * 17 + 3, width - 2);
                        int top = Math.floorMod(star * 7 + 1, height - 2);
                        PackArt.fill(image, x + left, y + top, 1, 1, mix(card.accent(), 0xFFFFFFFF, 0.6));
                    }
                }
                case STUDIO -> {
                    PackArt.fill(image, x + 3, y + 3, width - 6, 1, mix(card.accent(), 0xFF263348, 0.3));
                    PackArt.fill(image, x + 2, y + height - 3, width - 4, 1, mix(card.accent(), 0xFF263348, 0.4));
                }
            }
        }

        static int mix(int a, int b, double weight) {
            int red = (int) (((a >> 16) & 255) * weight + ((b >> 16) & 255) * (1 - weight));
            int green = (int) (((a >> 8) & 255) * weight + ((b >> 8) & 255) * (1 - weight));
            int blue = (int) ((a & 255) * weight + (b & 255) * (1 - weight));
            return 0xFF000000 | red << 16 | green << 8 | blue;
        }
    }
}
