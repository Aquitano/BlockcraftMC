import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CombatArt {

    public static void main(String[] args) throws Exception {
        var root = Path.of(args.length == 0 ? "src" : args[0]).resolve("assets/blockcraft/textures/font");
        Files.createDirectories(root);
        var icons = new BufferedImage(60, 12, BufferedImage.TYPE_INT_ARGB);
        var g = icons.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(2, 3, 3, 3);
        g.fillRect(6, 3, 3, 3);
        g.fillPolygon(new int[] {2, 9, 5}, new int[] {5, 5, 10}, 3);
        g.fillPolygon(new int[] {18, 15, 15, 21, 21, 19, 19}, new int[] {1, 6, 9, 9, 6, 8, 4}, 7);
        g.drawPolygon(new int[] {26, 33, 33, 30, 26}, new int[] {2, 2, 7, 10, 7}, 5);
        g.drawRect(37, 1, 9, 9);
        g.drawLine(40, 3, 40, 8);
        g.drawLine(40, 3, 44, 3);
        g.drawLine(40, 5, 43, 5);
        g.drawRect(49, 1, 9, 9);
        g.drawOval(51, 3, 4, 5);
        g.drawLine(54, 7, 56, 9);
        g.dispose();
        ImageIO.write(icons, "PNG", root.resolve("combat_icons.png").toFile());
        var bars = new BufferedImage(462, 12, BufferedImage.TYPE_INT_ARGB);
        g = bars.createGraphics();
        for (int level = 0; level <= 10; level++) {
            int x = level * 42;
            g.setColor(new Color(0xD0141C29, true));
            g.fillRoundRect(x, 2, 41, 8, 3, 3);
            g.setColor(new Color(0xFF627187, true));
            g.drawRect(x, 2, 40, 7);
            for (int cell = 0; cell < 10; cell++) {
                g.setColor(cell < level ? Color.WHITE : new Color(0xFF2B3545, true));
                g.fillRect(x + 2 + cell * 4, 4, 3, 4);
            }
        }
        g.dispose();
        ImageIO.write(bars, "PNG", root.resolve("combat_bars.png").toFile());
    }
}
