import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class PresentationArt {

    public static void main(String[] args) throws Exception {
        var root = Path.of(args.length == 0 ? "src" : args[0]).resolve("assets/blockcraft");
        for (String shape : new String[] {"ring", "crystal", "flare"}) {
            texture(root, shape);
            write(root, "items/presentation/" + shape + ".json", """
                    {"model":{"type":"minecraft:model","model":"blockcraft:item/presentation/%s",
                    "tints":[{"type":"minecraft:custom_model_data","index":0,"default":-1}]}}
                    """.formatted(shape));
            String elements = switch (shape) {
                case "ring" -> """
                        {"from":[0,8,0],"to":[16,8,16],"shade":false,
                        "faces":{"up":{"texture":"#surface","tintindex":0},"down":{"texture":"#surface","tintindex":0}}}
                        """;
                case "crystal" -> """
                        {"from":[4,4,4],"to":[12,12,12],"shade":false,
                        "rotation":{"origin":[8,8,8],"axis":"z","angle":45},
                        "faces":{"up":{"texture":"#surface","tintindex":0},"down":{"texture":"#surface","tintindex":0},
                        "north":{"texture":"#surface","tintindex":0},"south":{"texture":"#surface","tintindex":0},
                        "east":{"texture":"#surface","tintindex":0},"west":{"texture":"#surface","tintindex":0}}}
                        """;
                default -> """
                        {"from":[0,0,8],"to":[16,16,8],"shade":false,
                        "faces":{"north":{"texture":"#surface","tintindex":0},"south":{"texture":"#surface","tintindex":0}}},
                        {"from":[8,0,0],"to":[8,16,16],"shade":false,
                        "faces":{"east":{"texture":"#surface","tintindex":0},"west":{"texture":"#surface","tintindex":0}}}
                        """;
            };
            write(root, "models/item/presentation/" + shape + ".json", """
                    {"textures":{"surface":"blockcraft:item/presentation/%s","particle":"blockcraft:item/presentation/%s"},
                    "elements":[%s]}
                    """.formatted(shape, shape, elements));
        }
        for (int step = 0; step <= 32; step++) {
            post(root, "cinematic/fade/" + step, step / 32.0, 0, 0, 1);
            post(root, "cinematic/letterbox/" + step, step / 32.0, 1, 0, 1);
        }
        String[] atmospheres = {"hologram", "storm", "focus", "portal"};
        for (int i = 0; i < atmospheres.length; i++) {
            post(root, "atmosphere/" + atmospheres[i], 0, 0, i + 1, 1);
            for (int step = 1; step <= 8; step++) {
                post(root, "accent/" + atmospheres[i] + "/" + step, 0, 0, i + 1, step / 8.0);
            }
        }
        String[] channels = {"hurt", "suppressed", "rattled"};
        for (int i = 0; i < channels.length; i++) {
            for (int step = 1; step <= 8; step++) {
                post(root, channels[i] + "/" + step, 0, 0, atmospheres.length + 1 + i, step / 8.0);
            }
        }
    }

    private static void texture(Path root, String shape) throws Exception {
        var image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                double radius = Math.hypot(x - 15.5, y - 15.5);
                double alpha = switch (shape) {
                    case "ring" -> radius > 11 && radius < 15 ? 1 : radius > 7 && radius < 8 ? .5 : 0;
                    case "flare" -> Math.max(0, 1 - radius / 16) * Math.max(0, 1 - radius / 16);
                    default -> 1;
                };
                int value = shape.equals("crystal") && (x < 2 || y < 2 || x > 29 || y > 29) ? 255 : 210;
                image.setRGB(x, y, ((int) Math.round(alpha * 255) << 24) | (value << 16) | (value << 8) | value);
            }
        }
        var path = root.resolve("textures/item/presentation/" + shape + ".png");
        Files.createDirectories(path.getParent());
        ImageIO.write(image, "PNG", path.toFile());
    }

    private static void post(Path root, String name, double fade, int letterbox, int mode, double intensity) throws Exception {
        write(root, "post_effect/presentation/" + name + ".json", """
                {
                  "targets":{"swap":{}},
                  "passes":[
                    {
                      "vertex_shader":"minecraft:core/screenquad",
                      "fragment_shader":"blockcraft:post/presentation",
                      "inputs":[{"sampler_name":"In","target":"minecraft:main"}],
                      "output":"swap",
                      "uniforms":{"PresentationConfig":[{"name":"Parameters","type":"vec4","value":[%s,%d,%d,%s]}]}
                    },
                    {
                      "vertex_shader":"minecraft:core/screenquad",
                      "fragment_shader":"minecraft:post/blit",
                      "inputs":[{"sampler_name":"In","target":"swap"}],
                      "uniforms":{"BlitConfig":[{"name":"ColorModulate","type":"vec4","value":[1,1,1,1]}]},
                      "output":"minecraft:main"
                    }
                  ]
                }
                """.formatted(String.format(Locale.ROOT, "%.5f", fade), letterbox, mode,
                        intensity == 1 ? "1" : String.format(Locale.ROOT, "%.3f", intensity)));
    }

    private static void write(Path root, String name, String contents) throws Exception {
        var path = root.resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents);
    }
}
