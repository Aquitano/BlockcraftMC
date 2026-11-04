import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PropArt {
    private record Surface(String name, int color, String pattern) { }
    private record Box(double x1, double y1, double z1, double x2, double y2, double z2, String surface) { }
    private record Prop(String id, String name, List<Box> boxes) { }
    private record Point(double x, double y, double z) { }
    private record Face(List<Point> points, String surface, double shade) {
        double depth() { return points.stream().mapToDouble(p -> p.x - p.z + p.y * .2).average().orElseThrow(); }
    }

    private static final List<Surface> SURFACES = List.of(
            new Surface("timber", 0x60462F, "wood"), new Surface("dark_timber", 0x352C26, "wood"),
            new Surface("brass", 0xB99448, "metal"), new Surface("iron", 0x727D80, "metal"),
            new Surface("canvas", 0xD3C39A, "weave"), new Surface("green", 0x536C48, "weave"),
            new Surface("red", 0x9C463D, "weave"), new Surface("snow", 0xE0ECEA, "plain"),
            new Surface("stone", 0x77796F, "stone"), new Surface("glow", 0xE9C06B, "plain"),
            new Surface("glass", 0x7EB4AD, "gem"),
            new Surface("leaf", 0x365A3E, "leaf"), new Surface("orange", 0xBE773A, "weave"),
            new Surface("paper", 0xDFD1A6, "paper"), new Surface("cocoa", 0x583A2B, "plain"),
            new Surface("biscuit", 0xC08E54, "stone"), new Surface("pink", 0xCB8D9C, "plain"));

    public static void main(String[] args) throws IOException {
        Locale.setDefault(Locale.ROOT);
        Path root = Path.of(args.length > 0 ? args[0] : "src");
        Path nexo = Path.of(args.length > 1 ? args[1] : "../nexo");
        var props = props();
        var textures = new LinkedHashMap<String, BufferedImage>();
        Path assets = root.resolve("assets/blockcraft");
        Files.createDirectories(assets.resolve("textures/item/props"));
        Files.createDirectories(assets.resolve("models/item/props"));
        Files.createDirectories(assets.resolve("items/props"));
        Files.createDirectories(nexo.resolve("items"));
        for (var surface : SURFACES) {
            var texture = texture(surface);
            textures.put(surface.name, texture);
            ImageIO.write(texture, "PNG", assets.resolve("textures/item/props/" + surface.name + ".png").toFile());
        }
        var config = new StringBuilder();
        for (var prop : props) {
            validate(prop);
            Files.writeString(assets.resolve("models/item/props/" + prop.id + ".json"), model(prop));
            Files.writeString(assets.resolve("items/props/" + prop.id + ".json"), """
                    {
                      "model": {
                        "type": "minecraft:model",
                        "model": "blockcraft:item/props/%s"
                      }
                    }
                    """.formatted(prop.id));
            config.append(nexo(prop));
        }
        Files.writeString(nexo.resolve("items/blockcraft_props.yml"), config.toString());
        if (args.length > 2) {
            Path preview = Path.of(args[2]);
            Files.createDirectories(preview.toAbsolutePath().getParent());
            ImageIO.write(preview(props, textures), "PNG", preview.toFile());
        }
        System.out.println("Generated " + props.size() + " props and " + SURFACES.size() + " textures.");
    }

    private static List<Prop> props() {
        var result = new ArrayList<Prop>();
        var b = new ArrayList<Box>();
        box(b, 4, 0, 4, 12, 2, 12, "brass");
        box(b, 5, 2, 5, 11, 11, 11, "glow");
        for (int x : new int[]{4, 11}) for (int z : new int[]{4, 11}) box(b, x, 2, z, x + 1, 12, z + 1, "dark_timber");
        box(b, 3, 12, 3, 13, 14, 13, "brass");
        box(b, 5, 14, 5, 11, 16, 11, "dark_timber");
        box(b, 6, 16, 7, 7, 20, 9, "brass");
        box(b, 9, 16, 7, 10, 20, 9, "brass");
        box(b, 6, 19, 7, 10, 20, 9, "brass");
        box(b, 6.5, 5, 3.4, 8, 10, 4, "brass");
        box(b, 5, 6.5, 3.4, 10, 8, 4, "brass");
        add(result, "windmill_lantern", "Mühlenlaterne", b);

        b = new ArrayList<>();
        sack(b, 2, 0, 3, 8, 11, "canvas"); sack(b, 9, 0, 7, 6, 8, "canvas");
        box(b, 4, 3, 2.8, 8, 7, 3, "paper");
        box(b, 5.5, 3.5, 2.6, 6.5, 6.5, 2.8, "brass");
        add(result, "flour_sacks", "Mehlsäcke", b);

        b = new ArrayList<>();
        box(b, -2, 4, 1, 18, 6, 16, "timber");
        box(b, -2, 6, 1, 0, 13, 16, "timber"); box(b, 16, 6, 1, 18, 13, 16, "timber");
        box(b, 0, 6, 14, 16, 13, 16, "timber");
        for (int x : new int[]{-4, 18}) {
            box(b, x, 0, 6, x + 2, 8, 13, "dark_timber");
            box(b, x - .1, 3, 8.5, x + 2.1, 5, 10.5, "brass");
        }
        for (int x : new int[]{0, 14}) box(b, x, 6, -12, x + 2, 8, 2, "timber");
        sack(b, 1, 6, 3, 7, 9, "canvas"); sack(b, 9, 6, 6, 6, 7, "green");
        box(b, 2, 14, 5, 7, 15, 8, "brass");
        add(result, "grain_cart", "Getreidekarren", b);

        b = new ArrayList<>();
        box(b, -5, 0, 1, 21, 13, 15, "timber");
        box(b, -6, 13, 0, 22, 15, 16, "dark_timber");
        for (int x : new int[]{-5, 19}) box(b, x, 15, 12, x + 2, 29, 14, "timber");
        for (int x = -7; x < 23; x += 5) box(b, x, 28, -1, x + 5, 30, 17, x % 2 == 0 ? "canvas" : "green");
        for (int x = -7; x < 23; x += 5) box(b, x, 25, -1, x + 5, 28, 1, x % 2 == 0 ? "canvas" : "green");
        box(b, -3, 2, .7, 19, 4, 1, "brass");
        box(b, -1, 15, 4, 1, 23, 6, "brass"); box(b, -5, 21, 4, 5, 22, 6, "brass");
        box(b, -5, 19, 3, -1, 20, 7, "iron"); box(b, 2, 19, 3, 6, 20, 7, "iron");
        parcel(b, 10, 15, 6, 7, 6, 7, "canvas");
        box(b, 7, 15, 1, 10, 16, 4, "brass");
        add(result, "merchant_stall", "Marktstand", b);

        b = bench(false); add(result, "timber_bench", "Holzbank", b);
        b = bench(true); add(result, "winter_bench", "Verschneite Holzbank", b);

        b = new ArrayList<>();
        box(b, 2, 0, 2, 14, 3, 14, "stone");
        box(b, 5, 3, 5, 11, 16, 11, "dark_timber");
        box(b, 0, 16, 4, 16, 30, 12, "timber");
        box(b, 2, 18, 3.5, 14, 28, 4, "brass");
        box(b, 3, 19, 3, 13, 27, 3.5, "paper");
        box(b, 7.5, 22, 2.5, 8.5, 26, 3, "dark_timber");
        box(b, 8, 22, 2.5, 11, 23, 3, "dark_timber");
        for (int x : new int[]{3, 12}) box(b, x, 22, 2.7, x + 1, 23, 3, "brass");
        box(b, 7.5, 19, 2.7, 8.5, 20, 3, "brass");
        box(b, -1, 30, 3, 17, 32, 13, "brass");
        box(b, 6, 6, 4.5, 10, 12, 5, "brass");
        add(result, "departure_clock", "Abreiseuhr", b);

        b = new ArrayList<>();
        box(b, 1, 0, 3, 15, 1, 13, "timber");
        for (int x : new int[]{3, 8}) {
            box(b, x, 1, 5, x + 3, 5, 9, "canvas");
            box(b, x + .5, 4.7, 5.5, x + 2.5, 5, 8.5, "cocoa");
            box(b, x + 3, 2, 6, x + 4, 4, 8, "brass");
        }
        box(b, 3, 1, 10, 8, 2, 12, "biscuit"); box(b, 6, 2, 10, 10, 3, 12, "biscuit");
        add(result, "cocoa_tray", "Kakao und Kekse", b);

        b = basket();
        for (int x : new int[]{4, 8, 11}) { box(b, x, 5, 6, x + 2, 8, 8, x == 8 ? "pink" : "canvas"); }
        box(b, 5, 6, 10, 6, 13, 11, "leaf"); box(b, 3, 12, 9, 8, 14, 12, "pink");
        box(b, 10, 6, 10, 11, 11, 11, "leaf"); box(b, 9, 11, 9, 13, 13, 12, "glow");
        add(result, "spring_basket", "Frühlingskorb", b);

        b = basket();
        box(b, 3, 5, 4, 9, 11, 10, "orange"); box(b, 5.5, 11, 6.5, 7, 13, 8, "leaf");
        box(b, 9, 5, 8, 14, 10, 13, "orange"); box(b, 11, 10, 10, 12, 12, 11, "leaf");
        add(result, "harvest_basket", "Erntekorb", b);

        b = new ArrayList<>();
        for (int x = -8; x < 24; x += 4) {
            int y = 4 + Math.abs(x - 6) / 4;
            box(b, x, y, 7, x + 4, y + 3, 10, "leaf");
            if (x % 8 == 0) box(b, x + 1, y - 2, 6, x + 3, y, 8, "red");
        }
        box(b, 6, 2, 6, 10, 5, 8, "brass");
        add(result, "garland", "Tannengirlande", b);

        b = new ArrayList<>();
        box(b, 2, 3, 7, 5, 13, 10, "leaf"); box(b, 11, 3, 7, 14, 13, 10, "leaf");
        box(b, 5, 1, 7, 11, 4, 10, "leaf"); box(b, 5, 12, 7, 11, 15, 10, "leaf");
        box(b, 4, 1, 6, 8, 4, 7, "red"); box(b, 8, 1, 6, 12, 4, 7, "red");
        box(b, 7, 0, 5.5, 9, 4, 7, "brass");
        add(result, "wreath", "Türkranz", b);

        b = new ArrayList<>();
        box(b, 0, 13, 7, 16, 15, 10, "snow");
        for (int x : new int[]{1, 5, 10, 14}) {
            int y = x % 3 * 3;
            box(b, x, y + 4, 7, x + 2, 13, 9, "glass");
            box(b, x + .5, y, 7.5, x + 1.5, y + 4, 8.5, "snow");
        }
        add(result, "icicles", "Eiszapfen", b);

        b = new ArrayList<>();
        parcel(b, 0, 0, 3, 9, 8, 9, "red"); parcel(b, 9, 0, 7, 7, 11, 7, "green");
        parcel(b, 2, 8, 5, 5, 5, 5, "canvas");
        add(result, "presents", "Geschenke", b);

        b = new ArrayList<>();
        box(b, 5, 0, 5, 11, 7, 11, "timber");
        box(b, -3, 6, -3, 19, 10, 19, "leaf"); box(b, 0, 10, 0, 16, 16, 16, "leaf");
        box(b, 3, 16, 3, 13, 23, 13, "leaf"); box(b, 5, 23, 5, 11, 28, 11, "leaf");
        box(b, 7, 27, 7, 9, 32, 9, "brass"); box(b, 5, 29, 7, 11, 30, 9, "brass");
        for (int[] p : new int[][]{{1, 11, 0}, {12, 13, 0}, {4, 18, 3}, {9, 22, 3}, {-2, 7, 2}, {17, 7, 12}, {15, 13, 8}}) {
            box(b, p[0], p[1], p[2] - .5, p[0] + 2, p[1] + 2, p[2] + 1.5, p[1] % 2 == 0 ? "red" : "glow");
        }
        parcel(b, -3, 0, -4, 6, 5, 6, "red"); parcel(b, 13, 0, 0, 6, 4, 5, "canvas");
        add(result, "christmas_tree", "Weihnachtsbaum", b);

        b = new ArrayList<>();
        box(b, 0, 0, 4, 16, 2, 13, "timber");
        for (int x : new int[]{1, 13}) box(b, x, 2, 10, x + 2, 20, 12, "dark_timber");
        box(b, 0, 17, 9, 16, 19, 12, "timber");
        for (int x : new int[]{3, 8}) {
            box(b, x, 5, 4, x + 3, 8, 10, "dark_timber");
            box(b, x, 8, 7, x + 3, 12, 10, "canvas");
            box(b, x + 1, 3, 3, x + 2, 5, 11, "iron");
            box(b, x + 1, 12, 9, x + 2, 18, 10, "brass");
        }
        box(b, 13, 2, 2, 14, 23, 3, "timber"); box(b, 11, 0, 1, 16, 5, 2, "iron");
        add(result, "skate_rack", "Schlittschuhe und Schneeschaufel", b);

        b = new ArrayList<>();
        box(b, 1, 0, 2, 15, 7, 13, "timber");
        box(b, 1, 7, 2, 15, 9, 13, "dark_timber");
        for (int x : new int[]{3, 11}) {
            box(b, x, 0, 1.8, x + 1, 9.2, 13.2, "iron");
        }
        box(b, 7, 5, 1.5, 9, 8, 2.2, "brass");
        box(b, 3, 9, 5, 13, 13, 11, "green");
        box(b, 4, 9, 4.8, 5, 13.2, 11.2, "canvas");
        box(b, 11, 9, 4.8, 12, 13.2, 11.2, "canvas");
        box(b, 2, 9, 2, 8, 14, 5, "canvas");
        box(b, 2, 13, 1.8, 8, 14, 5.2, "dark_timber");
        box(b, 3, 14, 3, 4, 16, 4, "dark_timber");
        box(b, 6, 14, 3, 7, 16, 4, "dark_timber");
        box(b, 3, 15, 3, 7, 16, 4, "dark_timber");
        add(result, "luggage", "Reisegepäck", b);
        return List.copyOf(result);
    }

    private static ArrayList<Box> bench(boolean winter) {
        var b = new ArrayList<Box>();
        for (int x : new int[]{-4, 17}) for (int z : new int[]{3, 11}) box(b, x, 0, z, x + 3, 7, z + 3, "dark_timber");
        box(b, -5, 7, 2, 21, 9, 15, "timber");
        box(b, -4, 9, 13, -2, 20, 15, "dark_timber"); box(b, 18, 9, 13, 20, 20, 15, "dark_timber");
        box(b, -4, 12, 13, 20, 15, 15, "timber"); box(b, -4, 17, 13, 20, 20, 15, "timber");
        box(b, -5, 12, 2, -3, 14, 15, "brass"); box(b, 19, 12, 2, 21, 14, 15, "brass");
        if (winter) { box(b, -4, 20, 12.5, 20, 21, 15.5, "snow"); box(b, 13, 9, 3, 20, 10, 13, "snow"); }
        return b;
    }

    private static ArrayList<Box> basket() {
        var b = new ArrayList<Box>();
        box(b, 2, 0, 3, 14, 5, 13, "timber");
        box(b, 1, 4, 2, 15, 6, 4, "brass"); box(b, 1, 4, 12, 15, 6, 14, "brass");
        box(b, 1, 4, 4, 3, 6, 12, "brass"); box(b, 13, 4, 4, 15, 6, 12, "brass");
        return b;
    }

    private static void sack(List<Box> b, double x, double y, double z, double width, double height, String material) {
        box(b, x, y, z, x + width, y + height - 2, z + width, material);
        box(b, x + 1, y + height - 2, z + 1, x + width - 1, y + height - 1, z + width - 1, "dark_timber");
        box(b, x + 2, y + height - 1, z + 2, x + width - 2, y + height + 1, z + width - 2, material);
    }

    private static void parcel(List<Box> b, double x, double y, double z, double w, double h, double d, String material) {
        box(b, x, y, z, x + w, y + h, z + d, material);
        box(b, x + w / 2 - .5, y, z - .1, x + w / 2 + .5, y + h + .1, z + d + .1, "brass");
        box(b, x - .1, y + h / 2 - .5, z - .1, x + w + .1, y + h / 2 + .5, z + d + .1, "brass");
    }

    private static void add(List<Prop> result, String id, String name, List<Box> boxes) { result.add(new Prop(id, name, List.copyOf(boxes))); }
    private static void box(List<Box> boxes, double x1, double y1, double z1, double x2, double y2, double z2, String surface) {
        boxes.add(new Box(x1, y1, z1, x2, y2, z2, surface));
    }

    private static void validate(Prop prop) {
        if (prop.boxes.isEmpty()) throw new IllegalArgumentException(prop.id);
        for (var b : prop.boxes) {
            if (!(b.x1 < b.x2 && b.y1 < b.y2 && b.z1 < b.z2)) throw new IllegalArgumentException(prop.id + ": empty cuboid " + b);
            for (double n : new double[]{b.x1, b.y1, b.z1, b.x2, b.y2, b.z2}) {
                if (n < -16 || n > 32) throw new IllegalArgumentException(prop.id + ": outside Java model bounds");
            }
            if (SURFACES.stream().noneMatch(s -> s.name.equals(b.surface))) throw new IllegalArgumentException(prop.id + ": unknown texture " + b.surface);
        }
    }

    private static String model(Prop prop) {
        var out = new StringBuilder("{\n  \"ambientocclusion\": true,\n  \"textures\": {\n    \"particle\": \"blockcraft:item/props/timber\"");
        prop.boxes.stream().map(Box::surface).distinct().sorted().forEach(name -> out.append(",\n    \"").append(name)
                .append("\": \"blockcraft:item/props/").append(name).append('"'));
        out.append("\n  },\n  \"display\": {\n    \"gui\": {\"rotation\": [25, 225, 0], \"translation\": [0, -2, 0], \"scale\": [0.4, 0.4, 0.4]},\n")
                .append("    \"ground\": {\"translation\": [0, 3, 0], \"scale\": [0.25, 0.25, 0.25]},\n")
                .append("    \"fixed\": {\"translation\": [0, 0, 0], \"scale\": [1, 1, 1]}\n  },\n  \"elements\": [\n");
        var elements = new ArrayList<String>();
        for (var b : prop.boxes) {
            var faces = new ArrayList<String>();
            for (String face : List.of("north", "south", "east", "west", "up", "down")) {
                double width = face.equals("east") || face.equals("west") ? b.z2 - b.z1 : b.x2 - b.x1;
                double height = face.equals("up") || face.equals("down") ? b.z2 - b.z1 : b.y2 - b.y1;
                faces.add("        \"%s\": {\"uv\": [0, 0, %s, %s], \"texture\": \"#%s\"}".formatted(face, num(Math.min(16, width)), num(Math.min(16, height)), b.surface));
            }
            elements.add("    {\n      \"from\": [%s, %s, %s],\n      \"to\": [%s, %s, %s],\n      \"faces\": {\n%s\n      }\n    }".formatted(
                    num(b.x1), num(b.y1), num(b.z1), num(b.x2), num(b.y2), num(b.z2), String.join(",\n", faces)));
        }
        return out.append(String.join(",\n", elements)).append("\n  ]\n}\n").toString();
    }

    private static String nexo(Prop prop) {
        double height = prop.boxes.stream().mapToDouble(Box::y2).max().orElse(16) / 16;
        String material = switch (prop.id) {
            case "windmill_lantern" -> "LANTERN";
            case "grain_cart" -> "MINECART";
            case "flour_sacks" -> "WHEAT";
            case "merchant_stall" -> "BARREL";
            case "timber_bench", "winter_bench" -> "OAK_STAIRS";
            case "departure_clock" -> "CLOCK";
            case "presents", "luggage" -> "CHEST";
            case "cocoa_tray" -> "COOKIE";
            case "spring_basket" -> "FLOWER_POT";
            case "harvest_basket" -> "CARVED_PUMPKIN";
            case "garland", "wreath" -> "SPRUCE_LEAVES";
            case "icicles" -> "PACKED_ICE";
            case "christmas_tree" -> "SPRUCE_SAPLING";
            case "skate_rack" -> "LEATHER_BOOTS";
            default -> throw new IllegalArgumentException("Missing vanilla furniture material: " + prop.id);
        };
        String barriers = switch (prop.id) {
            case "merchant_stall" -> "        barriers:\n          - 0,0,0\n";
            case "christmas_tree" -> "        barriers:\n          - 0,0,0\n          - 0,1,0\n";
            default -> "";
        };
        String seats = switch (prop.id) {
            case "timber_bench" -> "      seats:\n        - -0.4,0.0625,0\n        - 0.4,0.0625,0\n";
            case "winter_bench" -> "      seats:\n        - -0.4,0.0625,0\n        - 0.4,0.125,0\n";
            default -> "";
        };
        return """
                blockcraft_%s:
                  itemname: '<white>%s'
                  material: %s
                  Components:
                    item_model: blockcraft:props/%s
                  Pack:
                    model: blockcraft:item/props/%s
                  Mechanics:
                    furniture:
                      properties:
                        display_transform: NONE
                        cullable: false
                      restricted_rotation: VERY_STRICT
                      hitbox:
                        interaction: 0,0,0 1,%s
                %s%s
                """.formatted(prop.id, prop.name, material, prop.id, prop.id, num(height), barriers, seats);
    }

    private static String num(double n) { return n == (int) n ? Integer.toString((int) n) : Double.toString(n); }

    private static BufferedImage texture(Surface surface) {
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int noise = Math.floorMod(x * 31 + y * 67 + x * y * 13, 13) - 6;
            int detail = switch (surface.pattern) {
                case "wood" -> y % 5 == 0 ? -19 : y % 5 == 1 ? 9 : (x + y * 3) % 13 == 0 ? -12 : 0;
                case "metal" -> x == 0 || y == 0 ? 17 : x == 15 || y == 15 ? -19 : 0;
                case "weave" -> (x + y) % 2 == 0 ? 4 : -4;
                case "stone" -> (x * 3 + y * 7) % 17 < 3 ? -15 : 0;
                case "gem" -> x == y || x + y == 15 ? 25 : x < 3 || y < 3 ? 12 : -4;
                case "leaf" -> (x * 3 + y * 5) % 11 < 3 ? -15 : 5;
                case "paper" -> y % 4 == 2 && x > 2 && x < 12 ? -20 : 0;
                default -> 0;
            };
            image.setRGB(x, y, shade(surface.color, 1, noise + detail));
        }
        return image;
    }

    private static int shade(int color, double factor, int add) {
        int r = Math.clamp((int) (((color >> 16) & 255) * factor) + add, 0, 255);
        int g = Math.clamp((int) (((color >> 8) & 255) * factor) + add, 0, 255);
        int b = Math.clamp((int) ((color & 255) * factor) + add, 0, 255);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static BufferedImage preview(List<Prop> props, Map<String, BufferedImage> textures) {
        int columns = 4, width = 350, height = 350;
        var image = new BufferedImage(columns * width, ((props.size() + columns - 1) / columns) * height, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(new Color(0x17201D)); g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for (int index = 0; index < props.size(); index++) {
            var prop = props.get(index);
            int left = index % columns * width, top = index / columns * height;
            g.setColor(new Color(0x22312B)); g.fillRoundRect(left + 8, top + 8, width - 16, height - 16, 12, 12);
            var faces = new ArrayList<Face>();
            for (var b : prop.boxes) {
                faces.add(new Face(List.of(new Point(b.x1, b.y2, b.z1), new Point(b.x2, b.y2, b.z1), new Point(b.x2, b.y1, b.z1), new Point(b.x1, b.y1, b.z1)), b.surface, .88));
                faces.add(new Face(List.of(new Point(b.x2, b.y2, b.z1), new Point(b.x2, b.y2, b.z2), new Point(b.x2, b.y1, b.z2), new Point(b.x2, b.y1, b.z1)), b.surface, .70));
                faces.add(new Face(List.of(new Point(b.x1, b.y2, b.z2), new Point(b.x2, b.y2, b.z2), new Point(b.x2, b.y2, b.z1), new Point(b.x1, b.y2, b.z1)), b.surface, 1));
            }
            faces.sort(Comparator.comparingDouble(Face::depth));
            double scale = 7.3;
            for (var face : faces) drawFace(g, face, textures.get(face.surface), left + width / 2.0, top + 245, scale);
            g.setColor(new Color(0xE5D8B7)); g.setFont(new Font("SansSerif", Font.BOLD, 16));
            g.drawString(prop.name, left + 20, top + height - 40);
            g.setColor(new Color(0x93A599)); g.setFont(new Font("Monospaced", Font.PLAIN, 12));
            g.drawString(prop.id, left + 20, top + height - 21);
        }
        g.dispose();
        return image;
    }

    private static void drawFace(Graphics2D g, Face face, BufferedImage texture, double ox, double oy, double scale) {
        var p = face.points.stream().map(v -> new double[]{ox + ((v.x - 8) * .866 + (v.z - 8) * .5) * scale,
                oy + ((v.x - 8) * .25 - (v.z - 8) * .433 - v.y * .866) * scale}).toList();
        var polygon = new Polygon();
        p.forEach(v -> polygon.addPoint((int) Math.round(v[0]), (int) Math.round(v[1])));
        var tinted = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) tinted.setRGB(x, y, shade(texture.getRGB(x, y), face.shade, 0));
        var transform = new AffineTransform((p.get(1)[0] - p.get(0)[0]) / 16, (p.get(1)[1] - p.get(0)[1]) / 16,
                (p.get(3)[0] - p.get(0)[0]) / 16, (p.get(3)[1] - p.get(0)[1]) / 16, p.get(0)[0], p.get(0)[1]);
        var clip = g.getClip();
        g.setClip(polygon); g.drawImage(tinted, transform, null); g.setClip(clip);
    }
}
