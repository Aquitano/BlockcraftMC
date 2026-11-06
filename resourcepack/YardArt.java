import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class YardArt {
    private record Point(double x, double y, double z) {
        Point subtract(Point other) { return new Point(x - other.x, y - other.y, z - other.z); }
        Point cross(Point other) { return new Point(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x); }
    }

    private record Turn(Point origin, String axis, double angle) {
        Point apply(Point p) {
            var v = p.subtract(origin);
            double c = Math.cos(Math.toRadians(angle)), s = Math.sin(Math.toRadians(angle));
            return switch (axis) {
                case "x" -> new Point(p.x, origin.y + v.y * c - v.z * s, origin.z + v.y * s + v.z * c);
                case "y" -> new Point(origin.x + v.x * c + v.z * s, p.y, origin.z - v.x * s + v.z * c);
                case "z" -> new Point(origin.x + v.x * c - v.y * s, origin.y + v.x * s + v.y * c, p.z);
                default -> throw new IllegalArgumentException(axis);
            };
        }
    }

    private enum Surface {
        ENAMEL(0, 0, 16, 16), STEEL(16, 0, 16, 16), RECESS(32, 0, 16, 16), COPPER(48, 0, 16, 16),
        SCREEN(0, 16, 24, 16), KEYS(24, 16, 16, 8), VENT(40, 16, 16, 16), AMBER(56, 16, 8, 8),
        TEAL(56, 24, 8, 8), ACCESS(0, 32, 24, 16), RIB(24, 32, 16, 16), HAZARD(40, 32, 16, 8),
        STRAP(56, 32, 8, 16), STENCIL(0, 48, 16, 16), BOLT(16, 48, 8, 8), LENS(24, 48, 8, 8),
        GRILLE(32, 48, 16, 16), ENGINE(48, 48, 16, 16);

        final int x, y, width, height;

        Surface(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        double[] uv(double width, double height) {
            boolean material = ordinal() <= COPPER.ordinal();
            return new double[]{x / 4.0, y / 4.0,
                    (x + (material ? Math.min(this.width, Math.max(1, Math.ceil(width))) : this.width)) / 4.0,
                    (y + (material ? Math.min(this.height, Math.max(1, Math.ceil(height))) : this.height)) / 4.0};
        }
    }

    private record Element(String name, Point from, Point to, Surface material, Surface front, Surface top, Turn turn) {
        Element front(Surface surface) { return new Element(name, from, to, material, surface, top, turn); }
        Element top(Surface surface) { return new Element(name, from, to, material, front, surface, turn); }
        Element turn(double x, double y, double z, String axis, double angle) {
            return new Element(name, from, to, material, front, top, new Turn(new Point(x, y, z), axis, angle));
        }
    }

    private record Face(String direction, List<Point> corners, Surface surface, double[] uv) { }
    private record Prop(String id, String label, List<Element> elements) { }
    private record Projected(double x, double y, double depth) { }

    private static final int IVORY = 0xD8D9CB, BLUE = 0x627C89, DARK = 0x283A43, TEAL = 0x6BC8BE,
            AMBER = 0xDBAC61, COPPER = 0xAB8164, OXIDE = 0x5D968B;

    public static void main(String[] args) throws IOException {
        Path root = Path.of(args.length > 0 ? args[0] : "src").resolve("assets/blockcraft");
        Path models = root.resolve("models/item/sternenfront/yard");
        Path textures = root.resolve("textures/item/sternenfront/yard");
        Files.createDirectories(models);
        Files.createDirectories(textures);
        var props = List.of(console(), powerCoupler(), engineCradle(), cargoStack(), repairDroid(), wallPanel());
        var atlases = new ArrayList<BufferedImage>();
        for (var prop : props) {
            validate(prop);
            var atlas = texture(prop.id);
            atlases.add(atlas);
            Files.writeString(models.resolve(prop.id + ".json"), model(prop));
            ImageIO.write(atlas, "PNG", textures.resolve(prop.id + ".png").toFile());
            var points = prop.elements.stream().flatMap(e -> faces(e).stream()).flatMap(f -> f.corners.stream()).toList();
            System.out.printf(Locale.ROOT, "%s: %d elements, bounds [%s] to [%s]%n", prop.id, prop.elements.size(),
                    xyz(new Point(points.stream().mapToDouble(Point::x).min().orElseThrow(), points.stream().mapToDouble(Point::y).min().orElseThrow(), points.stream().mapToDouble(Point::z).min().orElseThrow())),
                    xyz(new Point(points.stream().mapToDouble(Point::x).max().orElseThrow(), points.stream().mapToDouble(Point::y).max().orElseThrow(), points.stream().mapToDouble(Point::z).max().orElseThrow())));
        }
        if (args.length > 1) {
            Path preview = Path.of(args[1]);
            Files.createDirectories(preview.toAbsolutePath().getParent());
            ImageIO.write(preview(props, atlases), "PNG", preview.toFile());
        }
    }

    private static Element part(String name, double x1, double y1, double z1, double x2, double y2, double z2, Surface surface) {
        return new Element(name, new Point(x1, y1, z1), new Point(x2, y2, z2), surface, surface, surface, null);
    }

    private static Prop console() {
        var b = new ArrayList<Element>();
        for (int x : new int[]{-3, 9}) {
            b.add(part("split plinth", x, 8, 1, x + 10, 9.5, 17, Surface.STEEL));
            b.add(part("ivory plinth lip", x, 9.5, 2, x + 10, 10.5, 16, Surface.ENAMEL));
        }
        b.add(part("service pedestal", 1, 10.5, 4, 15, 19, 13, Surface.ENAMEL).front(Surface.ACCESS));
        b.add(part("pedestal ventilation", 4, 12, 13, 12, 16, 13.4, Surface.VENT));
        b.add(part("rear spine", 5, 17, 3, 11, 27, 6, Surface.STEEL));
        b.add(part("angled screen casing", -2, 20, 5, 18, 30.5, 8, Surface.ENAMEL).turn(8, 22, 7, "x", -22.5));
        b.add(part("screen gasket", -.8, 21, 8, 16.8, 29.5, 8.5, Surface.RECESS).turn(8, 22, 7, "x", -22.5));
        b.add(part("navigation glass", .2, 21.8, 8.5, 13.5, 28.7, 8.7, Surface.SCREEN).turn(8, 22, 7, "x", -22.5));
        b.add(part("screen status column", 14.3, 22, 8.5, 15.6, 28.5, 8.8, Surface.STEEL).turn(8, 22, 7, "x", -22.5));
        for (int y : new int[]{23, 25, 27}) {
            b.add(part("status lamp", 14.5, y, 8.8, 15.4, y + .5, 9, y == 27 ? Surface.AMBER : Surface.TEAL).turn(8, 22, 7, "x", -22.5));
        }
        b.add(part("sloped input tray", -2, 19, 9, 18, 20.5, 18, Surface.STEEL).turn(8, 20, 12, "x", 22.5));
        b.add(part("recessed key bed", 0, 20.5, 10, 12.5, 20.6, 16.5, Surface.RECESS).top(Surface.KEYS).turn(8, 20, 12, "x", 22.5));
        for (double x : new double[]{-2, 13.5}) {
            b.add(part("input tray cheek", x, 20.5, 9, x + 4.5, 21, 18, Surface.ENAMEL).turn(8, 20, 12, "x", 22.5));
        }
        b.add(part("rotary control", 14.5, 21, 13, 16.5, 21.7, 15, Surface.COPPER).top(Surface.BOLT).turn(8, 20, 12, "x", 22.5));
        return new Prop("console", "Navigation terminal", List.copyOf(b));
    }

    private static Prop powerCoupler() {
        var b = new ArrayList<Element>();
        b.add(part("anchored foot", 0, 8, 1, 16, 10, 15, Surface.STEEL).top(Surface.HAZARD));
        b.add(part("lower socket", 3, 10, 3, 13, 13, 13, Surface.ENAMEL).front(Surface.ACCESS));
        b.add(part("copper conductor", 5, 12, 5, 11, 28, 11, Surface.COPPER));
        for (int y = 14; y <= 24; y += 2) {
            b.add(part("cooling rib left", 2, y, 4, 4, y + 1, 12, Surface.STEEL));
            b.add(part("cooling rib right", 12, y, 4, 14, y + 1, 12, Surface.STEEL));
            b.add(part("cooling rib cheeks", 4, y, 2, 12, y + 1, 14, Surface.STEEL));
        }
        b.add(part("upper collar", 2, 26, 3, 14, 29, 13, Surface.ENAMEL).front(Surface.STENCIL));
        b.add(part("sealed cap", 4, 29, 5, 12, 30, 11, Surface.STEEL));
        b.add(part("voltage witness", 6, 26.8, 13, 10, 28.2, 13.3, Surface.AMBER));
        for (int x : new int[]{0, 13}) {
            b.add(part("hose port", x, 11, 11, x + 3, 14, 14.5, Surface.COPPER).front(Surface.BOLT));
            b.add(part("hose elbow", x + .5, 11.3, 14.5, x + 2.5, 13.3, 18, Surface.RECESS));
            b.add(part("downward hose", x + .5, 9.3, 17, x + 2.5, 12.5, 19, Surface.RIB));
            b.add(part("hose shoe", x, 8, 16, x + 3, 9.5, 20, Surface.STEEL));
        }
        return new Prop("power_coupler", "Ribbed power coupler", List.copyOf(b));
    }

    private static void collar(List<Element> b, double z1, double z2, double radius, Surface material) {
        double diagonal = radius * .7071067811865476;
        double half = radius * .41421356237309503;
        b.add(part("collar crown", 8 - half, 21 + radius - .8, z1, 8 + half, 21 + radius + .8, z2, material));
        b.add(part("collar sole", 8 - half, 21 - radius - .8, z1, 8 + half, 21 - radius + .8, z2, material));
        for (int sign : new int[]{-1, 1}) {
            double x = 8 + sign * radius;
            b.add(part("collar side", x - .8, 21 - half, z1, x + .8, 21 + half, z2, material));
            for (int rise : new int[]{-1, 1}) {
                double cx = 8 + sign * diagonal, cy = 21 + rise * diagonal;
                b.add(part("collar bevel", cx - half, cy - .8, z1, cx + half, cy + .8, z2, material)
                        .turn(cx, cy, (z1 + z2) / 2, "z", -sign * rise * 45));
            }
        }
    }

    private static Prop engineCradle() {
        var b = new ArrayList<Element>();
        for (int x : new int[]{-3, 17}) {
            for (int z : new int[]{-6, 8}) {
                b.add(part("maintenance skid", x, 8, z, x + 2, 10, z + 14, Surface.STEEL));
                b.add(part("skid edge", x - .4, 9, z, x, 10.5, z + 14, Surface.ENAMEL));
            }
            for (int z : new int[]{-2, 15}) {
                b.add(part("upright clamp jack", x, 10, z, x + 2, 21, z + 3, Surface.STEEL).front(Surface.RIB));
                b.add(part("copper clamp pivot", x - .4, 17, z + 3, x + 2.4, 20, z + 3.5, Surface.COPPER).front(Surface.BOLT));
                b.add(part("clamp jaw", x == -3 ? -1 : 13, 20, z, x == -3 ? 3 : 17, 23, z + 3, Surface.ENAMEL));
            }
        }
        for (int z : new int[]{-3, 16}) {
            b.add(part("cradle bridge", -1, 10, z, 8, 12, z + 2, Surface.STEEL));
            b.add(part("cradle bridge", 8, 10, z, 17, 12, z + 2, Surface.STEEL));
        }
        b.add(part("engine barrel", 3, 16, -3, 13, 26, 9, Surface.STEEL).front(Surface.ENGINE));
        b.add(part("engine barrel", 3, 16, 9, 13, 26, 18, Surface.STEEL).front(Surface.ENGINE));
        b.add(part("lower casing", 5, 14, -3, 11, 16, 12, Surface.ENAMEL));
        b.add(part("upper casing", 5, 26, -3, 11, 28, 12, Surface.ENAMEL));
        for (int z : new int[]{-4, 1, 9, 16}) collar(b, z, z + 1.5, 6, z == 9 ? Surface.COPPER : Surface.ENAMEL);
        collar(b, 18, 20.5, 4.5, Surface.STEEL);
        b.add(part("exhaust cavity", 4.5, 17.5, 20, 11.5, 24.5, 20.7, Surface.RECESS));
        b.add(part("exhaust honeycomb", 5, 18, 20.7, 11, 24, 20.8, Surface.GRILLE));
        b.add(part("turbine center", 6.8, 19.8, 20.8, 9.2, 22.2, 21.1, Surface.COPPER).front(Surface.BOLT));
        b.add(part("exposed upper manifold", 6, 28, 0, 10, 29.3, 13, Surface.COPPER));
        for (int z : new int[]{2, 6, 10}) b.add(part("manifold saddle", 5.5, 27.5, z, 10.5, 30, z + 1, Surface.STEEL));
        b.add(part("service instrument", -4, 13, 3, -2, 18, 10, Surface.ENAMEL));
        b.add(part("service instrument glass", -4.2, 14, 4, -4, 17, 9, Surface.SCREEN));
        b.add(part("safety tag", -1, 12.1, 17, 5, 12.4, 19, Surface.HAZARD));
        return new Prop("engine_cradle", "Engine maintenance cradle", List.copyOf(b));
    }

    private static void crate(List<Element> b, double x, double y, double z, double width, double height, double depth) {
        b.add(part("enamel cargo shell", x, y, z, x + width, y + height - 1.5, z + depth, Surface.ENAMEL).front(Surface.STENCIL));
        b.add(part("cargo lid", x - .3, y + height - 1.5, z - .3, x + width + .3, y + height, z + depth + .3, Surface.STEEL));
        for (double dx : new double[]{2, width - 3}) {
            b.add(part("front cargo strap", x + dx, y, z + depth, x + dx + 1, y + height, z + depth + .3, Surface.STRAP));
            b.add(part("lid cargo strap", x + dx, y + height, z, x + dx + 1, y + height + .3, z + depth, Surface.STRAP));
            b.add(part("rear cargo strap", x + dx, y, z - .3, x + dx + 1, y + height, z, Surface.STRAP));
            b.add(part("strap buckle", x + dx - .4, y + 2, z + depth + .3, x + dx + 1.4, y + 4, z + depth + .7, Surface.COPPER).front(Surface.BOLT));
        }
        b.add(part("recessed latch", x + width / 2 - 1.5, y + height - 3, z + depth, x + width / 2 + 1.5, y + height - 1, z + depth + .5, Surface.RECESS));
        b.add(part("latch handle", x + width / 2 - 1, y + height - 2.5, z + depth + .5, x + width / 2 + 1, y + height - 1.7, z + depth + .8, Surface.STEEL));
    }

    private static Prop cargoStack() {
        var b = new ArrayList<Element>();
        for (int x : new int[]{-5, 7, 19}) b.add(part("pallet runner", x, 8, -3, x + 2, 9.5, 10, Surface.STEEL));
        for (int x : new int[]{-5, 8}) b.add(part("pallet deck", x, 9.5, -3, x + 13, 10.5, 10, Surface.STEEL));
        crate(b, -4, 10.5, -2, 12, 11, 11);
        crate(b, 10, 10.5, -2, 10, 9, 11);
        crate(b, -2, 22, -1, 12, 8, 9);
        b.add(part("inventory plate", 12, 13, 9.1, 18, 16, 9.3, Surface.ACCESS));
        b.add(part("sealed shipment witness", 11, 17, 9.1, 13, 18, 9.5, Surface.AMBER));
        return new Prop("cargo_stack", "Strapped cargo stack", List.copyOf(b));
    }

    private static Prop repairDroid() {
        var b = new ArrayList<Element>();
        for (int x : new int[]{1, 11}) {
            b.add(part("splayed service foot", x, 8, 4, x + 4, 10, 14, Surface.STEEL));
            b.add(part("foot toe enamel", x, 9, 10, x + 4, 10.5, 14, Surface.ENAMEL));
            b.add(part("ankle strut", x + 1, 10, 6, x + 3, 15, 9, Surface.STEEL));
            b.add(part("ankle gaiter", x + .5, 11, 6, x + 3.5, 13, 10, Surface.RIB));
        }
        b.add(part("lower body", 3, 14, 3, 13, 17, 12, Surface.STEEL));
        b.add(part("ivory service body", 2, 17, 3, 14, 23, 12, Surface.ENAMEL).front(Surface.ACCESS));
        b.add(part("side battery", 13.5, 17, 4, 15, 22, 10, Surface.COPPER));
        b.add(part("body chest seam", 3, 20, 12, 13, 20.6, 12.3, Surface.STEEL));
        b.add(part("neck swivel", 6, 23, 6, 10, 24.5, 9, Surface.RECESS));
        b.add(part("optical head lower", 1.5, 24.5, 4, 14.5, 27.5, 11, Surface.STEEL));
        b.add(part("optical head enamel", 2.5, 27.5, 4.5, 13.5, 30, 10.5, Surface.ENAMEL));
        b.add(part("optic visor", 3, 25, 11, 13, 27, 11.4, Surface.RECESS));
        b.add(part("wide optical lens", 4, 25.1, 11.4, 7, 27, 11.8, Surface.LENS));
        b.add(part("secondary amber optic", 10, 25.5, 11.4, 11.5, 26.6, 11.7, Surface.AMBER));
        b.add(part("head ridge", 6, 30, 5, 10, 30.7, 9, Surface.STEEL));
        b.add(part("tool shoulder", -1, 19, 6, 2, 22, 10, Surface.STEEL).front(Surface.BOLT));
        b.add(part("upper tool arm", -1.5, 16, 7, .5, 20, 9, Surface.ENAMEL).turn(-.5, 20, 8, "z", -22.5));
        b.add(part("elbow bearing", -3, 15, 7, 0, 17.5, 10, Surface.COPPER).front(Surface.BOLT));
        b.add(part("forward tool arm", -2.5, 15.5, 9.5, -.5, 17.2, 14, Surface.STEEL));
        b.add(part("tool chuck", -3, 15, 13.5, 0, 17.7, 15.5, Surface.RECESS));
        for (double x : new double[]{-3, -.7}) b.add(part("tool gripper", x, 15.5, 15.5, x + .7, 17, 18, Surface.STEEL));
        b.add(part("retracted probe", 14.5, 17, 7, 17, 18.5, 9, Surface.STEEL));
        b.add(part("copper probe tip", 16, 16, 8, 17.3, 18, 11, Surface.COPPER));
        return new Prop("repair_droid", "Dock repair droid", List.copyOf(b));
    }

    private static Prop wallPanel() {
        var b = new ArrayList<Element>();
        for (int x : new int[]{-3, 8}) {
            b.add(part("service backing", x, 8, 6.5, x + 11, 20, 7.5, Surface.STEEL));
            b.add(part("service backing", x, 20, 6.5, x + 11, 30, 7.5, Surface.STEEL));
        }
        for (int x : new int[]{-3, 18}) b.add(part("enamel side rail", x, 8, 7.5, x + 1, 30, 9, Surface.ENAMEL));
        for (int y : new int[]{8, 29}) b.add(part("enamel end rail", -2, y, 7.5, 18, y + 1, 9, Surface.ENAMEL));
        b.add(part("vent gasket", -1, 22, 7.5, 17, 28, 8.2, Surface.RECESS));
        b.add(part("upper louvers", 0, 22.5, 8.2, 16, 27.5, 8.5, Surface.VENT));
        b.add(part("access gasket", -1, 10, 7.5, 17, 21, 8, Surface.RECESS));
        b.add(part("removable access plate", -.5, 10.5, 8, 16.5, 20.5, 8.6, Surface.ENAMEL).front(Surface.ACCESS));
        for (double x : new double[]{.3, 14.7}) for (int y : new int[]{11, 19}) {
            b.add(part("captive plate fastener", x, y, 8.6, x + 1, y + 1, 8.8, Surface.BOLT));
        }
        b.add(part("plate pull recess", 10, 14, 8.6, 14, 16, 8.7, Surface.RECESS));
        b.add(part("plate pull handle", 10.5, 14.8, 8.7, 13.5, 15.4, 9.2, Surface.STEEL));
        b.add(part("amber interlock", 2, 17, 8.6, 3.5, 18.2, 8.9, Surface.AMBER));
        b.add(part("copper drain", 4, 8, 7.5, 6, 10.5, 9, Surface.COPPER));
        return new Prop("wall_panel", "Wall service panel", List.copyOf(b));
    }

    private static List<Face> faces(Element e) {
        var a = e.from;
        var b = e.to;
        var result = new ArrayList<Face>();
        var directions = List.of("south", "north", "east", "west", "up", "down");
        var corners = List.of(
                List.of(new Point(a.x, b.y, b.z), new Point(b.x, b.y, b.z), new Point(b.x, a.y, b.z), new Point(a.x, a.y, b.z)),
                List.of(new Point(b.x, b.y, a.z), new Point(a.x, b.y, a.z), new Point(a.x, a.y, a.z), new Point(b.x, a.y, a.z)),
                List.of(new Point(b.x, b.y, b.z), new Point(b.x, b.y, a.z), new Point(b.x, a.y, a.z), new Point(b.x, a.y, b.z)),
                List.of(new Point(a.x, b.y, a.z), new Point(a.x, b.y, b.z), new Point(a.x, a.y, b.z), new Point(a.x, a.y, a.z)),
                List.of(new Point(a.x, b.y, a.z), new Point(b.x, b.y, a.z), new Point(b.x, b.y, b.z), new Point(a.x, b.y, b.z)),
                List.of(new Point(a.x, a.y, b.z), new Point(b.x, a.y, b.z), new Point(b.x, a.y, a.z), new Point(a.x, a.y, a.z)));
        for (int i = 0; i < 6; i++) {
            var surface = i == 0 ? e.front : i == 4 ? e.top : e.material;
            double width = i == 2 || i == 3 ? b.z - a.z : b.x - a.x;
            double height = i >= 4 ? b.z - a.z : b.y - a.y;
            var points = e.turn == null ? corners.get(i) : corners.get(i).stream().map(e.turn::apply).toList();
            result.add(new Face(directions.get(i), points, surface, surface.uv(width, height)));
        }
        return result;
    }

    private static void validate(Prop prop) {
        for (var e : prop.elements) {
            if (e.from.x >= e.to.x || e.from.y >= e.to.y || e.from.z >= e.to.z) throw new IllegalArgumentException(e.name);
            if (e.turn != null && !List.of(-45.0, -22.5, 0.0, 22.5, 45.0).contains(e.turn.angle)) throw new IllegalArgumentException(e.name);
            for (var p : List.of(e.from, e.to)) checkBounds(prop.id, p);
            for (var face : faces(e)) {
                for (var p : face.corners) checkBounds(prop.id, p);
                for (double uv : face.uv) if (uv < 0 || uv > 16) throw new IllegalArgumentException(prop.id + ": UV " + uv);
            }
        }
    }

    private static void checkBounds(String id, Point p) {
        if (p.x < -8 || p.x > 24 || p.y < 8 || p.y > 32 || p.z < -8 || p.z > 24) {
            throw new IllegalArgumentException(id + ": outside yard contract " + p);
        }
    }

    private static String model(Prop prop) {
        var out = new StringBuilder("""
                {
                  "ambientocclusion": true,
                  "textures": {
                    "particle": "blockcraft:item/sternenfront/yard/%s",
                    "atlas": "blockcraft:item/sternenfront/yard/%s"
                  },
                  "elements": [
                """.formatted(prop.id, prop.id));
        var parts = new ArrayList<String>();
        for (var e : prop.elements) {
            var part = new StringBuilder("    {\n      \"name\": \"" + e.name + "\",\n      \"from\": [" + xyz(e.from) + "],\n      \"to\": [" + xyz(e.to) + "],\n");
            if (e.turn != null) part.append("      \"rotation\": {\"origin\": [").append(xyz(e.turn.origin))
                    .append("], \"axis\": \"").append(e.turn.axis).append("\", \"angle\": ").append(number(e.turn.angle)).append(", \"rescale\": false},\n");
            var drawn = new ArrayList<String>();
            for (var face : faces(e)) drawn.add("        \"" + face.direction + "\": {\"uv\": [" + numbers(face.uv) + "], \"texture\": \"#atlas\"}");
            parts.add(part.append("      \"faces\": {\n").append(String.join(",\n", drawn)).append("\n      }\n    }").toString());
        }
        return out.append(String.join(",\n", parts)).append("\n  ]\n}\n").toString();
    }

    private static String number(double n) { return String.format(Locale.ROOT, "%.4f", n).replaceAll("\\.?0+$", ""); }
    private static String numbers(double... values) { return String.join(", ", Arrays.stream(values).mapToObj(YardArt::number).toList()); }
    private static String xyz(Point p) { return numbers(p.x, p.y, p.z); }

    private static BufferedImage texture(String id) {
        var image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        rect(image, 0, 0, 64, 64, DARK);
        for (var s : Surface.values()) {
            int color = switch (s) {
                case ENAMEL, STENCIL, ACCESS -> IVORY;
                case STEEL, RIB, BOLT -> BLUE;
                case COPPER -> COPPER;
                case TEAL -> TEAL;
                case AMBER, HAZARD -> AMBER;
                default -> DARK;
            };
            for (int y = 0; y < s.height; y++) for (int x = 0; x < s.width; x++) {
                int noise = Math.floorMod(id.hashCode() + x * 11 + y * 31, 17) == 0 ? -5 : 0;
                int edge = x == 0 || y == 0 ? 13 : x == s.width - 1 || y == s.height - 1 ? -22 : 0;
                image.setRGB(s.x + x, s.y + y, shade(color, 1, edge + noise));
            }
        }
        for (int y : new int[]{2, 13}) for (int x : new int[]{2, 13}) {
            rect(image, 16 + x, y, 1, 1, 0xA2B1B1);
            rect(image, 16 + x, y + 1, 1, 1, DARK);
        }
        rect(image, 3, 11, 3, 1, 0x9EADA9);
        rect(image, 4, 10, 2, 1, 0xF0EFE0);
        for (int y = 2; y < 14; y += 4) {
            rect(image, 50, y, 2, 2, OXIDE);
            rect(image, 51, y + 1, 3, 1, 0x467A74);
            rect(image, 57, y - 1, 4, 1, 0xC7986C);
        }
        rect(image, 1, 17, 22, 14, 0x18373F);
        for (int x = 3; x < 22; x += 4) rect(image, x, 19, 1, 10, 0x284E56);
        for (int y = 20; y < 30; y += 4) rect(image, 2, y, 19, 1, 0x284E56);
        rect(image, 3, 19, 7, 1, TEAL);
        rect(image, 3, 21, 4, 1, 0x599F9D);
        rect(image, 3, 24, 11, 1, TEAL);
        rect(image, 13, 23, 1, 5, TEAL);
        rect(image, 13, 27, 6, 1, TEAL);
        rect(image, 18, 25, 1, 2, TEAL);
        rect(image, 18, 24, 3, 1, TEAL);
        rect(image, 9, 23, 2, 3, 0xB3E5D5);
        rect(image, 19, 19, 2, 2, AMBER);
        rect(image, 3, 28, 5, 1, 0x7DB6AE);
        rect(image, 25, 17, 14, 6, 0x1C2D35);
        for (int y = 18; y < 23; y += 2) for (int x = 26; x < 39; x += 3) {
            rect(image, x, y, 2, 1, y == 22 ? BLUE : 0xA9BCB8);
        }
        for (int y = 18; y < 31; y += 3) {
            rect(image, 42, y, 12, 1, 0x14272F);
            rect(image, 42, y + 1, 12, 1, 0x829A9D);
        }
        rect(image, 58, 18, 4, 4, 0xECC77D);
        rect(image, 58, 26, 4, 4, 0x9ED7C8);
        rect(image, 2, 34, 20, 12, 0xC1CBC3);
        rect(image, 3, 35, 18, 1, 0xECEDDD);
        rect(image, 3, 35, 1, 10, 0xECEDDD);
        rect(image, 3, 45, 18, 1, BLUE);
        rect(image, 20, 36, 1, 9, BLUE);
        for (int x : new int[]{4, 19}) for (int y : new int[]{36, 43}) {
            rect(image, x, y, 1, 2, BLUE);
            rect(image, x, y, 1, 1, 0xF1EEDD);
        }
        rect(image, 7, 38, 9, 1, BLUE);
        rect(image, 7, 40, 5, 1, BLUE);
        rect(image, 7, 42, 3, 1, 0x7A9A93);
        for (int y = 33; y < 47; y += 3) {
            rect(image, 25, y, 14, 1, 0x192D36);
            rect(image, 25, y + 1, 14, 1, 0x8A9FA2);
        }
        for (int y = 33; y < 39; y++) for (int x = 41; x < 55; x++) {
            if ((x + y) % 8 < 4) image.setRGB(x, y, 0xFF34454D);
        }
        rect(image, 58, 33, 4, 14, 0x324C5C);
        rect(image, 59, 33, 1, 14, 0x718D97);
        rect(image, 2, 50, 12, 2, BLUE);
        rect(image, 3, 53, 2, 6, BLUE);
        rect(image, 6, 56, 2, 3, BLUE);
        rect(image, 9, 53, 2, 6, BLUE);
        rect(image, 3, 60, 8, 1, 0x8EAAA4);
        for (int x = 2; x < 14; x += 2) rect(image, x, 62, 1, 1, BLUE);
        rect(image, 17, 49, 6, 6, 0xA4B6B6);
        rect(image, 18, 50, 4, 4, DARK);
        rect(image, 19, 50, 1, 4, 0xCBD2CA);
        rect(image, 25, 49, 6, 6, 0x1B333C);
        rect(image, 26, 50, 4, 4, 0x3F8F92);
        rect(image, 27, 50, 2, 2, 0xA7E1D2);
        for (int y = 49; y < 63; y += 3) for (int x = 33; x < 47; x += 3) {
            rect(image, x, y, 2, 2, 0x12272F);
            rect(image, x, y, 2, 1, 0x769095);
        }
        rect(image, 50, 50, 12, 12, 0x1C313A);
        for (int x : new int[]{51, 55, 59}) {
            rect(image, x, 51, 2, 10, COPPER);
            rect(image, x, 54, 2, 1, OXIDE);
        }
        rect(image, 52, 52, 8, 1, BLUE);
        rect(image, 52, 59, 8, 1, BLUE);
        return image;
    }

    private static void rect(BufferedImage image, int x, int y, int width, int height, int color) {
        for (int dy = 0; dy < height; dy++) for (int dx = 0; dx < width; dx++) image.setRGB(x + dx, y + dy, 0xFF000000 | color);
    }

    private static int shade(int color, double factor, int add) {
        int r = Math.clamp((int) (((color >> 16) & 255) * factor) + add, 0, 255);
        int g = Math.clamp((int) (((color >> 8) & 255) * factor) + add, 0, 255);
        int b = Math.clamp((int) ((color & 255) * factor) + add, 0, 255);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static BufferedImage preview(List<Prop> props, List<BufferedImage> atlases) {
        int width = 480, height = 440;
        var image = new BufferedImage(width * 3, height * 2, BufferedImage.TYPE_INT_RGB);
        double[] depths = new double[image.getWidth() * image.getHeight()];
        Arrays.fill(depths, Double.NEGATIVE_INFINITY);
        var g = image.createGraphics();
        g.setColor(new Color(0x172C38));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        for (int i = 0; i < props.size(); i++) {
            int left = i % 3 * width, top = i / 3 * height;
            g.setColor(new Color(0x243D49));
            g.fillRect(left + 8, top + 8, width - 16, height - 16);
            for (var e : props.get(i).elements) for (var face : faces(e)) {
                var p = face.corners.stream().map(v -> project(v, left + 240, top + 320)).toList();
                var normal = face.corners.get(1).subtract(face.corners.get(0)).cross(face.corners.get(3).subtract(face.corners.get(0)));
                double length = Math.sqrt(normal.x * normal.x + normal.y * normal.y + normal.z * normal.z);
                double light = .77 + .21 * (normal.x * -.3 + normal.y * -.8 + normal.z * -.5) / length;
                triangle(image, depths, atlases.get(i), p.get(0), p.get(1), p.get(2), face.uv, light, false);
                triangle(image, depths, atlases.get(i), p.get(0), p.get(2), p.get(3), face.uv, light, true);
            }
            g.setColor(new Color(0xE0E4D7));
            g.setFont(new Font("SansSerif", Font.BOLD, 20));
            g.drawString(props.get(i).label, left + 24, top + 365);
            g.setColor(new Color(0xA0BBB9));
            g.setFont(new Font("Monospaced", Font.PLAIN, 14));
            g.drawString(props.get(i).id + "  /  front +Z", left + 24, top + 390);
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) image.setRGB(left + 392 + x, top + 355 + y, atlases.get(i).getRGB(x, y));
        }
        g.dispose();
        return image;
    }

    private static Projected project(Point p, double ox, double oy) {
        double x = p.x - 8, y = p.y - 8, z = p.z - 8;
        return new Projected(ox + (x * .866 - z * .5) * 9, oy + (x * .171 + z * .296 - y * .94) * 9,
                x * .47 + z * .814 + y * .342);
    }

    private static double edge(Projected a, Projected b, double x, double y) {
        return (x - a.x) * (b.y - a.y) - (y - a.y) * (b.x - a.x);
    }

    private static void triangle(BufferedImage image, double[] depths, BufferedImage atlas, Projected a, Projected b,
                                 Projected c, double[] uv, double light, boolean second) {
        double area = edge(a, b, c.x, c.y);
        if (Math.abs(area) < .001) return;
        int minX = Math.max(0, (int) Math.floor(Math.min(a.x, Math.min(b.x, c.x))));
        int maxX = Math.min(image.getWidth() - 1, (int) Math.ceil(Math.max(a.x, Math.max(b.x, c.x))));
        int minY = Math.max(0, (int) Math.floor(Math.min(a.y, Math.min(b.y, c.y))));
        int maxY = Math.min(image.getHeight() - 1, (int) Math.ceil(Math.max(a.y, Math.max(b.y, c.y))));
        for (int y = minY; y <= maxY; y++) for (int x = minX; x <= maxX; x++) {
            double wa = edge(b, c, x + .5, y + .5) / area;
            double wb = edge(c, a, x + .5, y + .5) / area;
            double wc = 1 - wa - wb;
            if (wa < 0 || wb < 0 || wc < 0) continue;
            double depth = wa * a.depth + wb * b.depth + wc * c.depth;
            int pixel = y * image.getWidth() + x;
            if (depth <= depths[pixel]) continue;
            depths[pixel] = depth;
            double u = second ? wb : wb + wc, v = second ? wb + wc : wc;
            int tx = Math.clamp((int) ((uv[0] + u * (uv[2] - uv[0])) * 4), 0, 63);
            int ty = Math.clamp((int) ((uv[1] + v * (uv[3] - uv[1])) * 4), 0, 63);
            image.setRGB(x, y, shade(atlas.getRGB(tx, ty), light, 0));
        }
    }
}
