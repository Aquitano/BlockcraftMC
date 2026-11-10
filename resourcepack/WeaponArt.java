import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WeaponArt {

    public static void main(String[] args) throws IOException {
        write(Path.of(args[0]));
    }

    static void write(Path root) throws IOException {
        var models = root.resolve("assets/blockcraft/models/item/weapons");
        var textures = root.resolve("assets/blockcraft/textures/item/weapons");
        Files.createDirectories(models);
        Files.createDirectories(textures);
        for (var name : new String[] {"bone", "blaze_rod", "feather"}) {
            model(models, name, "minecraft:item/" + name);
        }
        for (var name : new String[] {"swiss_flag", "german_flag", "austrian_flag"}) {
            model(models, name, "blockcraft:item/weapons/" + name);
            ImageIO.write(flag(name), "PNG", textures.resolve(name + ".png").toFile());
        }
    }

    private static void model(Path directory, String name, String texture) throws IOException {
        Files.writeString(directory.resolve(name + ".json"), """
                {
                  "parent": "minecraft:item/handheld",
                  "textures": {
                    "layer0": "%s"
                  }
                }
                """.formatted(texture));
    }

    private static BufferedImage flag(String name) {
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 1; y < 16; y++) {
            image.setRGB(3, y, 0xFF6B4226);
            image.setRGB(4, y, 0xFFA97946);
        }
        for (int y = 1; y <= 9; y++) {
            for (int x = 5; x <= 14; x++) {
                int color = switch (name) {
                    case "german_flag" -> y <= 3 ? 0xFF202020 : y <= 6 ? 0xFFDB3030 : 0xFFFFCF36;
                    case "austrian_flag" -> y >= 4 && y <= 6 ? 0xFFF5F5F5 : 0xFFD93232;
                    default -> (x >= 9 && x <= 10 && y >= 3 && y <= 7)
                            || (y >= 4 && y <= 5 && x >= 7 && x <= 12) ? 0xFFF5F5F5 : 0xFFD93232;
                };
                image.setRGB(x, y, color);
            }
        }
        return image;
    }
}
