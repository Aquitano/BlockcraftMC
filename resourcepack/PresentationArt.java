import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class PresentationArt {

    public static void main(String[] args) throws Exception {
        var root = Path.of(args.length == 0 ? "src" : args[0]).resolve("assets/blockcraft");
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
