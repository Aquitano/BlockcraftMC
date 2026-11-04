import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class DialogueArt {
    private static final Color ACCENT = new Color(0x61DAD4);

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length == 0 ? "src" : args[0]);
        Path textures = root.resolve("assets/blockcraft/textures/gui/dialogue");
        Path fonts = root.resolve("assets/blockcraft/font");
        Path metrics = Path.of(args.length > 1 ? args[1] : "../paper/api/src/main/resources/dialogue-glyphs.properties");
        Files.createDirectories(textures);
        Files.createDirectories(fonts);
        var panel = new BufferedImage(300, 116, BufferedImage.TYPE_INT_ARGB);
        var g = panel.createGraphics();
        g.setColor(new Color(0xF015202D, true));
        g.fillRect(0, 0, 300, 116);
        g.setColor(new Color(0x567080));
        g.drawRect(0, 0, 299, 115);
        g.setColor(ACCENT);
        g.fillRect(0, 0, 36, 2);
        g.fillRect(264, 114, 36, 2);
        g.setColor(new Color(0x263B49));
        g.fillRect(61, 21, 231, 35);
        g.drawLine(8, 99, 291, 99);
        g.dispose();
        ImageIO.write(panel, "PNG", textures.resolve("panel.png").toFile());
        var providers = new ArrayList<String>();
        providers.add(bitmap("panel.png", 116, 116, List.of("\\uE600")));
        String[] portraits = {"pilot", "pilot_happy", "pilot_worried", "droid", "droid_alert"};
        for (int index = 0; index < portraits.length; index++) {
            var portrait = new BufferedImage(48, 128, BufferedImage.TYPE_INT_ARGB);
            g = portrait.createGraphics();
            portrait(g, index);
            g.dispose();
            ImageIO.write(portrait, "PNG", textures.resolve(portraits[index] + ".png").toFile());
            providers.add(bitmap(portraits[index] + ".png", 94, 128, List.of(escape(0xE610 + index))));
        }
        var spaces = new ArrayList<String>();
        for (int bit = 0; bit < 10; bit++) {
            spaces.add("\"" + escape(0xE640 + bit) + "\":" + -(1 << bit));
            spaces.add("\"" + escape(0xE650 + bit) + "\":" + (1 << bit));
        }
        providers.add("{\"type\":\"space\",\"advances\":{" + String.join(",", spaces) + "}}");
        Files.writeString(fonts.resolve("dialogue.json"), font(providers));
        var characters = new ArrayList<Integer>();
        for (int cp = 33; cp <= 126; cp++) characters.add(cp);
        for (int cp = 161; cp <= 255; cp++) characters.add(cp);
        characters.add(0x2026);
        int rows = (characters.size() + 15) / 16;
        var letters = new BufferedImage(96, rows * 128, BufferedImage.TYPE_INT_ARGB);
        g = letters.createGraphics();
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
                int x = column * 6;
                int y = row * 128;
                g.setClip(x, y, 6, 12);
                g.drawString(Character.toString(cp), x, y + 8);
                int ink = 0;
                for (int dx = 0; dx < 6; dx++) {
                    for (int dy = 0; dy < 12; dy++) {
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
        var baselines = new LinkedHashMap<String, Integer>();
        baselines.put("speaker", 108);
        baselines.put("body_0", 92);
        baselines.put("body_1", 81);
        baselines.put("body_2", 70);
        baselines.put("choice_0", 56);
        baselines.put("choice_1", 45);
        baselines.put("choice_2", 34);
        baselines.put("hint", 11);
        baselines.put("page", 38);
        for (var entry : baselines.entrySet()) {
            Files.writeString(fonts.resolve("dialogue_" + entry.getKey() + ".json"), font(List.of(
                    "{\"type\":\"space\",\"advances\":{\" \":4}}", bitmap("letters.png", entry.getValue(), 128, glyphRows))));
        }
    }

    private static void portrait(Graphics2D g, int index) {
        g.setColor(new Color(0x162F3C));
        g.fillRect(0, 0, 48, 48);
        g.setColor(new Color(0x345567));
        for (int y = 4; y < 48; y += 6) g.drawLine(1, y, 46, y);
        if (index < 3) {
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

    private static String bitmap(String texture, int ascent, int height, List<String> chars) {
        return "{\"type\":\"bitmap\",\"file\":\"blockcraft:gui/dialogue/" + texture + "\",\"ascent\":" + ascent
                + ",\"height\":" + height + ",\"chars\":[\"" + String.join("\",\"", chars) + "\"]}";
    }

    private static String font(List<String> providers) {
        return "{\n  \"providers\": [\n    " + String.join(",\n    ", providers) + "\n  ]\n}\n";
    }

    private static String escape(int codepoint) {
        return String.format("\\u%04x", codepoint);
    }
}
