package me.blockcraft.mapimport;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Turns an old Minecraft world into a map template in two steps around a Paper server that finishes its chunks:
 * {@code prepare} copies the old world in, {@code write} keeps what matters of the finished one. Gradle's
 * {@code importMap} runs all three (worlds/README.md, "Importing an old 1.8 map").
 */
public final class MapImport {

    private MapImport() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            fail("Usage: prepare --scratch <dir> --source <world> | write --scratch <dir> --target <dir> [--mode flat|void|area|all]"
                    + " [--bounds x1,z1,x2,z2;...] [--margin chunks] [--layout level|dimension] [--dry-run]");
        }
        var options = options(args);
        try {
            var files = ImportFiles.at(Path.of(required(options, "scratch")));
            switch (args[0]) {
                case "prepare" -> Prepare.run(Path.of(required(options, "source")).toAbsolutePath().normalize(), files);
                case "write" -> Write.run(files, Write.Settings.of(options));
                default -> fail("Unknown step " + args[0]);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            fail(e.getMessage());
        }
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            if (!args[i].startsWith("--")) {
                fail("Expected an option, not " + args[i]);
            }
            String name = args[i].substring(2);
            boolean flag = i + 1 >= args.length || args[i + 1].startsWith("--");
            options.put(name, flag ? "true" : args[++i]);
        }
        return options;
    }

    static Optional<String> option(Map<String, String> options, String name) {
        return Optional.ofNullable(options.get(name)).filter(value -> !value.isBlank());
    }

    private static String required(Map<String, String> options, String name) {
        return option(options, name).orElseThrow(() -> new IllegalArgumentException("Missing --" + name));
    }

    private static void fail(String message) {
        System.err.println(message);
        System.exit(1);
    }
}
