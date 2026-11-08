import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class HudArt {

    private static final int PIP = 13;
    private static final int OUTLINE = 0xFF0F141D;

    /** Corners around the crosshair for a hit, a headshot and a kill, each stronger and in the colour of its news; a headshot adds a diamond above. */
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
            if (mark == 1) {
                g.setColor(new Color(0x90000000, true));
                g.fill(diamond(middle + 0.5, 3, 3));
                g.setColor(colours[mark]);
                g.fill(diamond(middle + 0.5, 3, 2));
            }
        }
        g.dispose();
        return image;
    }

    private static Path2D diamond(double x, double y, double radius) {
        var path = new Path2D.Double();
        path.moveTo(x, y - radius);
        path.lineTo(x + radius, y);
        path.lineTo(x, y + radius);
        path.lineTo(x - radius, y);
        path.closePath();
        return path;
    }

    /**
     * The 32-pixel {@code art}, whose cells are centred on their middle pixel or, if not {@code aroundAPixel}, between the
     * middle four, redrawn at twice the resolution for a font height of 32 and centred between the middle texels.
     * The subtitle draws a font pixel two GUI pixels wide, so only half-pixel texels can centre the art on the title's
     * origin, the point the vanilla crosshair's middle pixel touches whatever the GUI size. The client measures a glyph's
     * advance up to its last inked column; a nearly clear texel in the last one makes every cell advance 33, which
     * {@code UiFont.centredBehind} expects, and the text shader discards it.
     */
    private static BufferedImage onTheCrosshair(BufferedImage art, boolean aroundAPixel) {
        var image = new BufferedImage(art.getWidth() * 2, art.getHeight() * 2, BufferedImage.TYPE_INT_ARGB);
        int offset = aroundAPixel ? -1 : 0;
        var g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(art, offset, offset, image.getWidth(), image.getHeight(), null);
        g.dispose();
        for (int x = 63; x < image.getWidth(); x += 64) {
            if ((image.getRGB(x, 63) >>> 24) == 0) {
                image.setRGB(x, 63, 0x01000000);
            }
        }
        return image;
    }

    /**
     * Ticks above, below and beside the crosshair, one set per spread size, each further out. They stand on the axes so
     * the diagonal hit marks drawn over them stay readable, and the tightest clears the vanilla crosshair's arms.
     */
    private static BufferedImage spreadTicks() {
        int[] inner = {5, 7, 9, 11, 13};
        var image = new BufferedImage(32 * inner.length, 32, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        for (int size = 0; size < inner.length; size++) {
            int middle = size * 32 + 16;
            int near = inner[size];
            int[][] ticks = {
                    {middle, 16 - near - 1, 1, 2}, {middle, 16 + near, 1, 2},
                    {middle - near - 1, 16, 2, 1}, {middle + near, 16, 2, 1}};
            for (var tick : ticks) {
                boolean upright = tick[2] == 1;
                g.setColor(new Color(0x90000000, true));
                g.fillRect(tick[0] - (upright ? 1 : 0), tick[1] - (upright ? 0 : 1), tick[2] + (upright ? 2 : 0), tick[3] + (upright ? 0 : 2));
                g.setComposite(AlphaComposite.Src);
                g.setColor(new Color(0xE0FFFFFF, true));
                g.fillRect(tick[0], tick[1], tick[2], tick[3]);
                g.setComposite(AlphaComposite.SrcOver);
            }
        }
        g.dispose();
        return image;
    }

    /**
     * For each cell of the heat meter's lower half, where a vent window lies, a bracket above and below it, in white so a
     * text colour tints it. Adjacent
     * cells' brackets join into one window. A nearly clear texel in the last column gives each glyph the meter's advance,
     * so one laid over the meter takes no room.
     */
    private static BufferedImage ventWindow() {
        var image = new BufferedImage(210, 12, BufferedImage.TYPE_INT_ARGB);
        for (int cell = 0; cell < 5; cell++) {
            int x = cell * 42;
            for (int column = 1; column <= 5; column++) {
                for (int row : new int[] {0, 1, 10, 11}) {
                    image.setRGB(x + cell * 4 + column, row, 0xFFFFFFFF);
                }
            }
            image.setRGB(x + 40, 11, 0x01000000);
        }
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
        // The flame takes the shield's advance, so the meter after either starts at the same place.
        padAdvance(icons, 21, 0, 12);
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
        ImageIO.write(ventWindow(), "PNG", root.resolve("combat_vent.png").toFile());
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
        ImageIO.write(onTheCrosshair(arcs, false), "PNG", root.resolve("damage_arcs.png").toFile());
        ImageIO.write(objectivePips(), "PNG", root.resolve("objective_pips.png").toFile());
        spawnMarkers(root.getParent().getParent());
        ImageIO.write(onTheCrosshair(crosshairMarks(), true), "PNG", root.resolve("crosshair_marks.png").toFile());
        ImageIO.write(onTheCrosshair(spreadTicks(), true), "PNG", root.resolve("spread_ticks.png").toFile());
        var letters = labelLetters(root.getParent().getParent().resolve("font"));
        minimap(root, letters);
        abilities(root, args.length > 1 ? Path.of(args[1]) : null, letters);
        var hidden = new BufferedImage(182, 5, BufferedImage.TYPE_INT_ARGB);
        var sprites = root.getParent().getParent().getParent().resolve("minecraft/textures/gui/sprites/boss_bar");
        Files.createDirectories(sprites);
        for (var sprite : new String[] {"pink_background", "pink_progress", "notched_20_background", "notched_20_progress"}) {
            ImageIO.write(hidden, "PNG", sprites.resolve(sprite + ".png").toFile());
        }
    }

    /**
     * The minimap's font: the disc in strips, the HUD's post badges, map marks, class marks and the menu tiles'
     * letters, every glyph with its top at the line's top. The text shader moves the line into the corner and finds the
     * boss bar line it came from by where a glyph lies inside it, so no glyph may reach more than 16 pixels down.
     */
    private static void minimap(Path root, String letters) throws Exception {
        ImageIO.write(minimapDisc(), "PNG", root.resolve("minimap_disc.png").toFile());
        ImageIO.write(minimapMarks(), "PNG", root.resolve("minimap_marks.png").toFile());
        ImageIO.write(minimapTarget(), "PNG", root.resolve("minimap_target.png").toFile());
        ImageIO.write(feedMarks(), "PNG", root.resolve("minimap_feed.png").toFile());
        ImageIO.write(mapLetters(ImageIO.read(root.resolve("labels.png").toFile()), letters), "PNG", root.resolve("minimap_letters.png").toFile());
        ImageIO.write(classMarks(), "PNG", root.resolve("minimap_classes.png").toFile());
        var underscore = new BufferedImage(3, 7, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 3; x++) {
            underscore.setRGB(x, 6, 0xFFFFFFFF);
        }
        ImageIO.write(underscore, "PNG", root.resolve("minimap_underscore.png").toFile());
        var disc = new String[MAP_STRIPS];
        for (int strip = 0; strip < MAP_STRIPS; strip++) {
            disc[strip] = String.valueOf((char) (0xE000 + strip));
        }
        Files.writeString(root.getParent().getParent().resolve("font/minimap.json"), String.join(",\n    ",
                "{\n  \"providers\": [\n    {\"type\":\"space\",\"advances\":{\" \":4}}",
                topAligned("minimap_disc.png", MAP_STRIP, disc),
                topAligned("objective_pips.png", 11, run(0xE010, 10)),
                topAligned("minimap_marks.png", MARK, run(0xE020, MARKS)),
                topAligned("minimap_target.png", TARGET, run(0xE034, 1)),
                topAligned("minimap_feed.png", FEED_MARK, run(0xE050, 3)),
                topAligned("minimap_classes.png", 7, run(0xE040, CLASS_MARKS.length)),
                topAligned("minimap_letters.png", 7, letters),
                topAligned("minimap_underscore.png", 7, "_")) + "\n  ]\n}\n");
    }

    private static final int MAP_STRIP = 16;
    private static final int MAP_STRIPS = 4;
    private static final int MAP_CENTRE = 31;
    private static final int MARK = 9;
    private static final int HEADINGS = 16;
    private static final int MARKS = HEADINGS + 4;
    private static final int TARGET = 13;
    private static final int FEED_MARK = 7;
    private static final int FEED_CELL = 20;
    /** A skull at twice the resolution: white bone, dark sockets, nose and teeth. */
    private static final String[] SKULL = {
            "....######....",
            "..##########..",
            ".############.",
            "##############",
            "##############",
            "##kkkk##kkkk##",
            "##kkkk##kkkk##",
            "##kkk####kkk##",
            ".#####kk#####.",
            "..####kk####..",
            "...########...",
            "...#k#kk#k#...",
            "...########...",
            ".............."};
    private static final String[] DIAMOND = {"...#...", "..###..", ".#####.", "#######", ".#####.", "..###..", "...#..."};
    private static final int TARGET_ARM = 7;
    private static final String NORTH = "10011101101110011001";
    /** Soldier, heavy, scout, medic and hero, five rows of five. */
    private static final String[] CLASS_MARKS = {
            "0010000100011100111011111", "1111111111111110111000100", "0010001010100010101000100",
            "0010000100111110010000100", "0010001110111110111001010"};

    /** The letters of PackArt's label sheet, in the order its menu label fonts list them. */
    private static String labelLetters(Path fonts) throws Exception {
        var chars = Pattern.compile("labels\\.png\"[^}]*\"chars\":\\[\"([^\"]*)\"").matcher(Files.readString(fonts.resolve("label_wide_0.json")));
        if (!chars.find()) {
            throw new IllegalStateException("label_wide_0.json lists no label letters; run PackArt first");
        }
        return Pattern.compile("\\\\u([0-9a-fA-F]{4})").matcher(chars.group(1))
                .replaceAll(escape -> String.valueOf((char) Integer.parseInt(escape.group(1), 16)));
    }

    private static String run(int first, int count) {
        var glyphs = new StringBuilder();
        for (int index = 0; index < count; index++) {
            glyphs.append((char) (first + index));
        }
        return glyphs.toString();
    }

    /** A bitmap provider whose glyphs hang from the line's top, each row of {@code rows} one row of cells in the file. */
    private static String topAligned(String file, int height, String... rows) {
        var encoded = new StringBuilder();
        for (var row : rows) {
            encoded.append(encoded.isEmpty() ? "\"" : ",\"");
            row.chars().forEach(glyph -> encoded.append(String.format(Locale.ROOT, "\\u%04x", glyph)));
            encoded.append('"');
        }
        return "{\"type\":\"bitmap\",\"file\":\"blockcraft:font/" + file + "\",\"ascent\":7,\"height\":" + height
                + ",\"chars\":[" + encoded + "]}";
    }

    /**
     * A disc 63 pixels across with a ring at half its radius, short cross hairs and N at the top, cut into strips. A nearly
     * clear texel in each strip's last column gives every strip the disc's full advance.
     */
    private static BufferedImage minimapDisc() {
        var image = new BufferedImage(MAP_STRIP * MAP_STRIPS, MAP_STRIP * MAP_STRIPS, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y <= 2 * MAP_CENTRE; y++) {
            for (int x = 0; x <= 2 * MAP_CENTRE; x++) {
                double from = Math.hypot(x - MAP_CENTRE, y - MAP_CENTRE);
                if (from > MAP_CENTRE + 0.5) {
                    continue;
                }
                boolean guide = Math.abs(from - MAP_CENTRE / 2.0) < 0.5
                        || from < MAP_CENTRE - 7 && (x == MAP_CENTRE || y == MAP_CENTRE);
                image.setRGB(x, y, from > MAP_CENTRE - 0.5 ? 0xFF627187 : guide ? 0xFF2B3545 : 0xB8141C29);
            }
        }
        for (int index = 0; index < NORTH.length(); index++) {
            if (NORTH.charAt(index) == '1') {
                image.setRGB(MAP_CENTRE - 2 + index % 4, 3 + index / 4, 0xFFB8C4D6);
            }
        }
        for (int strip = 0; strip < MAP_STRIPS; strip++) {
            padAdvance(image, 2 * MAP_CENTRE, strip * MAP_STRIP, MAP_STRIP);
        }
        return image;
    }

    /**
     * The player's arrow at sixteen headings clockwise from north, then a squadmate, another ally, an enemy and a
     * headquarters, each centred in a nine-pixel cell, in white so a tint colours them. They are drawn at twice the
     * resolution, because a nine-pixel arrow cannot tell sixteen headings apart.
     */
    private static BufferedImage minimapMarks() {
        int cell = 2 * MARK;
        double middle = MARK / 2.0;
        var image = new BufferedImage(cell * MARKS, cell, BufferedImage.TYPE_INT_ARGB);
        for (int heading = 0; heading < HEADINGS; heading++) {
            double angle = Math.toRadians(heading * 360.0 / HEADINGS);
            var arrow = new Path2D.Double();
            double[][] corners = {{0, 4.3}, {2.55, 3.9}, {Math.PI, 2.2}, {-2.55, 3.9}};
            for (var corner : corners) {
                double x = middle + Math.sin(angle + corner[0]) * corner[1];
                double y = middle - Math.cos(angle + corner[0]) * corner[1];
                if (arrow.getCurrentPoint() == null) {
                    arrow.moveTo(x, y);
                } else {
                    arrow.lineTo(x, y);
                }
            }
            arrow.closePath();
            paint(image, heading * cell, arrow);
            outline(image, heading * cell, cell);
        }
        paint(image, HEADINGS * cell, new Ellipse2D.Double(middle - 2.5, middle - 2.5, 5, 5));
        paint(image, (HEADINGS + 1) * cell, new Rectangle2D.Double(middle - 1.5, middle - 1.5, 3, 3));
        var diamond = new Path2D.Double();
        diamond.moveTo(middle, middle - 2.5);
        diamond.lineTo(middle + 2.5, middle);
        diamond.lineTo(middle, middle + 2.5);
        diamond.lineTo(middle - 2.5, middle);
        diamond.closePath();
        paint(image, (HEADINGS + 2) * cell, diamond);
        var headquarters = new Area(new Rectangle2D.Double(middle - 3.5, middle - 3.5, 7, 7));
        headquarters.subtract(new Area(new Rectangle2D.Double(middle - 2.5, middle - 2.5, 5, 5)));
        headquarters.add(new Area(new Rectangle2D.Double(middle - 1.5, middle - 1.5, 3, 3)));
        paint(image, (HEADINGS + 3) * cell, headquarters);
        for (int mark = 0; mark < MARKS; mark++) {
            padAdvance(image, mark * cell + cell - 1, 0, cell);
        }
        return image;
    }

    /**
     * Corners round a post's badge, which mark the post the player should make for. They are drawn at twice the
     * resolution, so they stay thin beside the badge.
     */
    private static BufferedImage minimapTarget() {
        int cell = 2 * TARGET;
        var image = new BufferedImage(cell, cell, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < cell; y++) {
            for (int x = 0; x < cell; x++) {
                int fromEdgeX = Math.min(x, cell - 1 - x);
                int fromEdgeY = Math.min(y, cell - 1 - y);
                boolean arm = fromEdgeX < 2 && fromEdgeY < TARGET_ARM || fromEdgeY < 2 && fromEdgeX < TARGET_ARM;
                if (arm) {
                    image.setRGB(x, y, 0xFFFFFFFF);
                }
            }
        }
        return image;
    }

    /**
     * The menu tiles' capitals for the minimap's names and call signs, with a 1 that has its stem in the middle: the
     * menus' 1 reads as a J, which turns B1-11 into BJ-JJ.
     */
    private static BufferedImage mapLetters(BufferedImage labels, String letters) {
        var image = new BufferedImage(labels.getWidth(), labels.getHeight(), BufferedImage.TYPE_INT_ARGB);
        image.createGraphics().drawImage(labels, 0, 0, null);
        int left = 3 * letters.indexOf('1');
        for (int y = 0; y < labels.getHeight(); y++) {
            for (int x = 0; x < 3; x++) {
                int row = y - (labels.getHeight() - ONE.length);
                image.setRGB(left + x, y, row >= 0 && ONE[row].charAt(x) == '#' ? 0xFFFFFFFF : 0);
            }
        }
        return image;
    }

    private static final String[] ONE = {".#.", "##.", ".#.", ".#.", "###"};

    /**
     * The kill feed's marks in cells as tall as a label letter, drawn at twice the resolution: a skull for a kill or a
     * death, its outline for an assist, and the skull with the crosshair's headshot diamond at its corner.
     */
    private static BufferedImage feedMarks() {
        int height = 2 * FEED_MARK;
        var image = new BufferedImage(FEED_CELL * 3, height, BufferedImage.TYPE_INT_ARGB);
        for (int cell = 0; cell < 3; cell++) {
            boolean hollow = cell == 1;
            for (int y = 0; y < SKULL.length; y++) {
                for (int x = 0; x < SKULL[y].length(); x++) {
                    char ink = SKULL[y].charAt(x);
                    if (ink == 'k') {
                        image.setRGB(cell * FEED_CELL + x, y, OUTLINE);
                    } else if (ink == '#' && (!hollow || nearSkullEdge(x, y))) {
                        image.setRGB(cell * FEED_CELL + x, y, 0xFFFFFFFF);
                    }
                }
            }
        }
        int left = 3 * FEED_CELL - DIAMOND.length - 1;
        for (int y = 0; y < DIAMOND.length; y++) {
            for (int x = -1; x <= DIAMOND.length; x++) {
                if (diamondInk(x, y)) {
                    image.setRGB(left + x, y, 0xFFFFB02E);
                } else if (diamondInk(x - 1, y) || diamondInk(x + 1, y) || diamondInk(x, y - 1) || diamondInk(x, y + 1)) {
                    image.setRGB(left + x, y, OUTLINE);
                }
            }
        }
        return image;
    }

    private static boolean diamondInk(int x, int y) {
        return x >= 0 && y >= 0 && y < DIAMOND.length && x < DIAMOND.length && DIAMOND[y].charAt(x) == '#';
    }

    /** Whether a bone texel lies within two texels of the skull's outside, which is all the assist's outline keeps. */
    private static boolean nearSkullEdge(int x, int y) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if (nx < 0 || ny < 0 || ny >= SKULL.length || nx >= SKULL[ny].length() || SKULL[ny].charAt(nx) == '.') {
                    return true;
                }
            }
        }
        return false;
    }

    /** A dark rim round the cell's ink, so the arrow stays readable over a white post badge where the player stands. */
    private static void outline(BufferedImage image, int left, int cell) {
        var rim = new ArrayList<int[]>();
        for (int y = 0; y < cell; y++) {
            for (int x = 0; x < cell; x++) {
                if ((image.getRGB(left + x, y) >>> 24) == 0 && touchesInk(image, left, cell, x, y)) {
                    rim.add(new int[] {x, y});
                }
            }
        }
        rim.forEach(texel -> image.setRGB(left + texel[0], texel[1], 0xFF141C29));
    }

    private static boolean touchesInk(BufferedImage image, int left, int cell, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < cell && ny < cell && image.getRGB(left + nx, ny) == 0xFFFFFFFF) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whites every texel of the double-resolution cell at {@code left} that {@code shape}, in font pixels, mostly covers. */
    private static void paint(BufferedImage image, int left, Shape shape) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getHeight(); x++) {
                int inside = 0;
                for (int sample = 0; sample < 16; sample++) {
                    if (shape.contains((x + (sample % 4 + 0.5) / 4) / 2, (y + (sample / 4 + 0.5) / 4) / 2)) {
                        inside++;
                    }
                }
                if (inside >= 8) {
                    image.setRGB(left + x, y, 0xFFFFFFFF);
                }
            }
        }
    }

    /** A class mark five pixels square in a cell as tall as a label letter, standing on the letters' baseline. */
    private static BufferedImage classMarks() {
        var image = new BufferedImage(6 * CLASS_MARKS.length, 7, BufferedImage.TYPE_INT_ARGB);
        for (int mark = 0; mark < CLASS_MARKS.length; mark++) {
            for (int index = 0; index < 25; index++) {
                if (CLASS_MARKS[mark].charAt(index) == '1') {
                    image.setRGB(mark * 6 + index % 5, 2 + index / 5, 0xFFFFFFFF);
                }
            }
            padAdvance(image, mark * 6 + 5, 0, 7);
        }
        return image;
    }

    /** The client measures an advance up to the last inked column, so a texel the shader discards fixes it at {@code column}. */
    private static void padAdvance(BufferedImage image, int column, int top, int height) {
        for (int y = top; y < top + height; y++) {
            if ((image.getRGB(column, y) >>> 24) != 0) {
                return;
            }
        }
        image.setRGB(column, top + height - 1, 0x01000000);
    }

    /**
     * The deploy menu's spawn markers: each post's letter on a filled tile and a hollow one, the headquarters' banner of
     * each faction and the landing's rocket. A select on the item's first two custom_model_data strings lays the
     * choice's frame and a badge over each, on the slot's border, which is why they may draw beyond their slot.
     */
    private static void spawnMarkers(Path assets) throws Exception {
        var models = assets.resolve("models/item/sternenfront");
        var items = assets.resolve("items/sternenfront");
        var textures = assets.resolve("textures/item/sternenfront");
        Files.createDirectories(models);
        Files.createDirectories(items);
        Files.createDirectories(textures);
        ImageIO.write(objectivePips(), "PNG", textures.resolve("objective_pips.png").toFile());
        for (var overlay : spawnOverlays().entrySet()) {
            ImageIO.write(overlay.getValue(), "PNG", textures.resolve(overlay.getKey() + ".png").toFile());
            Files.writeString(models.resolve(overlay.getKey() + ".json"), """
                    {
                      "gui_light": "front",
                      "textures": {"overlay": "blockcraft:item/sternenfront/%s"},
                      "elements": [{"from": [-1, -1, 16], "to": [17, 17, 16], "shade": false,
                        "faces": {"south": {"uv": [0, 0, 9, 9], "texture": "#overlay"}}}]
                    }
                    """.formatted(overlay.getKey()));
        }
        for (int post = 0; post < 5; post++) {
            for (int variant = 0; variant < 2; variant++) {
                String name = "post_" + (char) ('a' + post) + (variant == 0 ? "_filled" : "_hollow");
                double left = (post * 2 + variant) * 1.6;
                Files.writeString(models.resolve(name + ".json"), String.format(Locale.ROOT, """
                        {
                          "gui_light": "front",
                          "textures": {"marker": "blockcraft:item/sternenfront/objective_pips"},
                          "elements": [{"from": [1, 1, 8], "to": [15, 15, 8], "shade": false,
                            "faces": {"south": {"uv": [%.1f, 0, %.1f, 16], "texture": "#marker", "tintindex": 0}}}]
                        }
                        """, left, left + 1.6));
                spawnMarker(items.resolve(name + ".json"), """
                        {"type": "minecraft:model", "model": "blockcraft:item/sternenfront/%s",
                              "tints": [{"type": "minecraft:custom_model_data", "index": 0, "default": 16777215}]}""".formatted(name));
            }
        }
        for (var colour : HEADQUARTERS_BANNERS) {
            spawnMarker(items.resolve("spawn_headquarters_" + colour + ".json"), """
                    {"type": "minecraft:special", "base": "minecraft:item/template_banner",
                          "model": {"type": "minecraft:banner", "color": "%s"},
                          "transformation": {"left_rotation": [0, 0, 0, 1], "right_rotation": [0, 0, 0, 1],
                            "scale": [0.6666667, -0.6666667, -0.6666667], "translation": [0.5, 0, 0.5]}}""".formatted(colour));
        }
        spawnMarker(items.resolve("spawn_dropship.json"), """
                {"type": "minecraft:model", "model": "minecraft:item/firework_rocket"}""");
    }

    /** {@code base} with the choice's frame from the first custom_model_data string and a badge from the second. */
    private static void spawnMarker(Path file, String base) throws Exception {
        Files.writeString(file, """
                {
                  "oversized_in_gui": true,
                  "model": {"type": "minecraft:composite", "models": [
                    %s,
                    %s,
                    %s]}
                }
                """.formatted(base, overlaySelect(0, "pending", "chosen"), overlaySelect(1, "recommended", "locked")));
    }

    private static String overlaySelect(int index, String... cases) {
        var when = new ArrayList<String>();
        for (var name : cases) {
            when.add("""
                    {"when": "%s", "model": {"type": "minecraft:model", "model": "blockcraft:item/sternenfront/spawn_%s"}}"""
                    .formatted(name, name));
        }
        return """
                {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": %d,
                      "cases": [%s],
                      "fallback": {"type": "minecraft:empty"}}""".formatted(index, String.join(", ", when));
    }

    /** The banner colours of both factions' headquarters, as Sternenfront's {@code Faction.blocks()} names them. */
    private static final List<String> HEADQUARTERS_BANNERS = List.of("light_blue", "red");
    private static final int SLOT = 18;

    /**
     * Over the 18-pixel slot, in the top-left of a 32-pixel texture: gold corners for a spawn chosen but not confirmed, a
     * green frame for a confirmed one, a star for the recommended spawn and a padlock for a locked one.
     */
    private static Map<String, BufferedImage> spawnOverlays() {
        return Map.of(
                "spawn_pending", slotFrame(0xFFFFB830, 6),
                "spawn_chosen", slotFrame(0xFF5BEA6B, SLOT),
                "spawn_recommended", slotBadge(10, 1, 0xFFFFD447, new String[] {
                        "...#...",
                        "...#...",
                        "#######",
                        ".#####.",
                        "..###..",
                        ".##.##.",
                        "##...##"}),
                "spawn_locked", slotBadge(10, 9, 0xFFFF5B4B, new String[] {
                        "..lll..",
                        ".l...l.",
                        ".l...l.",
                        "#######",
                        "###k###",
                        "###k###",
                        "#######"}));
    }

    /** A two-pixel border round the slot, whole or as corners {@code reach} pixels long. */
    private static BufferedImage slotFrame(int colour, int reach) {
        var image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SLOT; y++) {
            for (int x = 0; x < SLOT; x++) {
                boolean border = x < 2 || y < 2 || x >= SLOT - 2 || y >= SLOT - 2;
                boolean nearCorner = (x < reach || x >= SLOT - reach) && (y < reach || y >= SLOT - reach);
                if (border && nearCorner) {
                    image.setRGB(x, y, colour);
                }
            }
        }
        return image;
    }

    /** {@code art} in {@code colour} with a dark outline, its top-left at ({@code left}, {@code top}) inside the slot. */
    private static BufferedImage slotBadge(int left, int top, int colour, String[] art) {
        var image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = -1; y <= art.length; y++) {
            for (int x = -1; x <= art[0].length(); x++) {
                char ink = y >= 0 && y < art.length && x >= 0 && x < art[0].length() ? art[y].charAt(x) : '.';
                if (ink != '.') {
                    image.setRGB(left + x, top + y, ink == '#' ? colour : INK.get(ink));
                } else if (badgeInk(art, x - 1, y) || badgeInk(art, x + 1, y) || badgeInk(art, x, y - 1) || badgeInk(art, x, y + 1)) {
                    image.setRGB(left + x, top + y, OUTLINE);
                }
            }
        }
        return image;
    }

    private static boolean badgeInk(String[] art, int x, int y) {
        return y >= 0 && y < art.length && x >= 0 && x < art[0].length() && art[y].charAt(x) != '.';
    }

    /** The colours that a badge's art names by letter besides {@code #}. */
    private static final Map<Character, Integer> INK = Map.ofEntries(
            Map.entry('k', OUTLINE), Map.entry('w', 0xFFFFFFFF), Map.entry('l', 0xFFD9E0EA), Map.entry('m', 0xFF9AA6B8),
            Map.entry('d', 0xFF5A667A), Map.entry('e', 0xFF323B4C), Map.entry('r', 0xFFFF5B4B), Map.entry('R', 0xFFB0342C),
            Map.entry('o', 0xFFFFA53A), Map.entry('y', 0xFFFFE680), Map.entry('g', 0xFF74E37E), Map.entry('G', 0xFF2E9C47),
            Map.entry('c', 0xFFA6F3FF), Map.entry('C', 0xFF3FC3EE), Map.entry('B', 0xFF2273B8), Map.entry('p', 0xFFE0C4FF),
            Map.entry('P', 0xFF9B6BFF), Map.entry('V', 0xFF5C37C0), Map.entry('u', 0xFFFFD447), Map.entry('U', 0xFFC48A12),
            Map.entry('n', 0xFFA8743F), Map.entry('N', 0xFF6A4524));

    /**
     * The ability tiles' font in the screen's bottom-right corner, drawn at twice the resolution of its GUI size so a tile
     * shows finer detail at GUI scale 2 and up: a dark backing, a frame the shader tints by state, a fill that rises
     * while an ability recharges, every ability's icon in colour and in grey in the order of Sternenfront's
     * {@code AbilityIcon}, the keys' badges, the cooldown's digits, the digits that count stored uses and the label
     * letters of the names.
     * Every glyph hangs from the line's top, which the text shader moves into the corner.
     */
    private static void abilities(Path root, Path contactSheet, String letters) throws Exception {
        var icons = new BufferedImage(2 * ICON * ABILITY_ICONS.size(), 2 * ICON, BufferedImage.TYPE_INT_ARGB);
        var cooling = new BufferedImage(icons.getWidth(), icons.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int left = 0;
        for (var art : ABILITY_ICONS.values()) {
            var icon = outlined(drawn(2 * ICON, art));
            icons.createGraphics().drawImage(icon, left, 0, null);
            cooling.createGraphics().drawImage(greyed(icon), left, 0, null);
            padAdvance(icons, left + 2 * ICON - 1, 0, 2 * ICON);
            padAdvance(cooling, left + 2 * ICON - 1, 0, 2 * ICON);
            left += 2 * ICON;
        }
        ImageIO.write(tileBacking(), "PNG", root.resolve("ability_tile.png").toFile());
        ImageIO.write(tileFrame(), "PNG", root.resolve("ability_frame.png").toFile());
        ImageIO.write(tileFills(), "PNG", root.resolve("ability_fill.png").toFile());
        ImageIO.write(icons, "PNG", root.resolve("ability_icons.png").toFile());
        ImageIO.write(cooling, "PNG", root.resolve("ability_icons_cooling.png").toFile());
        ImageIO.write(keyBadges(), "PNG", root.resolve("ability_keys.png").toFile());
        ImageIO.write(cooldownDigits(), "PNG", root.resolve("ability_digits.png").toFile());
        var labels = ImageIO.read(root.resolve("labels.png").toFile());
        ImageIO.write(countDigits(mapLetters(labels, letters), letters), "PNG", root.resolve("ability_count.png").toFile());
        ImageIO.write(nameLetters(labels, letters), "PNG", root.resolve("ability_letters.png").toFile());
        Files.writeString(root.getParent().getParent().resolve("font/abilities.json"), String.join(",\n    ",
                "{\n  \"providers\": [\n    {\"type\":\"space\",\"advances\":{\" \":4}}",
                topAligned("ability_tile.png", TILE, run(0xE000, 1)),
                topAligned("ability_frame.png", TILE, run(0xE001, 1)),
                topAligned("ability_icons.png", ICON, run(0xE010, ABILITY_ICONS.size())),
                topAligned("ability_icons_cooling.png", ICON, run(0xE030, ABILITY_ICONS.size())),
                topAligned("ability_keys.png", BADGE, run(0xE050, BADGES.length)),
                topAligned("ability_fill.png", TILE, run(0xE060, FILL_LEVELS)),
                topAligned("ability_digits.png", DIGIT, run(0xE080, DIGITS.length)),
                topAligned("ability_count.png", COUNT_HEIGHT, run(0xE090, 10)),
                topAligned("ability_letters.png", 7, letters)) + "\n  ]\n}\n");
        if (contactSheet != null) {
            Files.createDirectories(contactSheet.toAbsolutePath().getParent());
            ImageIO.write(contactSheet(icons, cooling), "PNG", contactSheet.toFile());
        }
    }

    private static final int TILE = 24;
    private static final int ICON = 20;
    private static final int BADGE = 9;
    private static final int DIGIT = 11;
    /** A count digit's cell: a label digit with a pixel of rim on every side, and a column to spare for its advance. */
    private static final int COUNT_WIDTH = 6;
    private static final int COUNT_HEIGHT = 7;
    /** One fill per GUI pixel of the tile's inside, which the frame's pixel borders on every side. */
    private static final int FILL_LEVELS = TILE - 2;
    private static final Color LIGHT = new Color(0xF2F6FB);
    private static final Color SHADE = new Color(0xAEB9C9);
    private static final Color STEEL = new Color(0x68758A);
    private static final Color DEEP = new Color(0x283041);
    private static final Color ORANGE = new Color(0xFF9D2E);
    private static final Color YELLOW = new Color(0xFFE27A);
    private static final Color RED = new Color(0xFF4B3E);
    private static final Color GREEN = new Color(0x5FE07F);
    private static final Color BLUE = new Color(0x3E9BFF);
    private static final Color VIOLET = new Color(0xB48CFF);

    /** Dark glass with rounded corners, a little lighter at the top. */
    private static BufferedImage tileBacking() {
        int size = 2 * TILE;
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g = smooth(image);
        var shape = new RoundRectangle2D.Double(0, 0, size, size, 8, 8);
        g.setPaint(new GradientPaint(0, 0, new Color(0x1C2638, false), 0, size, new Color(0x0B1019, false)));
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.86f));
        g.fill(shape);
        g.dispose();
        padAdvance(image, size - 1, 0, size);
        return image;
    }

    /** A ring a GUI pixel wide in white, so the shader's tint colours it, with brighter corners. */
    private static BufferedImage tileFrame() {
        int size = 2 * TILE;
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g = smooth(image);
        var ring = new Area(new RoundRectangle2D.Double(0, 0, size, size, 8, 8));
        ring.subtract(new Area(new RoundRectangle2D.Double(2, 2, size - 4, size - 4, 5, 5)));
        g.setColor(new Color(0xFFFFFF));
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.78f));
        g.fill(ring);
        g.setComposite(AlphaComposite.SrcOver);
        for (int corner = 0; corner < 4; corner++) {
            var bracket = new Area(new Rectangle2D.Double(corner % 2 == 0 ? 0 : size - 9, corner < 2 ? 0 : size - 9, 9, 9));
            bracket.intersect(ring);
            g.fill(bracket);
        }
        g.dispose();
        return image;
    }

    /** The recharge fill at each height, a pale wash with a bright edge on top, inside the frame. */
    private static BufferedImage tileFills() {
        int size = 2 * TILE;
        var image = new BufferedImage(size * FILL_LEVELS, size, BufferedImage.TYPE_INT_ARGB);
        var g = smooth(image);
        for (int level = 1; level <= FILL_LEVELS; level++) {
            int left = (level - 1) * size;
            int top = size - 2 - 2 * level;
            var inside = new Area(new RoundRectangle2D.Double(left + 2, 2, size - 4, size - 4, 5, 5));
            var fill = new Area(new Rectangle2D.Double(left, top, size, size));
            fill.intersect(inside);
            g.setColor(new Color(0x7FB6E8));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.34f));
            g.fill(fill);
            var edge = new Area(new Rectangle2D.Double(left, top, size, 1));
            edge.intersect(inside);
            g.setColor(new Color(0xCFE8FF));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.9f));
            g.fill(edge);
            padAdvance(image, left + size - 1, 0, size);
        }
        g.dispose();
        return image;
    }

    /** The key in a dark badge with a light rim: F, Q, Shift and F, and a double jump's two chevrons. */
    private static BufferedImage keyBadges() {
        int height = 2 * BADGE;
        int cell = 2 * WIDE_BADGE;
        var image = new BufferedImage(cell * BADGES.length, height, BufferedImage.TYPE_INT_ARGB);
        for (int badge = 0; badge < BADGES.length; badge++) {
            var symbols = BADGES[badge];
            int width = 2 * (symbols.length == 1 ? BADGE : WIDE_BADGE);
            var g = smooth(image);
            g.translate(badge * cell, 0);
            g.setColor(new Color(0xE6ECF4));
            g.fill(new RoundRectangle2D.Double(0, 0, width, height, 6, 6));
            g.setColor(new Color(0x111722));
            g.fill(new RoundRectangle2D.Double(2, 2, width - 4, height - 4, 4, 4));
            g.dispose();
            int symbolsWidth = symbols.length * 10 + (symbols.length - 1) * 2;
            int x = badge * cell + (width - symbolsWidth) / 2;
            for (var symbol : symbols) {
                for (int y = 0; y < symbol.length; y++) {
                    for (int column = 0; column < symbol[y].length(); column++) {
                        if (symbol[y].charAt(column) == '#') {
                            image.setRGB(x + column, 2 + y, 0xFFF2F6FB);
                        }
                    }
                }
                x += 12;
            }
        }
        return image;
    }

    private static final int WIDE_BADGE = 15;
    /** Ten by fourteen texels each: F, Q, an outlined arrow up for Shift, and two chevrons for the double jump. */
    private static final String[] KEY_F = {
            "..........", ".########.", ".########.", ".##.......", ".##.......", ".######...", ".######...",
            ".##.......", ".##.......", ".##.......", ".##.......", ".##.......", "..........", ".........."};
    private static final String[] KEY_Q = {
            "..........", "..######..", ".##....##.", ".##....##.", ".##....##.", ".##....##.", ".##....##.",
            ".##....##.", ".##.##.##.", ".##..####.", "..#######.", "........##", "..........", ".........."};
    private static final String[] KEY_SHIFT = {
            "....##....", "...####...", "..##..##..", ".##....##.", "###....###", "..##..##..", "..##..##..",
            "..##..##..", "..##..##..", "..######..", "..........", "..........", "..........", ".........."};
    private static final String[] KEY_JUMPS = {
            "....##....", "...####...", "..##..##..", ".##....##.", "##......##", "..........", "....##....",
            "...####...", "..##..##..", ".##....##.", "##......##", "..........", "..........", ".........."};
    private static final String[][][] BADGES = {{KEY_F}, {KEY_Q}, {KEY_SHIFT, KEY_F}, {KEY_JUMPS}};

    /** Bold digits for the seconds left, six by nine GUI pixels, ringed dark so they read over any icon. */
    private static BufferedImage cooldownDigits() {
        int width = 2 * DIGIT_CELL;
        var image = new BufferedImage(width * DIGITS.length, 2 * DIGIT, BufferedImage.TYPE_INT_ARGB);
        for (int digit = 0; digit < DIGITS.length; digit++) {
            var art = DIGITS[digit];
            for (int y = -1; y <= art.length; y++) {
                for (int x = -1; x <= art[0].length(); x++) {
                    boolean ink = digitInk(art, x, y);
                    boolean rim = !ink && (digitInk(art, x - 1, y) || digitInk(art, x + 1, y) || digitInk(art, x, y - 1)
                            || digitInk(art, x, y + 1) || digitInk(art, x - 1, y - 1) || digitInk(art, x + 1, y + 1)
                            || digitInk(art, x - 1, y + 1) || digitInk(art, x + 1, y - 1));
                    if (ink || rim) {
                        for (int dy = 0; dy < 2; dy++) {
                            for (int dx = 0; dx < 2; dx++) {
                                image.setRGB(digit * width + 2 * (x + 1) + dx, 2 * (y + 1) + dy, ink ? 0xFFFFFFFF : 0xE00B0F17);
                            }
                        }
                    }
                }
            }
        }
        return image;
    }

    private static boolean digitInk(String[] art, int x, int y) {
        return y >= 0 && x >= 0 && y < art.length && x < art[y].length() && art[y].charAt(x) == '#';
    }

    private static final int DIGIT_CELL = 8;
    private static final String[][] DIGITS = {
            {".####.", "##..##", "##..##", "##..##", "##..##", "##..##", "##..##", "##..##", ".####."},
            {"..##..", ".###..", "####..", "..##..", "..##..", "..##..", "..##..", "..##..", "######"},
            {".####.", "##..##", "....##", "....##", "...##.", "..##..", ".##...", "##....", "######"},
            {".####.", "##..##", "....##", "....##", "..###.", "....##", "....##", "##..##", ".####."},
            {"...##.", "..###.", ".####.", "##.##.", "##.##.", "######", "...##.", "...##.", "...##."},
            {"######", "##....", "##....", "#####.", "....##", "....##", "....##", "##..##", ".####."},
            {"..###.", ".##...", "##....", "#####.", "##..##", "##..##", "##..##", "##..##", ".####."},
            {"######", "....##", "....##", "...##.", "...##.", "..##..", "..##..", "..##..", "..##.."},
            {".####.", "##..##", "##..##", "##..##", ".####.", "##..##", "##..##", "##..##", ".####."},
            {".####.", "##..##", "##..##", "##..##", ".#####", "....##", "....##", "...##.", ".###.."}};

    /**
     * The uses an ability has stored, for a tile's corner as the hotbar counts a stack: the label digits from
     * {@code letterSheet} at twice the resolution, white and ringed dark so they read over any icon.
     */
    private static BufferedImage countDigits(BufferedImage letterSheet, String letters) {
        int cell = 2 * COUNT_WIDTH;
        var image = new BufferedImage(cell * 10, 2 * COUNT_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        for (int digit = 0; digit < 10; digit++) {
            int left = 3 * letters.indexOf((char) ('0' + digit));
            for (int y = -1; y <= 5; y++) {
                for (int x = -1; x <= 3; x++) {
                    boolean ink = countInk(letterSheet, left, x, y);
                    boolean rim = false;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            rim |= countInk(letterSheet, left, x + dx, y + dy);
                        }
                    }
                    if (ink || rim) {
                        for (int dy = 0; dy < 2; dy++) {
                            for (int dx = 0; dx < 2; dx++) {
                                image.setRGB(digit * cell + 2 * (x + 1) + dx, 2 * (y + 1) + dy, ink ? 0xFFFFFFFF : 0xE00B0F17);
                            }
                        }
                    }
                }
            }
        }
        g.dispose();
        return image;
    }

    /** Whether the label digit whose three columns start at {@code left} inks its pixel ({@code x}, {@code y}). */
    private static boolean countInk(BufferedImage letterSheet, int left, int x, int y) {
        return x >= 0 && x < 3 && y >= 0 && y < 5 && (letterSheet.getRGB(left + x, 2 + y) >>> 24) != 0;
    }

    /**
     * The menu tiles' capitals at twice the resolution for the abilities' names, each in a cell wide enough for M, N
     * and W, which take five pixels instead of three so their diagonals show; a three-pixel M reads as an N.
     */
    private static BufferedImage nameLetters(BufferedImage labels, String letters) {
        int cell = 2 * 5;
        int height = 2 * labels.getHeight();
        var image = new BufferedImage(cell * letters.length(), height, BufferedImage.TYPE_INT_ARGB);
        for (int letter = 0; letter < letters.length(); letter++) {
            var wide = WIDE_LETTERS.get(letters.charAt(letter));
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < cell; x++) {
                    boolean ink = wide != null ? y >= height - wide.length && wide[y - (height - wide.length)].charAt(x) == '#'
                            : x < 6 && (labels.getRGB(letter * 3 + x / 2, y / 2) >>> 24) != 0;
                    if (ink) {
                        image.setRGB(letter * cell + x, y, 0xFFFFFFFF);
                    }
                }
            }
        }
        return image;
    }

    private static final Map<Character, String[]> WIDE_LETTERS = Map.of(
            'M', new String[] {"##......##", "###....###", "####..####", "##.####.##", "##..##..##", "##......##", "##......##",
                    "##......##", "##......##", "##......##"},
            'N', new String[] {"##......##", "###.....##", "####....##", "##.##...##", "##..##..##", "##...##.##", "##....####",
                    "##.....###", "##......##", "##......##"},
            'W', new String[] {"##......##", "##......##", "##......##", "##......##", "##......##", "##..##..##", "##.####.##",
                    "####..####", "###....###", "##......##"});

    private static Graphics2D smooth(BufferedImage image) {
        var g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    private static BufferedImage drawn(int size, Consumer<Graphics2D> art) {
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g = smooth(image);
        art.accept(g);
        return image;
    }

    /** The icon over a dark rim two texels wide, a GUI pixel, round everything at least half covered. */
    private static BufferedImage outlined(BufferedImage art) {
        int size = art.getWidth();
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (covered(art, x, y)) {
                    continue;
                }
                double nearest = Double.MAX_VALUE;
                for (int dy = -3; dy <= 3; dy++) {
                    for (int dx = -3; dx <= 3; dx++) {
                        if (covered(art, x + dx, y + dy)) {
                            nearest = Math.min(nearest, Math.hypot(dx, dy));
                        }
                    }
                }
                if (nearest <= 2.2) {
                    image.setRGB(x, y, nearest <= 1.6 ? 0xF00B0F17 : 0x900B0F17);
                }
            }
        }
        var g = image.createGraphics();
        g.drawImage(art, 0, 0, null);
        g.dispose();
        return image;
    }

    private static boolean covered(BufferedImage art, int x, int y) {
        return x >= 0 && y >= 0 && x < art.getWidth() && y < art.getHeight() && (art.getRGB(x, y) >>> 24) >= 128;
    }

    /** The icon in grey and darker, as it shows while it recharges. */
    private static BufferedImage greyed(BufferedImage icon) {
        var image = new BufferedImage(icon.getWidth(), icon.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < icon.getHeight(); y++) {
            for (int x = 0; x < icon.getWidth(); x++) {
                int argb = icon.getRGB(x, y);
                int luma = (int) Math.round(0.3 * (argb >> 16 & 255) + 0.59 * (argb >> 8 & 255) + 0.11 * (argb & 255));
                int grey = (int) Math.round(luma * 0.55);
                image.setRGB(x, y, argb & 0xFF000000 | grey << 16 | grey << 8 | grey);
            }
        }
        return image;
    }

    private static void fill(Graphics2D g, Shape shape, Color colour) {
        g.setPaint(colour);
        g.fill(shape);
    }

    /** {@code shape} lit from the upper left: {@code lit} there, {@code shaded} towards the lower right. */
    private static void lit(Graphics2D g, Shape shape, Color lit, Color shaded) {
        var bounds = shape.getBounds2D();
        g.setPaint(new GradientPaint((float) bounds.getMinX(), (float) bounds.getMinY(), lit,
                (float) bounds.getMaxX(), (float) bounds.getMaxY(), shaded));
        g.fill(shape);
    }

    private static void stroke(Graphics2D g, Shape shape, Color colour, double width) {
        g.setPaint(colour);
        g.setStroke(new BasicStroke((float) width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(shape);
    }

    private static Path2D path(double... points) {
        var path = new Path2D.Double();
        path.moveTo(points[0], points[1]);
        for (int index = 2; index < points.length; index += 2) {
            path.lineTo(points[index], points[index + 1]);
        }
        return path;
    }

    private static Path2D closed(double... points) {
        var path = path(points);
        path.closePath();
        return path;
    }

    private static Path2D curve(double fromX, double fromY, double viaX, double viaY, double toX, double toY) {
        var path = new Path2D.Double();
        path.moveTo(fromX, fromY);
        path.quadTo(viaX, viaY, toX, toY);
        return path;
    }

    /** A flame pointing down from ({@code x}, {@code top}), orange with a yellow core. */
    private static void flame(Graphics2D g, double x, double top, double width, double length) {
        var outer = new Path2D.Double();
        outer.moveTo(x - width / 2, top);
        outer.quadTo(x - width / 2, top + length * 0.6, x, top + length);
        outer.quadTo(x + width / 2, top + length * 0.6, x + width / 2, top);
        outer.closePath();
        fill(g, outer, ORANGE);
        var inner = new Path2D.Double();
        inner.moveTo(x - width / 4, top);
        inner.quadTo(x - width / 4, top + length * 0.4, x, top + length * 0.65);
        inner.quadTo(x + width / 4, top + length * 0.4, x + width / 4, top);
        inner.closePath();
        fill(g, inner, YELLOW);
    }

    /** One icon per ability on a 40-texel grid, a light silhouette with one colour that names it. */
    private static final Map<String, Consumer<Graphics2D>> ABILITY_ICONS = linked(
            "grenade", (Consumer<Graphics2D>) g -> {
                fill(g, new RoundRectangle2D.Double(15, 3, 10, 8, 3, 3), SHADE);
                fill(g, new Rectangle2D.Double(17, 1, 6, 3), STEEL);
                var body = new Ellipse2D.Double(5, 9, 30, 30);
                lit(g, body, LIGHT, SHADE);
                var band = new Area(new Rectangle2D.Double(4, 21, 32, 7));
                band.intersect(new Area(body));
                fill(g, band, STEEL);
                for (double x : new double[] {10, 18.5, 27}) {
                    fill(g, new RoundRectangle2D.Double(x, 22.5, 3.5, 4, 1.5, 1.5), ORANGE);
                }
                fill(g, new Ellipse2D.Double(11, 13, 7, 5), Color.WHITE);
            },
            "adrenaline", (Consumer<Graphics2D>) g -> {
                g.translate(20, 20);
                g.rotate(-Math.PI / 4);
                fill(g, new Rectangle2D.Double(-19, -1, 8, 2), LIGHT);
                fill(g, closed(-12, -3, -8, -5, -8, 5, -12, 3), SHADE);
                lit(g, new RoundRectangle2D.Double(-9, -6.5, 18, 13, 4, 4), LIGHT, SHADE);
                fill(g, new RoundRectangle2D.Double(-6.5, -3.5, 12, 7, 2, 2), GREEN);
                fill(g, new Rectangle2D.Double(-6.5, -3.5, 12, 2), new Color(0xB9FFC9));
                fill(g, new RoundRectangle2D.Double(8, -9, 3, 18, 1.5, 1.5), SHADE);
                fill(g, new Rectangle2D.Double(11, -2, 5, 4), STEEL);
                fill(g, new RoundRectangle2D.Double(15.5, -6.5, 3.5, 13, 1.5, 1.5), LIGHT);
            },
            "shield_wall", (Consumer<Graphics2D>) g -> {
                var shield = new Path2D.Double();
                shield.moveTo(9, 5);
                shield.quadTo(20, 1, 34, 5);
                shield.lineTo(34, 19);
                shield.quadTo(33, 31, 21.5, 38);
                shield.quadTo(10, 31, 9, 19);
                shield.closePath();
                g.setPaint(new GradientPaint(0, 3, new Color(0xC2F8FF), 0, 36, new Color(0x2295D6)));
                g.fill(shield);
                var lines = new Path2D.Double();
                for (double y : new double[] {14, 21, 28}) {
                    lines.moveTo(5, y);
                    lines.lineTo(38, y);
                }
                var scan = new Area(new BasicStroke(1.4f).createStrokedShape(lines));
                scan.intersect(new Area(shield));
                fill(g, scan, new Color(0x5BC4F0));
                var rim = new Path2D.Double();
                rim.moveTo(12.5, 8);
                rim.quadTo(21, 5, 30.5, 8);
                rim.lineTo(30.5, 19);
                rim.quadTo(30, 28, 21.5, 33.5);
                stroke(g, rim, new Color(0xF2FDFF), 1.8);
                stroke(g, path(1, 3, 9.5, 11), new Color(0xFF6A4A), 3.2);
                stroke(g, path(2, 4, 9.5, 11), new Color(0xFFE2D6), 1.2);
                fill(g, closed(9, 7, 10.5, 10, 14, 10.5, 10.5, 11.5, 9.5, 15, 8.5, 11.5, 5, 11, 8.5, 10), YELLOW);
            },
            "jump_pack", (Consumer<Graphics2D>) g -> {
                flame(g, 9, 29, 7, 10);
                flame(g, 15.5, 31, 5, 7);
                stroke(g, curve(10, 30, 12, 12, 28, 9), LIGHT, 6);
                fill(g, closed(24, 1, 37, 8.5, 25, 17), LIGHT);
            },
            "med_station", (Consumer<Graphics2D>) g -> {
                for (double radius : new double[] {14, 19}) {
                    var box = new Rectangle2D.Double(20 - radius, 16 - radius, 2 * radius, 2 * radius);
                    stroke(g, new Arc2D.Double(box, -35, 70, Arc2D.OPEN), new Color(0x9CF2B0), 2.2);
                    stroke(g, new Arc2D.Double(box, 145, 70, Arc2D.OPEN), new Color(0x9CF2B0), 2.2);
                }
                fill(g, closed(11, 37, 29, 37, 26, 30, 14, 30), STEEL);
                var cross = new Area(new RoundRectangle2D.Double(15, 4, 10, 25, 3, 3));
                cross.add(new Area(new RoundRectangle2D.Double(7.5, 11.5, 25, 10, 3, 3)));
                lit(g, cross, new Color(0xA8FFBA), new Color(0x22B14C));
                fill(g, new Rectangle2D.Double(17, 6, 2, 8), new Color(0xE6FFEA));
            },
            "siege_mode", (Consumer<Graphics2D>) g -> {
                fill(g, new Rectangle2D.Double(1, 34, 38, 4), STEEL);
                fill(g, closed(7, 25, 15, 25, 11, 37), SHADE);
                fill(g, closed(25, 25, 33, 25, 29, 37), SHADE);
                lit(g, new RoundRectangle2D.Double(5, 21, 30, 6, 2, 2), SHADE, STEEL);
                lit(g, new RoundRectangle2D.Double(3, 7, 22, 15, 4, 4), LIGHT, SHADE);
                fill(g, new Rectangle2D.Double(25, 9, 12, 4), SHADE);
                fill(g, new Rectangle2D.Double(25, 15.5, 12, 4), SHADE);
                fill(g, new Rectangle2D.Double(7, 11, 10, 3), STEEL);
                fill(g, new Rectangle2D.Double(36, 8, 3, 12.5), ORANGE);
            },
            "mining_charge", (Consumer<Graphics2D>) g -> {
                fill(g, closed(29, 1, 31.5, 7, 38, 5, 33.5, 10, 39, 15, 32, 14.5, 30, 20, 27, 14, 21, 15, 25, 10, 21, 5, 27, 7),
                        YELLOW);
                lit(g, new RoundRectangle2D.Double(3, 14, 30, 23, 4, 4), new Color(0xE3A25A), new Color(0x8E5420));
                fill(g, new Rectangle2D.Double(8, 14, 3, 23), DEEP);
                fill(g, new Rectangle2D.Double(25, 14, 3, 23), DEEP);
                fill(g, new RoundRectangle2D.Double(12.5, 19, 11, 9, 2, 2), DEEP);
                fill(g, new Rectangle2D.Double(14.5, 21, 3, 5), RED);
                fill(g, new Rectangle2D.Double(19, 21, 3, 5), RED);
                stroke(g, curve(17, 14, 15, 7, 22, 6), RED, 1.8);
            },
            "force_push", (Consumer<Graphics2D>) g -> {
                for (int wave = 0; wave < 3; wave++) {
                    double radius = 9 + wave * 5.5;
                    stroke(g, new Arc2D.Double(12 - radius, 20 - radius, 2 * radius, 2 * radius, -45, 90, Arc2D.OPEN),
                            wave == 0 ? new Color(0xBFE3FF) : BLUE, 2.6);
                }
                var hand = new Area(new RoundRectangle2D.Double(3, 15, 13, 17, 5, 5));
                double[][] fingers = {{3, 7}, {6.5, 4}, {10, 5}, {13.5, 8}};
                for (var finger : fingers) {
                    hand.add(new Area(new RoundRectangle2D.Double(finger[0], finger[1], 3.2, 16 - finger[1] + 4, 3, 3)));
                }
                hand.add(new Area(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                        .createStrokedShape(path(15, 26, 19, 21))));
                lit(g, hand, LIGHT, SHADE);
            },
            "saber_throw", (Consumer<Graphics2D>) g -> {
                stroke(g, new Arc2D.Double(3, 3, 34, 34, 110, 110, Arc2D.OPEN), LIGHT, 2.4);
                fill(g, closed(3, 25, 9, 29, 3, 31), LIGHT);
                stroke(g, path(13, 27, 33, 7), new Color(0x7FE2FF), 6);
                stroke(g, path(13, 27, 33, 7), Color.WHITE, 2.6);
                stroke(g, path(7, 33, 13, 27), STEEL, 5);
                stroke(g, path(9.5, 30.5, 11, 29), SHADE, 5);
            },
            "force_grip", (Consumer<Graphics2D>) g -> {
                var aura = new Color(0xC9A8FF);
                fill(g, new Ellipse2D.Double(11, 34, 18, 4.5), new Color(0x9A0B0F17, true));
                for (int side = -1; side <= 1; side += 2) {
                    stroke(g, new Arc2D.Double(20 - 15, 4, 30, 30, side < 0 ? 140 : -40, 80, Arc2D.OPEN), aura, 2);
                    stroke(g, new Arc2D.Double(20 - 19, 2, 38, 34, side < 0 ? 150 : -30, 60, Arc2D.OPEN), VIOLET, 1.6);
                }
                var body = new Area(new Ellipse2D.Double(16.5, 3, 8, 8));
                body.add(new Area(new RoundRectangle2D.Double(15, 12, 11, 12, 5, 5)));
                var limbs = new Path2D.Double();
                limbs.append(path(18, 22, 17, 30), false);
                limbs.append(path(23, 22, 24.5, 30), false);
                limbs.append(path(15.5, 15, 13.5, 21), false);
                limbs.append(path(25.5, 15, 27.5, 21), false);
                body.add(new Area(new BasicStroke(3.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(limbs)));
                lit(g, body, LIGHT, SHADE);
                stroke(g, new Ellipse2D.Double(14, 9.5, 13, 4), VIOLET, 2.2);
            },
            "force_lightning", (Consumer<Graphics2D>) g -> {
                stroke(g, path(16, 19, 8, 24, 5, 31), new Color(0xD7C2FF), 1.6);
                stroke(g, path(25, 23, 33, 26, 35, 33), new Color(0xD7C2FF), 1.6);
                var bolt = closed(17, 1, 8, 20, 17, 20, 11, 39, 32, 15, 22, 15, 29, 1);
                lit(g, bolt, new Color(0xF4EDFF), VIOLET);
                stroke(g, path(20, 5, 15, 17), Color.WHITE, 1.5);
            },
            "jetpack", (Consumer<Graphics2D>) g -> {
                flame(g, 13.5, 27, 6, 12);
                flame(g, 26.5, 27, 6, 12);
                fill(g, closed(20, 1, 23, 7, 17, 7), RED);
                lit(g, new RoundRectangle2D.Double(8, 6, 11, 20, 6, 6), LIGHT, SHADE);
                lit(g, new RoundRectangle2D.Double(21, 6, 11, 20, 6, 6), LIGHT, SHADE);
                fill(g, new Rectangle2D.Double(17, 10, 6, 5), STEEL);
                fill(g, closed(10, 25, 17, 25, 16, 29, 11, 29), DEEP);
                fill(g, closed(23, 25, 30, 25, 29, 29, 24, 29), DEEP);
            },
            "wrist_rocket", (Consumer<Graphics2D>) g -> {
                fill(g, new Ellipse2D.Double(1, 30, 7, 7), new Color(0x8C96A6));
                fill(g, new Ellipse2D.Double(5, 26, 6, 6), new Color(0xA7B0BE));
                g.translate(21, 19);
                g.rotate(-Math.PI / 4);
                var exhaust = new Path2D.Double();
                exhaust.moveTo(-8, -3.5);
                exhaust.quadTo(-16, -3, -19, 0);
                exhaust.quadTo(-16, 3, -8, 3.5);
                exhaust.closePath();
                fill(g, exhaust, ORANGE);
                fill(g, closed(-8, -2, -14, 0, -8, 2), YELLOW);
                fill(g, closed(-8, -4, -3, -4, -6, -9), SHADE);
                fill(g, closed(-8, 4, -3, 4, -6, 9), SHADE);
                lit(g, new RoundRectangle2D.Double(-8, -4, 17, 8, 3, 3), LIGHT, SHADE);
                fill(g, closed(8, -4, 16, 0, 8, 4), RED);
            },
            "grapple", (Consumer<Graphics2D>) g -> {
                var rope = new Path2D.Double();
                rope.moveTo(3, 38);
                rope.curveTo(5, 30, 12, 31, 16.5, 23.5);
                stroke(g, rope, SHADE, 2.2);
                stroke(g, new Ellipse2D.Double(14.5, 21.5, 5, 5), LIGHT, 1.8);
                var hook = new Path2D.Double();
                hook.append(path(18.5, 21.5, 31, 9), false);
                hook.append(curve(28, 12, 35, 16, 30, 21), false);
                hook.append(curve(28, 12, 24, 5, 19, 10), false);
                stroke(g, hook, LIGHT, 3.8);
                fill(g, closed(29, 18, 33, 23, 27.5, 22), LIGHT);
                fill(g, closed(22, 7, 17, 11, 18, 5.5), LIGHT);
                fill(g, closed(31, 4, 36, 4, 33.5, 9.5), LIGHT);
            },
            "orbital_strike", (Consumer<Graphics2D>) g -> {
                fill(g, new Ellipse2D.Double(4, 27, 32, 11), new Color(0x60FF4B3E, true));
                stroke(g, new Ellipse2D.Double(4, 27, 32, 11), RED, 2.4);
                stroke(g, path(1, 32.5, 6, 32.5), RED, 2);
                stroke(g, path(34, 32.5, 39, 32.5), RED, 2);
                g.setPaint(new GradientPaint(0, 0, YELLOW, 0, 32, ORANGE));
                g.fill(closed(15, 0, 25, 0, 24, 31, 16, 31));
                fill(g, new Rectangle2D.Double(18.5, 0, 3, 31), Color.WHITE);
                fill(g, new Ellipse2D.Double(11, 27.5, 18, 7), YELLOW);
                fill(g, new Ellipse2D.Double(15, 29, 10, 4), Color.WHITE);
            },
            "rally", (Consumer<Graphics2D>) g -> {
                lit(g, new RoundRectangle2D.Double(7, 4, 4, 34, 2, 2), LIGHT, SHADE);
                fill(g, new Ellipse2D.Double(6, 1, 6, 6), LIGHT);
                var flag = new Path2D.Double();
                flag.moveTo(11, 6);
                flag.curveTo(18, 2, 24, 10, 36, 6);
                flag.lineTo(36, 21);
                flag.curveTo(24, 25, 18, 17, 11, 21);
                flag.closePath();
                lit(g, flag, new Color(0xFFE38A), new Color(0xE08E1A));
                stroke(g, path(17, 17, 23, 11, 29, 17), new Color(0xFFFBEA), 2.6);
            },
            "leap", (Consumer<Graphics2D>) g -> {
                stroke(g, curve(3, 38, 6, 22, 19, 23), new Color(0x7FC8FF), 2.4);
                stroke(g, curve(9, 38, 11, 28, 18, 28), new Color(0x4FA5F2), 1.8);
                fill(g, new Ellipse2D.Double(25, 2, 8, 8), LIGHT);
                var body = new Path2D.Double();
                body.append(path(27, 11, 21, 21), false);
                body.append(path(21, 21, 29, 24, 26, 32), false);
                body.append(path(21, 21, 14, 27, 9, 25), false);
                body.append(path(26, 13, 34, 17), false);
                body.append(path(25, 13, 17, 10), false);
                var figure = new Area(new BasicStroke(4.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(body));
                lit(g, figure, LIGHT, SHADE);
            });

    private static <T> Map<String, T> linked(Object... namesAndArt) {
        var art = new LinkedHashMap<String, T>();
        for (int index = 0; index < namesAndArt.length; index += 2) {
            @SuppressWarnings("unchecked") var drawing = (T) namesAndArt[index + 1];
            art.put((String) namesAndArt[index], drawing);
        }
        return art;
    }

    /** Every icon at four times its GUI size, ready and recharging, on its tile: the sheet to judge the set by. */
    private static BufferedImage contactSheet(BufferedImage icons, BufferedImage cooling) {
        int zoom = 4;
        int cell = 2 * TILE * zoom / 2;
        int columns = 6;
        int rows = (ABILITY_ICONS.size() + columns - 1) / columns;
        int labelHeight = 18;
        var sheet = new BufferedImage(columns * (2 * cell + 24), rows * (cell + labelHeight + 16) + 8, BufferedImage.TYPE_INT_ARGB);
        var g = sheet.createGraphics();
        g.setColor(new Color(0x5A6676));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        var backing = tileBacking();
        var frame = tileFrame();
        int index = 0;
        for (var name : ABILITY_ICONS.keySet()) {
            int x = (index % columns) * (2 * cell + 24) + 8;
            int y = (index / columns) * (cell + labelHeight + 16) + 8;
            for (int state = 0; state < 2; state++) {
                int left = x + state * (cell + 8);
                g.drawImage(backing, left, y, cell, cell, null);
                var source = state == 0 ? icons : cooling;
                int iconLeft = left + 2 * zoom;
                g.drawImage(source.getSubimage(index * 2 * ICON, 0, 2 * ICON, 2 * ICON), iconLeft, y + 2 * zoom, 2 * ICON * zoom / 2,
                        2 * ICON * zoom / 2, null);
                g.drawImage(tinted(frame, state == 0 ? 0xFFFFFFFF : 0xFF999999), left, y, cell, cell, null);
            }
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            g.drawString(name, x, y + cell + labelHeight - 2);
            index++;
        }
        g.dispose();
        return sheet;
    }

    private static BufferedImage tinted(BufferedImage image, int tint) {
        var out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int r = (argb >> 16 & 255) * (tint >> 16 & 255) / 255;
                int gg = (argb >> 8 & 255) * (tint >> 8 & 255) / 255;
                int b = (argb & 255) * (tint & 255) / 255;
                out.setRGB(x, y, argb & 0xFF000000 | r << 16 | gg << 8 | b);
            }
        }
        return out;
    }
}
