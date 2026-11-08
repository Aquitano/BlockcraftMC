import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
     * Under the crosshair, where the spread ticks give way to it: a grey chevron pointing down while a sprint keeps the gun
     * lowered, and a white one pointing up for the moment it can fire again.
     */
    private static BufferedImage readiness() {
        var image = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        int[] colours = {0xE0A9B2BF, 0xF0FFFFFF};
        for (int cell = 0; cell < colours.length; cell++) {
            int middle = cell * 32 + 16;
            var ink = new ArrayList<int[]>();
            for (int step = -3; step <= 3; step++) {
                int rise = 3 - Math.abs(step);
                int y = cell == 0 ? 7 + rise : 10 - rise;
                ink.add(new int[] {middle + step, 16 + y});
                ink.add(new int[] {middle + step, 16 + y + 1});
            }
            for (var pixel : ink) {
                for (int[] near : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                    int x = pixel[0] + near[0];
                    int y = pixel[1] + near[1];
                    if (image.getRGB(x, y) == 0) {
                        image.setRGB(x, y, 0x90000000);
                    }
                }
            }
            for (var pixel : ink) {
                image.setRGB(pixel[0], pixel[1], colours[cell]);
            }
        }
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
        for (int y = 0; y < FLAME.length; y++) {
            for (int x = 0; x < FLAME[y].length(); x++) {
                if (FLAME[y].charAt(x) == '#') {
                    icons.setRGB(14 + x, y, 0xFFFFFFFF);
                }
            }
        }
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
        ImageIO.write(onTheCrosshair(readiness(), true), "PNG", root.resolve("readiness.png").toFile());
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

    /** The heat meter's flame, with the inner flame cut out so it reads as fire and not as a warning sign. */
    private static final String[] FLAME = {
            "...#....",
            "...##...",
            "..###...",
            "..####.#",
            ".#####.#",
            ".######.",
            "###.####",
            "##...###",
            "##....##",
            ".##..##.",
            "..####.."};

    /**
     * The minimap's font: the disc in strips, the HUD's post badges, map marks, class marks and the menu tiles'
     * letters, every glyph with its top at the line's top, with the abilities' wide M, N and W, which a three-pixel cell
     * draws alike. The text shader moves the line into the corner and finds the boss bar line it came from by where a
     * glyph lies inside it, so no glyph may reach more than 16 pixels down.
     */
    private static void minimap(Path root, String letters) throws Exception {
        ImageIO.write(minimapDisc(), "PNG", root.resolve("minimap_disc.png").toFile());
        ImageIO.write(minimapMarks(), "PNG", root.resolve("minimap_marks.png").toFile());
        ImageIO.write(minimapTarget(), "PNG", root.resolve("minimap_target.png").toFile());
        ImageIO.write(feedMarks(), "PNG", root.resolve("minimap_feed.png").toFile());
        var labels = ImageIO.read(root.resolve("labels.png").toFile());
        ImageIO.write(mapLetters(labels, letters), "PNG", root.resolve("minimap_letters.png").toFile());
        ImageIO.write(nameLetters(labels, WIDE), "PNG", root.resolve("minimap_wide.png").toFile());
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
                topAligned("minimap_wide.png", 7, WIDE),
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
    /**
     * Soldier, heavy, scout, medic and hero, each a picture of what the role carries at twice the resolution: a rifle, a
     * shield, a scope's reticle, a medic's cross and a crown.
     */
    private static final String[][] CLASS_MARKS = {
            {"..........", "..........", ".....###..", "##########", "#######...", "##.###....", "....##....", "....##....",
                    "..........", ".........."},
            {"##########", "##########", "####..####", "####..####", "####..####", ".###..###.", ".###..###.", "..##..##..",
                    "...####...", "....##...."},
            {"....##....", "..######..", ".#..##..#.", ".#..##..#.", "##########", "##########", ".#..##..#.", ".#..##..#.",
                    "..######..", "....##...."},
            {"...####...", "...####...", "...####...", "##########", "##########", "##########", "##########", "...####...",
                    "...####...", "...####..."},
            {"..........", "#...##...#", "##..##..##", "##.####.##", "##########", "##########", "##########", "..........",
                    "##########", "##########"}};

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
        int cell = 2 * 6;
        var image = new BufferedImage(cell * CLASS_MARKS.length, 2 * 7, BufferedImage.TYPE_INT_ARGB);
        for (int mark = 0; mark < CLASS_MARKS.length; mark++) {
            var art = CLASS_MARKS[mark];
            for (int y = 0; y < art.length; y++) {
                for (int x = 0; x < art[y].length(); x++) {
                    if (art[y].charAt(x) == '#') {
                        image.setRGB(mark * cell + x, 2 * 2 + y, 0xFFFFFFFF);
                    }
                }
            }
            padAdvance(image, mark * cell + cell - 1, 0, image.getHeight());
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
     * The ability tiles' font in the screen's bottom-right corner: a dark backing, a frame the shader tints by state, a
     * fill that rises while an ability recharges and every ability's icon in colour and in grey, one texel per GUI pixel
     * as Minecraft draws its items, then at twice that resolution the keys' badges, the cooldown's digits, the digits
     * that count stored uses and the label letters of the names, each of their pixels a square of four texels.
     * Every glyph hangs from the line's top, which the text shader moves into the corner.
     */
    private static void abilities(Path root, Path contactSheet, String letters) throws Exception {
        var icons = new BufferedImage(ICON * ABILITY_ICONS.size(), ICON, BufferedImage.TYPE_INT_ARGB);
        var cooling = new BufferedImage(icons.getWidth(), icons.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int left = 0;
        for (var art : ABILITY_ICONS.values()) {
            var icon = pixels(art, ICON_PALETTE);
            icons.createGraphics().drawImage(icon, left, 0, null);
            cooling.createGraphics().drawImage(greyed(icon), left, 0, null);
            padAdvance(icons, left + ICON - 1, 0, ICON);
            padAdvance(cooling, left + ICON - 1, 0, ICON);
            left += ICON;
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
    private static final int ICON = 16;
    private static final int BADGE = 9;
    private static final int DIGIT = 11;
    /** A count digit's cell: a label digit with a pixel of rim on every side, and a column to spare for its advance. */
    private static final int COUNT_WIDTH = 6;
    private static final int COUNT_HEIGHT = 7;
    /** One fill per GUI pixel of the tile's inside, which the frame's pixel borders on every side. */
    private static final int FILL_LEVELS = TILE - 2;

    /** Dark glass with its corners cut, its top row a shade lighter. */
    private static BufferedImage tileBacking() {
        var image = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
        fill(image, 0, 0, TILE, TILE, 0xDB141B28);
        fill(image, 1, 1, TILE - 2, 1, 0xDB222D41);
        cutCorners(image, 0, 0, TILE, TILE);
        padAdvance(image, TILE - 1, 0, TILE);
        return image;
    }

    /** A ring a GUI pixel wide in white, so the shader's tint colours it, with brighter brackets round its cut corners. */
    private static BufferedImage tileFrame() {
        var image = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
        for (int edge = 0; edge < TILE; edge++) {
            int alpha = edge < BRACKET || edge >= TILE - BRACKET ? 0xFF000000 : 0xC7000000;
            for (int[] at : new int[][] {{edge, 0}, {edge, TILE - 1}, {0, edge}, {TILE - 1, edge}}) {
                image.setRGB(at[0], at[1], alpha | 0xFFFFFF);
            }
        }
        cutCorners(image, 0, 0, TILE, TILE);
        return image;
    }

    private static final int BRACKET = 5;

    /** The recharge fill at each height, a pale wash with a bright edge on top, inside the frame. */
    private static BufferedImage tileFills() {
        var image = new BufferedImage(TILE * FILL_LEVELS, TILE, BufferedImage.TYPE_INT_ARGB);
        for (int level = 1; level <= FILL_LEVELS; level++) {
            int left = (level - 1) * TILE;
            int top = TILE - 1 - level;
            fill(image, left + 1, top, TILE - 2, level, 0x577FB6E8);
            fill(image, left + 1, top, TILE - 2, 1, 0xE6CFE8FF);
            padAdvance(image, left + TILE - 1, 0, TILE);
        }
        return image;
    }

    private static void fill(BufferedImage image, int left, int top, int width, int height, int argb) {
        for (int y = top; y < top + height; y++) {
            for (int x = left; x < left + width; x++) {
                image.setRGB(x, y, argb);
            }
        }
    }

    private static void cutCorners(BufferedImage image, int left, int top, int width, int height) {
        for (int[] corner : new int[][] {{left, top}, {left + width - 1, top}, {left, top + height - 1}, {left + width - 1, top + height - 1}}) {
            image.setRGB(corner[0], corner[1], 0);
        }
    }

    /** The key in a dark badge with a light rim: F, Q, Shift and F, and the space bar twice for a double jump. */
    private static BufferedImage keyBadges() {
        int height = 2 * BADGE;
        int cell = 2 * WIDE_BADGE;
        var image = new BufferedImage(cell * BADGES.length, height, BufferedImage.TYPE_INT_ARGB);
        for (int badge = 0; badge < BADGES.length; badge++) {
            var symbols = BADGES[badge];
            int width = 2 * (symbols.length == 1 ? BADGE : WIDE_BADGE);
            fill(image, badge * cell + 2, 0, width - 4, height, 0xFFE6ECF4);
            fill(image, badge * cell, 2, width, height - 4, 0xFFE6ECF4);
            fill(image, badge * cell + 2, 2, width - 4, height - 4, 0xFF111722);
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
    /** Ten by fourteen texels each: F, Q, an outlined arrow up for Shift, and the space bar's bracket. */
    private static final String[] KEY_F = {
            "..........", ".########.", ".########.", ".##.......", ".##.......", ".######...", ".######...",
            ".##.......", ".##.......", ".##.......", ".##.......", ".##.......", "..........", ".........."};
    private static final String[] KEY_Q = {
            "..........", "..######..", ".##....##.", ".##....##.", ".##....##.", ".##....##.", ".##....##.",
            ".##....##.", ".##.##.##.", ".##..####.", "..#######.", "........##", "..........", ".........."};
    private static final String[] KEY_SHIFT = {
            "....##....", "...####...", "..##..##..", ".##....##.", "###....###", "..##..##..", "..##..##..",
            "..##..##..", "..##..##..", "..######..", "..........", "..........", "..........", ".........."};
    private static final String[] KEY_SPACE = {
            "..........", "..........", "..........", "..........", "..........", "..........", ".##....##.",
            ".##....##.", ".########.", ".########.", "..........", "..........", "..........", ".........."};
    private static final String[][][] BADGES = {{KEY_F}, {KEY_Q}, {KEY_SHIFT, KEY_F}, {KEY_SPACE, KEY_SPACE}};

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

    /** The capitals drawn five pixels wide, which HudPanel counts six pixels each with their spacing. */
    private static final String WIDE = "MNW";
    private static final Map<Character, String[]> WIDE_LETTERS = Map.of(
            'M', new String[] {"##......##", "###....###", "####..####", "##.####.##", "##..##..##", "##......##", "##......##",
                    "##......##", "##......##", "##......##"},
            'N', new String[] {"##......##", "###.....##", "####....##", "##.##...##", "##..##..##", "##...##.##", "##....####",
                    "##.....###", "##......##", "##......##"},
            'W', new String[] {"##......##", "##......##", "##......##", "##......##", "##......##", "##..##..##", "##.####.##",
                    "####..####", "###....###", "##......##"});

    /** {@code rows} as an image: '.' is transparent and every other letter a colour of {@code palette}. */
    private static BufferedImage pixels(String[] rows, Map<Character, Integer> palette) {
        var image = new BufferedImage(rows[0].length(), rows.length, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                char pixel = rows[y].charAt(x);
                if (pixel != '.') {
                    image.setRGB(x, y, palette.get(pixel));
                }
            }
        }
        return image;
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

    /**
     * One icon per ability, in the order of Sternenfront's {@code AbilityIcon}, drawn as Minecraft draws an item: sixteen
     * pixels square, one texel per GUI pixel, a few flat colours lit from the upper left inside a dark outline.
     * '.' is transparent and every other letter a colour of {@link #ICON_PALETTE}.
     */
    private static final Map<String, String[]> ABILITY_ICONS = linked(
            "grenade", new String[] {
                    "......oooo......",
                    ".....oLLLSo.....",
                    ".....oSSSSo.....",
                    "....ooLLLLoo....",
                    "...oLLLLLLLLo...",
                    "..oLWWWLLLLLLo..",
                    ".oLLWWWLLLLLLLo.",
                    ".oLLWWWLLLLLLLo.",
                    ".oLLLLLLLLLLLSo.",
                    ".oKOKKOKKOKKOKo.",
                    ".oKyKKyKKyKKyKo.",
                    ".oLLLLLLLLSSSDo.",
                    ".oLLLLLLLSSSDDo.",
                    "..oLLLLLSSSDDo..",
                    "...oLLLSSSDDo...",
                    "....ooSSSDoo...."},
            "adrenaline", new String[] {
                    "................",
                    "...........oo...",
                    "..........oLLo..",
                    "...........oLLo.",
                    ".........ooSoLo.",
                    "........oWWo.o..",
                    ".......oWWLo....",
                    "......oWGLo.....",
                    "...o.oWGgo......",
                    "..oSoWGgo.......",
                    "..oSSGgo........",
                    "...oSSo.........",
                    "..oSoSSo........",
                    ".oSo.oo.........",
                    "oSo.............",
                    ".o.............."},
            "shield_wall", new String[] {
                    "................",
                    ".Ro.............",
                    "oRPo.oooooooo...",
                    ".oRPoWWWWWWWWo..",
                    "..oRPCCBBBBBBWo.",
                    "..oWoCCBBBBBbWo.",
                    "..oWCCBBBBBBbWo.",
                    "..oWCBBBBBBbbWo.",
                    "..oWCBBBBBBbbWo.",
                    "..oWBBBBBBbbbWo.",
                    "...oWBBBBBbbWo..",
                    "...oWBBBBbbbWo..",
                    "....oWBBbbbWo...",
                    ".....oWbbbWo....",
                    "......oWWWo.....",
                    ".......ooo......"},
            "jump_pack", new String[] {
                    "...........o....",
                    "..........oWo...",
                    "..........oWWo..",
                    "..........oWWWo.",
                    "........ooWWWWWo",
                    ".......oWWWWLLo.",
                    "......oWWLoWLo..",
                    ".....oWWoooWo...",
                    "....oWWo...o....",
                    "....oWWo........",
                    "...oWWo.........",
                    "...oWWo.........",
                    "...oWWo.........",
                    "...oWWo.........",
                    "..oOYOo.........",
                    "...oOo.........."},
            "med_station", new String[] {
                    "................",
                    "......oooo......",
                    ".....oHGGgo.....",
                    "..o..oGGGgo..o..",
                    ".oGo.oGGGgo.oGo.",
                    "oGo.ooGGGgoo.oGo",
                    "oGooHGGGGGGgooGo",
                    "oGooGGGGGGGgooGo",
                    "oGo.ogggGggg.oGo",
                    ".oGo.ooGGgoo.oGo",
                    "..o...oGGgo.oGo.",
                    "......ogggo..o..",
                    "....oooooooo....",
                    "...oLLLLLLSSo...",
                    "...oSSSSSDDDo...",
                    "....oooooooo...."},
            "siege_mode", new String[] {
                    "................",
                    "................",
                    "...ooooooo......",
                    "..oWWWWWLSoooooo",
                    "..oWLLLLLSWWWWWo",
                    "..oWLRLLLSoooooo",
                    "..oWLLLLLSWWWWWo",
                    "..oLSSSSSDoooooo",
                    "...ooDDDoo......",
                    "....oSDSo.......",
                    "...oSDDDSo......",
                    "..ooooooooo.....",
                    ".oLLLLLLLLLo....",
                    ".oSSSSSSSSDo....",
                    "..ooooooooo.....",
                    "................"},
            "mining_charge", new String[] {
                    "..........Y.....",
                    ".........YoY....",
                    "........oYYo....",
                    ".......o.Y......",
                    "......o.........",
                    "....oo.oooo.....",
                    "..ooooooMMoooo..",
                    ".oMMMMMMMMMMNNo.",
                    ".oMNNNNNNNNNNno.",
                    ".oNNoooooNNNNno.",
                    ".oNNoRRroNNNNno.",
                    ".oNNoRrroNNNNno.",
                    ".oNNoooooNNNNno.",
                    ".oNNNNNNNNNNNno.",
                    ".onnnnnnnnnnnno.",
                    "..oooooooooooo.."},
            "force_push", new String[] {
                    "................",
                    "....oo..........",
                    "...oWWoo....o...",
                    "...oWLWWoo...Bo.",
                    "..ooWLWLWWo.o.Bo",
                    ".oWWWLWLWLo..o.B",
                    ".oWLWWLWLWo.Bo.B",
                    ".oWLWLWLWLo.Bo.B",
                    ".oWLWLWLWLo.Bo.B",
                    "..oWWWWWWWo.Bo.B",
                    "..oWWWWWWLo..o.B",
                    "...oWWWWLLo.o.Bo",
                    "...oWWWLLSo..Bo.",
                    "....oLLLSo..o...",
                    ".....ooooo......",
                    "................"},
            "saber_throw", new String[] {
                    ".............o..",
                    ".....oo.....oCo.",
                    "....oCCo...oBWCo",
                    "...oCoo...oBWBo.",
                    "..oCo....oBWBo..",
                    ".oCo....oBWBo...",
                    ".oCo...oBWBo....",
                    "oCo...oBWBo.....",
                    "oCo..oBWBo......",
                    "oCo.oDSBo.......",
                    ".o.oLSDo........",
                    "..oLSSo.........",
                    "..oDSo..........",
                    "...oo...........",
                    "................",
                    "................"},
            "force_grip", new String[] {
                    "................",
                    ".......oo.......",
                    "......oWWo......",
                    "..o..oWLLSo..o..",
                    ".oVo.oVVVVo.oVo.",
                    ".oVo.oUVVvo.oVo.",
                    "oVo.ooWLLSoo.oVo",
                    "oVo.oWWLLLSo.oVo",
                    "oVo.oWoLLoSo.oVo",
                    "oVo.oWoLSoSo.oVo",
                    ".oVo.ooLSoo.oVo.",
                    ".oVo..oLSo..oVo.",
                    "..o..oLooSo..o..",
                    ".....oLo.So.....",
                    ".....ooo.oo.....",
                    "................"},
            "force_lightning", new String[] {
                    "........oooooo..",
                    ".......oUUVVvo..",
                    "......oUVVvoo...",
                    ".....oUVVvo.....",
                    "....oUVVvoooooo.",
                    "...oUVVVVVVVVvo.",
                    "..oUUUVVVVVVvo..",
                    "..oooooVVVvoo...",
                    "......oVVvo.....",
                    ".....oVVvo......",
                    "....oVVvo.......",
                    "...oVvo.....o...",
                    "..oVvo.....oVo..",
                    "..oVo.....oVo...",
                    "..oo.......o....",
                    "................"},
            "jetpack", new String[] {
                    "....o......o....",
                    "...oRo....oRo...",
                    "..oWRWo..oWRWo..",
                    "..oWLSo..oWLSo..",
                    ".oWWLLSooWWLLSo.",
                    ".oWLLLSDDWLLLSo.",
                    ".oWLLLSDDWLLLSo.",
                    ".oWLLLSKKWLLLSo.",
                    ".oWLLLSDDWLLLSo.",
                    ".oWLLLSDDWLLLSo.",
                    ".oLSSSDooLSSSDo.",
                    "..oDDDo..oDDDo..",
                    "..oOYOo..oOYOo..",
                    "...oYo....oYo...",
                    "...oOo....oOo...",
                    "....o......o...."},
            "wrist_rocket", new String[] {
                    "................",
                    "................",
                    "...........oo...",
                    "..........oRRo..",
                    ".........oWWRo..",
                    "........oWWLo...",
                    ".......oRRLo....",
                    "....oooWWro.....",
                    "...oSSWWLo......",
                    "....oWWLo.......",
                    "...oOYLSo.......",
                    "..oOYOoSo.......",
                    "..oOOo.o........",
                    "...oo...........",
                    "................",
                    "................"},
            "grapple", new String[] {
                    ".........oo.....",
                    "........oWLo....",
                    ".......oWooLo...",
                    ".......oLooLo...",
                    "......oNoLLo....",
                    ".....oNooWSo....",
                    "....oNo.oWSo....",
                    "...oNo..oWSo....",
                    "..oNoo..oWSo..oo",
                    ".oNoLWo.oWSo.oSD",
                    "oNo.oWo.oWSo.oSo",
                    "No..oWLooWSooDSo",
                    "o....oWooWSooSo.",
                    ".....oLWWWSSSDo.",
                    "......oooWSooo..",
                    ".........oo....."},
            "orbital_strike", new String[] {
                    ".....oYWWYo.....",
                    ".....oYWWYo.....",
                    ".....oYWWYo.....",
                    ".....oYWWYo.....",
                    ".....oYWWYo.....",
                    ".....oYWWYo.....",
                    ".....oYWWYo.....",
                    "....oYYWWYYo....",
                    "..oooYWWWWYooo..",
                    ".oRRoYWWWWYoRRo.",
                    "oRro.oYYYYo.orRo",
                    "oRo..........oRo",
                    "oRro........orRo",
                    ".oRRooooooooRRo.",
                    "..ooRRRRRRRRoo..",
                    "....oooooooo...."},
            "rally", new String[] {
                    ".oo.............",
                    "oWLo............",
                    "oLSoooooooooo...",
                    ".oSoYYYYYYYYYo..",
                    ".oSoYYYYoYYYYYo.",
                    ".oSoYYYoWoYYYYYo",
                    ".oSoYYoWYWoYYyo.",
                    ".oSoYoWYYYWoyo..",
                    ".oSoYYYYYYyyyo..",
                    ".oSoyyyyyyyyyyo.",
                    ".oSooooooooooo..",
                    ".oSo............",
                    ".oSo............",
                    ".oSo............",
                    ".oDo............",
                    "..o............."},
            "leap", new String[] {
                    "...........oo...",
                    "..........oWWo..",
                    ".........oWWWWo.",
                    ".........oWWWWo.",
                    "..........oWWoLo",
                    "........ooWWLLo.",
                    "BBC...ooLLWLoo..",
                    ".....oLLWWoo....",
                    "....oLooWWo.....",
                    ".BC..ooWWo......",
                    ".....ooLWWoo....",
                    "..oooLLoooWWo...",
                    ".oLLLoo...ooWo..",
                    "B.ooo......oWWo.",
                    "............oo..",
                    "................"});
    private static final Map<Character, Integer> ICON_PALETTE = Map.ofEntries(
            Map.entry('o', 0xFF10141C), Map.entry('W', 0xFFF4F7FB), Map.entry('L', 0xFFC9D2DE), Map.entry('S', 0xFF8A96A8),
            Map.entry('D', 0xFF4A5466), Map.entry('K', 0xFF262D3A), Map.entry('R', 0xFFFF5040), Map.entry('r', 0xFFB0281E),
            Map.entry('P', 0xFFFFB4A4), Map.entry('O', 0xFFFF9D2E), Map.entry('Y', 0xFFFFE27A), Map.entry('y', 0xFFC88A1C),
            Map.entry('G', 0xFF6FF08A), Map.entry('g', 0xFF2E9A4A), Map.entry('H', 0xFFD2FFDA), Map.entry('B', 0xFF4AA8FF),
            Map.entry('b', 0xFF1E5FB8), Map.entry('C', 0xFFBDE6FF), Map.entry('V', 0xFFC09CFF), Map.entry('v', 0xFF7448C8),
            Map.entry('U', 0xFFEFE6FF), Map.entry('N', 0xFFC07A40), Map.entry('n', 0xFF7A4522), Map.entry('M', 0xFFF0B070));

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
        int cell = TILE * zoom;
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
                int inset = (TILE - ICON) / 2 * zoom;
                g.drawImage(source.getSubimage(index * ICON, 0, ICON, ICON), left + inset, y + inset, ICON * zoom, ICON * zoom, null);
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
