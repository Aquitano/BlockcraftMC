import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

/**
 * Generates Sternenfront's bodies, speeders, dropships and effect models. Every model family gets one square atlas:
 * its faces are packed at a constant texel density and painted from their material, so the same input always gives
 * byte-identical PNGs. Model space: +z is the front, +x the body's left, 1.0 is one block, and (0, 0, 0) sits on the
 * display entity's position. Vanilla limits elements to +-1.5 blocks from there.
 */
public final class SternenfrontArt {

    private enum Face {
        NORTH, SOUTH, EAST, WEST, UP, DOWN;

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        boolean side() {
            return this != UP && this != DOWN;
        }
    }

    private static final Set<Face> ALL_FACES = EnumSet.allOf(Face.class);

    private sealed interface Surface permits Paint, Sprite {
        String id();

        boolean glow();

        int[] draw(Face face, int width, int height, Random random);

        default boolean tinted() {
            return false;
        }
    }

    private enum Grain { SMOOTH, BRUSHED, WEAVE, BLOTCH, GRIT, HAZARD, GLASS, GLOW, FURNACE }

    private enum Wear { NONE, SCUFF, CHIP, RUST, PATCHED, SCORCH }

    private record Sprite(String id, boolean glow, boolean tinted, Drawing drawing) implements Surface {
        @Override
        public int[] draw(Face face, int width, int height, Random random) {
            return drawing.draw(width, height, random);
        }
    }

    @FunctionalInterface
    private interface Drawing {
        int[] draw(int width, int height, Random random);
    }

    private static final int[] RUST_RAMP = {0x3B1F12, 0x5E2F16, 0x84431B, 0xA85A24};
    private static final int[] BARE_METAL = {0x6A6660, 0x8C867C, 0xB0A99C};
    private static final int[] CHAR = {0x0C0A09, 0x1A1512, 0x2B231D};

    private record Paint(String id, int[] ramp, int base, Grain grain, int panel, Wear wear) implements Surface {

        @Override
        public boolean glow() {
            return grain == Grain.GLOW || grain == Grain.FURNACE;
        }

        Paint burnt() {
            var dark = Arrays.stream(ramp).map(colour -> mix(colour, 0x17120F, glow() ? .35 : .22)).toArray();
            return new Paint(id + "_burnt", dark, base, grain, panel, glow() ? Wear.NONE : Wear.SCORCH);
        }

        @Override
        public int[] draw(Face face, int width, int height, Random random) {
            var shades = new int[width * height];
            Arrays.fill(shades, face == Face.DOWN ? Math.max(base - 1, 0) : base);
            long seed = random.nextLong();
            grain(shades, width, height, seed, random);
            if (!glow() && grain != Grain.HAZARD) {
                seams(shades, width, height, random);
                bevel(shades, face, width, height);
            }
            var colours = new int[width * height];
            for (int index = 0; index < colours.length; index++) {
                colours[index] = ramp[Math.clamp(shades[index], 0, ramp.length - 1)];
            }
            wear(colours, width, height, seed, random);
            return colours;
        }

        private void grain(int[] shades, int width, int height, long seed, Random random) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int index = y * width + x;
                    switch (grain) {
                        case SMOOTH -> shades[index] -= cell(x / 2, y / 3, seed) < .03 ? 1 : 0;
                        case BRUSHED -> {
                            double streak = cell(x / 7, y, seed);
                            shades[index] += streak < .13 ? -1 : streak > .92 ? 1 : 0;
                        }
                        case WEAVE -> {
                            shades[index] += cell(x / 4, y, seed) < .05 ? -1 : cell(x, y / 3, seed + 1) > .985 ? 1 : 0;
                        }
                        case BLOTCH -> {
                            double blot = cell((x + y / 3) / 4, (y + x / 5) / 3, seed);
                            shades[index] += blot < .12 ? -1 : blot > .93 ? 1 : 0;
                        }
                        case GRIT -> {
                            double speck = cell(x, y, seed);
                            shades[index] += speck < .08 ? -1 : speck > .95 ? 1 : 0;
                        }
                        case FURNACE -> shades[index] = base;
                        case HAZARD -> shades[index] = Math.floorMod(x + y, 16) < 8 ? 3 : 0;
                        case GLASS -> shades[index] = Math.floorMod(x - y, 11) < 2 && y < height * 2 / 3 ? 3 : base;
                        case GLOW -> {
                            int edge = Math.min(Math.min(x, width - 1 - x), Math.min(y, height - 1 - y));
                            shades[index] = Math.min(width, height) <= 2 ? 3 : edge == 0 ? 2 : edge == 1 ? 3 : 4;
                        }
                    }
                }
            }
        }

        private void seams(int[] shades, int width, int height, Random random) {
            if (panel == 0) {
                return;
            }
            for (int at = panel / 2 + random.nextInt(panel / 2 + 1); at < width - 3; at += panel + random.nextInt(panel / 2 + 1)) {
                for (int y = 1; y < height - 1; y++) {
                    shades[y * width + at] -= 2;
                    shades[y * width + at + 1] += 1;
                }
            }
            for (int at = panel / 2 + random.nextInt(panel / 2 + 1); at < height - 3; at += panel + random.nextInt(panel / 2 + 1)) {
                for (int x = 1; x < width - 1; x++) {
                    shades[at * width + x] -= 2;
                    shades[(at + 1) * width + x] += 1;
                }
            }
        }

        private void bevel(int[] shades, Face face, int width, int height) {
            if (width < 3 || height < 3) {
                return;
            }
            int band = Math.clamp(Math.min(width, height) / 12, 1, 2);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int index = y * width + x;
                    if (face.side()) {
                        shades[index] += y < band ? 1 : y >= height - band ? -1 : x >= width - band ? -1 : 0;
                    } else {
                        boolean rim = x < band || y < band || x >= width - band || y >= height - band;
                        shades[index] += rim ? face == Face.UP ? 1 : -1 : 0;
                    }
                }
            }
        }

        private void wear(int[] colours, int width, int height, long seed, Random random) {
            int area = width * height;
            switch (wear) {
                case NONE -> { }
                case SCUFF -> {
                    for (int mark = area / 900; mark > 0; mark--) {
                        int x = random.nextInt(width);
                        int y = random.nextInt(height);
                        for (int run = random.nextInt(4); run >= 0 && x + run < width; run--) {
                            colours[y * width + x + run] = ramp[Math.max(base - 1, 0)];
                        }
                    }
                }
                case CHIP -> {
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            int edge = Math.min(Math.min(x, width - 1 - x), Math.min(y, height - 1 - y));
                            double chip = cell(x / 2, y / 2, seed + 3);
                            if (edge <= 1 && chip < .13 || edge <= 3 && chip < .025) {
                                colours[y * width + x] = BARE_METAL[(int) (cell(x, y, seed + 4) * BARE_METAL.length)];
                            }
                        }
                    }
                }
                case RUST -> rust(colours, width, height, seed, random);
                case PATCHED -> patch(colours, width, height, seed, random);
                case SCORCH -> scorch(colours, width, height, seed, random);
            }
        }

        private void rust(int[] colours, int width, int height, long seed, Random random) {
            for (int blot = width * height / 1100 + 1; blot > 0; blot--) {
                int cx = random.nextInt(width);
                int cy = height - 1 - (int) (random.nextDouble() * random.nextDouble() * height);
                int radius = 2 + random.nextInt(Math.max(1, Math.min(width, height) / 4 + 1));
                for (int y = Math.max(0, cy - radius); y < Math.min(height, cy + radius + 1); y++) {
                    for (int x = Math.max(0, cx - radius); x < Math.min(width, cx + radius + 1); x++) {
                        double reach = Math.hypot(x - cx, y - cy) / radius;
                        if (reach + cell(x / 2, y / 2, seed + blot) * .7 < 1) {
                            colours[y * width + x] = RUST_RAMP[(int) (cell(x / 2, y / 2, seed + 7) * RUST_RAMP.length)];
                        }
                    }
                }
            }
            for (int drip = width / 24; drip > 0; drip--) {
                int x = random.nextInt(width);
                int start = random.nextInt(Math.max(1, height / 2));
                int length = height / 4 + random.nextInt(Math.max(1, height / 3));
                for (int y = start; y < Math.min(height, start + length); y++) {
                    colours[y * width + x] = RUST_RAMP[1];
                }
            }
        }

        private void patch(int[] colours, int width, int height, long seed, Random random) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (y >= height - height / 6 && cell(x / 3, y / 2, seed + 9) < .2) {
                        colours[y * width + x] = ramp[1];
                    }
                }
            }
            if (width < 14 || height < 14) {
                return;
            }
            int patchWidth = width / 3 + random.nextInt(width / 6 + 1);
            int patchHeight = height / 4 + random.nextInt(height / 6 + 1);
            int left = 2 + random.nextInt(width - patchWidth - 3);
            int top = 2 + random.nextInt(height - patchHeight - 3);
            int tone = ramp[random.nextBoolean() ? ramp.length - 1 : 1];
            for (int y = top; y < top + patchHeight; y++) {
                for (int x = left; x < left + patchWidth; x++) {
                    boolean rim = x == left || y == top || x == left + patchWidth - 1 || y == top + patchHeight - 1;
                    colours[y * width + x] = rim
                            ? (x + y) % 3 == 0 ? mix(tone, 0xFFFFFF, .25) : ramp[0]
                            : (x + y) % 2 == 0 && cell(x / 3, y / 3, seed + 11) < .4 ? mix(tone, ramp[0], .3) : tone;
                }
            }
        }

        private void scorch(int[] colours, int width, int height, long seed, Random random) {
            double reach = Math.max(width, height) * (.45 + random.nextDouble() * .35);
            int cx = random.nextInt(width);
            int cy = random.nextInt(height);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    double at = Math.hypot(x - cx, y - cy) / reach + (cell(x / 2, y / 2, seed + 13) - .5) * .3;
                    int index = y * width + x;
                    if (at < .45) {
                        colours[index] = CHAR[(int) (cell(x / 2, y / 2, seed + 15) * CHAR.length)];
                    } else if (at < .7) {
                        colours[index] = mix(colours[index], CHAR[1], .6);
                    } else if (at < .95) {
                        colours[index] = mix(colours[index], CHAR[2], .25);
                    }
                }
            }
            for (int tear = width * height / 900; tear > 0; tear--) {
                int x = random.nextInt(width);
                int y = random.nextInt(height);
                int run = 3 + random.nextInt(9);
                for (int step = 0; step < run && x < width && y < height && y >= 0; step++) {
                    colours[y * width + x] = 0x050404;
                    if (y + 1 < height) {
                        colours[(y + 1) * width + x] = BARE_METAL[2];
                    }
                    x++;
                    y += random.nextInt(3) - 1;
                }
            }
        }
    }

    private static double cell(int x, int y, long seed) {
        long mixed = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL);
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return ((mixed ^ (mixed >>> 31)) >>> 1) / (double) Long.MAX_VALUE;
    }

    private static int mix(int from, int to, double amount) {
        int red = (int) (((from >> 16) & 255) * (1 - amount) + ((to >> 16) & 255) * amount);
        int green = (int) (((from >> 8) & 255) * (1 - amount) + ((to >> 8) & 255) * amount);
        int blue = (int) ((from & 255) * (1 - amount) + (to & 255) * amount);
        return red << 16 | green << 8 | blue;
    }

    private static Paint paint(String id, int base, Grain grain, int panel, Wear wear, int... ramp) {
        return new Paint(id, ramp, base, grain, panel, wear);
    }

    private static final Paint CERAMIC = paint("ceramic", 3, Grain.SMOOTH, 22, Wear.SCUFF,
            0x6F7A84, 0x9AA5AE, 0xC3CBD1, 0xE3E7EA, 0xF6F8F9);
    private static final Paint TRIM = paint("trim", 2, Grain.SMOOTH, 0, Wear.NONE,
            0x46525E, 0x62707D, 0x8493A0, 0xA3B0BB, 0xC2CCD4);
    private static final Paint STEEL = paint("steel", 2, Grain.BRUSHED, 26, Wear.NONE,
            0x2A3038, 0x414A54, 0x5F6A75, 0x7F8A95, 0xA3AEB8);
    private static final Paint FRAME = paint("frame", 2, Grain.BRUSHED, 0, Wear.NONE,
            0x12161A, 0x1D2329, 0x2B333B, 0x3C4650, 0x515D69);
    private static final Paint CYAN = paint("cyan", 3, Grain.GLOW, 0, Wear.NONE,
            0x14708C, 0x1F9DBF, 0x45D6FF, 0x8CE9FF, 0xD8FAFF);
    private static final Paint LENS = paint("lens", 0, Grain.GLASS, 0, Wear.NONE,
            0x06090C, 0x0E1419, 0x1A242C, 0x54707F);

    private static final Paint KHAKI = paint("khaki", 2, Grain.WEAVE, 0, Wear.PATCHED,
            0x3F3524, 0x5F5138, 0x7F6E4C, 0x98855E, 0xB29F78);
    private static final Paint DRAB = paint("drab", 2, Grain.WEAVE, 0, Wear.PATCHED,
            0x25281E, 0x393D2C, 0x4F533B, 0x65694B, 0x7E8260);
    private static final Paint SAND = paint("sand", 2, Grain.WEAVE, 0, Wear.PATCHED,
            0x5B4A32, 0x7B6746, 0x9C865E, 0xB7A276, 0xCDBA92);
    private static final Paint WRAP = paint("wrap", 3, Grain.WEAVE, 0, Wear.NONE,
            0x6E6450, 0x8F8469, 0xB0A587, 0xC8BEA0, 0xDDD5BB);
    private static final Paint LEATHER = paint("leather", 2, Grain.BLOTCH, 0, Wear.NONE,
            0x23150C, 0x3B2415, 0x56361F, 0x72492B, 0x8F5F3A);
    private static final Paint RUBBER = paint("rubber", 1, Grain.GRIT, 0, Wear.NONE,
            0x121110, 0x1C1A18, 0x292623, 0x3A3631);
    private static final Paint SCRAP = paint("scrap", 2, Grain.GRIT, 18, Wear.RUST,
            0x28261F, 0x3F3C33, 0x5C574B, 0x7A7464, 0x9C9483);
    private static final Paint OILED = paint("oiled", 2, Grain.BRUSHED, 0, Wear.NONE,
            0x171512, 0x25221D, 0x38342C, 0x4E4A3F, 0x696456);
    private static final Paint ORANGE = paint("orange", 2, Grain.SMOOTH, 20, Wear.CHIP,
            0x6F2F0D, 0xA24712, 0xCF611A, 0xEC8230, 0xFBA758);
    private static final Paint RUSTED = paint("rusted", 2, Grain.BLOTCH, 16, Wear.RUST,
            0x2F180D, 0x4F2813, 0x743C1A, 0x965224, 0xB36C34);
    private static final Paint TANK = paint("tank", 2, Grain.SMOOTH, 0, Wear.CHIP,
            0x2E3524, 0x455036, 0x5F6C49, 0x78855C, 0x96A276);
    private static final Paint CANISTER = paint("canister", 2, Grain.BRUSHED, 0, Wear.CHIP,
            0x22272A, 0x363D42, 0x4E575D, 0x68727A, 0x8B959C);
    private static final Paint HAZARD = paint("hazard", 0, Grain.HAZARD, 0, Wear.NONE,
            0x1B1815, 0x2B2622, 0x8F3F10, 0xE07C20, 0xFBA758);
    private static final Paint SKIN = paint("skin", 2, Grain.SMOOTH, 0, Wear.NONE,
            0x6E452E, 0x94634A, 0xB58560, 0xCB9E78, 0xDDB896);
    private static final Paint EMBER = paint("ember", 3, Grain.GLOW, 0, Wear.NONE,
            0x8A3208, 0xC44F0E, 0xFF6A1F, 0xFFA648, 0xFFE3A8);
    private static final Paint FURNACE = paint("furnace", 2, Grain.FURNACE, 0, Wear.NONE,
            0x8A3208, 0xC44F0E, 0xFF6A1F, 0xFFA648, 0xFFE3A8);
    private static final Paint FURNACE_CORE = paint("furnace_core", 4, Grain.FURNACE, 0, Wear.NONE,
            0x8A3208, 0xC44F0E, 0xFF6A1F, 0xFFA648, 0xFFE3A8);
    private static final Paint GOGGLE = paint("goggle", 0, Grain.GLASS, 0, Wear.NONE,
            0x241006, 0x3A1C0A, 0x54290F, 0xE08A3C);

    private record Spin(char axis, double angle, double x, double y, double z) { }

    private record Box(double x1, double y1, double z1, double x2, double y2, double z2, Surface surface, Spin spin,
                       Set<Face> faces) {

        Box spinX(double angle) {
            return spun('x', angle);
        }

        Box spinY(double angle) {
            return spun('y', angle);
        }

        Box spinZ(double angle) {
            return spun('z', angle);
        }

        private Box spun(char axis, double angle) {
            return new Box(x1, y1, z1, x2, y2, z2, surface, new Spin(axis, angle, (x1 + x2) / 2, (y1 + y2) / 2, (z1 + z2) / 2), faces);
        }

        /** Turns around a pivot instead of the box's own centre. */
        Box about(double x, double y, double z) {
            return new Box(x1, y1, z1, x2, y2, z2, surface, new Spin(spin.axis, spin.angle, x, y, z), faces);
        }

        Box only(Face... kept) {
            return new Box(x1, y1, z1, x2, y2, z2, surface, spin, Set.of(kept));
        }

        Box mirrored() {
            var flipped = spin == null ? null : new Spin(spin.axis, spin.axis == 'x' ? spin.angle : -spin.angle, -spin.x, spin.y, spin.z);
            return new Box(-x2, y1, z1, -x1, y2, z2, surface, flipped, faces);
        }

        Box surfaced(Surface replaced) {
            return new Box(x1, y1, z1, x2, y2, z2, replaced, spin, faces);
        }

        Box shifted(double x, double y, double z) {
            var moved = spin == null ? null : new Spin(spin.axis, spin.angle, spin.x + x, spin.y + y, spin.z + z);
            return new Box(x1 + x, y1 + y, z1 + z, x2 + x, y2 + y, z2 + z, surface, moved, faces);
        }

        double edge(Face face, boolean horizontal) {
            return switch (face) {
                case NORTH, SOUTH -> horizontal ? x2 - x1 : y2 - y1;
                case EAST, WEST -> horizontal ? z2 - z1 : y2 - y1;
                case UP, DOWN -> horizontal ? x2 - x1 : z2 - z1;
            };
        }
    }

    /** A centred box: centre first, then its size along x, y and z. */
    private static Box box(double x, double y, double z, double width, double height, double depth, Surface surface) {
        return new Box(x - width / 2, y - height / 2, z - depth / 2, x + width / 2, y + height / 2, z + depth / 2, surface, null, ALL_FACES);
    }

    private static final class Shape {
        private final List<Box> boxes = new ArrayList<>();

        Shape add(Box... added) {
            boxes.addAll(List.of(added));
            return this;
        }

        Shape add(List<Box> added) {
            boxes.addAll(added);
            return this;
        }

        /** Adds each box and its reflection across x = 0. */
        Shape both(Box... added) {
            for (var each : added) {
                boxes.add(each);
                boxes.add(each.mirrored());
            }
            return this;
        }

        Shape burnt(double share) {
            var result = new Shape();
            int counter = 0;
            for (var each : boxes) {
                boolean burn = each.surface instanceof Paint && cell(counter++, 3, 41) < share;
                result.add(burn ? each.surfaced(((Paint) each.surface).burnt()) : each);
            }
            return result;
        }
    }

    /** Eight-sided column along z; four long boxes whose union is a regular octagon in cross-section. */
    private static List<Box> drumZ(double x, double y, double z, double across, double length, Surface surface) {
        double flat = across * .4142;
        return List.of(box(x, y, z, across, flat, length, surface),
                box(x, y, z, flat, across, length - .004, surface),
                box(x, y, z, across, flat, length - .008, surface).spinZ(45),
                box(x, y, z, across, flat, length - .012, surface).spinZ(-45));
    }

    /** Eight-sided column along y. */
    private static List<Box> drumY(double x, double y, double z, double across, double height, Surface surface) {
        double flat = across * .4142;
        return List.of(box(x, y, z, across, height, flat, surface),
                box(x, y, z, flat, height - .004, across, surface),
                box(x, y, z, across, height - .008, flat, surface).spinY(45),
                box(x, y, z, across, height - .012, flat, surface).spinY(-45));
    }

    private record Model(String path, Shape shape) { }

    private record Family(String name, int density, boolean translucent, List<Model> models) { }

    private static Family body(String name, Shape torso, Shape head, Shape arm, Shape leg, Shape... toolArm) {
        var models = new ArrayList<>(List.of(model(name, "torso", torso), model(name, "head", head),
                model(name, "arm", arm), model(name, "leg", leg)));
        for (var extra : toolArm) {
            models.add(model(name, "tool_arm", extra));
        }
        return new Family(name, 64, false, models);
    }

    private static Model model(String name, String part, Shape shape) {
        return new Model("sternenfront/body/" + name + "/" + part, shape);
    }

    private static Family hegemonieSoldier() {
        var torso = new Shape()
                .add(box(0, .02, 0, .2, .1, .13, STEEL))
                .both(box(.07, -.02, .07, .05, .08, .015, CERAMIC))
                .add(box(0, .19, -.015, .06, .26, .06, FRAME))
                .add(box(0, .19, .03, .17, .03, .1, TRIM))
                .add(box(0, .25, .025, .22, .03, .12, TRIM))
                .add(box(0, .31, .015, .27, .035, .14, TRIM))
                .add(box(0, .39, .012, .28, .15, .16, CERAMIC))
                .both(box(.105, .34, .1, .1, .1, .03, CERAMIC).spinX(-22.5))
                .add(box(0, .39, .095, .04, .13, .012, CYAN))
                .add(box(0, .485, 0, .46, .05, .09, STEEL))
                .add(box(0, .58, 0, .035, .15, .035, FRAME))
                .add(box(0, .36, -.098, .12, .17, .05, STEEL))
                .add(box(0, .36, -.126, .07, .1, .008, CYAN));
        var head = new Shape()
                .add(box(0, .0, 0, .075, .04, .075, FRAME))
                .add(box(0, .075, .01, .34, .085, .105, CERAMIC))
                .add(box(0, .075, .066, .3, .026, .012, CYAN))
                .both(box(.18, .075, .01, .035, .105, .125, FRAME))
                .add(box(0, .13, -.025, .022, .05, .1, CERAMIC))
                .add(box(0, .07, -.06, .13, .065, .04, STEEL));
        var arm = new Shape()
                .add(box(0, 0, 0, .085, .085, .085, FRAME))
                .add(box(0, .03, 0, .115, .05, .115, CERAMIC))
                .add(box(0, -.16, 0, .04, .25, .04, STEEL))
                .add(box(0, -.13, .027, .055, .13, .012, CERAMIC))
                .add(box(0, -.3, 0, .065, .06, .065, FRAME))
                .add(box(0, -.44, 0, .036, .22, .036, STEEL))
                .add(box(0, -.43, 0, .07, .14, .07, CERAMIC))
                .add(box(0, -.565, 0, .06, .05, .06, FRAME))
                .both(box(.022, -.615, 0, .016, .055, .03, STEEL));
        var leg = new Shape()
                .add(box(0, -.01, 0, .09, .09, .09, FRAME))
                .add(box(0, -.22, 0, .045, .36, .045, STEEL))
                .add(box(0, -.2, .028, .09, .22, .02, CERAMIC))
                .add(box(0, -.44, 0, .085, .085, .085, FRAME))
                .add(box(0, -.44, .047, .05, .05, .012, CYAN))
                .add(box(0, -.66, 0, .04, .38, .04, STEEL))
                .add(box(0, -.65, .024, .08, .27, .02, CERAMIC))
                .add(box(0, -.88, 0, .065, .05, .065, FRAME))
                .add(box(0, -.935, .055, .1, .05, .24, CERAMIC))
                .both(box(.035, -.95, .205, .03, .02, .07, STEEL));
        return body("hegemonie_soldier", torso, head, arm, leg);
    }

    private static Family hegemonieHeavy() {
        var torso = new Shape()
                .add(box(0, .03, 0, .6, .13, .24, FRAME))
                .add(box(0, .17, 0, .4, .2, .28, STEEL))
                .add(box(0, .32, 0, .34, .14, .28, FRAME))
                .add(box(0, .47, .03, .68, .36, .42, CERAMIC))
                .both(box(.26, .41, .24, .2, .26, .06, CERAMIC).spinY(22.5))
                .add(box(0, .4, .262, .3, .22, .05, TRIM))
                .add(box(0, .47, .29, .22, .03, .01, CYAN))
                .add(box(0, .4, .29, .22, .03, .01, CYAN))
                .add(box(0, .33, .29, .22, .03, .01, CYAN))
                .add(box(0, .69, -.02, .8, .1, .34, STEEL))
                .add(box(0, .51, -.25, .48, .32, .14, STEEL))
                .both(box(.14, .51, -.325, .1, .22, .012, CYAN))
                .both(box(.24, .22, -.14, .05, .36, .05, TRIM).spinX(22.5));
        var head = new Shape()
                .add(box(0, .02, -.04, .17, .1, .18, FRAME))
                .add(box(0, .1, .02, .3, .15, .24, CERAMIC))
                .add(box(0, .19, .0, .2, .04, .18, TRIM))
                .add(box(0, .105, .145, .24, .04, .012, CYAN))
                .both(box(.16, .09, .03, .03, .15, .17, TRIM))
                .add(box(0, .02, .12, .19, .05, .06, STEEL));
        var arm = new Shape()
                .add(box(0, .08, 0, .38, .28, .38, CERAMIC))
                .add(box(0, .235, 0, .28, .05, .28, CERAMIC))
                .add(box(0, .195, .165, .3, .03, .14, TRIM).spinX(45))
                .add(box(0, .195, -.165, .3, .03, .14, TRIM).spinX(-45))
                .add(box(0, -.08, 0, .32, .07, .32, STEEL))
                .add(box(0, -.22, 0, .16, .22, .16, FRAME))
                .both(box(.1, -.22, 0, .03, .24, .06, TRIM))
                .add(box(0, -.44, 0, .27, .26, .27, CERAMIC))
                .add(box(0, -.44, .14, .19, .09, .012, CYAN))
                .add(box(0, -.44, -.14, .19, .09, .012, TRIM))
                .add(box(0, -.59, 0, .23, .05, .23, FRAME))
                .both(box(.06, -.72, 0, .065, .22, .065, STEEL))
                .add(box(0, -.68, 0, .05, .13, .17, FRAME))
                .both(box(.06, -.84, 0, .085, .035, .085, FRAME))
                .both(box(.06, -.86, 0, .035, .008, .035, CYAN));
        var leg = new Shape()
                .add(box(0, -.02, 0, .2, .17, .2, FRAME))
                .add(box(0, -.17, .07, .15, .32, .16, STEEL).spinX(-22.5))
                .add(box(0, -.16, .155, .2, .22, .04, CERAMIC).spinX(-22.5))
                .add(box(0, -.33, .125, .19, .13, .19, FRAME))
                .add(box(0, -.33, .224, .1, .05, .012, CYAN))
                .add(box(0, -.51, .06, .13, .28, .14, STEEL).spinX(22.5))
                .add(box(0, -.5, .135, .2, .26, .04, CERAMIC).spinX(22.5))
                .both(box(.085, -.38, -.1, .028, .56, .028, TRIM))
                .add(box(0, -.68, -.01, .19, .08, .2, FRAME))
                .add(box(0, -.745, .05, .34, .07, .44, CERAMIC))
                .both(box(.11, -.76, .3, .09, .04, .11, STEEL))
                .add(box(0, -.76, -.2, .1, .04, .09, STEEL));
        return body("hegemonie_heavy", torso, head, arm, leg);
    }

    private static Family hegemonieScout() {
        var torso = new Shape()
                .add(box(0, .02, 0, .18, .09, .12, STEEL))
                .add(box(0, .17, 0, .055, .22, .055, FRAME))
                .add(box(0, .34, 0, .24, .2, .15, CERAMIC))
                .both(box(.08, .34, .085, .08, .14, .02, TRIM))
                .add(box(0, .34, .096, .025, .15, .01, CYAN))
                .add(box(0, .47, 0, .32, .045, .09, STEEL))
                .add(box(0, .34, -.1, .13, .16, .05, STEEL))
                .add(box(0, .52, 0, .03, .1, .03, FRAME))
                .add(box(.04, .72, -.13, .024, .62, .024, STEEL))
                .add(box(-.04, .62, -.13, .024, .42, .024, STEEL))
                .add(box(0, .54, -.13, .024, .3, .024, STEEL))
                .add(box(.04, .84, -.13, .1, .02, .02, TRIM))
                .add(box(.04, .94, -.13, .07, .02, .02, TRIM))
                .add(box(-.04, .72, -.13, .08, .02, .02, TRIM))
                .add(box(.04, 1.035, -.13, .03, .03, .03, CYAN))
                .add(box(-.04, .835, -.13, .03, .03, .03, CYAN));
        var head = new Shape()
                .add(box(0, .03, -.01, .05, .05, .05, FRAME))
                .add(box(0, .09, .05, .14, .09, .2, CERAMIC))
                .add(box(0, .075, .17, .09, .06, .1, TRIM).spinX(22.5))
                .add(box(0, .085, .226, .06, .022, .012, CYAN))
                .both(box(.08, .12, -.01, .014, .1, .2, CERAMIC).spinX(-22.5))
                .add(box(0, .15, -.01, .03, .05, .17, STEEL));
        var arm = new Shape()
                .add(box(0, 0, 0, .07, .07, .07, FRAME))
                .add(box(0, -.15, 0, .032, .23, .032, STEEL))
                .add(box(0, -.13, .021, .045, .12, .01, CERAMIC))
                .add(box(0, -.28, 0, .055, .05, .055, FRAME))
                .add(box(0, -.42, 0, .03, .23, .03, STEEL))
                .add(box(0, -.42, 0, .052, .12, .052, CERAMIC))
                .add(box(0, -.55, 0, .05, .04, .05, FRAME))
                .both(box(.018, -.595, 0, .012, .05, .025, STEEL));
        var leg = new Shape()
                .add(box(0, 0, 0, .08, .08, .08, FRAME))
                .add(box(0, -.17, .1, .05, .36, .05, STEEL).spinX(-45))
                .add(box(0, -.15, .12, .07, .24, .02, CERAMIC).spinX(-45))
                .add(box(0, -.31, .225, .075, .075, .075, FRAME))
                .add(box(0, -.47, .13, .04, .38, .04, STEEL).spinX(22.5))
                .add(box(0, -.48, .14, .06, .22, .02, CERAMIC).spinX(22.5))
                .add(box(0, -.63, .05, .06, .06, .06, FRAME))
                .add(box(0, -.73, .06, .032, .22, .032, STEEL).spinX(-22.5))
                .add(box(0, -.83, .09, .05, .04, .05, FRAME))
                .add(box(0, -.84, .15, .03, .03, .14, CERAMIC))
                .both(box(.04, -.84, .13, .025, .03, .11, CERAMIC).spinY(22.5))
                .add(box(0, -.84, .02, .025, .03, .1, STEEL));
        return body("hegemonie_scout", torso, head, arm, leg);
    }

    private static Family hegemonieMedic() {
        var torso = new Shape()
                .add(box(0, .03, 0, .2, .11, .14, STEEL))
                .add(box(0, .17, 0, .1, .2, .08, FRAME))
                .add(box(0, .36, 0, .28, .2, .17, CERAMIC))
                .add(box(0, .3, .02, .22, .08, .17, CERAMIC))
                .add(box(0, .38, .09, .13, .1, .012, TRIM))
                .add(box(0, .38, .098, .09, .025, .006, CYAN))
                .add(box(0, .47, 0, .36, .04, .1, STEEL))
                .add(box(0, .55, 0, .04, .1, .04, FRAME))
                .add(box(0, .36, -.1, .18, .14, .04, STEEL))
                .add(box(.17, .56, -.34, .26, .06, .26, CERAMIC))
                .add(box(.17, .56, -.34, .2, .065, .2, CERAMIC).spinY(45))
                .add(box(.17, .52, -.34, .16, .02, .16, CYAN))
                .add(box(.17, .615, -.34, .1, .05, .1, STEEL))
                .add(box(.17, .49, -.34, .04, .04, .04, FRAME))
                .add(box(.17, .67, -.34, .018, .07, .018, STEEL))
                .add(box(.17, .71, -.34, .03, .018, .03, CYAN));
        var head = new Shape()
                .add(box(0, .01, 0, .07, .05, .07, FRAME))
                .add(box(0, .11, 0, .2, .15, .2, CERAMIC))
                .add(box(0, .2, 0, .13, .04, .13, CERAMIC))
                .add(box(0, .115, .104, .15, .05, .012, LENS))
                .add(box(0, .115, .112, .05, .025, .006, CYAN))
                .both(box(.11, .115, 0, .03, .08, .08, TRIM));
        var arm = new Shape()
                .add(box(0, 0, 0, .08, .08, .08, FRAME))
                .add(box(0, .025, 0, .1, .045, .1, CERAMIC))
                .add(box(0, -.15, 0, .05, .24, .05, CERAMIC))
                .add(box(0, -.29, 0, .06, .05, .06, FRAME))
                .add(box(0, -.43, 0, .055, .22, .055, CERAMIC))
                .add(box(0, -.43, .03, .03, .1, .01, CYAN))
                .add(box(0, -.56, 0, .06, .05, .06, FRAME))
                .both(box(.022, -.61, 0, .016, .05, .03, STEEL));
        var toolArm = new Shape()
                .add(box(0, 0, 0, .08, .08, .08, FRAME))
                .add(box(0, .025, 0, .1, .045, .1, CERAMIC))
                .add(box(0, -.15, 0, .05, .24, .05, CERAMIC))
                .add(box(0, -.29, 0, .06, .05, .06, FRAME))
                .add(box(0, -.4, 0, .09, .17, .09, STEEL))
                .add(box(0, -.4, .048, .05, .08, .01, CYAN))
                .add(box(0, -.5, 0, .12, .03, .12, FRAME))
                .add(box(0, -.63, 0, .024, .25, .024, TRIM))
                .add(box(0, -.76, 0, .014, .03, .014, CYAN))
                .both(box(.07, -.59, 0, .018, .15, .03, STEEL).spinZ(-22.5))
                .add(box(0, -.57, .07, .03, .12, .018, STEEL).spinX(22.5))
                .add(box(0, -.57, -.07, .03, .12, .018, STEEL).spinX(-22.5));
        var leg = new Shape()
                .add(box(0, -.01, 0, .085, .085, .085, FRAME))
                .add(box(0, -.22, 0, .08, .34, .08, CERAMIC))
                .add(box(0, -.44, 0, .085, .08, .085, FRAME))
                .add(box(0, -.66, 0, .07, .36, .07, CERAMIC))
                .add(box(0, -.56, .037, .03, .1, .01, CYAN))
                .add(box(0, -.88, 0, .06, .05, .06, FRAME))
                .add(box(0, -.93, .04, .11, .05, .22, TRIM));
        return body("hegemonie_medic", torso, head, arm, leg, toolArm);
    }

    private static Family kolonienSoldier() {
        var torso = new Shape()
                .add(box(0, .05, 0, .42, .13, .23, DRAB))
                .add(box(0, .0, 0, .44, .045, .25, LEATHER))
                .add(box(0, 0, .13, .08, .05, .015, SCRAP))
                .add(box(0, .28, 0, .46, .36, .24, KHAKI))
                .add(box(0, .28, .125, .36, .24, .02, LEATHER))
                .both(box(.1, .24, .14, .12, .11, .025, KHAKI))
                .add(box(0, .28, .0, .47, .03, .25, LEATHER).spinZ(45))
                .add(box(0, .47, 0, .3, .05, .22, KHAKI))
                .add(box(0, .28, -.135, .34, .3, .04, LEATHER))
                .add(box(0, .52, 0, .14, .06, .14, ORANGE))
                .add(box(.04, .46, .09, .08, .1, .02, ORANGE));
        var head = new Shape()
                .add(box(0, .02, 0, .1, .08, .1, SKIN))
                .add(box(0, .15, 0, .22, .22, .22, SKIN))
                .both(box(.05, .175, .113, .04, .024, .006, FRAME))
                .add(box(0, .205, .112, .16, .018, .006, LEATHER))
                .add(box(0, .135, .116, .03, .04, .012, SKIN))
                .add(box(0, .09, .113, .07, .012, .006, LEATHER))
                .add(box(0, .3, -.01, .27, .14, .27, TANK))
                .add(box(0, .39, -.01, .2, .05, .2, TANK))
                .add(box(0, .232, -.005, .295, .022, .3, TANK))
                .both(box(.128, .15, -.03, .03, .13, .16, TANK))
                .both(box(.12, .07, .02, .012, .1, .03, LEATHER))
                .add(box(0, .3, .142, .15, .05, .02, LEATHER))
                .both(box(.042, .3, .152, .055, .045, .012, GOGGLE))
                .add(box(.1, .37, -.02, .02, .06, .1, ORANGE));
        var arm = new Shape()
                .add(box(0, -.02, 0, .13, .13, .13, KHAKI))
                .add(box(0, -.17, 0, .115, .22, .115, KHAKI))
                .add(box(0, -.16, 0, .125, .05, .125, ORANGE))
                .add(box(0, -.38, 0, .1, .22, .1, KHAKI))
                .add(box(0, -.48, 0, .11, .05, .11, LEATHER))
                .add(box(0, -.56, 0, .09, .1, .09, LEATHER));
        var leg = new Shape()
                .add(box(0, -.21, 0, .17, .42, .18, DRAB))
                .add(box(.0, -.3, .094, .12, .1, .012, KHAKI))
                .add(box(0, -.56, 0, .15, .28, .16, DRAB))
                .add(box(0, -.74, 0, .16, .12, .17, LEATHER))
                .add(box(0, -.87, .015, .165, .15, .2, LEATHER))
                .add(box(0, -.925, .035, .17, .05, .26, RUBBER));
        return body("kolonien_soldier", torso, head, arm, leg);
    }

    private static Family kolonienHeavy() {
        var torso = new Shape()
                .add(box(0, .05, 0, .5, .17, .3, OILED))
                .add(box(0, .3, 0, .52, .4, .3, DRAB))
                .add(box(0, .34, .165, .56, .36, .05, ORANGE))
                .add(box(0, .14, .165, .46, .08, .05, HAZARD))
                .add(box(0, .49, .165, .34, .04, .055, SCRAP))
                .both(box(.3, .34, 0, .05, .5, .36, SCRAP))
                .add(box(0, .55, 0, .7, .08, .3, SCRAP))
                .add(box(0, .16, -.18, .4, .05, .08, SCRAP))
                .add(box(0, .36, -.18, .44, .4, .07, OILED))
                .add(drumY(.13, .55, -.3, .23, .78, CANISTER))
                .add(drumY(-.13, .55, -.3, .23, .78, CANISTER))
                .both(box(.13, .955, -.3, .1, .05, .1, SCRAP))
                .both(box(.13, .985, -.3, .05, .03, .05, ORANGE))
                .both(box(.13, .36, -.3, .245, .04, .245, ORANGE).spinY(45))
                .both(box(.13, .74, -.3, .245, .04, .245, ORANGE).spinY(45))
                .add(box(.0, .62, -.1, .05, .2, .05, SCRAP));
        var head = new Shape()
                .add(box(0, .02, 0, .13, .08, .13, SKIN))
                .add(box(0, .14, 0, .23, .2, .23, SKIN))
                .add(box(0, .15, .118, .17, .05, .012, LENS))
                .add(box(0, .07, .118, .15, .05, .02, RUBBER))
                .add(box(0, .28, -.005, .28, .1, .28, ORANGE))
                .add(box(0, .24, .03, .33, .02, .35, ORANGE))
                .add(box(0, .28, .15, .07, .05, .03, SCRAP))
                .add(box(0, .28, .167, .05, .035, .006, EMBER));
        var arm = new Shape()
                .add(box(0, .0, 0, .2, .2, .2, SCRAP))
                .add(box(0, .1, 0, .26, .06, .26, ORANGE))
                .add(box(0, -.2, 0, .13, .24, .13, DRAB))
                .both(box(.085, -.2, 0, .025, .28, .06, SCRAP))
                .add(box(0, -.36, 0, .17, .08, .17, OILED))
                .add(box(0, -.46, 0, .15, .18, .15, SCRAP))
                .add(box(0, -.46, .08, .1, .1, .012, ORANGE))
                .add(box(0, -.58, 0, .15, .12, .15, LEATHER));
        var leg = new Shape()
                .add(box(0, -.1, 0, .2, .26, .2, DRAB))
                .both(box(.115, -.2, 0, .03, .42, .07, SCRAP))
                .add(box(0, -.33, .08, .15, .13, .07, OILED))
                .add(box(0, -.45, 0, .18, .2, .19, DRAB))
                .add(box(0, -.68, .01, .25, .28, .26, ORANGE))
                .add(box(0, -.56, .01, .27, .04, .28, SCRAP))
                .add(box(0, -.84, .06, .28, .08, .38, RUBBER))
                .add(box(0, -.86, .06, .3, .04, .42, SCRAP));
        return body("kolonien_heavy", torso, head, arm, leg);
    }

    private static Family kolonienScout() {
        var torso = new Shape()
                .add(box(0, .1, 0, .3, .2, .19, DRAB))
                .add(box(0, .3, 0, .34, .26, .2, DRAB))
                .add(box(0, .1, 0, .46, .34, .32, SAND))
                .add(box(0, .33, .135, .44, .4, .03, SAND).spinX(-22.5))
                .add(box(0, .33, -.135, .44, .4, .03, SAND).spinX(22.5))
                .both(box(.19, .33, 0, .03, .4, .34, SAND).spinZ(22.5))
                .add(box(0, .03, .25, .64, .36, .03, SAND).spinX(-22.5))
                .add(box(0, .03, -.25, .64, .36, .03, SAND).spinX(22.5))
                .both(box(.26, .03, 0, .03, .36, .62, SAND).spinZ(22.5))
                .add(box(0, -.146, .325, .64, .02, .032, KHAKI).spinX(-22.5))
                .add(box(0, -.146, -.325, .64, .02, .032, KHAKI).spinX(22.5))
                .both(box(.335, -.146, 0, .032, .02, .62, KHAKI).spinZ(22.5))
                .both(box(.08, -.175, .35, .03, .045, .025, KHAKI), box(.22, -.175, .35, .03, .045, .025, KHAKI))
                .both(box(.08, -.175, -.35, .03, .045, .025, KHAKI), box(.22, -.175, -.35, .03, .045, .025, KHAKI))
                .add(box(0, .46, 0, .26, .1, .24, WRAP))
                .add(box(.0, .14, .32, .14, .08, .012, ORANGE).spinX(-22.5))
                .add(box(.0, .28, -.2, .07, .3, .02, WRAP).spinX(22.5));
        var head = new Shape()
                .add(box(0, .02, 0, .12, .07, .12, WRAP))
                .add(box(0, .15, 0, .22, .22, .22, WRAP))
                .add(box(0, .27, -.015, .25, .06, .24, SAND))
                .add(box(0, .325, -.04, .16, .06, .17, SAND))
                .both(box(.118, .17, -.03, .02, .22, .19, SAND))
                .add(box(0, .17, .113, .2, .05, .008, LEATHER))
                .both(box(.052, .17, .121, .06, .055, .012, GOGGLE))
                .add(box(0, .17, .118, .03, .02, .012, SCRAP))
                .add(box(0, .07, .05, .26, .06, .22, WRAP))
                .add(box(.0, .15, -.13, .07, .22, .02, WRAP));
        var arm = new Shape()
                .add(box(0, -.04, 0, .1, .1, .1, SAND))
                .add(box(0, -.2, 0, .09, .24, .09, DRAB))
                .add(box(0, -.4, 0, .085, .2, .085, DRAB))
                .add(box(0, -.34, 0, .092, .025, .092, WRAP))
                .add(box(0, -.43, 0, .092, .025, .092, WRAP))
                .add(box(0, -.54, 0, .08, .09, .08, LEATHER));
        var leg = new Shape()
                .add(box(0, -.21, 0, .13, .42, .14, DRAB))
                .add(box(0, -.56, 0, .115, .28, .125, DRAB))
                .add(box(0, -.48, 0, .122, .03, .132, KHAKI))
                .add(box(0, -.56, 0, .122, .03, .132, KHAKI))
                .add(box(0, -.64, 0, .122, .03, .132, KHAKI))
                .add(box(0, -.76, 0, .12, .1, .13, LEATHER))
                .add(box(0, -.88, .015, .125, .13, .17, LEATHER))
                .add(box(0, -.93, .03, .13, .04, .22, RUBBER))
                .add(box(.07, -.2, .0, .02, .1, .1, LEATHER));
        return body("kolonien_scout", torso, head, arm, leg);
    }

    private static Family kolonienMedic() {
        var torso = new Shape()
                .add(box(0, .05, 0, .4, .13, .22, DRAB))
                .add(box(0, .0, 0, .42, .045, .24, LEATHER))
                .add(box(0, .28, 0, .42, .36, .22, WRAP))
                .add(box(0, .34, .118, .1, .24, .018, ORANGE).spinZ(45))
                .add(box(0, .47, 0, .28, .05, .2, WRAP))
                .add(box(0, .34, -.25, .46, .54, .26, LEATHER))
                .add(box(0, .44, -.25, .47, .05, .27, ORANGE))
                .add(box(0, .2, -.385, .36, .16, .02, KHAKI))
                .add(box(0, .34, -.39, .2, .05, .012, ORANGE))
                .add(box(0, .69, -.25, .54, .17, .17, KHAKI))
                .both(box(.27, .69, -.25, .012, .13, .13, ORANGE))
                .both(box(.29, .24, -.22, .1, .28, .18, KHAKI))
                .both(box(.14, .28, .125, .09, .1, .03, LEATHER))
                .add(box(-.27, -.01, .02, .11, .15, .17, LEATHER))
                .add(box(-.33, -.01, .02, .012, .07, .1, ORANGE));
        var head = new Shape()
                .add(box(0, .02, 0, .1, .08, .1, SKIN))
                .add(box(0, .15, 0, .22, .22, .22, SKIN))
                .both(box(.05, .175, .113, .04, .024, .006, FRAME))
                .add(box(0, .09, .114, .2, .09, .012, WRAP))
                .add(box(0, .27, -.005, .25, .07, .25, DRAB))
                .add(box(0, .25, .15, .22, .022, .1, DRAB))
                .add(box(0, .3, .0, .2, .04, .2, DRAB))
                .add(box(.0, .275, .129, .1, .03, .006, ORANGE));
        var arm = new Shape()
                .add(box(0, -.02, 0, .125, .125, .125, WRAP))
                .add(box(0, -.17, 0, .11, .22, .11, WRAP))
                .add(box(0, -.15, 0, .12, .07, .12, ORANGE))
                .add(box(0, -.38, 0, .095, .22, .095, WRAP))
                .add(box(0, -.46, 0, .105, .07, .105, LEATHER))
                .add(box(0, -.56, 0, .09, .1, .09, RUBBER));
        var leg = new Shape()
                .add(box(0, -.21, 0, .165, .42, .175, DRAB))
                .both(box(.087, -.26, .0, .03, .12, .11, LEATHER))
                .add(box(0, -.56, 0, .14, .28, .155, DRAB))
                .add(box(0, -.74, 0, .15, .12, .165, LEATHER))
                .add(box(0, -.87, .015, .155, .15, .19, LEATHER))
                .add(box(0, -.925, .035, .16, .05, .25, RUBBER));
        return body("kolonien_medic", torso, head, arm, leg);
    }

    private static Shape kolonienSpeeder(boolean damaged) {
        var shape = new Shape()
                .add(box(0, .28, -.05, .72, .15, 1.95, SCRAP))
                .add(box(0, .17, -.05, .5, .08, 1.7, RUSTED))
                .add(box(0, .3, 1.0, .58, .12, .3, RUSTED))
                .add(box(0, .28, 1.24, .4, .09, .26, ORANGE))
                .add(box(0, .25, 1.4, .24, .06, .14, HAZARD))
                .add(box(0, .37, 1.02, .5, .04, .5, ORANGE).spinX(22.5))
                .add(box(0, .48, -.12, .36, .1, .52, LEATHER))
                .add(box(0, .57, -.42, .34, .24, .1, LEATHER))
                .add(box(0, .4, .34, .42, .12, .3, OILED))
                .add(box(0, .52, .52, .42, .03, .22, ORANGE).spinX(45))
                .add(box(0, .62, .5, .04, .2, .04, SCRAP))
                .add(box(0, .71, .5, .46, .035, .035, OILED))
                .both(box(.23, .71, .5, .05, .05, .05, RUBBER))
                .add(drumZ(0, .62, -.92, .86, 1.0, SCRAP))
                .add(drumZ(0, .62, -.56, .94, .1, RUSTED))
                .add(drumZ(0, .62, -.92, .94, .08, OILED))
                .add(drumZ(0, .62, -1.26, .94, .1, RUSTED))
                .add(drumZ(0, .62, -.42, .66, .03, FRAME))
                .add(drumZ(0, .62, -1.38, .72, .16, OILED))
                .add(drumZ(0, .62, -1.462, .46, .012, FURNACE))
                .add(drumZ(0, .62, -1.47, .22, .012, FURNACE_CORE))
                .add(box(0, .62, -.4, .13, .13, .03, SCRAP))
                .add(box(0, .62, -.402, .6, .04, .015, SCRAP))
                .add(box(0, .62, -.402, .6, .04, .014, SCRAP).spinZ(45))
                .add(box(0, .62, -.402, .6, .04, .013, SCRAP).spinZ(-45))
                .add(box(0, .62, -.402, .04, .6, .012, SCRAP))
                .both(box(.52, .28, -.3, .34, .08, .5, SCRAP))
                .both(box(.64, .28, .1, .2, .18, 1.25, ORANGE))
                .both(box(.64, .28, .74, .14, .12, .12, RUSTED))
                .both(box(.64, .28, -.53, .12, .1, .04, EMBER))
                .both(box(.64, .18, .1, .24, .04, 1.1, OILED))
                .both(box(.5, .82, -.92, .08, .08, .7, SCRAP))
                .both(box(.3, .96, -.92, .07, .07, .5, OILED));
        if (!damaged) {
            return shape;
        }
        var torn = shape.burnt(.55);
        return torn.add(
                box(.42, .22, .6, .36, .025, .5, RUSTED.burnt()).spinX(-22.5),
                box(-.42, .42, -.2, .3, .025, .36, ORANGE.burnt()).spinZ(45),
                box(0, .6, -.5, .5, .4, .02, FRAME),
                box(.3, 1.02, -.8, .22, .02, .3, SCRAP.burnt()).spinZ(22.5),
                box(-.2, .34, 1.1, .2, .025, .34, ORANGE.burnt()).spinY(22.5));
    }

    private static Shape hegemonieSpeeder(boolean damaged) {
        var shape = new Shape()
                .add(box(0, .3, -.1, .66, .14, 1.9, CERAMIC))
                .add(box(0, .205, -.1, .46, .05, 1.8, STEEL))
                .add(box(0, .19, .3, .3, .012, .9, CYAN))
                .add(box(0, .3, 1.0, .42, .1, .5, CERAMIC))
                .add(box(0, .28, 1.3, .22, .06, .3, CERAMIC))
                .add(box(0, .275, 1.44, .1, .04, .1, TRIM))
                .add(box(0, .38, .95, .34, .03, .56, CERAMIC).spinX(22.5))
                .add(box(0, .4, .98, .03, .012, .5, CYAN).spinX(22.5))
                .both(box(.46, .285, -.15, .24, .055, 1.25, CERAMIC).spinY(-22.5))
                .both(box(.48, .315, -.15, .03, .012, 1.15, CYAN).spinY(-22.5))
                .both(box(.335, .3, -.3, .012, .03, 1.2, CYAN))
                .add(box(0, .46, -.2, .32, .1, .5, FRAME))
                .add(box(0, .57, -.48, .3, .24, .08, TRIM))
                .both(box(.25, .46, -.15, .14, .18, .7, CERAMIC))
                .add(box(0, .44, .28, .46, .14, .3, CERAMIC))
                .add(box(0, .57, .39, .36, .18, .025, LENS).spinX(-45))
                .add(box(0, .47, -.82, .56, .22, .6, CERAMIC))
                .add(box(0, .59, -.85, .3, .02, .5, TRIM))
                .add(drumZ(.24, .33, -1.15, .3, .6, STEEL))
                .add(drumZ(-.24, .33, -1.15, .3, .6, STEEL))
                .both(box(.24, .33, -1.455, .2, .2, .012, CYAN))
                .both(box(.24, .33, -.88, .34, .08, .02, CYAN))
                .both(box(.37, .62, -.95, .03, .34, .62, CERAMIC).spinZ(-22.5).about(.28, .45, -.95))
                .both(box(.37, .86, -1.08, .03, .18, .46, CERAMIC).spinZ(-22.5).about(.28, .45, -.95))
                .both(box(.37, 1.0, -1.18, .03, .12, .3, TRIM).spinZ(-22.5).about(.28, .45, -.95))
                .both(box(.37, .62, -.635, .012, .32, .012, CYAN).spinZ(-22.5).about(.28, .45, -.95));
        if (!damaged) {
            return shape;
        }
        var torn = shape.burnt(.6);
        return torn.add(
                box(.3, .44, -.3, .34, .02, .5, CERAMIC.burnt()).spinZ(-22.5),
                box(-.4, .2, .6, .3, .02, .5, STEEL.burnt()).spinX(22.5),
                box(0, .36, .7, .2, .1, .3, FRAME),
                box(-.55, .46, -.7, .22, .02, .3, CERAMIC.burnt()).spinZ(45));
    }

    private static Family speeders(String faction, Shape intact, Shape damaged) {
        return new Family("speeder_" + faction, 32, false, List.of(
                new Model("sternenfront/speeder/" + faction, intact),
                new Model("sternenfront/speeder/" + faction + "_damaged", damaged)));
    }

    private static Family hegemonieDropship() {
        var hull = new Shape()
                .add(box(0, .62, -.1, .9, .34, 2.1, CERAMIC))
                .add(box(0, .44, -.1, .7, .1, 1.9, STEEL))
                .add(box(0, .6, 1.04, .62, .24, .3, CERAMIC))
                .add(box(0, .57, 1.26, .36, .15, .24, CERAMIC))
                .add(box(0, .72, .95, .5, .16, .5, CERAMIC).spinX(22.5))
                .add(box(0, .78, .92, .34, .1, .03, LENS).spinX(-45).shifted(0, .02, .2))
                .add(box(0, .87, -.3, .36, .18, 1.5, CERAMIC))
                .add(box(0, .97, -.3, .05, .04, 1.3, CYAN))
                .add(box(0, 1.19, -1.1, .05, .5, .6, CERAMIC))
                .add(box(0, 1.3, -1.25, .06, .3, .2, TRIM))
                .both(box(.72, .6, -.5, .7, .06, 1.3, CERAMIC).spinY(-22.5))
                .both(box(1.0, .6, -.72, .24, .07, .5, TRIM).spinY(-22.5))
                .both(box(.76, .635, -.46, .03, .012, 1.1, CYAN).spinY(-22.5))
                .both(box(.56, .52, -.75, .34, .26, .7, STEEL))
                .both(box(.56, .52, -1.11, .24, .18, .012, CYAN))
                .both(box(.56, .385, -.75, .24, .012, .4, CYAN))
                .both(box(.46, .6, .3, .02, .22, .6, FRAME))
                .both(box(.461, .6, .3, .012, .03, .5, CYAN))
                .both(box(.33, .22, .55, .06, .4, .06, FRAME))
                .both(box(.33, .03, .55, .2, .06, .26, STEEL))
                .both(box(.4, .22, -.75, .07, .4, .07, FRAME))
                .both(box(.4, .03, -.75, .22, .06, .3, STEEL))
                .add(box(0, .4, 0, .6, .02, .5, FRAME));
        return new Family("dropship_hegemonie", 72, false, List.of(new Model("sternenfront/dropship/hegemonie", hull)));
    }

    private static Family kolonienDropship() {
        var hull = new Shape()
                .add(box(0, .55, -.15, 1.05, .5, 1.9, SCRAP))
                .add(box(0, .28, -.15, .85, .08, 1.7, RUSTED))
                .add(box(0, .86, -.3, .8, .14, 1.3, RUSTED))
                .add(box(0, .56, .95, .9, .44, .3, ORANGE))
                .add(box(0, .5, 1.14, .7, .3, .1, HAZARD))
                .add(box(0, .75, 1.0, .72, .24, .3, RUSTED))
                .add(box(0, .78, 1.15, .52, .13, .03, GOGGLE))
                .add(box(0, .6, 1.25, .36, .22, .2, ORANGE))
                .both(box(.56, .55, .3, .05, .44, .9, ORANGE))
                .both(box(.585, .55, -.4, .02, .34, .3, FRAME))
                .both(box(.585, .75, -.4, .02, .03, .34, HAZARD))
                .add(drumZ(.74, .58, -.7, .44, 1.2, SCRAP))
                .add(drumZ(-.74, .58, -.7, .44, 1.2, SCRAP))
                .add(drumZ(.74, .58, -.3, .5, .05, OILED))
                .add(drumZ(-.74, .58, -.3, .5, .05, OILED))
                .add(drumZ(.74, .58, -1.0, .5, .05, OILED))
                .add(drumZ(-.74, .58, -1.0, .5, .05, OILED))
                .add(drumZ(.74, .58, -1.31, .34, .012, FURNACE))
                .add(drumZ(-.74, .58, -1.31, .34, .012, FURNACE))
                .add(drumZ(.74, .58, -1.32, .16, .012, FURNACE_CORE))
                .add(drumZ(-.74, .58, -1.32, .16, .012, FURNACE_CORE))
                .both(box(.74, .34, -.7, .26, .012, .5, FURNACE))
                .add(box(0, .24, .55, .3, .012, .4, FURNACE))
                .add(box(0, 1.03, -.5, .5, .2, .7, OILED))
                .both(box(.3, 1.2, -.5, .05, .22, .05, SCRAP))
                .both(box(.45, .22, .55, .08, .44, .08, SCRAP))
                .both(box(.45, .03, .55, .26, .06, .32, RUSTED))
                .both(box(.56, .22, -.8, .1, .44, .1, SCRAP))
                .both(box(.56, .03, -.8, .3, .06, .36, RUSTED))
                .both(box(.53, .36, .2, .03, .2, .06, OILED).spinZ(22.5));
        return new Family("dropship_kolonien", 72, false, List.of(new Model("sternenfront/dropship/kolonien", hull)));
    }

    private static int argb(int alpha, int colour) {
        return alpha << 24 | colour;
    }

    private static int[] ring(int size, int height, Random random) {
        var pixels = new int[size * height];
        long seed = random.nextLong();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < size; x++) {
                double reach = Math.hypot(x + .5 - size / 2.0, y + .5 - height / 2.0) / (size / 2.0);
                double angle = Math.atan2(y - height / 2.0, x - size / 2.0);
                double wobble = (cell((int) (angle * 9), 0, seed) - .5) * .05;
                double band = reach + wobble;
                if (band > .84 && band < .99) {
                    pixels[y * size + x] = argb(band > .94 ? 110 : band > .9 ? 190 : 240, band > .88 && band < .94 ? 0xFFFFFF : 0xD6D6D6);
                } else if (band > .72 && band <= .84) {
                    pixels[y * size + x] = argb(band > .78 ? 96 : 48, 0xBEBEBE);
                }
            }
        }
        return pixels;
    }

    private static Drawing star(int core, int tip) {
        return (width, height, random) -> {
            var pixels = new int[width * height];
            long seed = random.nextLong();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    double dx = Math.abs(x + .5 - width / 2.0) / (width / 2.0);
                    double dy = Math.abs(y + .5 - height / 2.0) / (height / 2.0);
                    double at = Math.sqrt(dx) + Math.sqrt(dy) + (cell(x / 2, y / 2, seed) - .5) * .1;
                    if (at < .55) {
                        pixels[y * width + x] = argb(255, 0xFFFFFF);
                    } else if (at < .72) {
                        pixels[y * width + x] = argb(245, mix(core, 0xFFFFFF, .5));
                    } else if (at < .88) {
                        pixels[y * width + x] = argb(215, core);
                    } else if (at < 1) {
                        pixels[y * width + x] = argb(130, tip);
                    }
                }
            }
            return pixels;
        };
    }

    private static int[] scorchMark(int size, int height, Random random) {
        var pixels = new int[size * height];
        long seed = random.nextLong();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < size; x++) {
                double reach = Math.hypot(x + .5 - size / 2.0, y + .5 - height / 2.0) / (size / 2.0);
                double angle = Math.atan2(y - height / 2.0, x - size / 2.0);
                double rays = cell((int) ((angle + Math.PI) * 7), 0, seed) * .34;
                double blot = cell(x / 3, y / 3, seed + 1) * .16;
                double at = reach + blot - rays;
                if (at < .34) {
                    pixels[y * size + x] = argb(245, CHAR[(int) (cell(x / 2, y / 2, seed + 2) * CHAR.length)]);
                } else if (at < .56) {
                    pixels[y * size + x] = argb(200, 0x201A15);
                } else if (at < .74) {
                    pixels[y * size + x] = argb(120, 0x2E2720);
                } else if (at < .86) {
                    pixels[y * size + x] = argb(56, 0x3A332B);
                }
            }
        }
        return pixels;
    }

    /** Half a cut's width down its length, {@code along} from -1 to 1: even through the middle, tapering to a point. */
    private static double taper(double along) {
        return Math.max(0, 1 - Math.pow(Math.abs(along), 3));
    }

    /** How far a texel lies from a cut's slightly hand-drawn centre line, in units of half its width there; beyond 1 is outside. */
    private static double fromCut(int x, int y, int width, int height, double length, double halfWidth) {
        double along = (x + .5 - width / 2.0) / (width / 2.0);
        double centre = height / 2.0 + Math.sin(along * 2.6 + .4) * height * .02;
        double half = taper(along / length) * halfWidth * height;
        return half <= 0 ? Double.MAX_VALUE : Math.abs(y + .5 - centre) / half;
    }

    private static int[] slashEdge(int width, int height, Random random) {
        var pixels = new int[width * height];
        long seed = random.nextLong();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double at = fromCut(x, y, width, height, .99, .3) + (cell(x / 3, y / 2, seed) - .5) * .34;
                if (at < .34) {
                    pixels[y * width + x] = argb(250, CHAR[(int) (cell(x, y, seed + 1) * CHAR.length)]);
                } else if (at < .6) {
                    pixels[y * width + x] = argb(232, 0x1C130E);
                } else if (at < .78) {
                    pixels[y * width + x] = argb(178, 0x2C1E15);
                } else if (at < .95) {
                    pixels[y * width + x] = argb(96, 0x33281E);
                } else if (at < 1.1) {
                    pixels[y * width + x] = argb(40, 0x3A332B);
                }
            }
        }
        return pixels;
    }

    private static int[] slashGlow(int width, int height, Random random) {
        var pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double at = fromCut(x, y, width, height, .95, .18);
                if (at < .5) {
                    pixels[y * width + x] = argb(250, 0xFFFFFF);
                } else if (at < .78) {
                    pixels[y * width + x] = argb(215, 0xFFFFFF);
                } else if (at < 1) {
                    pixels[y * width + x] = argb(120, 0xD6D6D6);
                }
            }
        }
        return pixels;
    }

    private static int[] slashCore(int width, int height, Random random) {
        var pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (fromCut(x, y, width, height, .86, .05) < 1) {
                    pixels[y * width + x] = argb(255, 0xFFFFFF);
                }
            }
        }
        return pixels;
    }

    private static int[] smoke(int width, int height, Random random) {
        var pixels = new int[width * height];
        long seed = random.nextLong();
        int[] greys = {0x7C8084, 0x93979B, 0xA8ACAF, 0xBDC0C2};
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double reach = Math.hypot(x + .5 - width / 2.0, y + .5 - height / 2.0) / (width / 2.0);
                double lumps = cell(x / 4, y / 4, seed) * .22 + cell(x / 2, y / 2, seed + 1) * .08;
                double at = reach + lumps;
                if (at < 1) {
                    int tone = greys[Math.clamp((int) ((1 - at) * 1.2 * greys.length), 0, greys.length - 1)];
                    pixels[y * width + x] = argb(at > .9 ? 70 : at > .75 ? 130 : 185, tone);
                }
            }
        }
        return pixels;
    }

    private static final double SLASH_HEIGHT = .22;

    private static Family effects() {
        var ring = new Sprite("ring", true, true, SternenfrontArt::ring);
        var scorch = new Sprite("scorch", false, false, SternenfrontArt::scorchMark);
        var kolonien = new Sprite("flash_kolonien", true, false, star(0xFF8A2E, 0xFF5A10));
        var hegemonie = new Sprite("flash_hegemonie", true, false, star(0x7CE4FF, 0x25B8EE));
        var puff = new Sprite("smoke", false, false, SternenfrontArt::smoke);
        var melted = new Sprite("slash_edge", false, false, SternenfrontArt::slashEdge);
        var glowing = new Sprite("slash_glow", true, true, SternenfrontArt::slashGlow);
        var core = new Sprite("slash_core", true, false, SternenfrontArt::slashCore);
        return new Family("fx", 96, true, List.of(
                new Model("sternenfront/fx/saber_slash", new Shape().add(
                        box(0, 0, 0, 1, SLASH_HEIGHT, 0, melted).only(Face.NORTH, Face.SOUTH),
                        box(0, 0, 0, 1, SLASH_HEIGHT, .008, glowing).only(Face.NORTH, Face.SOUTH),
                        box(0, 0, 0, 1, SLASH_HEIGHT, .016, core).only(Face.NORTH, Face.SOUTH))),
                new Model("sternenfront/fx/saber_slash_cold", new Shape().add(box(0, 0, 0, 1, SLASH_HEIGHT, 0, melted).only(Face.NORTH, Face.SOUTH))),
                new Model("sternenfront/fx/ring", new Shape().add(box(0, 0, 0, 1, 1, 0, ring).only(Face.NORTH, Face.SOUTH))),
                new Model("sternenfront/fx/scorch", new Shape().add(box(0, 0, 0, 1, 1, 0, scorch).only(Face.NORTH, Face.SOUTH))),
                new Model("sternenfront/fx/flash_kolonien", flash(kolonien, EMBER)),
                new Model("sternenfront/fx/flash_hegemonie", flash(hegemonie, CYAN)),
                new Model("sternenfront/fx/smoke_puff", new Shape()
                        .add(puff(0, 0, 0, 1, puff))
                        .add(puff(.2, .14, .12, .6, puff))
                        .add(puff(-.2, .2, -.1, .66, puff)))));
    }

    /** Three crossed sheets read as a round puff from any side, which a translucent cube does not. */
    private static List<Box> puff(double x, double y, double z, double across, Sprite sprite) {
        return List.of(box(x, y, z, across, across, 0, sprite).only(Face.NORTH, Face.SOUTH),
                box(x, y, z, 0, across, across, sprite).only(Face.EAST, Face.WEST),
                box(x, y, z, across, 0, across, sprite).only(Face.UP, Face.DOWN));
    }

    private static Shape flash(Sprite star, Paint core) {
        return new Shape().add(
                box(0, 0, 0, 1, 1, 0, star).only(Face.NORTH, Face.SOUTH),
                box(0, 0, 0, 0, 1, 1, star).only(Face.EAST, Face.WEST),
                box(0, 0, 0, 1, 0, 1, star).only(Face.UP, Face.DOWN),
                box(0, 0, 0, .8, .8, 0, star).only(Face.NORTH, Face.SOUTH).spinY(45),
                box(0, 0, 0, .8, .8, 0, star).only(Face.NORTH, Face.SOUTH).spinY(-45),
                box(0, 0, 0, .08, .08, .08, core));
    }

    private static Drawing crescent(boolean core) {
        return (width, height, random) -> {
            var pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    double across = (x + .5) * .52 / width - .26;
                    double forward = 1 - (y + .5) * .2 / height;
                    double radius = Math.hypot(across, forward);
                    double angle = Math.abs(Math.atan2(across, forward));
                    double taper = Math.max(0, 1 - Math.pow(angle / Math.toRadians(15), 4));
                    double thickness = (core ? .035 : .18) * taper;
                    if (radius <= 1 && radius > 1 - thickness) {
                        double edge = (1 - radius) / thickness;
                        int alpha = core ? 255 : (int) (220 * Math.pow(1 - edge, .7));
                        pixels[y * width + x] = argb(alpha, 0xFFFFFF);
                    }
                }
            }
            return pixels;
        };
    }

    private static Family saberSweep() {
        var glow = new Sprite("crescent_glow", true, true, crescent(false));
        var core = new Sprite("crescent_core", true, false, crescent(true));
        return new Family("saber_sweep", 256, true, List.of(
                new Model("sternenfront/fx/saber_crescent", new Shape().add(
                        box(0, .9, 0, .52, .2, 0, glow).only(Face.NORTH, Face.SOUTH),
                        box(0, .9, 0, .52, .2, .004, core).only(Face.NORTH, Face.SOUTH)))));
    }

    private static List<Family> families() {
        return List.of(hegemonieSoldier(), hegemonieHeavy(), hegemonieScout(), hegemonieMedic(),
                kolonienSoldier(), kolonienHeavy(), kolonienScout(), kolonienMedic(),
                speeders("kolonien", kolonienSpeeder(false), kolonienSpeeder(true)),
                speeders("hegemonie", hegemonieSpeeder(false), hegemonieSpeeder(true)),
                hegemonieDropship(), kolonienDropship(), effects(), saberSweep());
    }

    private record Tile(String key, Surface surface, Face face, int width, int height) { }

    private record Placed(int x, int y) { }

    private static final int GUTTER = 2;

    private static int pixels(double length, int density) {
        return Math.max(1, (int) Math.round(length * density));
    }

    private static String tileKey(Box each, Face face, int density) {
        String direction = each.surface instanceof Sprite || face.side() ? "side" : face.key();
        return each.surface.id() + "/" + direction + "/" + pixels(each.edge(face, true), density) + "x" + pixels(each.edge(face, false), density);
    }

    private static int shelfPack(List<Tile> tiles, int size, Map<String, Placed> out) {
        int x = 0;
        int y = 0;
        int shelf = 0;
        for (var tile : tiles) {
            int width = tile.width + GUTTER * 2;
            int height = tile.height + GUTTER * 2;
            if (x + width > size) {
                x = 0;
                y += shelf;
                shelf = 0;
            }
            if (width > size || y + height > size) {
                return -1;
            }
            out.put(tile.key, new Placed(x + GUTTER, y + GUTTER));
            x += width;
            shelf = Math.max(shelf, height);
        }
        return y + shelf;
    }

    private static void write(Path root, Family family) throws IOException {
        var tiles = new LinkedHashMap<String, Tile>();
        for (var model : family.models) {
            for (var each : model.shape.boxes) {
                for (var face : each.faces) {
                    tiles.computeIfAbsent(tileKey(each, face, family.density), key -> new Tile(key, each.surface, face,
                            pixels(each.edge(face, true), family.density), pixels(each.edge(face, false), family.density)));
                }
            }
        }
        var ordered = tiles.values().stream()
                .sorted(Comparator.comparingInt(Tile::height).reversed().thenComparing(Comparator.comparingInt(Tile::width).reversed()).thenComparing(Tile::key))
                .toList();
        var placed = new LinkedHashMap<String, Placed>();
        int size = 32;
        while (shelfPack(ordered, size, placed) < 0) {
            placed.clear();
            size *= 2;
        }
        var atlas = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        if (!family.translucent) {
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    atlas.setRGB(x, y, 0xFF000000 | 0x2A2A2A);
                }
            }
        }
        for (var tile : ordered) {
            var random = new Random(fnv(family.name + "/" + tile.key));
            var colours = tile.surface.draw(tile.face, tile.width, tile.height, random);
            var at = placed.get(tile.key);
            for (int y = -GUTTER; y < tile.height + GUTTER; y++) {
                for (int x = -GUTTER; x < tile.width + GUTTER; x++) {
                    int colour = colours[Math.clamp(y, 0, tile.height - 1) * tile.width + Math.clamp(x, 0, tile.width - 1)];
                    atlas.setRGB(at.x + x, at.y + y, family.translucent ? colour : 0xFF000000 | colour);
                }
            }
        }
        var texture = root.resolve("textures/block/sternenfront/rig/" + family.name + ".png");
        Files.createDirectories(texture.getParent());
        ImageIO.write(atlas, "png", texture.toFile());
        for (var model : family.models) {
            writeModel(root, family, model, placed, size);
        }
        System.out.printf("%-22s atlas %4d, %d tiles, %d boxes%n", family.name, size, ordered.size(),
                family.models.stream().mapToInt(model -> model.shape.boxes.size()).sum());
    }

    private static long fnv(String text) {
        long hash = 0xCBF29CE484222325L;
        for (byte each : text.getBytes(StandardCharsets.UTF_8)) {
            hash = (hash ^ each) * 0x100000001B3L;
        }
        return hash;
    }

    private static void writeModel(Path root, Family family, Model model, Map<String, Placed> placed, int size) throws IOException {
        var elements = model.shape.boxes.stream().map(each -> element(each, family, placed, size)).collect(Collectors.joining(",\n    "));
        var texture = "blockcraft:block/sternenfront/rig/" + family.name;
        var json = root.resolve("models/item/" + model.path + ".json");
        var definition = root.resolve("items/" + model.path + ".json");
        Files.createDirectories(json.getParent());
        Files.createDirectories(definition.getParent());
        Files.writeString(json, "{\n  \"textures\":{\"rig\":\"" + texture + "\",\"particle\":\"" + texture + "\"},\n  \"elements\":[\n    "
                + elements + "\n  ]\n}\n");
        boolean tinted = model.shape.boxes.stream().anyMatch(each -> each.surface.tinted());
        Files.writeString(definition, "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"blockcraft:item/" + model.path + "\""
                + (tinted ? ",\"tints\":[{\"type\":\"minecraft:custom_model_data\",\"index\":0,\"default\":-1}]" : "") + "}}\n");
    }

    private static String element(Box each, Family family, Map<String, Placed> placed, int size) {
        var faces = each.faces.stream().sorted().map(face -> {
            var at = placed.get(tileKey(each, face, family.density));
            int width = pixels(each.edge(face, true), family.density);
            int height = pixels(each.edge(face, false), family.density);
            return "\"" + face.key() + "\":{\"uv\":[" + uv(at.x, size) + "," + uv(at.y, size) + "," + uv(at.x + width, size) + ","
                    + uv(at.y + height, size) + "],\"texture\":\"#rig\"" + (each.surface.tinted() ? ",\"tintindex\":0" : "") + "}";
        }).collect(Collectors.joining(","));
        var parts = new StringBuilder("{\"from\":").append(point(each.x1, each.y1, each.z1))
                .append(",\"to\":").append(point(each.x2, each.y2, each.z2));
        if (each.spin != null) {
            if (Math.abs(each.spin.angle) != 22.5 && Math.abs(each.spin.angle) != 45) {
                throw new IllegalArgumentException(family.name + " has a rotation vanilla models refuse: " + each.spin);
            }
            parts.append(",\"rotation\":{\"origin\":").append(point(each.spin.x, each.spin.y, each.spin.z))
                    .append(",\"axis\":\"").append(each.spin.axis).append("\",\"angle\":").append(each.spin.angle).append('}');
        }
        if (each.surface.glow()) {
            parts.append(",\"light_emission\":15,\"shade\":false");
        }
        return parts.append(",\"faces\":{").append(faces).append("}}").toString();
    }

    private static String uv(int pixel, int size) {
        return Double.toString(pixel * 16.0 / size);
    }

    private static String point(double x, double y, double z) {
        return "[" + pixel(x) + "," + pixel(y) + "," + pixel(z) + "]";
    }

    private static double pixel(double coordinate) {
        double value = Math.round((8 + coordinate * 16) * 10_000) / 10_000.0;
        if (value < -16 || value > 32) {
            throw new IllegalArgumentException("Coordinate " + coordinate + " leaves the model space vanilla accepts");
        }
        return value;
    }

    public static void main(String[] args) throws IOException {
        var root = Path.of(args.length == 0 ? "src" : args[0]).resolve("assets/blockcraft");
        for (var family : families()) {
            write(root, family);
        }
    }
}
