import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The shared dialogue panel's art in {@code blockcraft:dialogue}, its letters in {@code blockcraft:dialogue_text} and their
 * widths for the server's {@code DialogueText}. The server's {@code DialogueView} lays these out as a bottom-centre
 * {@code HudPanel}, which gives every glyph its row, so each one hangs from its line's top with an ascent of seven.
 */
public final class DialogueArt {
    private static final Color ACCENT = new Color(0x61DAD4);
    private static final int WIDTH = 300;
    /** The panel for up to four rows of text beside the portrait, and the one for five; DialogueView picks between them. */
    private static final int[] HEIGHTS = {56, 66};
    private static final int PORTRAIT = 48;
    private static final int LETTER_WIDTH = 6;
    private static final int LETTER_HEIGHT = 12;

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length == 0 ? "src" : args[0]);
        Path textures = root.resolve("assets/blockcraft/textures/gui/dialogue");
        Path fonts = root.resolve("assets/blockcraft/font");
        Path metrics = Path.of(args.length > 1 ? args[1] : "../paper/api/src/main/resources/dialogue-glyphs.properties");
        Files.createDirectories(textures);
        Files.createDirectories(fonts);
        var providers = new ArrayList<String>();
        for (int size = 0; size < HEIGHTS.length; size++) {
            int height = HEIGHTS[size];
            var panel = panel(height);
            // The client draws a glyph wider than its 256-pixel font atlas as a missing-glyph box, so the panel is two halves.
            String name = "panel_" + height;
            ImageIO.write(panel.getSubimage(0, 0, WIDTH / 2, height), "PNG", textures.resolve(name + "_left.png").toFile());
            ImageIO.write(panel.getSubimage(WIDTH / 2, 0, WIDTH / 2, height), "PNG", textures.resolve(name + "_right.png").toFile());
            providers.add(bitmap(name + "_left.png", height, List.of(escape(0xE600 + 2 * size))));
            providers.add(bitmap(name + "_right.png", height, List.of(escape(0xE601 + 2 * size))));
        }
        String[] portraits = {"pilot", "pilot_happy", "pilot_worried", "droid", "droid_alert", "courier", "local", "note"};
        for (int index = 0; index < portraits.length; index++) {
            var portrait = new BufferedImage(PORTRAIT, PORTRAIT, BufferedImage.TYPE_INT_ARGB);
            var g = portrait.createGraphics();
            portrait(g, index);
            g.dispose();
            ImageIO.write(portrait, "PNG", textures.resolve(portraits[index] + ".png").toFile());
            providers.add(bitmap(portraits[index] + ".png", PORTRAIT, List.of(escape(0xE610 + index))));
        }
        Files.writeString(fonts.resolve("dialogue.json"), font(providers));
        var characters = new ArrayList<Integer>();
        for (int cp = 33; cp <= 126; cp++) characters.add(cp);
        for (int cp = 161; cp <= 255; cp++) characters.add(cp);
        characters.add(0x2026);
        int rows = (characters.size() + 15) / 16;
        var letters = new BufferedImage(16 * LETTER_WIDTH, rows * LETTER_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        var g = letters.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 9));
        g.setColor(Color.WHITE);
        var glyphRows = new ArrayList<String>();
        var widths = new StringBuilder("32=4\n");
        for (int row = 0; row < rows; row++) {
            var chars = new StringBuilder();
            for (int column = 0; column < 16; column++) {
                int index = row * 16 + column;
                if (index >= characters.size()) {
                    chars.append("\\u0000");
                    continue;
                }
                int cp = characters.get(index);
                int x = column * LETTER_WIDTH;
                int y = row * LETTER_HEIGHT;
                g.setClip(x, y, LETTER_WIDTH, LETTER_HEIGHT);
                g.drawString(Character.toString(cp), x, y + 8);
                int ink = 0;
                for (int dx = 0; dx < LETTER_WIDTH; dx++) {
                    for (int dy = 0; dy < LETTER_HEIGHT; dy++) {
                        if (letters.getRGB(x + dx, y + dy) >>> 24 != 0) ink = dx + 1;
                    }
                }
                widths.append(cp).append('=').append(ink + 1).append('\n');
                chars.append(escape(cp));
            }
            glyphRows.add(chars.toString());
        }
        g.dispose();
        ImageIO.write(letters, "PNG", textures.resolve("letters.png").toFile());
        Files.createDirectories(metrics.getParent());
        Files.writeString(metrics, widths.toString());
        Files.writeString(fonts.resolve("dialogue_text.json"), font(List.of(
                "{\"type\":\"space\",\"advances\":{\" \":4}}", bitmap("letters.png", LETTER_HEIGHT, glyphRows))));
    }

    /** Dark glass with a rim and two accent strips, one over the speaker's name and one in the opposite corner. */
    private static BufferedImage panel(int height) {
        var panel = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_ARGB);
        var g = panel.createGraphics();
        g.setColor(new Color(0xF015202D, true));
        g.fillRect(0, 0, WIDTH, height);
        g.setColor(new Color(0x567080));
        g.drawRect(0, 0, WIDTH - 1, height - 1);
        g.setColor(ACCENT);
        g.fillRect(0, 0, 36, 2);
        g.fillRect(WIDTH - 36, height - 2, 36, 2);
        g.dispose();
        return panel;
    }

    private static void portrait(Graphics2D g, int index) {
        g.setColor(new Color(0x162F3C));
        g.fillRect(0, 0, 48, 48);
        g.setColor(new Color(0x345567));
        for (int y = 4; y < 48; y += 6) g.drawLine(1, y, 46, y);
        if (index > 4) {
            switch (index) {
                case 5 -> courier(g);
                case 6 -> local(g);
                default -> note(g);
            }
        } else if (index < 3) {
            g.setColor(new Color(0xC47F4E));
            g.fillRect(9, 5, 29, 33);
            g.setColor(new Color(0xF2BB8F));
            g.fillRect(15, 12, 20, 24);
            g.setColor(new Color(0xFFCEA5));
            g.fillRect(17, 14, 16, 16);
            g.setColor(new Color(0x593B36));
            g.fillRect(12, 7, 24, 8);
            g.fillRect(12, 14, 5, 13);
            g.fillRect(30, 11, 7, 7);
            g.setColor(new Color(0x24394B));
            g.fillRect(8, 36, 33, 11);
            g.setColor(new Color(0xE5AB44));
            g.fillRect(14, 36, 6, 11);
            g.fillRect(30, 36, 6, 11);
            g.setColor(new Color(0x203444));
            g.fillRect(19, index == 2 ? 21 : 20, 3, 2);
            g.fillRect(28, 20, 3, 2);
            g.setColor(new Color(0xA65E51));
            if (index == 1) {
                g.drawLine(22, 29, 29, 29);
                g.drawLine(23, 30, 28, 30);
            } else if (index == 2) {
                g.drawLine(23, 29, 27, 28);
            } else g.fillRect(23, 29, 5, 1);
            g.setColor(new Color(0x718B9B));
            g.fillRect(8, 18, 6, 13);
            g.fillRect(8, 13, 3, 17);
            g.fillRect(10, 30, 9, 2);
        } else {
            g.setColor(new Color(0x465E73));
            g.fillRect(9, 12, 31, 26);
            g.fillRect(15, 5, 19, 8);
            g.fillRect(20, 2, 4, 5);
            g.fillRect(7, 39, 35, 8);
            g.setColor(new Color(0xB1C5CE));
            g.fillRect(12, 13, 25, 22);
            g.fillRect(13, 40, 23, 7);
            g.setColor(new Color(0x203444));
            g.fillRect(15, 17, 19, 8);
            g.setColor(index == 4 ? new Color(0xF4B05C) : ACCENT);
            g.fillRect(17, 19, 6, 4);
            g.fillRect(27, 19, 5, 4);
            g.setColor(new Color(0x60798B));
            g.fillRect(19, 29, 12, 2);
            g.fillRect(23, 35, 5, 5);
        }
        g.setColor(ACCENT);
        g.drawRect(0, 0, 47, 47);
    }

    private static void courier(Graphics2D g) {
        fill(g, 0xF2BB8F, 18, 33, 12, 4);
        fill(g, 0xFFCEA5, 14, 13, 20, 21);
        fill(g, 0xF2BB8F, 31, 15, 3, 18);
        fill(g, 0x6B4A35, 12, 13, 3, 9);
        fill(g, 0x6B4A35, 33, 13, 3, 9);
        fill(g, 0x2B4C8C, 11, 4, 26, 6);
        fill(g, 0xE5C33A, 11, 9, 26, 2);
        fill(g, 0x1B2F57, 11, 11, 26, 2);
        fill(g, 0xE5C33A, 22, 5, 4, 3);
        fill(g, 0x6B4A35, 17, 17, 5, 1);
        fill(g, 0x6B4A35, 26, 17, 5, 1);
        fill(g, 0x203444, 18, 19, 3, 2);
        fill(g, 0x203444, 27, 19, 3, 2);
        fill(g, 0xF2BB8F, 23, 21, 2, 4);
        fill(g, 0x6B4A35, 19, 26, 10, 2);
        fill(g, 0x6B4A35, 18, 27, 2, 2);
        fill(g, 0x6B4A35, 28, 27, 2, 2);
        fill(g, 0xA65E51, 22, 30, 4, 1);
        fill(g, 0x2B4C8C, 8, 36, 32, 11);
        fill(g, 0x1B2F57, 16, 36, 16, 2);
        g.setColor(new Color(0x8A5A32));
        for (int width = 0; width < 3; width++) g.drawLine(36 + width, 36, 14 + width, 47);
    }

    private static void local(Graphics2D g) {
        fill(g, 0x7A3B2A, 11, 8, 26, 30);
        fill(g, 0xFFCEA5, 15, 13, 18, 21);
        fill(g, 0xF2BB8F, 30, 15, 3, 17);
        fill(g, 0x8E4632, 13, 7, 22, 7);
        fill(g, 0x8E4632, 13, 13, 6, 3);
        fill(g, 0x8E4632, 29, 13, 4, 2);
        fill(g, 0x203444, 18, 20, 3, 2);
        fill(g, 0x203444, 26, 20, 3, 2);
        fill(g, 0xD9906E, 17, 24, 1, 1);
        fill(g, 0xD9906E, 19, 25, 1, 1);
        fill(g, 0xD9906E, 28, 24, 1, 1);
        fill(g, 0xD9906E, 30, 25, 1, 1);
        fill(g, 0xA65E51, 21, 28, 1, 1);
        fill(g, 0xA65E51, 27, 28, 1, 1);
        fill(g, 0xA65E51, 22, 29, 5, 1);
        fill(g, 0x6E5038, 8, 37, 32, 10);
        fill(g, 0xC2453A, 13, 34, 22, 5);
        fill(g, 0xA8392F, 28, 38, 5, 8);
        fill(g, 0xE8EEF0, 28, 41, 5, 1);
    }

    private static void note(Graphics2D g) {
        fill(g, 0x0E1F29, 12, 43, 28, 2);
        fill(g, 0x0E1F29, 38, 7, 2, 38);
        fill(g, 0xEDE3C8, 10, 5, 28, 38);
        fill(g, 0xC9BC98, 10, 41, 28, 2);
        fill(g, 0x4B5868, 14, 9, 12, 2);
        int[] lines = {20, 18, 20, 14, 19, 10};
        for (int line = 0; line < lines.length; line++) fill(g, 0x7D8A99, 14, 14 + line * 4, lines[line], 1);
        fill(g, 0x3F6BA8, 24, 38, 10, 1);
        fill(g, 0x3F6BA8, 25, 37, 2, 1);
        fill(g, 0xC2453A, 22, 3, 4, 4);
        fill(g, 0xF08070, 23, 4, 1, 1);
    }

    private static void fill(Graphics2D g, int rgb, int x, int y, int width, int height) {
        g.setColor(new Color(rgb));
        g.fillRect(x, y, width, height);
    }

    private static String bitmap(String texture, int height, List<String> chars) {
        return "{\"type\":\"bitmap\",\"file\":\"blockcraft:gui/dialogue/" + texture + "\",\"ascent\":7"
                + ",\"height\":" + height + ",\"chars\":[\"" + String.join("\",\"", chars) + "\"]}";
    }

    private static String font(List<String> providers) {
        return "{\n  \"providers\": [\n    " + String.join(",\n    ", providers) + "\n  ]\n}\n";
    }

    private static String escape(int codepoint) {
        return String.format("\\u%04x", codepoint);
    }
}
