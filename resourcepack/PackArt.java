import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        for (var icon : ICONS.entrySet()) {
            write(pixels(icon.getValue().rows(), icon.getValue().palette()), item.resolve(icon.getKey() + ".png"));
        }
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
}
