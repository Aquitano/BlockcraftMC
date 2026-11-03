import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class DroidArt {

    private record Box(double x1, double y1, double z1, double x2, double y2, double z2, String texture) {
        String json() {
            var faces = List.of("north", "south", "east", "west", "up", "down").stream()
                    .map(face -> "\"" + face + "\":{\"uv\":[0,0,16,16],\"texture\":\"#" + texture + "\"}")
                    .collect(Collectors.joining(","));
            return "{\"from\":" + point(x1, y1, z1) + ",\"to\":" + point(x2, y2, z2) + ",\"faces\":{" + faces + "}}";
        }
    }

    private static final String TEXTURES = """
            {"shell":"minecraft:block/sandstone_top","joint":"minecraft:block/gray_concrete",
             "dark":"minecraft:block/black_concrete","stripe":"minecraft:block/light_blue_concrete",
             "metal":"minecraft:block/iron_block","particle":"minecraft:block/sandstone_top"}
            """.strip();

    private static final Map<String, List<Box>> PARTS = Map.of(
            "torso", List.of(
                    box(-.13, -.05, -.08, .13, .06, .08, "shell"),
                    box(-.055, .06, -.045, .055, .2, .045, "joint"),
                    box(-.15, .2, -.09, .15, .4, .09, "shell"),
                    box(-.19, .31, -.07, .19, .4, .07, "shell"),
                    box(-.035, .21, .091, .035, .39, .1, "stripe"),
                    box(-.03, .4, -.03, .03, .51, .03, "joint"),
                    box(-.115, .17, -.18, .115, .39, -.09, "shell"),
                    box(-.075, .2, -.19, .075, .35, -.18, "joint"),
                    box(.085, .39, -.16, .105, .67, -.14, "metal")),
            "head", List.of(
                    box(-.085, .02, -.09, .085, .27, .105, "shell"),
                    box(-.07, .09, .105, .07, .22, .31, "shell"),
                    box(-.05, .065, .23, .05, .09, .31, "joint"),
                    box(-.071, .18, .311, -.02, .21, .32, "dark"),
                    box(.02, .18, .311, .071, .21, .32, "dark"),
                    box(-.09, .09, -.02, -.085, .18, .065, "joint"),
                    box(.085, .09, -.02, .09, .18, .065, "joint"),
                    box(-.035, .271, -.05, .035, .28, .12, "stripe")),
            "arm", List.of(
                    box(-.06, -.05, -.06, .06, .05, .06, "joint"),
                    box(-.04, -.26, -.04, .04, -.05, .04, "shell"),
                    box(-.052, -.31, -.052, .052, -.25, .052, "joint"),
                    box(-.035, -.5, -.035, .035, -.31, .035, "shell"),
                    box(-.047, -.55, -.05, .047, -.5, .055, "joint"),
                    box(-.03, -.56, .015, .03, -.55, .09, "shell")),
            "leg", List.of(
                    box(-.055, -.05, -.055, .055, .025, .055, "joint"),
                    box(-.045, -.43, -.045, .045, -.05, .045, "shell"),
                    box(-.065, -.49, -.055, .065, -.42, .06, "joint"),
                    box(-.04, -.87, -.04, .04, -.49, .04, "shell"),
                    box(-.055, -.91, -.055, .055, -.86, .055, "joint"),
                    box(-.065, -.95, -.07, .065, -.9, .19, "shell"),
                    box(-.055, -.951, .03, .055, -.946, .18, "dark")));

    private static Box box(double x1, double y1, double z1, double x2, double y2, double z2, String texture) {
        return new Box(x1, y1, z1, x2, y2, z2, texture);
    }

    private static final String INFANTRY_TEXTURES = """
            {"skin":"minecraft:block/terracotta","uniform":"minecraft:block/brown_concrete",
             "vest":"minecraft:block/orange_terracotta","helmet":"minecraft:block/sandstone_top",
             "dark":"minecraft:block/black_concrete","metal":"minecraft:block/iron_block",
             "particle":"minecraft:block/orange_terracotta"}
            """.strip();

    private static final Map<String, List<Box>> INFANTRY = Map.of(
            "torso", List.of(box(-.25, 0, -.13, .25, .4, .13, "uniform"),
                    box(-.26, .08, .13, .26, .37, .17, "vest"),
                    box(-.255, -.04, -.135, .255, .03, .17, "dark"),
                    box(-.08, -.025, .171, .08, .02, .19, "metal"),
                    box(-.2, .11, .17, -.04, .24, .2, "helmet"),
                    box(.04, .11, .17, .2, .24, .2, "helmet")),
            "head", List.of(box(-.2, -.02, -.2, .2, .36, .2, "skin"),
                    box(-.22, .23, -.22, .22, .42, .22, "helmet"),
                    box(-.23, .19, -.22, -.195, .33, .2, "helmet"),
                    box(.195, .19, -.22, .23, .33, .2, "helmet"),
                    box(-.21, .17, .201, .21, .26, .23, "dark"),
                    box(-.04, .06, .2, .04, .17, .25, "skin"),
                    box(-.1, .015, .201, .1, .045, .21, "uniform")),
            "arm", List.of(box(-.12, -.27, -.12, .12, .025, .12, "uniform"),
                    box(-.125, -.12, -.125, .125, .01, .125, "vest"),
                    box(-.105, -.48, -.105, .105, -.27, .105, "uniform"),
                    box(-.11, -.59, -.11, .11, -.48, .11, "dark")),
            "leg", List.of(box(-.12, -.68, -.12, .12, 0, .12, "uniform"),
                    box(-.125, -.5, .12, .125, -.35, .15, "vest"),
                    box(-.125, -.95, -.125, .125, -.68, .18, "dark")));

    private static final List<Box> SPEEDER = List.of(
            box(-.32, .23, -.65, .32, .43, .65, "metal"),
            box(-.23, .42, -.55, .23, .54, .07, "dark"),
            box(-.32, .3, .4, .32, .58, .68, "shell"),
            box(-.035, .46, .55, .035, .52, 1.45, "metal"),
            box(-.48, .3, 1.0, .48, .39, 1.45, "shell"),
            box(-.35, .59, .35, .35, .64, .43, "dark"),
            box(-.43, .2, -.8, -.24, .4, -.35, "joint"),
            box(.24, .2, -.8, .43, .4, -.35, "joint"),
            box(-.4, .24, -.83, -.27, .36, -.8, "stripe"),
            box(.27, .24, -.83, .4, .36, -.8, "stripe"));

    private static String point(double x, double y, double z) {
        return "[" + pixel(x) + "," + pixel(y) + "," + pixel(z) + "]";
    }

    private static double pixel(double coordinate) {
        return Math.round((8 + coordinate * 16) * 10_000) / 10_000.0;
    }

    public static void main(String[] args) throws IOException {
        var root = Path.of(args.length == 0 ? "src" : args[0]).resolve("assets/blockcraft");
        for (var family : Map.of("droid", PARTS, "infantry", INFANTRY).entrySet()) {
            for (var entry : family.getValue().entrySet()) {
                write(root, "sternenfront/" + family.getKey() + "/" + entry.getKey(), entry.getValue(),
                        family.getKey().equals("droid") ? TEXTURES : INFANTRY_TEXTURES);
            }
        }
        write(root, "sternenfront/speeder", SPEEDER, TEXTURES);
    }

    private static void write(Path root, String path, List<Box> boxes, String textures) throws IOException {
        var model = root.resolve("models/item/" + path + ".json");
        var definition = root.resolve("items/" + path + ".json");
        Files.createDirectories(model.getParent());
        Files.createDirectories(definition.getParent());
        var elements = boxes.stream().map(Box::json).collect(Collectors.joining(",\n    "));
        Files.writeString(model, "{\n  \"textures\":" + textures + ",\n  \"elements\":[\n    " + elements + "\n  ]\n}\n");
        Files.writeString(definition, "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"blockcraft:item/" + path + "\"}}\n");
    }
}
