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
    static final int OUTLINE = 0xFF111318;
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

    static final Map<Character, Integer> LIGHT = Map.of('W', 0xFFF4F5F7, 'w', 0xFFC9CDD6);
    static final Map<Character, Integer> RED = Map.of('R', 0xFFEF4444, 'r', 0xFFB91C1C);
    static final Map<Character, Integer> GOLD = Map.of('G', 0xFFF5C542, 'g', 0xFFB98A16, 'B', 0xFF7A4B1F, 'W', 0xFFF4F5F7);
    static final Map<Character, Integer> GREEN = Map.of('G', 0xFF2ECC71, 'g', 0xFF8EF0B3, 'd', 0xFF1B8A4B);
    static final Map<Character, Integer> AQUA = Map.of('A', 0xFF5EEAD4, 'a', 0xFF14B8A6, 'B', 0xFF7A4B1F, 'W', 0xFFF4F5F7);
    static final Map<Character, Integer> CYAN = Map.of('C', 0xFF67E8F9, 'c', 0xFF0891B2);
    static final Map<Character, Integer> GEAR = Map.of('L', 0xFFE2E5EA, 'M', 0xFFA7AEBB, 'D', 0xFF6F7887);
    static final Map<Character, Integer> STAR = Map.of('L', 0xFFFBCFE8, 'M', 0xFFEC4899, 'D', 0xFFBE185D);
    static final Map<Character, Integer> HOUSE = Map.of('R', 0xFFE04E4E, 'r', 0xFF9E2B2B, 'W', 0xFFF4F5F7, 'w', 0xFFC9CDD6,
            'e', 0xFF4C6EF5, 'B', 0xFF9A6B3F, 'b', 0xFF7A4B1F);
    static final Map<Character, Integer> CHEST = Map.of('B', 0xFF9A6B3F, 'b', 0xFF7A4B1F, 'd', 0xFF55321A, 'G', 0xFFF5C542, 'g', 0xFFB98A16);
    static final Map<Character, Integer> PERSON = Map.of('H', 0xFF5A3A1E, 'S', 0xFFF0C8A0, 's', 0xFFC89A6C, 'E', 0xFFF4F5F7,
            'e', 0xFF4C6EF5, 'T', 0xFF22B5B5, 't', 0xFF178383);
    static final Map<Character, Integer> HEAD = Map.of('H', 0xFF5A3A1E, 'h', 0xFF3D2612, 'S', 0xFFF0C8A0, 's', 0xFFC89A6C,
            'E', 0xFFF4F5F7, 'e', 0xFF4C6EF5);
    static final Map<Character, Integer> SHIRT = Map.of('W', 0xFFF4F5F7, 'w', 0xFFC9CDD6, 'R', 0xFFE04E4E);

    static final Map<String, Icon> ICONS = Map.ofEntries(
            Map.entry("back", new Icon(new String[] {
                    "................",
                    "................",
                    "................",
                    "......oo........",
                    ".....oWWo.......",
                    "....oWWoo.......",
                    "...oWWooooooooo.",
                    "..oWWoWWWWWWWWWo",
                    "..oWWoWWWWWWWWWo",
                    "...oWWooooooooo.",
                    "....oWWoo.......",
                    ".....oWWo.......",
                    "......oo........",
                    "................",
                    "................",
                    "................",
            }, LIGHT)),
            Map.entry("close", new Icon(new String[] {
                    "................",
                    "................",
                    "..oo........oo..",
                    ".oRRo......oRRo.",
                    ".oRRRo....oRRRo.",
                    "..oRRRo..oRRRo..",
                    "...oRRRooRRRo...",
                    "....oRRRRRRo....",
                    "....oRRRRRRo....",
                    "...oRRRooRRRo...",
                    "..oRRRo..oRRRo..",
                    ".oRRRo....oRRRo.",
                    ".oRRo......oRRo.",
                    "..oo........oo..",
                    "................",
                    "................",
            }, RED)),
            Map.entry("tool_close", new Icon(new String[] {
                    "................",
                    ".oo..........oo.",
                    "oWWo........oWWo",
                    "oWWWo......oWWWo",
                    ".oWWWo....oWWWo.",
                    "..oWWWo..oWWWo..",
                    "...oWWWooWWWo...",
                    "....oWWWWWWo....",
                    "....oWWWWWWo....",
                    "...oWWWooWWWo...",
                    "..oWWWo..oWWWo..",
                    ".oWWWo....oWWWo.",
                    "oWWWo......oWWWo",
                    "oWWo........oWWo",
                    ".oo..........oo.",
                    "................",
            }, LIGHT)),
            Map.entry("tool_shop", new Icon(new String[] {
                    "................",
                    "..oooooooooooo..",
                    ".oBBBBBBBBBBBBo.",
                    ".oBBBBBBBBBBBBo.",
                    ".oBBBBBBBBBBBBo.",
                    ".obbbbbbbbbbbbo.",
                    ".oGGGGGggGGGGGo.",
                    ".obbbbbggbbbbbo.",
                    ".obbbbbbbbbbbbo.",
                    ".obbbbbbbbbbbbo.",
                    ".obbbbbbbbbbbbo.",
                    ".obbbbbbbbbbbbo.",
                    ".oddddddddddddo.",
                    ".oooooooooooooo.",
                    "................",
                    "................",
            }, CHEST)),
            Map.entry("tool_home", new Icon(new String[] {
                    "................",
                    ".......oo.......",
                    "......oRRo......",
                    ".....oRRRRo.....",
                    "....oRRRRRRo....",
                    "...oRRRRRRRRo...",
                    "..oRRRRRRRRRRo..",
                    ".orrrrrrrrrrrro.",
                    "..oWWWWWWWWWWo..",
                    "..oWWeeWWWBBWo..",
                    "..oWWeeWWWBbWo..",
                    "..oWWWWWWWBBWo..",
                    "..oWWWWWWWBbWo..",
                    "..owwwwwwwbbwo..",
                    "..oooooooooooo..",
                    "................",
            }, HOUSE)),
            Map.entry("tool_settings", new Icon(new String[] {
                    "......oooo......",
                    "..oo.oLLLLo.oo..",
                    ".oLLooMMMMooLLo.",
                    ".oDMLLMMMMLLMDo.",
                    "..oMMMDDDDMMMo..",
                    ".oLMMDooooDMMLo.",
                    "oLMMMo....oMMMLo",
                    "oMMMMo....oMMMMo",
                    "oMMMMo....oMMMMo",
                    "oDMMMo....oMMMDo",
                    ".oDMMLooooLMMDo.",
                    "..oMMMLLLLMMMo..",
                    ".oLMDDMMMMDDMLo.",
                    ".oDDooMMMMooDDo.",
                    "..oo.oDDDDo.oo..",
                    "......oooo......",
            }, GEAR)),
            Map.entry("tool_cosmetics", new Icon(new String[] {
                    "......ooo.......",
                    "......oLo.......",
                    ".....ooMoo......",
                    ".....oLMLo......",
                    ".....oMMMo......",
                    "ooooooMMMoooooo.",
                    "oLLLLLMMMLLLLLo.",
                    "ooDMMMMMMMMMDoo.",
                    ".ooDMMMMMMMDoo..",
                    "..ooMMMMMMMoo...",
                    "...oMMMMMMMo....",
                    "...oMMDDDMMo....",
                    "..ooMDoooDMoo...",
                    "..oLDoo.ooDLo...",
                    "..oooo...oooo...",
                    "................",
            }, STAR)),
            Map.entry("tool_profile", new Icon(new String[] {
                    "................",
                    "....oooooooo....",
                    "...oHHHHHHHHo...",
                    "...oHHHHHHHHo...",
                    "...oHSSSSSSHo...",
                    "...oSEeSSeESo...",
                    "...oSSSSSSSSo...",
                    "...oSSSssSSSo...",
                    "....oSSSSSSo....",
                    ".oooooSSSSooooo.",
                    "oTTTTtSSSStTTTTo",
                    "oTTTTTtSStTTTTTo",
                    "oTTTTTTttTTTTTTo",
                    "oTTTTTTTTTTTTTTo",
                    "oooooooooooooooo",
                    "................",
            }, PERSON)),
            Map.entry("tool_heads", new Icon(new String[] {
                    ".oooooooooooooo.",
                    "oHHHHHHHHHHHHHHo",
                    "oHHHHHHHHHHHHHHo",
                    "oHHHHHHHHHHHHHHo",
                    "oHhSSSSSSSSSShHo",
                    "oSSSSSSSSSSSSSSo",
                    "oSSEEeSSSSeEESSo",
                    "oSSEEeSSSSeEESSo",
                    "oSSSSSSssSSSSSSo",
                    "oSSSSSSssSSSSSSo",
                    "oSSSSHHHHHHSSSSo",
                    "oSSSSHhhhhHSSSSo",
                    "oSSSSSSSSSSSSSSo",
                    "oSSSSSSSSSSSSSSo",
                    "oSSSSSSSSSSSSSSo",
                    ".oooooooooooooo.",
            }, HEAD)),
            Map.entry("tool_shirts", new Icon(new String[] {
                    "................",
                    "..ooo......ooo..",
                    ".oWWWooooooWWWo.",
                    "oWWWWWWRRWWWWWWo",
                    "oWWWWWWWWWWWWWWo",
                    "oWWWWWWWWWWWWWWo",
                    "oWWooWWWWWWooWWo",
                    ".ooooWWWWWWoooo.",
                    "....oWWWWWwo....",
                    "....oWWWWWwo....",
                    "....oWWWWWwo....",
                    "....oWWWWWwo....",
                    "....oWWWWWwo....",
                    "....owwwwwwo....",
                    "....oooooooo....",
                    "................",
            }, SHIRT)),
            Map.entry("quicksg", new Icon(new String[] {
                    ".............oo.",
                    "............oGGo",
                    "...........oGWGo",
                    "..........oGWGo.",
                    ".........oGWGo..",
                    "........oGWGo...",
                    "..oo...oGWGo....",
                    "..oBo.oGWGo.....",
                    "...oBoGWGo......",
                    "....oBGGo.......",
                    ".....oBo........",
                    "....oBoBo.......",
                    "...oBo.oo.......",
                    "..oBo...........",
                    "..oo............",
                    "................",
            }, GOLD)),
            Map.entry("emeraldrush", new Icon(new String[] {
                    "................",
                    ".....oooooo.....",
                    "....oGgGGGGo....",
                    "...oGgGGGGGGo...",
                    "..oGgGGGGGGGGo..",
                    "..oGgGGGGGGGGo..",
                    "..oGGGGGGGGGGo..",
                    "..oGGGGGGGGGdo..",
                    "..oGGGGGGGGGdo..",
                    "..oGGGGGGGGddo..",
                    "...oGGGGGGGdo...",
                    "....oGGGGGdo....",
                    ".....oooooo.....",
                    "................",
                    "................",
                    "................",
            }, GREEN)),
            Map.entry("duell", new Icon(new String[] {
                    "oo............oo",
                    "oAo..........oAo",
                    ".oAo........oAo.",
                    "..oAo......oAo..",
                    "...oAo....oAo...",
                    "....oAo..oAo....",
                    ".....oAooAo.....",
                    "......oAAo......",
                    "......oAAo......",
                    ".....oAooAo.....",
                    "....oBo..oBo....",
                    "...oBo....oBo...",
                    "..oBo......oBo..",
                    ".oBo........oBo.",
                    ".oo..........oo.",
                    "................",
            }, AQUA)),
            Map.entry("jump", new Icon(new String[] {
                    "................",
                    "....oooo........",
                    "...oCCCCo.......",
                    "...oCccCo.......",
                    "...oCCCCo.......",
                    "...oCccCo.......",
                    "...oCCCCoooo....",
                    "...oCccCCCCCo...",
                    "...oCCCCCCCCo...",
                    "..ooCCCCCCCCo...",
                    ".oCCCCCCCCCCo...",
                    ".oCCCCCCCCCCo...",
                    "..oooooooooo....",
                    "................",
                    "................",
                    "................",
            }, CYAN)),
            Map.entry("premium", new Icon(new String[] {
                    "................",
                    "................",
                    ".oo....oo....oo.",
                    "oGGo..oGGo..oGGo",
                    "oGGGo.oGGo.oGGGo",
                    "oGGGGooGGooGGGGo",
                    "oGGGGGGGGGGGGGGo",
                    "oGGGGGGGGGGGGGGo",
                    "oGGGWGGGWGGGWGGo",
                    "oGGGGGGGGGGGGGGo",
                    "oggggggggggggggo",
                    "oGGGGGGGGGGGGGGo",
                    "oggggggggggggggo",
                    ".oooooooooooooo.",
                    "................",
                    "................",
            }, GOLD))
    );

    static final String[] ICON_PROFILE = {
            "...##...",
            "..####..",
            "..####..",
            "...##...",
            ".######.",
            "########",
            "########",
            "########",
    };

    static final String[] ICON_COINS = {
            "..####..",
            ".######.",
            "##.##.##",
            "##.#..##",
            "##.#..##",
            "##.##.##",
            ".######.",
            "..####..",
    };

    static final String[] ICON_LEVEL = {
            "...#....",
            "...#....",
            "..###...",
            "#######.",
            ".#####..",
            "..###...",
            ".##.##..",
            "#.....#.",
    };

    static final String[] ICON_CHAT = {
            ".######.",
            "########",
            "########",
            "########",
            ".######.",
            "..##....",
            ".#......",
            "........",
    };

    static final String[] ICON_SERVER = {
            "########",
            "#.....##",
            "########",
            "........",
            "########",
            "#.....##",
            "########",
            "........",
    };

    static final String[] ICON_PLAYERS = {
            ".##...##",
            ".##...##",
            "........",
            "###..###",
            "###..###",
            "###..###",
            "###..###",
            "###..###",
    };

    static final String[] ICON_MAP = {
            "..####..",
            ".######.",
            ".##..##.",
            ".##..##.",
            ".######.",
            "..####..",
            "...##...",
            "....#...",
    };

    static final String[] ICON_KIT = {
            "...##...",
            "..####..",
            "..####..",
            "..####..",
            "..####..",
            "########",
            "...##...",
            "..####..",
    };

    static final String[] ICON_SPECTATOR = {
            "........",
            "..####..",
            ".##..##.",
            "##.##.##",
            "##.##.##",
            ".##..##.",
            "..####..",
            "........",
    };

    static final String[] ICON_TIME = {
            "..####..",
            ".#....#.",
            "#..#...#",
            "#..#...#",
            "#..###.#",
            "#......#",
            ".#....#.",
            "..####..",
    };

    static final String[] ICON_EMERALD = {
            "..####..",
            ".#.####.",
            "#..#####",
            "#..#####",
            "########",
            "########",
            ".######.",
            "..####..",
    };

    static final String[] ICON_WINNER = {
            "########",
            "#.####.#",
            "#.####.#",
            "##.##.##",
            "..####..",
            "...##...",
            "..####..",
            ".######.",
    };

    static final String[] ICON_MODE = {
            "#.......",
            "#######.",
            "########",
            "########",
            "#######.",
            "#.......",
            "#.......",
            "#.......",
    };

    static final String[] ICON_OPPONENT = {
            "#......#",
            "##....##",
            ".##..##.",
            "..####..",
            "..####..",
            ".##..##.",
            "##....##",
            "#......#",
    };

    static final String[] ICON_KILLS = {
            ".######.",
            "########",
            "#..##..#",
            "#..##..#",
            "########",
            ".##..##.",
            "..####..",
            "..#..#..",
    };
}
