import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The TTT 2 chore games as pictures of their stations. Each game gets a screen, the only glyph of the font
 * {@code blockcraft:chore/<game>} and as large as the chest screen it replaces, and the pieces that move on it, each an
 * item model {@code items/chore/<game>/<part>.json} that the server swaps per slot. The slots each screen frames are the
 * ones the games in {@code paper/ttt/.../task/game} place their pieces on.
 * <p>
 * A new chore game adds one {@link Game} here: its rows, its accent, a painter for the scene and one for each part.
 */
public final class ChoreArt {

    static final char SCREEN = '';
    static final int WIDTH = PackArt.PANEL_WIDTH;
    static final int SCENE_LEFT = 7;
    static final int SCENE_TOP = 17;
    static final int SCENE_WIDTH = 162;
    static final int TRANSPARENT = 0;

    record Game(String key, int rows, int accent, Consumer<Canvas> scene, Map<String, Consumer<Canvas>> parts,
                Map<Integer, String> sample) {
    }

    public static void main(String[] args) throws IOException {
        var root = Path.of(args[0]);
        var games = games();
        for (var game : games) {
            write(root, game);
        }
        if (args.length > 1) {
            var sheet = Path.of(args[1]);
            Files.createDirectories(sheet.toAbsolutePath().getParent());
            PackArt.write(contactSheet(games, root), sheet);
        }
    }

    static void write(Path root, Game game) throws IOException {
        var fonts = root.resolve("assets/blockcraft/font/chore");
        var fontTextures = root.resolve("assets/blockcraft/textures/font/chore");
        var textures = root.resolve("assets/blockcraft/textures/item/chore/" + game.key());
        var models = root.resolve("assets/blockcraft/models/item/chore/" + game.key());
        var items = root.resolve("assets/blockcraft/items/chore/" + game.key());
        for (var directory : List.of(fonts, fontTextures, textures, models, items)) {
            Files.createDirectories(directory);
        }
        var screen = screen(game);
        PackArt.write(screen, fontTextures.resolve(game.key() + ".png"));
        Files.writeString(fonts.resolve(game.key() + ".json"), """
                {
                  "providers": [
                    {"type":"bitmap","file":"blockcraft:font/chore/%s.png","ascent":13,"height":%d,"chars":["\\u%04x"]}
                  ]
                }
                """.formatted(game.key(), screen.getHeight(), (int) SCREEN));
        for (var part : game.parts().entrySet()) {
            String name = "chore/" + game.key() + "/" + part.getKey();
            PackArt.write(piece(part.getValue()), textures.resolve(part.getKey() + ".png"));
            Files.writeString(models.resolve(part.getKey() + ".json"), """
                    {
                      "parent": "minecraft:item/generated",
                      "textures": {
                        "layer0": "blockcraft:item/%s"
                      }
                    }
                    """.formatted(name));
            Files.writeString(items.resolve(part.getKey() + ".json"), """
                    {
                      "model": {
                        "type": "minecraft:model",
                        "model": "blockcraft:item/%s"
                      }
                    }
                    """.formatted(name));
        }
    }

    /** The network's menu panel with the game's accent under the title and its scene over the chest's slots. */
    static BufferedImage screen(Game game) {
        var image = PackArt.panel(game.rows(), false);
        var canvas = new Canvas(image);
        canvas.fill(1, 15, WIDTH - 2, 1, mix(game.accent(), PackArt.BORDER, .55));
        canvas.fill(1, 16, WIDTH - 2, 1, game.accent());
        game.scene().accept(canvas);
        return image;
    }

    static BufferedImage piece(Consumer<Canvas> painter) {
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        painter.accept(new Canvas(image));
        return image;
    }

    static int slotX(int slot) {
        return 8 + slot % 9 * 18;
    }

    static int slotY(int slot) {
        return 18 + slot / 9 * 18;
    }

    static int sceneHeight(int rows) {
        return rows * 18;
    }

    static List<Game> games() {
        return List.of(forge(), organ(), potion(), chronicle(), ledger(), millwork());
    }

    // Forge ----------------------------------------------------------------------------------------------------------

    static final int SOOT = 0xFF1A1110;
    static final int MORTAR = 0xFF2A1612;
    static final int BRICK = 0xFF6B2E22;
    static final int IRON = 0xFF3C3F46;
    static final int IRON_LIGHT = 0xFF5A5F69;
    static final int IRON_DARK = 0xFF24262B;
    static final int COAL = 0xFF161414;
    static final int EMBER = 0xFFFF8A1E;
    static final int FLAME = 0xFFFFD23F;
    static final int HOT_WHITE = 0xFFFFF4D6;
    static final int DEEP_RED = 0xFF9E2216;
    static final int ZONE = 0xFF5BC236;
    static final int LEATHER = 0xFF7A4A2A;
    static final int LEATHER_DARK = 0xFF4E2E19;
    static final int WOOD = 0xFF8A5A30;
    static final int BRASS = 0xFFC9963A;

    /**
     * A smithy wall: a bed of coals that glow as the work progresses, the scale that marks the right heat, the heat
     * gauge's glass tube, and the hearth floor with the bellows between an anvil and the tongs.
     */
    static Game forge() {
        var parts = new LinkedHashMap<String, Consumer<Canvas>>();
        parts.put("ember", c -> coal(c, true));
        parts.put("ember_off", c -> coal(c, false));
        parts.put("band", c -> {
            c.fill(0, 12, 16, 3, ZONE);
            c.fill(0, 12, 16, 1, mix(ZONE, HOT_WHITE, .5));
            for (int row = 0; row < 4; row++) {
                c.fill(4 + row, 5 + row, 8 - 2 * row, 1, ZONE);
            }
        });
        parts.put("band_off", c -> {
            c.fill(0, 13, 16, 1, IRON_LIGHT);
            c.fill(7, 10, 2, 3, IRON_LIGHT);
        });
        parts.put("heat_off", c -> heat(c, mix(SOOT, IRON, .4), mix(SOOT, IRON, .6)));
        parts.put("heat_low", c -> heat(c, DEEP_RED, mix(DEEP_RED, EMBER, .5)));
        parts.put("heat", c -> heat(c, EMBER, FLAME));
        parts.put("heat_high", c -> heat(c, 0xFFE0402A, HOT_WHITE));
        parts.put("bellows", ChoreArt::bellows);
        return new Game("forge", 4, EMBER, c -> {
            int height = sceneHeight(4);
            c.fill(SCENE_LEFT, SCENE_TOP, SCENE_WIDTH, height, MORTAR);
            for (int y = SCENE_TOP; y < SCENE_TOP + height; y += 6) {
                int offset = (y / 6) % 2 == 0 ? 0 : 6;
                for (int x = SCENE_LEFT - offset; x < SCENE_LEFT + SCENE_WIDTH; x += 12) {
                    int shade = Math.floorMod(x * 31 + y * 17, 5);
                    c.fillClipped(x, y, 11, 5, mix(BRICK, SOOT, shade * .08), SCENE_LEFT, SCENE_WIDTH);
                }
            }
            c.fill(SCENE_LEFT, 17, SCENE_WIDTH, 18, IRON_DARK);
            c.fill(SCENE_LEFT, 34, SCENE_WIDTH, 1, IRON_LIGHT);
            for (int column = 0; column < 9; column++) {
                int x = slotX(column);
                c.fill(x - 1, 17, 18, 17, SOOT);
                c.fill(x, 18, 16, 15, COAL);
                c.fill(x - 1, 17, 18, 1, IRON);
            }
            c.fill(SCENE_LEFT, 46, SCENE_WIDTH, 7, mix(IRON_DARK, SOOT, .3));
            c.fill(SCENE_LEFT, 46, SCENE_WIDTH, 1, IRON);
            c.fill(SCENE_LEFT - 1, 53, SCENE_WIDTH + 2, 18, IRON_LIGHT);
            c.fill(SCENE_LEFT, 54, SCENE_WIDTH, 16, 0xFF120C0C);
            c.fill(SCENE_LEFT, 54, SCENE_WIDTH, 1, IRON);
            c.fill(SCENE_LEFT, 72, SCENE_WIDTH, 17, 0xFF3A3532);
            for (int x = SCENE_LEFT; x < SCENE_LEFT + SCENE_WIDTH; x += 18) {
                c.fill(x, 72, 1, 17, 0xFF2A2624);
            }
            c.fill(SCENE_LEFT, 72, SCENE_WIDTH, 1, 0xFF4A4542);
            anvil(c, 14, 76);
            c.fill(slotX(31) - 3, 71, 22, 18, SOOT);
            c.fill(slotX(31) - 2, 72, 20, 16, mix(SOOT, EMBER, .12));
            tongs(c, 120, 74);
            c.well(35, IRON_DARK, IRON, IRON_LIGHT);
        }, parts, Map.ofEntries(Map.entry(0, "ember"), Map.entry(1, "ember"), Map.entry(2, "ember"), Map.entry(3, "ember_off"),
                Map.entry(4, "ember_off"), Map.entry(5, "ember_off"), Map.entry(6, "ember_off"), Map.entry(7, "ember_off"), Map.entry(8, "ember_off"),
                Map.entry(9, "band_off"), Map.entry(10, "band_off"), Map.entry(11, "band_off"), Map.entry(12, "band"), Map.entry(13, "band"),
                Map.entry(14, "band"), Map.entry(15, "band_off"), Map.entry(16, "band_off"), Map.entry(17, "band_off"),
                Map.entry(18, "heat_low"), Map.entry(19, "heat_low"), Map.entry(20, "heat_low"), Map.entry(21, "heat"), Map.entry(22, "heat"),
                Map.entry(23, "heat_off"), Map.entry(24, "heat_off"), Map.entry(25, "heat_off"), Map.entry(26, "heat_off"),
                Map.entry(31, "bellows")));
    }

    static void coal(Canvas c, boolean lit) {
        int body = lit ? 0xFF2A1A14 : COAL;
        int rim = lit ? 0xFF4A2A1A : 0xFF2C2A2A;
        c.disk(5.5, 10.5, 4.2, body);
        c.disk(10.5, 9.5, 4.6, body);
        c.disk(8, 6.5, 3.6, body);
        c.fill(5, 4, 3, 1, rim);
        c.fill(9, 5, 3, 1, rim);
        if (lit) {
            c.line(4, 11, 7, 8, EMBER);
            c.line(7, 8, 9, 10, EMBER);
            c.line(9, 10, 12, 7, FLAME);
            c.line(8, 5, 8, 7, EMBER);
            c.set(9, 10, FLAME);
            c.set(11, 12, EMBER);
            c.set(6, 12, DEEP_RED);
        } else {
            c.set(6, 9, 0xFF3A3838);
            c.set(11, 8, 0xFF3A3838);
        }
    }

    static void heat(Canvas c, int body, int shine) {
        c.fill(0, 1, 16, 14, body);
        c.fill(0, 2, 16, 2, shine);
        c.fill(0, 13, 16, 2, mix(body, SOOT, .35));
    }

    static void bellows(Canvas c) {
        c.fill(1, 3, 10, 2, WOOD);
        c.fill(1, 11, 10, 2, WOOD);
        for (int x = 2; x < 10; x++) {
            int bulge = x < 6 ? 0 : x - 5;
            c.fill(x, 5 + bulge / 2, 1, 6 - bulge, x % 2 == 0 ? LEATHER : LEATHER_DARK);
        }
        c.fill(0, 2, 3, 3, mix(WOOD, SOOT, .3));
        c.fill(0, 11, 3, 3, mix(WOOD, SOOT, .3));
        c.fill(10, 6, 3, 4, BRASS);
        c.fill(13, 7, 3, 2, BRASS);
        c.set(15, 7, HOT_WHITE);
        c.fill(1, 3, 10, 1, mix(WOOD, HOT_WHITE, .25));
    }

    static void anvil(Canvas c, int x, int y) {
        c.fill(x, y, 30, 4, IRON_LIGHT);
        c.fill(x - 4, y + 1, 4, 2, IRON_LIGHT);
        c.fill(x + 6, y + 4, 16, 3, IRON);
        c.fill(x + 3, y + 7, 22, 4, IRON);
        c.fill(x, y, 30, 1, 0xFF8A909A);
    }

    static void tongs(Canvas c, int x, int y) {
        c.line(x, y + 12, x + 22, y + 2, IRON_LIGHT);
        c.line(x, y + 13, x + 22, y + 5, IRON);
        c.disk(x + 4.5, y + 10.5, 1.6, BRASS);
    }

    // Organ ----------------------------------------------------------------------------------------------------------

    static final int WALNUT = 0xFF3B2416;
    static final int WALNUT_DARK = 0xFF26170E;
    static final int WALNUT_LIGHT = 0xFF5A3A24;
    static final int PIPE = 0xFFB9BEC7;
    static final int PIPE_SHADE = 0xFF7C828E;
    static final int PIPE_DARK = 0xFF3A3D44;
    static final int GOLD = 0xFFD9A441;
    static final int IVORY = 0xFFEDE3C8;
    static final int[][] KEY_COLOURS = {
            {0xFF7A2A2A, 0xFFFF5A4E}, {0xFF7A4A22, 0xFFFF9E3A}, {0xFF7A6A22, 0xFFFFE04A},
            {0xFF3E6A26, 0xFF8CF05A}, {0xFF22606A, 0xFF5AE6F0}, {0xFF52306E, 0xFFC27AFF},
    };

    /** An organ console: pipes either side of the music stand, the keys on their keybed with stops at the ends, pedals below. */
    static Game organ() {
        var parts = new LinkedHashMap<String, Consumer<Canvas>>();
        for (int key = 0; key < KEY_COLOURS.length; key++) {
            int[] colours = KEY_COLOURS[key];
            parts.put("key_" + key, c -> organKey(c, colours[0], false));
            parts.put("key_" + key + "_lit", c -> organKey(c, colours[1], true));
        }
        parts.put("listen", c -> sheet(c, GOLD, false));
        parts.put("play", c -> sheet(c, ZONE, true));
        return new Game("organ", 3, 0xFFB07CE8, c -> {
            c.fill(SCENE_LEFT, SCENE_TOP, SCENE_WIDTH, sceneHeight(3), WALNUT);
            for (int x = SCENE_LEFT + 3; x < SCENE_LEFT + SCENE_WIDTH; x += 7) {
                c.fill(x, SCENE_TOP, 1, sceneHeight(3), WALNUT_DARK);
            }
            c.fill(SCENE_LEFT, 17, SCENE_WIDTH, 18, WALNUT_DARK);
            int[] heights = {6, 8, 10, 12, 13, 14, 15, 16};
            for (int side = 0; side < 2; side++) {
                for (int pipe = 0; pipe < 13; pipe++) {
                    int x = side == 0 ? 9 + pipe * 5 : 166 - pipe * 5 - 4;
                    if (x > 74 && x < 98) {
                        continue;
                    }
                    int height = heights[Math.min(pipe * 8 / 13, heights.length - 1)];
                    int top = 34 - height;
                    c.fill(x, top, 4, height, PIPE_SHADE);
                    c.fill(x + 1, top, 2, height, PIPE);
                    c.fill(x, top, 4, 1, PIPE_DARK);
                    c.fill(x + 1, 30, 2, 1, PIPE_DARK);
                }
            }
            c.fill(SCENE_LEFT, 33, SCENE_WIDTH, 2, GOLD);
            c.well(4, mix(WALNUT_DARK, GOLD, .15), GOLD, mix(GOLD, IVORY, .4));
            c.fill(SCENE_LEFT, 35, SCENE_WIDTH, 18, 0xFF16100B);
            c.fill(slotX(10) - 2, 35, 6 * 18 + 2, 18, 0xFF0E0A07);
            for (int x : new int[] {slotX(9) + 8, slotX(16) + 8, slotX(17) + 8}) {
                c.disk(x - 0.5, 41.5, 3.2, IVORY);
                c.disk(x - 0.5, 41.5, 2, 0xFFB43C30);
                c.fill(x - 2, 47, 3, 3, WALNUT_LIGHT);
            }
            c.fill(SCENE_LEFT, 52, SCENE_WIDTH, 1, GOLD);
            c.fill(SCENE_LEFT, 53, SCENE_WIDTH, 18, WALNUT_DARK);
            for (int pedal = 0; pedal < 14; pedal++) {
                int x = 12 + pedal * 10;
                if (x + 6 > slotX(26) - 2) {
                    break;
                }
                c.fill(x, 60, 7, 8, pedal % 3 == 1 ? 0xFF16100B : WALNUT_LIGHT);
                c.fill(x, 60, 7, 1, pedal % 3 == 1 ? WALNUT : mix(WALNUT_LIGHT, IVORY, .3));
            }
            c.well(26, WALNUT_DARK, WALNUT_LIGHT, mix(WALNUT_LIGHT, IVORY, .3));
        }, parts, Map.of(4, "listen", 10, "key_0", 11, "key_1", 12, "key_2_lit", 13, "key_3", 14, "key_4", 15, "key_5"));
    }

    static void organKey(Canvas c, int colour, boolean lit) {
        if (lit) {
            c.fill(2, 0, 12, 16, (colour & 0xFFFFFF) | 0x70000000);
        }
        c.fill(3, 1, 10, 14, mix(colour, 0xFF000000, .35));
        c.fill(4, 1, 8, 13, colour);
        c.fill(4, 2, 2, 10, mix(colour, 0xFFFFFFFF, lit ? .7 : .25));
        c.fill(4, 13, 8, 1, mix(colour, 0xFF000000, .2));
        c.fill(3, 14, 10, 1, 0xFF0E0A07);
    }

    static void sheet(Canvas c, int note, boolean play) {
        c.fill(2, 1, 12, 14, play ? note : 0xFFA89670);
        c.fill(3, 2, 10, 12, IVORY);
        for (int y = 4; y <= 10; y += 2) {
            c.fill(4, y, 8, 1, 0xFFB8AC90);
        }
        c.disk(6.5, 10.5, 1.8, note);
        c.fill(8, 4, 1, 7, note);
        c.fill(8, 4, 3, 1, note);
        c.fill(10, 5, 1, 2, note);
    }

    // Potion ---------------------------------------------------------------------------------------------------------

    static final int STONE = 0xFF3A3F45;
    static final int STONE_DARK = 0xFF2A2E33;
    static final int PARCHMENT = 0xFFE3D3A8;
    static final int PARCHMENT_SHADE = 0xFFC9B585;
    static final int INK = 0xFF4A3426;
    static final int SHELF = 0xFF6B4423;
    static final int SHELF_DARK = 0xFF3F2814;
    static final int COPPER = 0xFFB06A35;
    static final int BREW = 0xFF6FD86B;

    /** An apothecary's corner: the recipe pinned to the wall, the cauldron on its fire, and the shelf of ingredients. */
    static Game potion() {
        var parts = new LinkedHashMap<String, Consumer<Canvas>>();
        parts.put("brewed", ChoreArt::flask);
        return new Game("potion", 4, BREW, c -> {
            int height = sceneHeight(4);
            c.fill(SCENE_LEFT, SCENE_TOP, SCENE_WIDTH, height, STONE_DARK);
            for (int y = SCENE_TOP; y < SCENE_TOP + height; y += 9) {
                int offset = (y / 9) % 2 == 0 ? 0 : 8;
                for (int x = SCENE_LEFT - offset; x < SCENE_LEFT + SCENE_WIDTH; x += 16) {
                    c.fillClipped(x, y, 15, 8, mix(STONE, STONE_DARK, Math.floorMod(x * 13 + y * 7, 4) * .15), SCENE_LEFT, SCENE_WIDTH);
                }
            }
            int cardLeft = slotX(1) - 3;
            int cardRight = slotX(7) + 19;
            c.fill(cardLeft, 18, cardRight - cardLeft, 16, PARCHMENT);
            c.fill(cardLeft, 18, 2, 16, PARCHMENT_SHADE);
            c.fill(cardRight - 2, 18, 2, 16, PARCHMENT_SHADE);
            c.fill(cardLeft, 34, cardRight - cardLeft, 1, 0xFF1A1C1F);
            c.disk(slotX(1) + 8, 26, 3, 0xFFB43C30);
            c.disk(slotX(1) + 8, 26, 1.5, 0xFFE06050);
            for (int slot = 2; slot <= 5; slot++) {
                c.frame(slotX(slot) - 1, 17, 18, 18, PARCHMENT_SHADE);
            }
            c.line(slotX(7) + 3, 30, slotX(7) + 13, 21, INK);
            c.line(slotX(7) + 4, 30, slotX(7) + 13, 22, INK);
            int middle = 88;
            c.fill(middle - 14, 38, 28, 2, mix(COPPER, 0xFFFFFFFF, .3));
            c.fill(middle - 13, 40, 26, 9, COPPER);
            c.fill(middle - 11, 49, 22, 2, mix(COPPER, 0xFF000000, .4));
            c.fill(middle - 12, 37, 24, 2, BREW);
            for (int[] bubble : new int[][] {{-6, 35}, {2, 34}, {7, 36}}) {
                c.disk(middle + bubble[0] + .5, bubble[1] + .5, 1.2, mix(BREW, 0xFFFFFFFF, .4));
            }
            c.fill(middle - 10, 51, 3, 2, 0xFF2A2A2A);
            c.fill(middle + 7, 51, 3, 2, 0xFF2A2A2A);
            for (int x = middle - 8; x < middle + 8; x += 3) {
                c.fill(x, 51, 2, 2, EMBER);
            }
            c.fill(SCENE_LEFT, 53, SCENE_WIDTH, 18, SHELF_DARK);
            c.fill(SCENE_LEFT, 68, SCENE_WIDTH, 3, SHELF);
            c.fill(SCENE_LEFT, 68, SCENE_WIDTH, 1, mix(SHELF, 0xFFFFFFFF, .25));
            c.line(slotX(18) + 8, 54, slotX(18) + 8, 58, 0xFF6A5A3A);
            for (int[] leaf : new int[][] {{4, 59}, {10, 60}, {6, 63}, {9, 65}, {7, 61}}) {
                c.disk(slotX(18) + leaf[0] + .5, leaf[1] + .5, 1.7, 0xFF4E8A3A);
            }
            c.fill(SCENE_LEFT, 71, SCENE_WIDTH, 18, SHELF);
            for (int y = 75; y < 89; y += 5) {
                c.fill(SCENE_LEFT, y, SCENE_WIDTH, 1, SHELF_DARK);
            }
            c.fill(slotX(27) + 3, 79, 10, 7, STONE);
            c.fill(slotX(27) + 4, 78, 8, 2, mix(STONE, 0xFFFFFFFF, .25));
            c.line(slotX(27) + 9, 72, slotX(27) + 13, 79, 0xFFA08A6A);
            c.well(35, SHELF_DARK, mix(SHELF, 0xFF000000, .3), mix(SHELF, 0xFFFFFFFF, .25));
        }, parts, Map.of(2, "brewed", 3, "vanilla", 4, "vanilla", 5, "vanilla", 19, "vanilla", 20, "vanilla", 21, "vanilla",
                22, "vanilla", 23, "vanilla", 24, "vanilla"));
    }

    static void flask(Canvas c) {
        c.fill(6, 1, 4, 2, 0xFF8A5A30);
        c.fill(6, 3, 4, 3, 0xFFCFE3EA);
        c.disk(7.5, 10, 5.4, 0xFFCFE3EA);
        c.disk(7.5, 10.5, 4.4, BREW);
        c.fill(4, 7, 8, 1, mix(BREW, 0xFFFFFFFF, .4));
        c.set(5, 9, 0xFFFFFFFF);
        c.line(9, 12, 10, 13, 0xFFFFFFFF);
        c.line(10, 13, 14, 9, 0xFFFFFFFF);
        c.line(9, 13, 10, 14, 0xFF2F6A2A);
        c.line(10, 14, 14, 10, 0xFF2F6A2A);
    }

    // Chronicle ------------------------------------------------------------------------------------------------------

    static final int OAK = 0xFF5C3A1E;
    static final int OAK_DARK = 0xFF3A2414;
    static final int OAK_LIGHT = 0xFF7A5230;
    static final int CASE_BACK = 0xFF22150C;
    static final int[] SPINES = {0xFF9E2B2B, 0xFFB5612A, 0xFFB59A2A, 0xFF3F7F3A, 0xFF2E7A7A, 0xFF2F4F9A, 0xFF6A3A8E};

    /** A bookcase: a candle and a scroll on the top shelf, the chronicle between two bookends, odd volumes lying below. */
    static Game chronicle() {
        var parts = new LinkedHashMap<String, Consumer<Canvas>>();
        for (int volume = 1; volume <= SPINES.length; volume++) {
            int colour = SPINES[volume - 1];
            parts.put("volume_" + volume, c -> spine(c, colour, 0, PARCHMENT, false));
            parts.put("volume_" + volume + "_held", c -> spine(c, colour, -1, PARCHMENT, true));
            parts.put("volume_" + volume + "_placed", c -> spine(c, colour, 0, ZONE, false));
        }
        return new Game("chronicle", 3, GOLD, c -> {
            c.fill(SCENE_LEFT, SCENE_TOP, SCENE_WIDTH, sceneHeight(3), CASE_BACK);
            for (int x = SCENE_LEFT + 5; x < SCENE_LEFT + SCENE_WIDTH; x += 9) {
                c.fill(x, SCENE_TOP, 1, sceneHeight(3), 0xFF1A1009);
            }
            for (int plank : new int[] {33, 51, 69}) {
                c.fill(SCENE_LEFT, plank, SCENE_WIDTH, 2, OAK_LIGHT);
                c.fill(SCENE_LEFT, plank + 1, SCENE_WIDTH, 1, OAK);
            }
            int candle = slotX(1) + 6;
            c.fill(candle, 24, 4, 9, IVORY);
            c.fill(candle + 1, 22, 2, 2, FLAME);
            c.set(candle + 1, 21, HOT_WHITE);
            c.fill(candle - 2, 32, 8, 1, BRASS);
            int scroll = slotX(5);
            c.fill(scroll, 27, 22, 5, PARCHMENT);
            c.fill(scroll, 27, 2, 5, PARCHMENT_SHADE);
            c.fill(scroll + 20, 27, 2, 5, PARCHMENT_SHADE);
            c.fill(scroll + 9, 27, 3, 5, 0xFFB43C30);
            bookend(c, slotX(9) + 2);
            bookend(c, slotX(17) + 4);
            int stack = 69;
            for (int[] book : new int[][] {{slotX(18) + 2, 32, 0xFF6A3A2A}, {slotX(18) + 4, 28, 0xFF2F4F6A}, {slotX(18) + 1, 30, 0xFF4E6A2A}}) {
                stack -= 4;
                c.fill(book[0], stack, book[1], 4, book[2]);
                c.fill(book[0], stack, book[1], 1, mix(book[2], 0xFFFFFFFF, .25));
                c.fill(book[0] + book[1] - 3, stack + 1, 2, 2, GOLD);
            }
            int ink = slotX(22) + 4;
            c.fill(ink, 61, 8, 8, 0xFF1E2330);
            c.fill(ink + 2, 59, 4, 2, 0xFF1E2330);
            c.line(ink + 4, 59, ink + 10, 53, IVORY);
            c.well(26, OAK_DARK, OAK, OAK_LIGHT);
        }, parts, Map.of(10, "volume_3_held", 11, "volume_2_placed", 12, "volume_1", 13, "volume_4_placed", 14, "volume_7",
                15, "volume_6_placed", 16, "volume_5"));
    }

    static void spine(Canvas c, int colour, int lift, int label, boolean held) {
        int top = 1 + lift;
        if (held) {
            c.fill(2, top - 1, 12, 17, GOLD);
        }
        c.fill(3, top, 10, 15, mix(colour, 0xFF000000, .35));
        c.fill(4, top, 8, 15, colour);
        c.fill(4, top, 2, 15, mix(colour, 0xFFFFFFFF, .2));
        c.fill(3, top + 2, 10, 1, GOLD);
        c.fill(3, top + 12, 10, 1, GOLD);
        c.fill(5, top + 5, 6, 3, label);
        c.fill(5, top + 5, 6, 1, mix(label, 0xFFFFFFFF, .4));
    }

    static void bookend(Canvas c, int x) {
        c.fill(x, 38, 3, 13, IRON);
        c.fill(x - 2, 48, 7, 3, IRON);
        c.fill(x, 38, 1, 13, IRON_LIGHT);
    }

    // Ledger ---------------------------------------------------------------------------------------------------------

    static final int DESK = 0xFF4A2E1B;
    static final int PAGE = 0xFFEADFC4;
    static final int PAGE_SHADE = 0xFFD6C8A6;
    static final int RULE = 0xFFB8C6D6;
    static final int MARGIN = 0xFFC0504D;
    static final int LEDGER_INK = 0xFF2B2A3A;

    /** An open ledger on the desk: a red header rule, the entries along the blue lines and the quill in the gutter. */
    static Game ledger() {
        var parts = new LinkedHashMap<String, Consumer<Canvas>>();
        parts.put("entry", c -> entry(c, false));
        parts.put("entry_struck", c -> entry(c, true));
        parts.put("quill", ChoreArt::quill);
        return new Game("ledger", 3, MARGIN, c -> {
            c.fill(SCENE_LEFT, SCENE_TOP, SCENE_WIDTH, sceneHeight(3), DESK);
            for (int y = SCENE_TOP + 4; y < SCENE_TOP + sceneHeight(3); y += 7) {
                c.fill(SCENE_LEFT, y, SCENE_WIDTH, 1, mix(DESK, 0xFF000000, .2));
            }
            c.fill(SCENE_LEFT + 1, 19, SCENE_WIDTH - 2, 51, mix(DESK, 0xFF000000, .45));
            c.fill(SCENE_LEFT + 1, 18, SCENE_WIDTH - 3, 50, PAGE);
            c.fill(SCENE_LEFT + 1, 66, SCENE_WIDTH - 3, 2, PAGE_SHADE);
            for (int x = 85; x < 92; x++) {
                c.fill(x, 18, 1, 50, mix(PAGE, 0xFF7A5A3A, 1 - Math.abs(x - 88) / 4.0));
            }
            c.fill(SCENE_LEFT + 1, 34, SCENE_WIDTH - 3, 1, MARGIN);
            c.fill(SCENE_LEFT + 1, 36, SCENE_WIDTH - 3, 1, MARGIN);
            for (int y = 40; y < 66; y += 6) {
                c.fill(SCENE_LEFT + 1, y, SCENE_WIDTH - 3, 1, RULE);
            }
            for (int x : new int[] {14, 98}) {
                for (int y = 22; y < 31; y += 3) {
                    c.fill(x, y, 18 + (y * 7) % 23, 1, mix(PAGE, LEDGER_INK, .35));
                }
            }
            c.well(26, PAGE_SHADE, mix(PAGE_SHADE, 0xFF000000, .25), PAGE);
        }, parts, Map.of(4, "quill", 9, "entry", 10, "entry_struck", 11, "entry", 12, "entry", 13, "entry", 14, "entry_struck",
                15, "entry", 16, "entry", 17, "entry"));
    }

    static void entry(Canvas c, boolean struck) {
        int ink = struck ? mix(LEDGER_INK, PAGE, .35) : LEDGER_INK;
        digit(c, 1, 2, ink);
        digit(c, 4, 2, ink);
        c.fill(8, 4, 3, 1, ink);
        c.fill(9, 3, 1, 3, ink);
        digit(c, 12, 2, ink);
        c.fill(2, 10, 3, 1, ink);
        c.fill(2, 12, 3, 1, ink);
        digit(c, 7, 9, ink);
        digit(c, 10, 9, ink);
        c.fill(1, 15, 14, 1, mix(ink, PAGE, .5));
        if (struck) {
            c.line(0, 13, 15, 2, MARGIN);
            c.line(0, 14, 15, 3, MARGIN);
        }
    }

    static void digit(Canvas c, int x, int y, int ink) {
        c.fill(x, y, 2, 1, ink);
        c.fill(x + 1, y + 1, 1, 2, ink);
        c.fill(x, y + 3, 2, 1, ink);
        c.fill(x, y + 4, 1, 1, ink);
    }

    static void quill(Canvas c) {
        c.fill(2, 9, 7, 6, 0xFF1E2330);
        c.fill(3, 7, 5, 2, 0xFF1E2330);
        c.fill(3, 10, 1, 3, 0xFF4A5470);
        c.line(5, 8, 14, 0, IVORY);
        c.line(7, 7, 14, 1, IVORY);
        c.line(8, 7, 15, 1, PAGE_SHADE);
        c.line(6, 7, 13, 0, PAGE_SHADE);
    }

    // Millwork -------------------------------------------------------------------------------------------------------

    static final int BEAM = 0xFF6B4A2B;
    static final int BEAM_DARK = 0xFF4A321C;
    static final int MILL = 0xFF1E1812;
    static final int BRASS_DARK = 0xFF8A6220;

    /** The mill's gear room: the timing beam with its notch rail and the axle the cog runs on, then the gearbox. */
    static Game millwork() {
        var parts = new LinkedHashMap<String, Consumer<Canvas>>();
        parts.put("rail", c -> c.fill(7, 11, 2, 5, mix(BEAM, 0xFFFFFFFF, .45)));
        parts.put("notch", c -> {
            c.fill(6, 0, 4, 16, (ZONE & 0xFFFFFF) | 0x50000000);
            for (int row = 0; row < 6; row++) {
                c.fill(2 + row, 6 + row, 12 - 2 * row, 1, ZONE);
            }
            c.fill(2, 6, 12, 1, mix(ZONE, 0xFFFFFFFF, .5));
            c.fill(7, 12, 2, 4, ZONE);
        });
        parts.put("track", c -> {
            axle(c, 0, 16);
            c.fill(7, 6, 2, 5, IRON_DARK);
        });
        parts.put("marker", c -> {
            axle(c, 0, 16);
            c.gear(8, 8.5, 7.4, 8, BRASS, BRASS_DARK);
            c.disk(8, 8.5, 2.2, mix(BRASS, 0xFFFFFFFF, .5));
        });
        parts.put("gear", c -> {
            c.gear(8, 8, 7.4, 8, BRASS, BRASS_DARK);
            c.disk(8, 8, 2.4, ZONE);
            c.disk(8, 8, 1, mix(ZONE, 0xFFFFFFFF, .5));
        });
        parts.put("gear_off", c -> {
            c.ring(8, 8, 7.5, 1, 0xFFB43C30);
            for (int[] gap : new int[][] {{7, 0}, {7, 15}, {0, 7}, {15, 7}}) {
                c.fill(gap[0], gap[1], 1, 1, TRANSPARENT);
            }
            c.disk(8, 8, 2, IRON_DARK);
        });
        return new Game("millwork", 3, BRASS, c -> {
            c.fill(SCENE_LEFT, SCENE_TOP, SCENE_WIDTH, sceneHeight(3), MILL);
            c.fill(SCENE_LEFT, 17, SCENE_WIDTH, 36, BEAM);
            for (int y = 19; y < 53; y += 5) {
                c.fill(SCENE_LEFT, y, SCENE_WIDTH, 1, BEAM_DARK);
            }
            c.fill(SCENE_LEFT, 31, SCENE_WIDTH, 3, BEAM_DARK);
            c.fill(SCENE_LEFT, 34, SCENE_WIDTH, 1, mix(BEAM, 0xFFFFFFFF, .2));
            c.fill(SCENE_LEFT, 35, SCENE_WIDTH, 18, mix(BEAM_DARK, MILL, .4));
            c.fill(SCENE_LEFT, 42, SCENE_WIDTH, 4, IRON);
            c.fill(SCENE_LEFT, 42, SCENE_WIDTH, 1, IRON_LIGHT);
            c.fill(SCENE_LEFT, 52, SCENE_WIDTH, 1, BEAM_DARK);
            c.gear(28, 66, 15, 10, mix(IRON, MILL, .3), mix(IRON_DARK, MILL, .3), SCENE_TOP + 36, SCENE_TOP + sceneHeight(3));
            c.gear(138, 66, 13, 9, mix(IRON, MILL, .3), mix(IRON_DARK, MILL, .3), SCENE_TOP + 36, SCENE_TOP + sceneHeight(3));
            c.fill(slotX(21) - 3, 53, 3 * 18 + 4, 18, BEAM_DARK);
            for (int slot = 21; slot <= 23; slot++) {
                c.disk(slotX(slot) + 8, slotY(slot) + 8, 8.5, MILL);
            }
            c.well(26, BEAM_DARK, IRON, IRON_LIGHT);
        }, parts, Map.ofEntries(Map.entry(0, "rail"), Map.entry(1, "rail"), Map.entry(2, "rail"), Map.entry(3, "rail"), Map.entry(4, "rail"),
                Map.entry(5, "rail"), Map.entry(6, "notch"), Map.entry(7, "rail"), Map.entry(8, "rail"), Map.entry(9, "track"),
                Map.entry(10, "track"), Map.entry(11, "marker"), Map.entry(12, "track"), Map.entry(13, "track"), Map.entry(14, "track"),
                Map.entry(15, "track"), Map.entry(16, "track"), Map.entry(17, "track"), Map.entry(21, "gear"), Map.entry(22, "gear_off"),
                Map.entry(23, "gear_off")));
    }

    static void axle(Canvas c, int from, int to) {
        c.fill(from, 6, to - from, 4, IRON);
        c.fill(from, 6, to - from, 1, IRON_LIGHT);
    }

    // Contact sheet --------------------------------------------------------------------------------------------------

    /** Every screen at twice its size with a sample state laid in, then every part of it below, for judging the set. */
    static BufferedImage contactSheet(List<Game> games, Path root) throws IOException {
        int scale = 2;
        int gap = 12;
        var close = ImageIO.read(root.resolve("assets/blockcraft/textures/item/close.png").toFile());
        var tiles = new ArrayList<BufferedImage>();
        for (var game : games) {
            var screen = screen(game);
            var canvas = new Canvas(screen);
            for (var placed : game.sample().entrySet()) {
                int x = slotX(placed.getKey());
                int y = slotY(placed.getKey());
                var part = game.parts().get(placed.getValue());
                if (part == null) {
                    canvas.frame(x + 2, y + 2, 12, 12, 0xFFFFFFFF);
                } else {
                    canvas.draw(piece(part), x, y);
                }
            }
            canvas.draw(close, slotX(game.rows() * 9 - 1), slotY(game.rows() * 9 - 1));
            int columns = 9;
            int partRows = (game.parts().size() + columns - 1) / columns;
            var tile = new BufferedImage(WIDTH, screen.getHeight() + 4 + partRows * 18, BufferedImage.TYPE_INT_ARGB);
            var tileCanvas = new Canvas(tile);
            tileCanvas.draw(screen, 0, 0);
            int index = 0;
            for (var part : game.parts().values()) {
                int x = 8 + index % columns * 18;
                int y = screen.getHeight() + 4 + index / columns * 18;
                tileCanvas.fill(x - 1, y - 1, 18, 18, PackArt.SLOT_FILL);
                tileCanvas.draw(piece(part), x, y);
                index++;
            }
            tiles.add(tile);
        }
        int columns = 3;
        int cellHeight = tiles.stream().mapToInt(BufferedImage::getHeight).max().orElseThrow();
        int rows = (tiles.size() + columns - 1) / columns;
        var sheet = new BufferedImage((columns * (WIDTH + gap) + gap) * scale, (rows * (cellHeight + gap) + gap) * scale, BufferedImage.TYPE_INT_ARGB);
        var sheetCanvas = new Canvas(sheet);
        sheetCanvas.fill(0, 0, sheet.getWidth(), sheet.getHeight(), 0xFF2B2D31);
        for (int index = 0; index < tiles.size(); index++) {
            int x = gap + index % columns * (WIDTH + gap);
            int y = gap + index / columns * (cellHeight + gap);
            sheetCanvas.drawScaled(tiles.get(index), x * scale, y * scale, scale);
        }
        return sheet;
    }

    // Drawing --------------------------------------------------------------------------------------------------------

    static int mix(int a, int b, double weight) {
        int alpha = (int) Math.round((a >>> 24) * (1 - weight) + (b >>> 24) * weight);
        int red = (int) Math.round(((a >> 16) & 0xFF) * (1 - weight) + ((b >> 16) & 0xFF) * weight);
        int green = (int) Math.round(((a >> 8) & 0xFF) * (1 - weight) + ((b >> 8) & 0xFF) * weight);
        int blue = (int) Math.round((a & 0xFF) * (1 - weight) + (b & 0xFF) * weight);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    record Canvas(BufferedImage image) {

        void set(int x, int y, int argb) {
            if (x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight()) {
                image.setRGB(x, y, argb);
            }
        }

        void fill(int x, int y, int width, int height, int argb) {
            for (int dy = 0; dy < height; dy++) {
                for (int dx = 0; dx < width; dx++) {
                    set(x + dx, y + dy, argb);
                }
            }
        }

        void fillClipped(int x, int y, int width, int height, int argb, int left, int span) {
            int from = Math.max(x, left);
            int to = Math.min(x + width, left + span);
            fill(from, y, to - from, height, argb);
        }

        void frame(int x, int y, int width, int height, int argb) {
            fill(x, y, width, 1, argb);
            fill(x, y + height - 1, width, 1, argb);
            fill(x, y, 1, height, argb);
            fill(x + width - 1, y, 1, height, argb);
        }

        /** A slot's sunk square in the scene's own colours, for the slots a game shows the network's items in. */
        void well(int slot, int fill, int edge, int shine) {
            int x = slotX(slot) - 1;
            int y = slotY(slot) - 1;
            fill(x, y, 18, 18, edge);
            fill(x + 1, y + 1, 16, 16, fill);
            fill(x + 1, y + 17, 16, 1, shine);
            fill(x + 17, y + 1, 1, 17, shine);
        }

        void disk(double centreX, double centreY, double radius, int argb) {
            for (int y = (int) Math.floor(centreY - radius); y <= Math.ceil(centreY + radius); y++) {
                for (int x = (int) Math.floor(centreX - radius); x <= Math.ceil(centreX + radius); x++) {
                    if (Math.hypot(x + .5 - centreX, y + .5 - centreY) <= radius) {
                        set(x, y, argb);
                    }
                }
            }
        }

        void ring(double centreX, double centreY, double radius, double thickness, int argb) {
            for (int y = (int) Math.floor(centreY - radius); y <= Math.ceil(centreY + radius); y++) {
                for (int x = (int) Math.floor(centreX - radius); x <= Math.ceil(centreX + radius); x++) {
                    double distance = Math.hypot(x + .5 - centreX, y + .5 - centreY);
                    if (distance <= radius && distance > radius - thickness) {
                        set(x, y, argb);
                    }
                }
            }
        }

        void gear(double centreX, double centreY, double radius, int teeth, int body, int shade) {
            gear(centreX, centreY, radius, teeth, body, shade, Integer.MIN_VALUE, Integer.MAX_VALUE);
        }

        /** A cog whose teeth stand out a quarter of its radius, drawn only between {@code top} and {@code bottom}. */
        void gear(double centreX, double centreY, double radius, int teeth, int body, int shade, int top, int bottom) {
            double inner = radius * .75;
            for (int y = (int) Math.floor(centreY - radius); y <= Math.ceil(centreY + radius); y++) {
                if (y < top || y >= bottom) {
                    continue;
                }
                for (int x = (int) Math.floor(centreX - radius); x <= Math.ceil(centreX + radius); x++) {
                    double distance = Math.hypot(x + .5 - centreX, y + .5 - centreY);
                    double angle = Math.atan2(y + .5 - centreY, x + .5 - centreX);
                    boolean tooth = Math.floorMod((int) Math.floor((angle + Math.PI) / (2 * Math.PI) * teeth * 2), 2) == 0;
                    if (distance <= inner || tooth && distance <= radius) {
                        set(x, y, distance > inner - 1.2 && distance <= inner || distance < radius * .3 ? shade : body);
                    }
                }
            }
        }

        void line(int x0, int y0, int x1, int y1, int argb) {
            int dx = Math.abs(x1 - x0);
            int dy = -Math.abs(y1 - y0);
            int stepX = x0 < x1 ? 1 : -1;
            int stepY = y0 < y1 ? 1 : -1;
            int error = dx + dy;
            while (true) {
                set(x0, y0, argb);
                if (x0 == x1 && y0 == y1) {
                    return;
                }
                int doubled = 2 * error;
                if (doubled >= dy) {
                    error += dy;
                    x0 += stepX;
                }
                if (doubled <= dx) {
                    error += dx;
                    y0 += stepY;
                }
            }
        }

        void draw(BufferedImage source, int left, int top) {
            for (int y = 0; y < source.getHeight(); y++) {
                for (int x = 0; x < source.getWidth(); x++) {
                    int over = source.getRGB(x, y);
                    if (over >>> 24 == 0 || left + x < 0 || top + y < 0 || left + x >= image.getWidth() || top + y >= image.getHeight()) {
                        continue;
                    }
                    int under = image.getRGB(left + x, top + y);
                    image.setRGB(left + x, top + y, mix(under | 0xFF000000, over | 0xFF000000, (over >>> 24) / 255.0));
                }
            }
        }

        void drawScaled(BufferedImage source, int left, int top, int scale) {
            for (int y = 0; y < source.getHeight() * scale; y++) {
                for (int x = 0; x < source.getWidth() * scale; x++) {
                    int argb = source.getRGB(x / scale, y / scale);
                    if (argb >>> 24 != 0) {
                        image.setRGB(left + x, top + y, argb);
                    }
                }
            }
        }
    }
}
