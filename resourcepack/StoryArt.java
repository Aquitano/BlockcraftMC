import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntBinaryOperator;

/**
 * The story dialogue's panel in {@code blockcraft:story}: caps and body slices, each as a left and a right half, that
 * stack to any height in steps of eight, the bar behind a highlighted answer, the badge behind an answer's number, the speaker's mark and the arrow
 * that says a line goes on. Every glyph has an ascent of seven, so it starts on its line's top like a letter does,
 * and the server's {@code DialoguePanel} counts the widths written here. Beside them, the empty skin the cast's
 * Mannequins wear under their BetterModel bodies.
 */
public final class StoryArt {

    private static final int WIDTH = 300;
    private static final int BACK = 0xD80B1119;
    private static final int EDGE = 0xFF2B3545;
    private static final int RIM = 0xFF3D4D63;
    private static final int ACCENT = 0xFF55E8F0;
    private static final int ACCENT_DIM = 0x9055E8F0;

    private record Glyph(char character, String file, BufferedImage image) { }

    public static void main(String[] args) throws Exception {
        var root = Path.of(args.length == 0 ? "src" : args[0]).resolve("assets/blockcraft");
        var glyphs = new ArrayList<Glyph>();
        char next = '\uE000';
        next = halves(glyphs, next, "top", top());
        next = halves(glyphs, next, "bottom", bottom());
        for (int height = 8; height <= 64; height *= 2) {
            next = halves(glyphs, next, "body_" + height, body(height));
        }
        next = halves(glyphs, next, "highlight", image(WIDTH - 12, 10, (x, y) ->
                x < 2 ? ACCENT : y == 0 || y == 9 ? 0x3055E8F0 : 0x2855E8F0));
        glyphs.add(new Glyph(next++, "badge", badge(EDGE)));
        glyphs.add(new Glyph(next++, "badge_lit", badge(ACCENT)));
        glyphs.add(new Glyph(next++, "speaker", image(5, 7, (x, y) ->
                Math.abs(x - 2) + Math.abs(y - 3) <= 2 ? ACCENT : 0)));
        glyphs.add(new Glyph(next, "more", image(7, 7, (x, y) ->
                y >= 3 && y - 3 <= 3 - Math.abs(x - 3) && y - 3 >= 2 - Math.abs(x - 3) ? ACCENT : 0)));
        var providers = new ArrayList<String>();
        for (var glyph : glyphs) {
            var file = root.resolve("textures/font/story/" + glyph.file() + ".png");
            Files.createDirectories(file.getParent());
            ImageIO.write(glyph.image(), "png", file.toFile());
            providers.add("    {\"type\":\"bitmap\",\"file\":\"blockcraft:font/story/%s.png\",\"ascent\":7,\"height\":%d,\"chars\":[\"\\u%04X\"]}"
                    .formatted(glyph.file(), glyph.image().getHeight(), (int) glyph.character()));
        }
        Files.writeString(root.resolve("font/story.json"), "{\n  \"providers\": [\n" + String.join(",\n", providers) + "\n  ]\n}\n");
        var blank = root.resolve("textures/entity/story/blank.png");
        Files.createDirectories(blank.getParent());
        ImageIO.write(image(64, 64, (x, y) -> 0), "png", blank.toFile());
    }

    /** {@code image} as its left and right halves, since the client's font atlas holds no glyph wider than 256 pixels. */
    private static char halves(List<Glyph> glyphs, char first, String name, BufferedImage image) {
        int half = image.getWidth() / 2;
        glyphs.add(new Glyph(first, name + "_left", image.getSubimage(0, 0, half, image.getHeight())));
        glyphs.add(new Glyph((char) (first + 1), name + "_right", image.getSubimage(half, 0, image.getWidth() - half, image.getHeight())));
        return (char) (first + 2);
    }

    /** The top edge with a cut left corner and a short lit strip where the speaker's name begins. */
    private static BufferedImage top() {
        return image(WIDTH, 8, (x, y) -> {
            int cut = 3 - y;
            if (x < cut) return 0;
            if (y == 0 || x == cut) return x >= 4 && x < 52 ? ACCENT : RIM;
            if (y == 1 && x >= 4 && x < 52) return ACCENT_DIM;
            return x == WIDTH - 1 ? EDGE : BACK;
        });
    }

    /** The bottom edge with a cut right corner, mirroring the top. */
    private static BufferedImage bottom() {
        return image(WIDTH, 8, (x, y) -> {
            int cut = WIDTH - 1 - (y - 4);
            if (y >= 4 && x > cut) return 0;
            if (y == 7 || y >= 4 && x == cut) return EDGE;
            return x == 0 ? EDGE : BACK;
        });
    }

    private static BufferedImage body(int height) {
        return image(WIDTH, height, (x, y) -> x == 0 || x == WIDTH - 1 ? EDGE : BACK);
    }

    private static BufferedImage badge(int frame) {
        return image(9, 9, (x, y) -> x == 0 || y == 0 || x == 8 || y == 8 ? frame : 0xE0141C29);
    }

    private static BufferedImage image(int width, int height, IntBinaryOperator colour) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, colour.applyAsInt(x, y));
            }
        }
        return image;
    }
}
