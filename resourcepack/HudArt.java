import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

public final class HudArt {

    private static final int PIP = 13;

    /** Corners around the crosshair for a hit, a headshot and a kill, each stronger and in the colour of its news. */
    private static BufferedImage crosshairMarks() {
        var image = new BufferedImage(96, 32, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        var colours = new Color[] {Color.WHITE, new Color(0xFFFFB02E), new Color(0xFFFF3B3B)};
        int[] inner = {4, 4, 3};
        int[] outer = {8, 9, 10};
        for (int mark = 0; mark < colours.length; mark++) {
            int middle = mark * 32 + 16;
            for (int xSign = -1; xSign <= 1; xSign += 2) {
                for (int ySign = -1; ySign <= 1; ySign += 2) {
                    g.setColor(new Color(0x90000000, true));
                    g.setStroke(new BasicStroke(mark == 2 ? 3 : 2));
                    g.drawLine(middle + xSign * inner[mark], 16 + ySign * inner[mark], middle + xSign * outer[mark], 16 + ySign * outer[mark]);
                    g.setColor(colours[mark]);
                    g.setStroke(new BasicStroke(1));
                    g.drawLine(middle + xSign * inner[mark], 16 + ySign * inner[mark], middle + xSign * outer[mark], 16 + ySign * outer[mark]);
                }
            }
        }
        g.dispose();
        return image;
    }

    /** Each post's letter on a filled tile and on a hollow one, in white so a text colour tints them. */
    private static BufferedImage objectivePips() {
        var image = new BufferedImage(PIP * 10, PIP, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
        for (int post = 0; post < 5; post++) {
            var letter = String.valueOf((char) ('A' + post));
            int filled = post * 2 * PIP;
            int hollow = filled + PIP;
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(Color.WHITE);
            g.fillRoundRect(filled, 0, PIP - 1, PIP - 1, 4, 4);
            g.drawRoundRect(hollow, 0, PIP - 2, PIP - 2, 4, 4);
            g.setComposite(AlphaComposite.Clear);
            g.drawString(letter, filled + 3, 10);
            g.setComposite(AlphaComposite.SrcOver);
            g.drawString(letter, hollow + 3, 10);
        }
        g.dispose();
        return image;
    }

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
        var arcs = new BufferedImage(128, 32, BufferedImage.TYPE_INT_ARGB);
        g = arcs.createGraphics();
        int[] centres = {90, 270, 180, 0};
        for (int cell = 0; cell < centres.length; cell++) {
            var arc = new Arc2D.Double(cell * 32 + 2, 2, 28, 28, centres[cell] - 32, 64, Arc2D.OPEN);
            g.setColor(new Color(0xB0200000, true));
            g.setStroke(new BasicStroke(4));
            g.draw(arc);
            g.setColor(new Color(0xFFFF4A4A, true));
            g.setStroke(new BasicStroke(2));
            g.draw(arc);
        }
        g.dispose();
        ImageIO.write(arcs, "PNG", root.resolve("damage_arcs.png").toFile());
        ImageIO.write(objectivePips(), "PNG", root.resolve("objective_pips.png").toFile());
        ImageIO.write(crosshairMarks(), "PNG", root.resolve("crosshair_marks.png").toFile());
        var hidden = new BufferedImage(182, 5, BufferedImage.TYPE_INT_ARGB);
        var sprites = root.getParent().getParent().getParent().resolve("minecraft/textures/gui/sprites/boss_bar");
        Files.createDirectories(sprites);
        for (var sprite : new String[] {"pink_background", "pink_progress", "notched_20_background", "notched_20_progress"}) {
            ImageIO.write(hidden, "PNG", sprites.resolve(sprite + ".png").toFile());
        }
    }
}
