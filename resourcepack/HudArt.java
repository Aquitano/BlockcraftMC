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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HudArt {

    private static final int PIP = 13;

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
        minimap(root);
        abilities(root);
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
    private static void minimap(Path root) throws Exception {
        ImageIO.write(minimapDisc(), "PNG", root.resolve("minimap_disc.png").toFile());
        ImageIO.write(minimapMarks(), "PNG", root.resolve("minimap_marks.png").toFile());
        ImageIO.write(minimapTarget(), "PNG", root.resolve("minimap_target.png").toFile());
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
                topAligned("minimap_classes.png", 7, run(0xE040, CLASS_MARKS.length)),
                topAligned("labels.png", 7, LABEL_LETTERS),
                topAligned("minimap_underscore.png", 7, "_")) + "\n  ]\n}\n");
    }

    private static final int MAP_STRIP = 16;
    private static final int MAP_STRIPS = 4;
    private static final int MAP_CENTRE = 31;
    private static final int MARK = 9;
    private static final int HEADINGS = 16;
    private static final int MARKS = HEADINGS + 4;
    private static final int TARGET = 13;
    private static final int TARGET_ARM = 7;
    /** PackArt's label letters, in the order its sheet draws them. */
    private static final String LABEL_LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789ÄÖÜ-.:!?…/+&";
    private static final String NORTH = "10011101101110011001";
    /** Soldier, heavy, scout, medic and hero, five rows of five. */
    private static final String[] CLASS_MARKS = {
            "0010000100011100111011111", "1111111111111110111000100", "0010001010100010101000100",
            "0010000100111110010000100", "0010001110111110111001010"};

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

    /**
     * The ability tiles' font in the screen's bottom-right corner: a tile, an icon for every ability in the order of
     * Sternenfront's {@code AbilityIcon}, two keycaps, the cooldown's digits and the keys' symbols, and the small digits
     * of a charge count. Every glyph hangs from the line's top, which the text shader moves into the corner.
     */
    private static void abilities(Path root) throws Exception {
        ImageIO.write(abilityTile(), "PNG", root.resolve("ability_tile.png").toFile());
        var icons = new BufferedImage(ICON * ABILITY_ICONS.size(), ICON, BufferedImage.TYPE_INT_ARGB);
        int left = 0;
        for (var art : ABILITY_ICONS.values()) {
            drawIcon(icons, left, art);
            padAdvance(icons, left + ICON - 1, 0, ICON);
            left += ICON;
        }
        ImageIO.write(icons, "PNG", root.resolve("ability_icons.png").toFile());
        ImageIO.write(abilityKeys(), "PNG", root.resolve("ability_keys.png").toFile());
        ImageIO.write(pixelRow(SYMBOLS, 5, 0xFFFFFFFF), "PNG", root.resolve("ability_symbols.png").toFile());
        ImageIO.write(outlinedCounts(), "PNG", root.resolve("ability_counts.png").toFile());
        Files.writeString(root.getParent().getParent().resolve("font/abilities.json"), String.join(",\n    ",
                "{\n  \"providers\": [",
                topAligned("ability_tile.png", TILE, run(0xE000, 1)),
                topAligned("ability_icons.png", ICON, run(0xE010, ABILITY_ICONS.size())),
                topAligned("ability_keys.png", KEY_HEIGHT, run(0xE030, KEY_WIDTHS.length)),
                topAligned("ability_symbols.png", 7, run(0xE040, SYMBOLS.length)),
                topAligned("ability_counts.png", 7, run(0xE050, COUNTS.length))).replace("[,", "[") + "\n  ]\n}\n");
    }

    private static final int TILE = 20;
    private static final int ICON = 16;
    private static final int[] KEY_WIDTHS = {9, 15};
    private static final int KEY_HEIGHT = 9;
    private static final int OUTLINE = 0xFF0F141D;

    /** A rounded tile in light grey on dark glass, which the shader dims while its ability cools down. */
    private static BufferedImage abilityTile() {
        var image = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < TILE; y++) {
            for (int x = 0; x < TILE; x++) {
                boolean edgeX = x == 0 || x == TILE - 1;
                boolean edgeY = y == 0 || y == TILE - 1;
                if (edgeX && edgeY) {
                    continue;
                }
                boolean rim = edgeX || edgeY;
                boolean bevel = !rim && (x == 1 || y == 1 || x == TILE - 2 || y == TILE - 2);
                image.setRGB(x, y, rim ? 0xFFB8C4D6 : bevel ? 0xE0263042 : 0xC8101722);
            }
        }
        return image;
    }

    /** A keycap for one symbol and one for two, light with a dark rim so a dark letter reads on any sky. */
    private static BufferedImage abilityKeys() {
        var image = new BufferedImage(KEY_WIDTHS[1] * KEY_WIDTHS.length, KEY_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        for (int key = 0; key < KEY_WIDTHS.length; key++) {
            int width = KEY_WIDTHS[key];
            for (int y = 0; y < KEY_HEIGHT; y++) {
                for (int x = 0; x < width; x++) {
                    boolean rim = x == 0 || y == 0 || x == width - 1 || y == KEY_HEIGHT - 1;
                    boolean corner = (x == 0 || x == width - 1) && (y == 0 || y == KEY_HEIGHT - 1);
                    if (!corner) {
                        image.setRGB(key * KEY_WIDTHS[1] + x, y, rim ? OUTLINE : y == KEY_HEIGHT - 2 ? 0xFFAEB8C6 : 0xFFE6ECF4);
                    }
                }
            }
        }
        return image;
    }

    /** The charge count's digits and slash, three pixels by five in white, ringed dark so they read over any icon. */
    private static BufferedImage outlinedCounts() {
        var image = new BufferedImage(5 * COUNTS.length, 7, BufferedImage.TYPE_INT_ARGB);
        for (int glyph = 0; glyph < COUNTS.length; glyph++) {
            for (int y = 0; y < 7; y++) {
                for (int x = 0; x < 5; x++) {
                    if (countInk(glyph, x - 1, y - 1)) {
                        image.setRGB(glyph * 5 + x, y, 0xFFFFFFFF);
                    } else if (nearCountInk(glyph, x - 1, y - 1)) {
                        image.setRGB(glyph * 5 + x, y, OUTLINE);
                    }
                }
            }
        }
        return image;
    }

    private static boolean nearCountInk(int glyph, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (countInk(glyph, x + dx, y + dy)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean countInk(int glyph, int x, int y) {
        return x >= 0 && y >= 0 && x < 3 && y < 5 && COUNTS[glyph][y].charAt(x) == '#';
    }

    /** Glyphs drawn as rows of {@code #}, each {@code width} wide, in one colour; the last column fixes every advance. */
    private static BufferedImage pixelRow(String[][] glyphs, int width, int colour) {
        var image = new BufferedImage(width * glyphs.length, glyphs[0].length, BufferedImage.TYPE_INT_ARGB);
        for (int glyph = 0; glyph < glyphs.length; glyph++) {
            for (int y = 0; y < glyphs[glyph].length; y++) {
                for (int x = 0; x < width; x++) {
                    if (glyphs[glyph][y].charAt(x) == '#') {
                        image.setRGB(glyph * width + x, y, colour);
                    }
                }
            }
            padAdvance(image, glyph * width + width - 1, 0, glyphs[glyph].length);
        }
        return image;
    }

    /** {@code art}'s letters in their colours at {@code left}, ringed by a dark outline wherever ink meets a clear side. */
    private static void drawIcon(BufferedImage image, int left, String[] art) {
        for (int y = 0; y < ICON; y++) {
            for (int x = 0; x < ICON; x++) {
                char ink = art[y].charAt(x);
                if (ink != '.') {
                    image.setRGB(left + x, y, INK.get(ink));
                }
            }
        }
        var rim = new ArrayList<int[]>();
        for (int y = 0; y < ICON; y++) {
            for (int x = 0; x < ICON; x++) {
                if (art[y].charAt(x) == '.' && (inked(art, x - 1, y) || inked(art, x + 1, y) || inked(art, x, y - 1) || inked(art, x, y + 1))) {
                    rim.add(new int[] {x, y});
                }
            }
        }
        rim.forEach(texel -> image.setRGB(left + texel[0], texel[1], OUTLINE));
    }

    private static boolean inked(String[] art, int x, int y) {
        return x >= 0 && y >= 0 && x < ICON && y < ICON && art[y].charAt(x) != '.';
    }

    private static final Map<Character, Integer> INK = Map.ofEntries(
            Map.entry('k', OUTLINE), Map.entry('w', 0xFFFFFFFF), Map.entry('l', 0xFFD9E0EA), Map.entry('m', 0xFF9AA6B8),
            Map.entry('d', 0xFF5A667A), Map.entry('e', 0xFF323B4C), Map.entry('r', 0xFFFF5B4B), Map.entry('R', 0xFFB0342C),
            Map.entry('o', 0xFFFFA53A), Map.entry('y', 0xFFFFE680), Map.entry('g', 0xFF74E37E), Map.entry('G', 0xFF2E9C47),
            Map.entry('c', 0xFFA6F3FF), Map.entry('C', 0xFF3FC3EE), Map.entry('B', 0xFF2273B8), Map.entry('p', 0xFFE0C4FF),
            Map.entry('P', 0xFF9B6BFF), Map.entry('V', 0xFF5C37C0), Map.entry('u', 0xFFFFD447), Map.entry('U', 0xFFC48A12),
            Map.entry('n', 0xFFA8743F), Map.entry('N', 0xFF6A4524));

    /** One icon per ability on a native 16-pixel grid, lit from the upper left; a dark outline is added round each. */
    private static final Map<String, String[]> ABILITY_ICONS = linked(
            "grenade", new String[] {
                    "................",
                    ".......lm.......",
                    ".......md.......",
                    ".....mlllmd.....",
                    "....mlwwllmd....",
                    "...mlwwlllmmd...",
                    "...llwllllmmd...",
                    "..eeeeeeeeeeee..",
                    "..eoeyeoeyeoee..",
                    "..mmmmmmmmmddd..",
                    "...mmmmmmmddd...",
                    "...dmmmmmdddd...",
                    "....ddmmdddd....",
                    ".....dddddd.....",
                    "................",
                    "................"},
            "adrenaline", new String[] {
                    "................",
                    "............rl..",
                    "...........rrm..",
                    "..........wrR...",
                    ".........wlRR...",
                    "........wlmR....",
                    ".......wgmd.....",
                    "......wgGd......",
                    ".....wgGd.......",
                    "....wgGd........",
                    "....lGd.........",
                    "...dmd..........",
                    "...dd...........",
                    "..l.............",
                    ".l..............",
                    "................"},
            "shield_wall", new String[] {
                    "................",
                    "................",
                    "..cccccccccccC..",
                    "..cwcccccccCCB..",
                    "..ccCCCCCCCCCB..",
                    "..cCCCCCCCCCCB..",
                    "..cCcccccccCCB..",
                    "..cCCCCCCCCCBB..",
                    "..cCCCCCCCCCBB..",
                    "...cCcccccCBB...",
                    "...cCCCCCCCBB...",
                    "....cCCCCCBB....",
                    ".....cCCCBB.....",
                    "......cCBB......",
                    ".......BB.......",
                    "................"},
            "jump_pack", new String[] {
                    "................",
                    "................",
                    ".......wwwwwww..",
                    "........wllllm..",
                    ".........wlllm..",
                    ".........wlllm..",
                    "........wllmlm..",
                    ".......wlm..mm..",
                    "......wlm....m..",
                    ".....wlm........",
                    "....wlm.........",
                    "...wlm..........",
                    ".oolm...........",
                    ".yym............",
                    ".oyo............",
                    "................"},
            "med_station", new String[] {
                    "................",
                    "......wwww......",
                    "......wggw......",
                    "......wggw......",
                    "...wwwwggwwww...",
                    "...wggggggggw...",
                    "...wgggggggGw...",
                    "...wwwwggwwww...",
                    "......wgGw......",
                    "......wgGw......",
                    "......wwww......",
                    "................",
                    "...llllllllll...",
                    "..mmmmmmmmmmmm..",
                    "..dddddddddddd..",
                    "................"},
            "siege_mode", new String[] {
                    "................",
                    "................",
                    "............dd..",
                    ".llllllllllldd..",
                    ".mmmmmmmmmmmddy.",
                    "...dmmmmd...dd..",
                    "...deeeed.......",
                    "....dddd........",
                    ".....dd.........",
                    "....dddd........",
                    "...dd.dd.d......",
                    "..dd..dd..d.....",
                    ".dd...dd...d....",
                    ".mmm.mmmm.mmm...",
                    "................",
                    "................"},
            "mining_charge", new String[] {
                    "................",
                    "...........r....",
                    "...........d....",
                    "..........d.....",
                    "..........d.....",
                    "..llllllllllll..",
                    "..lrlmmmmmmmmd..",
                    "..lmmmmmmmmmmd..",
                    "..yyeeyyeeyyed..",
                    "..yeeyyeeyyeed..",
                    "..lmmmmmmmmmmd..",
                    "..mmmmmmmmmmmd..",
                    "..dddddddddddd..",
                    "................",
                    "................",
                    "................"},
            "force_push", new String[] {
                    "................",
                    "...l.l..........",
                    "..ll.ll.l.......",
                    "..ll.ll.ll...c..",
                    "..ll.ll.ll....c.",
                    "..lllllllll...c.",
                    ".llllllllll.c.c.",
                    ".lwlllllllm.c.c.",
                    ".llllllllmm.c.c.",
                    "..lllllllmm...c.",
                    "...llllmmm....c.",
                    "....lmmmm....c..",
                    "................",
                    "................",
                    "................",
                    "................"},
            "saber_throw", new String[] {
                    "................",
                    ".............c..",
                    "............cwc.",
                    "...........cwc..",
                    "..........cwc...",
                    ".........cwc..l.",
                    "........cwc...l.",
                    ".......cwc....l.",
                    "......cwc.....l.",
                    ".....cwc.....l..",
                    "....dwc.....ll..",
                    "...mkd......l...",
                    "..mkd.....ll....",
                    "..dd.....ll.....",
                    "....llll........",
                    "................"},
            "force_grip", new String[] {
                    "................",
                    ".....pppppp.....",
                    "...ppp....ppp...",
                    "..pp........PP..",
                    "..p....ww....P..",
                    ".pp....ww....PP.",
                    ".p....wwww....P.",
                    ".p...w.ww.w...P.",
                    ".p.....ww.....P.",
                    ".p....w..w....P.",
                    ".pp...w..w...PP.",
                    "..p..........P..",
                    "..pp........PP..",
                    "...PPP....PPP...",
                    ".....PPPPPP.....",
                    "................"},
            "force_lightning", new String[] {
                    "................",
                    "........ppppp...",
                    ".......pwwwP....",
                    "......pwwwP.....",
                    ".....pwwwP......",
                    "....pwwwwpppp...",
                    "...pppppwwwwP...",
                    "........pwwP....",
                    ".......pwwP.....",
                    "......pwwP......",
                    ".....pwwP.......",
                    "....pwP.........",
                    "...pP...........",
                    "..P.............",
                    "................",
                    "................"},
            "jetpack", new String[] {
                    "................",
                    "....ll....ll....",
                    "...llll..llll...",
                    "...lwlm..lwlm...",
                    "...lwlmddlwlm...",
                    "...lllmddlllm...",
                    "...mmmmddmmmm...",
                    "...mmmmddmmmm...",
                    "...dmmdddmmmd...",
                    "...ddddddddd....",
                    "....dd...dd.....",
                    "....oo...oo.....",
                    "....yo...yo.....",
                    "....oy...oy.....",
                    ".....o....o.....",
                    "................"},
            "wrist_rocket", new String[] {
                    "................",
                    "................",
                    "................",
                    "................",
                    ".....dd.........",
                    ".....dlllllllr..",
                    "..oy.wllllllllr.",
                    ".oyy.mmmmmmmmRR.",
                    "..oy.dmmmmmmmR..",
                    ".....dd.........",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................"},
            "grapple", new String[] {
                    "................",
                    ".nN.............",
                    "..nN............",
                    "...nN...........",
                    "....nN...mm.....",
                    ".....nNNlm.m....",
                    ".........mmm....",
                    ".........l......",
                    ".........l......",
                    ".........l......",
                    "...l.....l.....l",
                    "...l....lll....l",
                    "...ll..lmlml..ll",
                    "....lllm...mlll.",
                    ".....mm.....mm..",
                    "................"},
            "orbital_strike", new String[] {
                    "......oyyo......",
                    "......oyyo......",
                    "......oyyo......",
                    "......oyyo......",
                    "......oyyo......",
                    "......oyyo......",
                    ".....ooyyoo.....",
                    ".....oywwyo.....",
                    "..rrroywwyoRRR..",
                    ".r...ooyyoo...R.",
                    "r......oo......R",
                    "r..............R",
                    ".r............R.",
                    "..rrr......RRR..",
                    ".....RRRRRR.....",
                    "................"},
            "rally", new String[] {
                    "................",
                    "..l.............",
                    "..luuuuuu.......",
                    "..luuuuuuuuu....",
                    "..luuUuuuuuuuu..",
                    "..luuUUuuuuuu...",
                    "..luuUuuuuuUU...",
                    "..luuuuuuUU.....",
                    "..lUUU..........",
                    "..m.............",
                    "..m.............",
                    "..m.............",
                    "..m.............",
                    ".dmd............",
                    ".ddd............",
                    "................"},
            "leap", new String[] {
                    "................",
                    "................",
                    ".......ww.......",
                    "......wwww......",
                    ".....ww..ww.....",
                    "....ww....ww....",
                    "...ww......ww...",
                    "................",
                    ".......cc.......",
                    "......cccc......",
                    ".....cc..cc.....",
                    "....cc....cc....",
                    "...cc......cc...",
                    "................",
                    "................",
                    "................"});

    private static Map<String, String[]> linked(Object... namesAndArt) {
        var art = new LinkedHashMap<String, String[]>();
        for (int index = 0; index < namesAndArt.length; index += 2) {
            var rows = (String[]) namesAndArt[index + 1];
            if (rows.length != ICON || Arrays.stream(rows).anyMatch(row -> row.length() != ICON)) {
                throw new IllegalStateException(namesAndArt[index] + " is not 16 by 16");
            }
            art.put((String) namesAndArt[index], rows);
        }
        return art;
    }

    /** Zero to nine and a slash, three pixels wide. */
    private static final String[][] COUNTS = {
            {"###", "#.#", "#.#", "#.#", "###"}, {".#.", "##.", ".#.", ".#.", "###"}, {"###", "..#", "###", "#..", "###"},
            {"###", "..#", ".##", "..#", "###"}, {"#.#", "#.#", "###", "..#", "..#"}, {"###", "#..", "###", "..#", "###"},
            {"###", "#..", "###", "#.#", "###"}, {"###", "..#", "..#", ".#.", ".#."}, {"###", "#.#", "###", "#.#", "###"},
            {"###", "#.#", "###", "..#", "###"}, {"..#", "..#", ".#.", "#..", "#.."}};

    /**
     * The cooldown's digits, five pixels wide like the vanilla ones, then F, Q, shift as an outlined arrow up, and two
     * chevrons for the double jump.
     */
    private static final String[][] SYMBOLS = {
            {".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."},
            {"..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."},
            {".###.", "#...#", "....#", "..##.", ".#...", "#....", "#####"},
            {".###.", "#...#", "....#", "..##.", "....#", "#...#", ".###."},
            {"...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."},
            {"#####", "#....", "####.", "....#", "....#", "#...#", ".###."},
            {"..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."},
            {"#####", "....#", "...#.", "..#..", "..#..", "..#..", "..#.."},
            {".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."},
            {".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."},
            {"#####", "#....", "#....", "####.", "#....", "#....", "#...."},
            {".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"},
            {"..#..", ".#.#.", "#...#", "##.##", ".#.#.", ".#.#.", ".###."},
            {"..#..", ".#.#.", "#...#", ".....", "..#..", ".#.#.", "#...#"}};
}
