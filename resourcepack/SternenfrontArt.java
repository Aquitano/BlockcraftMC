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

    private enum Wear { NONE, SCUFF, CHIP, RUST, PATCHED, SCORCH, SERVICED }

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

        Paint serviced() {
            return glow() || grain == Grain.GLASS ? this : new Paint(id + "_serviced", ramp, base, grain, panel, Wear.SERVICED);
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
                case SERVICED -> serviced(colours, width, height, seed, random);
            }
        }

        private void serviced(int[] colours, int width, int height, long seed, Random random) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int edge = Math.min(Math.min(x, width - 1 - x), Math.min(y, height - 1 - y));
                    if (edge < 2 && cell(x / 3, y / 2, seed) < .24) {
                        colours[y * width + x] = BARE_METAL[(int) (cell(x, y, seed + 1) * BARE_METAL.length)];
                    }
                }
            }
            for (int mark = 0; mark < width * height / 450 + 1; mark++) {
                int left = random.nextInt(width);
                int top = random.nextInt(height);
                int length = 3 + random.nextInt(8);
                for (int step = 0; step < length && left + step < width; step++) {
                    int y = Math.min(height - 1, top + step / 4);
                    colours[y * width + left + step] = BARE_METAL[0];
                }
            }
            if (width < 10 || height < 10) return;
            int seam = width / 3;
            for (int y = height / 2; y < height; y++) {
                double stain = (double) (height - y) / height;
                for (int x = Math.max(0, seam - 2); x <= seam + 1; x++) {
                    int index = y * width + x;
                    colours[index] = mix(colours[index], 0x241F1A, stain * .8);
                }
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

        Box scaled(double scale) {
            var resized = spin == null ? null : new Spin(spin.axis, spin.angle, spin.x * scale, spin.y * scale, spin.z * scale);
            return new Box(x1 * scale, y1 * scale, z1 * scale, x2 * scale, y2 * scale, z2 * scale, surface, resized, faces);
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

        Shape serviced() {
            return new Shape().add(boxes.stream().map(each -> each.surface instanceof Paint paint
                    ? each.surfaced(paint.serviced()) : each).toList());
        }

        Shape shifted(double x, double y, double z) {
            return new Shape().add(boxes.stream().map(each -> each.shifted(x, y, z)).toList());
        }

        Shape scaled(double scale) {
            return new Shape().add(boxes.stream().map(each -> each.scaled(scale)).toList());
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

    private record Model(String path, Shape shape, String display) {
        Model(String path, Shape shape) {
            this(path, shape, "");
        }
    }

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

    private enum CloneRole { SOLDIER, HEAVY, SCOUT, MEDIC, REX }

    private static final Paint BLUE = paint("republic_blue", 2, Grain.SMOOTH, 0, Wear.CHIP,
            0x142A56, 0x204476, 0x326CB0, 0x498CD0, 0x73B0E7);
    private static final Paint RED = paint("separatist_red", 2, Grain.GLOW, 0, Wear.NONE,
            0x681919, 0xAC2424, 0xFF3434, 0xFF7777, 0xFFE3E3);
    private static final Paint DROID = paint("droid_tan", 2, Grain.SMOOTH, 0, Wear.SCUFF,
            0x70614A, 0x9C8968, 0xBEAC87, 0xD7C8A7, 0xE9DFC5);
    private static final Paint MAROON = paint("republic_maroon", 2, Grain.SMOOTH, 0, Wear.CHIP,
            0x391723, 0x612537, 0x8B3B4B, 0xAE5360, 0xC97A83);
    private static final Paint HAIR = paint("auburn", 2, Grain.SMOOTH, 0, Wear.NONE,
            0x3E2417, 0x61402B, 0x855B3A, 0xA4774C, 0xBE9263);
    private static final Paint GREY_HAIR = paint("grey_hair", 2, Grain.SMOOTH, 0, Wear.NONE,
            0x777875, 0x9B9D99, 0xC0C2BC, 0xDCDED8, 0xF4F4EC);
    private static final Sprite SABER = new Sprite("saber", true, true, (width, height, random) -> {
        var pixels = new int[width * height];
        Arrays.fill(pixels, 0xFFFFFF);
        return pixels;
    });

    private static Family battleDroid(String role) {
        Paint marking = switch (role) {
            case "scout" -> FRAME;
            case "medic" -> BLUE;
            default -> DROID;
        };
        var torso = new Shape()
                .add(box(0, .02, 0, .2, .1, .13, DROID))
                .add(box(0, .19, 0, .055, .26, .055, FRAME))
                .add(box(0, .34, 0, .24, .24, .14, DROID))
                .add(box(0, .36, .077, .16, .14, .018, marking))
                .add(box(0, .49, 0, .45, .045, .085, DROID))
                .add(box(0, .56, 0, .035, .11, .035, STEEL))
                .add(box(0, .32, -.13, .17, .28, .12, DROID))
                .add(box(.06, .66, -.16, .018, .43, .018, FRAME));
        var head = new Shape()
                .add(box(0, .005, 0, .055, .07, .055, FRAME))
                .add(box(0, .095, .06, .13, .14, .23, DROID))
                .add(box(0, .065, .2, .105, .09, .14, DROID))
                .add(box(0, .045, .283, .075, .055, .035, DROID))
                .both(box(.067, .115, .145, .012, .024, .035, LENS))
                .add(box(0, .174, .03, .09, .025, .15, marking));
        var arm = new Shape()
                .add(box(0, 0, 0, .085, .085, .085, STEEL))
                .add(box(0, -.16, 0, .045, .25, .045, DROID))
                .add(box(0, -.3, 0, .07, .06, .07, FRAME))
                .add(box(0, -.44, 0, .05, .24, .05, DROID))
                .add(box(0, -.58, 0, .065, .05, .065, STEEL))
                .both(box(.023, -.62, .005, .015, .06, .04, DROID));
        var leg = new Shape()
                .add(box(0, -.015, 0, .09, .09, .09, STEEL))
                .add(box(0, -.23, 0, .055, .37, .055, DROID))
                .add(box(0, -.44, 0, .085, .065, .085, FRAME))
                .add(box(0, -.65, 0, .05, .36, .05, DROID))
                .add(box(0, -.88, .015, .065, .1, .07, STEEL))
                .add(box(0, -.935, .07, .1, .05, .24, DROID));
        if (role.equals("medic")) {
            torso.add(box(0, .32, -.205, .12, .16, .03, BLUE));
        }
        return body("hegemonie_" + role, torso, head, arm, leg);
    }

    private static Family superBattleDroid() {
        var torso = new Shape()
                .add(box(0, .03, 0, .52, .13, .25, FRAME))
                .add(box(0, .21, 0, .38, .3, .28, STEEL))
                .add(box(0, .49, .02, .71, .4, .4, STEEL))
                .both(box(.27, .46, .22, .21, .3, .06, TRIM).spinY(22.5))
                .add(box(0, .4, .24, .36, .18, .05, FRAME))
                .add(box(0, .68, -.015, .8, .1, .34, STEEL))
                .add(box(0, .53, -.24, .5, .32, .12, FRAME));
        var head = new Shape()
                .add(box(0, .04, -.06, .21, .16, .18, FRAME))
                .add(box(0, .08, .045, .19, .07, .04, STEEL))
                .add(box(-.055, .08, .071, .032, .026, .014, RED));
        var arm = new Shape()
                .add(box(0, .035, 0, .34, .25, .34, STEEL))
                .add(box(0, -.2, 0, .16, .24, .16, FRAME))
                .add(box(0, -.43, 0, .26, .3, .26, STEEL))
                .add(box(0, -.62, 0, .22, .08, .22, TRIM))
                .both(box(.055, -.73, .045, .07, .19, .09, FRAME))
                .both(box(.055, -.83, .045, .045, .015, .045, RED));
        var leg = new Shape()
                .add(box(0, -.06, 0, .2, .17, .2, FRAME))
                .add(box(0, -.21, .035, .2, .27, .2, STEEL))
                .add(box(0, -.37, .035, .19, .09, .19, FRAME))
                .add(box(0, -.54, 0, .18, .28, .19, STEEL))
                .add(box(0, -.74, .065, .28, .1, .4, STEEL));
        return body("hegemonie_heavy", torso, head, arm, leg);
    }

    private static Shape cloneHelmet(CloneRole role) {
        Paint stripe = role == CloneRole.MEDIC ? MAROON : BLUE;
        var head = new Shape()
                .add(box(0, .015, 0, .14, .06, .14, FRAME))
                .add(box(0, .18, 0, .28, .27, .27, CERAMIC))
                .add(box(0, .325, -.015, .23, .06, .22, CERAMIC))
                .add(box(0, .37, -.015, .05, .05, .24, stripe))
                .add(box(0, .235, .142, .25, .035, .02, LENS))
                .add(box(0, .177, .145, .04, .1, .025, LENS))
                .both(box(.09, .125, .14, .09, .09, .035, CERAMIC).spinZ(22.5))
                .both(box(.1, .085, .16, .045, .045, .04, FRAME))
                .add(box(0, .067, .125, .12, .025, .055, TRIM))
                .both(box(.145, .16, -.005, .035, .13, .16, CERAMIC))
                .both(box(.151, .12, .03, .027, .04, .075, FRAME))
                .add(box(0, .285, .142, .045, .065, .012, stripe));
        if (role == CloneRole.REX || role == CloneRole.SCOUT) {
            head.add(box(-.18, .3, -.01, .015, .26, .015, FRAME))
                    .add(box(-.15, .435, .02, .09, .035, .09, STEEL));
        }
        if (role == CloneRole.REX) {
            head.both(box(.075, .292, .15, .045, .035, .012, FRAME).spinZ(45))
                    .both(box(.05, .32, .15, .02, .035, .012, FRAME));
        }
        return head;
    }

    private static Family cloneTrooper(String name, CloneRole role) {
        Paint stripe = role == CloneRole.MEDIC ? MAROON : BLUE;
        var torso = new Shape()
                .add(box(0, .05, 0, .4, .13, .23, FRAME))
                .add(box(0, .25, 0, .42, .34, .23, FRAME))
                .add(box(0, .36, .045, .46, .2, .2, CERAMIC))
                .add(box(0, .19, .12, .35, .09, .035, CERAMIC))
                .add(box(0, .08, .12, .34, .07, .035, CERAMIC))
                .add(box(0, -.005, 0, .44, .06, .26, CERAMIC))
                .both(box(.14, -.015, .15, .075, .09, .06, CERAMIC))
                .add(box(0, .36, .151, .055, .2, .016, stripe))
                .add(box(0, .36, -.14, .34, .21, .04, CERAMIC))
                .add(box(0, .51, 0, .14, .065, .14, FRAME));
        var arm = new Shape()
                .add(box(0, -.025, 0, .18, .16, .19, CERAMIC))
                .add(box(0, -.025, .1, .09, .15, .015, stripe))
                .add(box(0, -.19, 0, .13, .21, .14, CERAMIC))
                .add(box(0, -.325, 0, .1, .055, .1, FRAME))
                .add(box(0, -.44, 0, .125, .19, .14, CERAMIC))
                .add(box(0, -.565, 0, .1, .09, .1, FRAME));
        var leg = new Shape()
                .add(box(0, -.21, 0, .18, .37, .2, CERAMIC))
                .add(box(0, -.43, .045, .145, .095, .18, CERAMIC))
                .add(box(0, -.5, 0, .1, .07, .1, FRAME))
                .add(box(0, -.66, 0, .155, .25, .17, CERAMIC))
                .add(box(0, -.87, .04, .165, .13, .23, CERAMIC))
                .add(box(0, -.945, .035, .17, .03, .25, FRAME));
        if (role == CloneRole.HEAVY || role == CloneRole.REX) {
            torso.both(box(.22, -.14, 0, .025, .28, .29, FRAME))
                    .add(box(0, .23, -.25, .35, .38, .2, STEEL))
                    .both(box(.2, .46, 0, .16, .055, .28, stripe));
        }
        if (role == CloneRole.SCOUT) {
            torso.add(box(0, .29, -.22, .27, .34, .16, FRAME));
        }
        if (role == CloneRole.MEDIC) {
            torso.add(box(0, .29, -.25, .35, .4, .22, CERAMIC))
                    .add(box(0, .3, -.37, .18, .055, .025, MAROON))
                    .add(box(0, .3, -.37, .055, .18, .026, MAROON));
        }
        return body(name, torso, cloneHelmet(role), arm, leg);
    }

    private static Shape humanHead(boolean dooku) {
        Paint hair = dooku ? GREY_HAIR : HAIR;
        return new Shape()
                .add(box(0, .025, 0, .12, .08, .12, SKIN))
                .add(box(0, .17, 0, .22, .23, .22, SKIN))
                .add(box(0, .3, -.015, .24, .05, .23, hair))
                .add(box(0, .21, -.11, .23, .18, .04, hair))
                .both(box(.116, .21, -.035, .025, .15, .15, hair))
                .both(box(.05, .21, .114, .035, .018, .012, LENS))
                .both(box(.052, .239, .114, .05, .014, .012, hair))
                .add(box(0, .16, .12, .035, .04, .03, SKIN))
                .add(box(0, .095, .115, .17, .075, .028, hair))
                .add(box(0, .123, .133, .065, .015, .008, SKIN));
    }

    private static Family forceHero(boolean dooku) {
        Paint cloth = dooku ? FRAME : WRAP;
        var torso = new Shape()
                .add(box(0, .27, 0, .43, .39, .24, cloth))
                .add(box(0, .025, 0, .44, .06, .26, LEATHER))
                .add(box(0, .025, .14, .07, .045, .025, STEEL))
                .both(box(.095, .3, .13, .09, .32, .02, dooku ? FRAME : SAND).spinZ(22.5))
                .add(box(0, .51, 0, .14, .07, .14, SKIN));
        var arm = new Shape()
                .add(box(0, -.15, 0, .16, .32, .16, cloth))
                .add(box(0, -.42, 0, .14, .2, .14, dooku ? FRAME : CERAMIC))
                .add(box(0, -.56, 0, .1, .1, .1, SKIN));
        var leg = new Shape()
                .add(box(0, -.25, 0, .18, .48, .18, dooku ? FRAME : SAND))
                .add(box(0, -.69, 0, .17, .37, .18, LEATHER))
                .add(box(0, -.91, .035, .18, .08, .26, LEATHER));
        if (dooku) {
            torso.add(box(0, -.06, -.19, .58, 1.05, .045, LEATHER))
                    .both(box(.22, .42, -.1, .16, .17, .2, LEATHER))
                    .add(box(0, .43, .14, .3, .02, .02, STEEL));
        } else {
            torso.both(box(.2, -.17, 0, .025, .32, .28, WRAP));
        }
        return body(dooku ? "hero_sith" : "hero_jedi", torso, humanHead(dooku), arm, leg);
    }

    private static Shape mandoHelmet() {
        return new Shape()
                .add(box(0, .19, 0, .28, .29, .27, TRIM))
                .add(box(0, .35, 0, .23, .045, .22, STEEL))
                .add(box(0, .245, .145, .26, .025, .02, LENS))
                .add(box(0, .16, .145, .045, .15, .02, LENS))
                .both(box(.09, .15, .145, .09, .15, .025, BLUE))
                .both(box(.15, .2, 0, .025, .18, .15, BLUE))
                .add(box(-.175, .34, -.01, .015, .25, .015, FRAME))
                .add(box(-.15, .475, .02, .08, .035, .09, STEEL));
    }

    private static Family jangoFett() {
        var torso = new Shape()
                .add(box(0, .27, 0, .43, .39, .24, BLUE))
                .both(box(.11, .36, .145, .18, .18, .05, TRIM))
                .add(box(0, .17, .145, .25, .1, .04, TRIM))
                .add(box(0, .015, 0, .45, .07, .27, LEATHER))
                .both(box(.17, .015, .16, .07, .1, .07, LEATHER))
                .add(box(0, .29, -.24, .25, .35, .2, BLUE))
                .both(box(.19, .29, -.24, .12, .4, .15, STEEL))
                .add(box(0, .56, -.24, .06, .2, .07, STEEL));
        var arm = new Shape()
                .add(box(0, -.03, 0, .19, .15, .2, TRIM))
                .add(box(0, -.2, 0, .13, .22, .14, BLUE))
                .add(box(0, -.43, 0, .15, .2, .16, TRIM))
                .add(box(0, -.56, 0, .1, .09, .1, LEATHER));
        var leg = new Shape()
                .add(box(0, -.22, 0, .18, .4, .18, BLUE))
                .add(box(0, -.22, .105, .15, .27, .04, TRIM))
                .add(box(0, -.46, .07, .15, .09, .1, TRIM))
                .add(box(0, -.66, 0, .15, .26, .17, BLUE))
                .add(box(0, -.66, .1, .13, .24, .035, TRIM))
                .add(box(0, -.9, .03, .17, .13, .25, FRAME));
        return body("hero_bounty_hunter", torso, mandoHelmet(), arm, leg);
    }

    private static Shape barcSpeeder(boolean damaged) {
        var shape = new Shape()
                .add(box(0, .31, -.1, .46, .18, 1.25, CERAMIC))
                .add(box(0, .32, .86, .19, .1, 1.1, CERAMIC))
                .add(box(0, .38, .86, .08, .025, 1.1, MAROON))
                .both(box(.22, .31, 1.2, .06, .06, .45, FRAME))
                .add(box(0, .46, -.18, .32, .1, .55, FRAME))
                .add(box(0, .55, -.47, .32, .2, .08, FRAME))
                .add(box(0, .49, .3, .04, .28, .04, STEEL))
                .add(box(0, .62, .3, .48, .035, .04, FRAME))
                .both(box(.36, .28, -.65, .24, .28, .88, CERAMIC))
                .both(box(.36, .435, -.65, .1, .025, .75, MAROON))
                .add(drumZ(.36, .28, -1.12, .19, .03, CYAN))
                .add(drumZ(-.36, .28, -1.12, .19, .03, CYAN))
                .both(box(.26, .18, -.06, .16, .04, .32, STEEL))
                .add(box(.235, .32, -.08, .025, .12, .26, TRIM))
                .add(box(-.365, .44, -.65, .12, .025, .21, STEEL))
                .serviced();
        return damaged ? shape.burnt(.6) : shape;
    }

    private static Shape stap(boolean damaged) {
        var shape = new Shape()
                .add(box(0, .45, .32, .18, .84, .22, DROID))
                .add(box(0, .25, .45, .16, .18, .35, DROID))
                .add(box(0, .85, .3, .34, .12, .3, DROID))
                .both(box(.11, .86, .6, .045, .045, .4, FRAME))
                .add(box(0, .9, .1, .5, .04, .04, FRAME))
                .both(box(.32, .35, .25, .3, .21, .65, DROID))
                .both(box(.32, .35, -.085, .19, .12, .02, RED))
                .add(box(0, .04, -.15, .72, .055, .32, STEEL))
                .add(box(0, .04, .1, .12, .08, .34, FRAME))
                .add(box(.485, .35, .25, .025, .13, .24, STEEL))
                .add(box(-.1, .47, .32, .025, .2, .13, TRIM))
                .serviced();
        return damaged ? shape.burnt(.6) : shape;
    }

    private static Family speeders(String faction, Shape intact, Shape damaged) {
        return new Family("speeder_" + faction, 48, false, List.of(
                new Model("sternenfront/speeder/" + faction, intact),
                new Model("sternenfront/speeder/" + faction + "_damaged", damaged)));
    }

    private static Family republicGunship() {
        var hull = new Shape()
                .add(box(0, .55, -.1, .68, .49, 1.9, CERAMIC))
                .add(box(0, .29, -.1, .64, .05, 1.8, STEEL))
                .both(box(.36, .55, -.06, .025, .34, .9, FRAME))
                .add(box(0, .64, .92, .45, .3, .45, CERAMIC))
                .add(box(0, .82, .75, .3, .16, .37, LENS).spinX(22.5))
                .add(box(0, .92, .38, .3, .16, .32, LENS).spinX(22.5))
                .both(box(.43, .54, .75, .12, .12, .12, FRAME))
                .both(box(.43, .54, .96, .04, .04, .34, STEEL))
                .both(box(.79, .83, -.45, .98, .07, .9, CERAMIC).spinZ(22.5))
                .both(box(.84, .87, -.45, .25, .02, .65, MAROON).spinZ(22.5))
                .both(box(.3, .99, -.46, .2, .22, 1.35, STEEL))
                .both(box(.3, .99, -1.145, .14, .14, .02, CYAN))
                .both(box(.72, .66, -.05, .18, .18, .18, LENS))
                .add(box(0, .55, -1.1, .5, .37, .2, MAROON))
                .add(box(0, 1.03, -1.04, .05, .5, .4, CERAMIC))
                .both(box(.28, .15, .55, .05, .26, .05, STEEL))
                .both(box(.28, .03, .55, .2, .05, .25, STEEL))
                .add(box(.355, .48, -.64, .025, .16, .24, TRIM))
                .serviced();
        return new Family("dropship_kolonien", 72, false, List.of(new Model("sternenfront/dropship/kolonien", hull)));
    }

    private static Family separatistTransport() {
        var hull = new Shape()
                .add(box(0, .5, -.15, .66, .33, 1.8, DROID))
                .add(box(0, .64, .6, .45, .45, .5, DROID))
                .add(box(0, .87, .61, .29, .04, .36, FRAME))
                .both(box(.76, .46, -.15, .86, .22, 1.7, DROID))
                .both(box(.76, .59, -.15, .6, .025, 1.3, MAROON))
                .both(box(1.16, .28, -.15, .12, .37, 1.65, DROID))
                .both(box(.76, .44, -1.02, .56, .14, .025, RED))
                .both(box(.76, .28, -.15, .56, .14, 1.3, FRAME))
                .add(box(0, .3, .45, .36, .1, .55, FRAME))
                .both(box(.6, .12, -.6, .08, .24, .08, STEEL))
                .both(box(.6, .02, -.6, .25, .04, .3, STEEL))
                .add(box(-1.225, .32, -.42, .025, .22, .34, STEEL))
                .serviced();
        return new Family("dropship_hegemonie", 72, false, List.of(new Model("sternenfront/dropship/hegemonie", hull)));
    }

    private static final String GUN_DISPLAY = """
            ,"gui_light":"front","display":{
              "firstperson_righthand":{"rotation":[0,180,0],"translation":[1,1.5,0],"scale":[0.55,0.55,0.55]},
              "firstperson_lefthand":{"rotation":[0,180,0],"translation":[1,1.5,0],"scale":[0.55,0.55,0.55]},
              "thirdperson_righthand":{"rotation":[-85,0,0],"translation":[0,2,1],"scale":[0.6,0.6,0.6]},
              "thirdperson_lefthand":{"rotation":[-85,0,0],"translation":[0,2,1],"scale":[0.6,0.6,0.6]},
              "gui":{"rotation":[25,140,0],"scale":[0.55,0.55,0.55]},
              "ground":{"translation":[0,3,0],"scale":[0.4,0.4,0.4]},
              "fixed":{"rotation":[0,90,0],"translation":[-0.9,1.5,0],"scale":[0.55,0.55,0.55]}}
            """;
    private static final String SABER_DISPLAY = """
            ,"display":{
              "firstperson_righthand":{"rotation":[0,0,15],"translation":[0,0,0],"scale":[0.7,0.7,0.7]},
              "firstperson_lefthand":{"rotation":[0,0,-15],"translation":[0,0,0],"scale":[0.7,0.7,0.7]},
              "thirdperson_righthand":{"rotation":[0,0,0],"translation":[0,2,0],"scale":[0.8,0.8,0.8]},
              "thirdperson_lefthand":{"rotation":[0,0,0],"translation":[0,2,0],"scale":[0.8,0.8,0.8]},
              "gui":{"rotation":[0,0,-35],"translation":[-3,-3,0],"scale":[0.6,0.6,0.6]},
              "ground":{"translation":[0,2,0],"scale":[0.4,0.4,0.4]},
              "fixed":{"scale":[0.6,0.6,0.6]}}
            """;
    private static final String HELMET_DISPLAY = """
            ,"display":{
              "head":{"rotation":[0,180,0],"translation":[0,0,0],"scale":[1,1,1]},
              "gui":{"rotation":[20,150,0],"scale":[0.7,0.7,0.7]},
              "ground":{"translation":[0,3,0],"scale":[0.4,0.4,0.4]}}
            """;

    private static Family equipment(String name, Shape shape, String display) {
        return new Family("equipment_" + name, 64, false, List.of(new Model("sternenfront/" + name, shape, display)));
    }

    private static Shape rifle(boolean separatist, boolean sniper) {
        var shape = new Shape()
                .add(box(0, .03, -.15, .16, .17, .57, FRAME))
                .add(box(0, .13, -.15, .11, .04, .48, STEEL))
                .add(box(0, -.15, -.28, .09, .23, .13, FRAME).spinX(22.5))
                .add(box(0, .015, -.58, .13, .2, .31, FRAME))
                .add(box(0, -.07, -.53, .16, .07, .27, STEEL))
                .add(drumZ(0, .03, sniper ? .65 : .46, .075, sniper ? 1.25 : .86, STEEL))
                .add(drumZ(0, .03, sniper ? 1.27 : .9, .13, .12, FRAME))
                .add(box(0, .16, .32, .035, .12, .035, FRAME));
        if (separatist) {
            shape.add(box(0, -.17, .06, .08, .27, .12, STEEL).spinX(-22.5))
                    .add(box(.087, .04, -.1, .012, .09, .2, TRIM));
        } else {
            shape.add(box(0, -.005, .16, .19, .19, .33, FRAME))
                    .both(box(.1, .03, .16, .02, .035, .25, STEEL));
        }
        if (sniper) {
            shape.add(drumZ(0, .25, -.12, .115, .36, FRAME))
                    .add(box(0, .17, -.12, .045, .11, .05, STEEL))
                    .add(box(0, .25, .066, .075, .075, .012, LENS));
        }
        return shape;
    }

    private static Shape rotaryBlaster() {
        var shape = new Shape()
                .add(drumZ(0, .02, -.23, .28, .46, FRAME))
                .add(drumZ(0, .02, .15, .3, .08, STEEL))
                .add(drumZ(0, .02, 1.02, .3, .07, STEEL))
                .add(box(0, -.2, -.27, .11, .25, .13, FRAME))
                .add(box(0, .27, -.13, .31, .06, .06, FRAME))
                .both(box(.13, .18, -.13, .05, .2, .06, STEEL));
        for (int index = 0; index < 6; index++) {
            double angle = Math.PI * index / 3;
            shape.add(drumZ(Math.cos(angle) * .1, .02 + Math.sin(angle) * .1, .6, .055, .92, FRAME));
        }
        return shape;
    }

    private static Shape pistol(boolean westar, boolean se14) {
        Paint metal = westar ? TRIM : FRAME;
        var shape = new Shape()
                .add(box(0, .015, -.14, .12, .14, .32, metal))
                .add(box(0, -.15, -.23, .085, .23, .1, westar ? LEATHER : FRAME).spinX(22.5))
                .add(drumZ(0, .025, .18, westar ? .09 : .075, .36, metal))
                .add(drumZ(0, .025, .37, .11, .04, STEEL))
                .add(box(0, .11, -.12, .025, .05, .035, FRAME))
                .add(box(0, .09, .29, .025, .05, .035, FRAME));
        if (se14) {
            shape.add(drumZ(0, .025, .12, .15, .13, STEEL))
                    .add(box(0, .15, -.02, .075, .06, .22, FRAME));
        }
        return shape;
    }

    private static Family lightsaber(boolean dooku, boolean lit) {
        var hilt = new Shape()
                .add(drumY(0, -.08, 0, .07, .25, STEEL))
                .add(drumY(0, -.22, 0, .09, .04, FRAME))
                .add(drumY(0, .075, 0, .065, .07, FRAME))
                .add(drumY(0, .135, 0, .11, .045, STEEL))
                .add(box(.045, .025, 0, .025, .055, .025, dooku ? RED : BLUE));
        if (dooku) {
            hilt.add(box(.035, -.17, 0, .065, .15, .065, STEEL).spinZ(22.5))
                    .add(box(.07, -.24, 0, .055, .055, .065, FRAME));
        } else {
            hilt.add(drumY(0, -.08, 0, .08, .11, FRAME))
                    .both(box(.035, -.08, 0, .012, .1, .04, STEEL));
        }
        if (lit) {
            hilt.add(box(0, .81, 0, .035, 1.29, .035, SABER))
                    .add(box(0, 1.46, 0, .025, .025, .025, SABER));
        }
        return equipment("lightsaber_" + (dooku ? "sith" : "jedi") + (lit ? "" : "_off"), hilt, SABER_DISPLAY);
    }

    private static Family darksaber() {
        var shape = new Shape()
                .add(box(0, -.07, 0, .08, .28, .06, FRAME))
                .add(box(0, .1, 0, .13, .045, .08, STEEL))
                .add(box(0, .72, 0, .13, 1.2, .025, CERAMIC))
                .add(box(0, .72, .016, .1, 1.18, .012, FRAME))
                .add(box(0, .72, -.016, .1, 1.18, .012, FRAME))
                .add(box(-.016, 1.34, 0, .09, .09, .025, CERAMIC).spinZ(-45))
                .add(box(-.016, 1.34, .016, .065, .065, .012, FRAME).spinZ(-45));
        return equipment("relic_blade", shape, SABER_DISPLAY);
    }

    private static List<Family> equipment() {
        var wrist = new Shape().add(box(0, -.04, -.12, .28, .25, .45, STEEL))
                .both(box(.065, .015, .23, .07, .07, .3, FRAME));
        return List.of(equipment("carbine", rifle(false, false), GUN_DISPLAY),
                equipment("heavy_rifle", rifle(false, false).add(drumZ(0, .03, 1.06, .075, .25, STEEL)), GUN_DISPLAY),
                equipment("repeater", rotaryBlaster(), GUN_DISPLAY),
                equipment("long_rifle", rifle(false, true), GUN_DISPLAY),
                equipment("pistol", pistol(false, false), GUN_DISPLAY),
                equipment("twin_pistol", pistol(false, false), GUN_DISPLAY),
                equipment("e5", rifle(true, false), GUN_DISPLAY),
                equipment("e5s", rifle(true, true), GUN_DISPLAY),
                equipment("wrist_blaster", wrist, GUN_DISPLAY),
                equipment("se14", pistol(false, true), GUN_DISPLAY),
                equipment("westar_pistols", pistol(true, false), GUN_DISPLAY),
                lightsaber(false, false), lightsaber(false, true), lightsaber(true, false), lightsaber(true, true), darksaber(),
                equipment("helmet_kolonien", cloneHelmet(CloneRole.SOLDIER).shifted(0, -.19, 0).scaled(1.8), HELMET_DISPLAY),
                equipment("helmet_hegemonie", battleDroid("soldier").models.get(1).shape.shifted(0, -.08, 0).scaled(1.8), HELMET_DISPLAY),
                equipment("cap_commander", cloneHelmet(CloneRole.REX).shifted(0, -.19, 0).scaled(1.8), HELMET_DISPLAY),
                equipment("helmet_bounty_hunter", mandoHelmet().shifted(0, -.19, 0).scaled(1.8), HELMET_DISPLAY),
                equipment("hood_jedi", humanHead(false).shifted(0, -.17, 0).scaled(2.1), HELMET_DISPLAY),
                equipment("mask_sith", humanHead(true).shifted(0, -.17, 0).scaled(2.1), HELMET_DISPLAY));
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
        var kolonien = new Sprite("flash_kolonien", true, false, star(0x82B5FF, 0x3D8BFF));
        var hegemonie = new Sprite("flash_hegemonie", true, false, star(0xFF9999, 0xFF3434));
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
                new Model("sternenfront/fx/flash_kolonien", flash(kolonien, CYAN)),
                new Model("sternenfront/fx/flash_hegemonie", flash(hegemonie, RED)),
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

    private static Family wristRocket() {
        var rocket = new Shape()
                .add(drumZ(0, 0, 0, .11, .42, STEEL))
                .add(drumZ(0, 0, .24, .075, .08, TRIM))
                .add(drumZ(0, 0, -.22, .07, .025, FURNACE))
                .both(box(.075, 0, -.13, .06, .025, .13, FRAME))
                .add(box(0, .075, -.13, .025, .06, .13, FRAME),
                        box(0, -.075, -.13, .025, .06, .13, FRAME));
        return new Family("wrist_rocket", 96, false, List.of(new Model("sternenfront/fx/wrist_rocket", rocket)));
    }

    private static Drawing crescentGlow() {
        return (width, height, random) -> {
            var pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    double across = (x + .5) * .6 / width - .3;
                    double forward = 1 - (y + .5) * .2 / height;
                    double edge = (1 - .1 * Math.pow(across / .3, 2) - forward) / .07;
                    if (edge >= 0 && edge < 1) {
                        int alpha = (int) (180 * Math.pow(1 - edge, .7));
                        pixels[y * width + x] = argb(alpha, 0xFFFFFF);
                    }
                }
            }
            return pixels;
        };
    }

    private static Family saberSweep() {
        var glow = new Sprite("crescent_glow", true, true, crescentGlow());
        var core = new Sprite("crescent_core", true, false, (width, height, random) -> {
            var pixels = new int[width * height];
            Arrays.fill(pixels, 0xFFFFFFFF);
            return pixels;
        });
        var line = new Shape().add(box(0, .9, 0, .6, .2, 0, glow).only(Face.NORTH, Face.SOUTH));
        for (int step = 0; step < 32; step++) {
            double from = -.3 + step * .6 / 32;
            double to = -.3 + (step + 1) * .6 / 32;
            double edge = Math.max(Math.abs(from), Math.abs(to)) / .3;
            double top = 1 - .1 * edge * edge;
            line.add(box((from + to) / 2, top - .015, 0, to - from, .03, .035, core));
        }
        return new Family("saber_sweep", 256, true, List.of(
                new Model("sternenfront/fx/saber_crescent", line)));
    }

    private static List<Family> families() {
        return List.of(battleDroid("soldier"), superBattleDroid(), battleDroid("scout"), battleDroid("medic"),
                cloneTrooper("kolonien_soldier", CloneRole.SOLDIER), cloneTrooper("kolonien_heavy", CloneRole.HEAVY),
                cloneTrooper("kolonien_scout", CloneRole.SCOUT), cloneTrooper("kolonien_medic", CloneRole.MEDIC),
                cloneTrooper("hero_commander", CloneRole.REX), forceHero(false), forceHero(true), jangoFett(),
                speeders("kolonien", barcSpeeder(false), barcSpeeder(true)),
                speeders("hegemonie", stap(false), stap(true)), republicGunship(), separatistTransport(), wristRocket(), effects(), saberSweep());
    }

    private record Point(double x, double y, double z) {
        Point rotate(Spin spin) {
            if (spin == null) {
                return this;
            }
            double angle = Math.toRadians(spin.angle);
            double c = Math.cos(angle);
            double s = Math.sin(angle);
            double dx = x - spin.x;
            double dy = y - spin.y;
            double dz = z - spin.z;
            return switch (spin.axis) {
                case 'x' -> new Point(x, spin.y + dy * c - dz * s, spin.z + dy * s + dz * c);
                case 'y' -> new Point(spin.x + dx * c + dz * s, y, spin.z - dx * s + dz * c);
                case 'z' -> new Point(spin.x + dx * c - dy * s, spin.y + dx * s + dy * c, z);
                default -> throw new IllegalArgumentException("Unknown rotation axis: " + spin.axis);
            };
        }

        Point camera() {
            double across = x * .866 + z * .5;
            double depth = -x * .5 + z * .866;
            return new Point(across, (y - 1.49) * .985 - depth * .174, depth * .985 + y * .174);
        }
    }

    private record PortraitFace(List<Point> points, int colour, double depth) { }

    private static List<Point> corners(Box box, Face face) {
        double x = box.x1;
        double y = box.y1;
        double z = box.z1;
        double X = box.x2;
        double Y = box.y2;
        double Z = box.z2;
        return switch (face) {
            case SOUTH -> List.of(new Point(x, y, Z), new Point(X, y, Z), new Point(X, Y, Z), new Point(x, Y, Z));
            case NORTH -> List.of(new Point(X, y, z), new Point(x, y, z), new Point(x, Y, z), new Point(X, Y, z));
            case EAST -> List.of(new Point(X, y, Z), new Point(X, y, z), new Point(X, Y, z), new Point(X, Y, Z));
            case WEST -> List.of(new Point(x, y, z), new Point(x, y, Z), new Point(x, Y, Z), new Point(x, Y, z));
            case UP -> List.of(new Point(x, Y, Z), new Point(X, Y, Z), new Point(X, Y, z), new Point(x, Y, z));
            case DOWN -> List.of(new Point(x, y, z), new Point(X, y, z), new Point(X, y, Z), new Point(x, y, Z));
        };
    }

    private static BufferedImage portrait(Family family, int width, int height) {
        boolean heavy = family.name.equals("hegemonie_heavy");
        boolean droid = family.name.startsWith("hegemonie");
        double scale = heavy ? 1.25 : 1;
        var boxes = new ArrayList<Box>();
        for (var model : family.models) {
            String part = model.path.substring(model.path.lastIndexOf('/') + 1);
            var shape = model.shape;
            switch (part) {
                case "torso" -> boxes.addAll(shape.shifted(0, heavy ? .8 : .97, 0).scaled(scale).boxes);
                case "head" -> boxes.addAll(shape.shifted(0, heavy ? 1.32 : droid ? 1.64 : 1.5, heavy ? .3 : 0).scaled(scale).boxes);
                case "arm" -> {
                    boxes.addAll(shape.shifted(heavy ? .47 : droid ? .25 : .3, heavy ? 1.46 : droid ? 1.47 : 1.4, heavy ? .14 : 0).scaled(scale).boxes);
                    boxes.addAll(shape.shifted(heavy ? -.47 : droid ? -.25 : -.3, heavy ? 1.46 : droid ? 1.47 : 1.4, heavy ? .14 : 0).scaled(scale).boxes);
                }
                default -> { }
            }
        }
        var faces = new ArrayList<PortraitFace>();
        for (var box : boxes) {
            for (var face : box.faces) {
                var points = corners(box, face).stream().map(point -> point.rotate(box.spin).camera()).toList();
                int colour = box.surface.draw(face, 1, 1, new Random(0))[0];
                double light = switch (face) {
                    case UP -> 1.08;
                    case SOUTH -> 1;
                    case WEST -> .84;
                    case EAST -> .67;
                    default -> .55;
                };
                int red = Math.clamp((int) (((colour >> 16) & 255) * light), 0, 255);
                int green = Math.clamp((int) (((colour >> 8) & 255) * light), 0, 255);
                int blue = Math.clamp((int) ((colour & 255) * light), 0, 255);
                faces.add(new PortraitFace(points, 0xFF000000 | red << 16 | green << 8 | blue,
                        points.stream().mapToDouble(Point::z).average().orElseThrow()));
            }
        }
        faces.sort(Comparator.comparingDouble(PortraitFace::depth));
        int supersampling = 6;
        var large = new BufferedImage(width * supersampling, height * supersampling, BufferedImage.TYPE_INT_ARGB);
        var graphics = large.createGraphics();
        graphics.setPaint(new java.awt.GradientPaint(0, 0, new java.awt.Color(0x29364B), 0, large.getHeight(), new java.awt.Color(0x101925)));
        graphics.fillRect(0, 0, large.getWidth(), large.getHeight());
        double zoom = large.getHeight() / .92;
        for (var face : faces) {
            var polygon = new java.awt.Polygon();
            for (var point : face.points) {
                polygon.addPoint((int) Math.round(large.getWidth() / 2.0 + point.x * zoom),
                        (int) Math.round(large.getHeight() / 2.0 - point.y * zoom));
            }
            graphics.setColor(new java.awt.Color(face.colour, true));
            graphics.fillPolygon(polygon);
        }
        graphics.dispose();
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var output = image.createGraphics();
        output.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        output.drawImage(large, 0, 0, width, height, null);
        output.dispose();
        return image;
    }

    private static void portraits(Path directory, Family family) throws IOException {
        Files.createDirectories(directory);
        ImageIO.write(portrait(family, 50, 22), "png", directory.resolve(family.name + "_large.png").toFile());
        ImageIO.write(portrait(family, 23, 14), "png", directory.resolve(family.name + "_wide.png").toFile());
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
                + elements + "\n  ]" + model.display + "\n}\n");
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
            if (family.name.startsWith("kolonien_") || family.name.startsWith("hegemonie_")) {
                portraits(Path.of(args.length == 0 ? "src" : args[0]).resolveSibling("art/sternenfront"), family);
            }
        }
        for (var family : equipment()) {
            write(root, family);
        }
    }
}
