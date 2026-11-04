package me.blockcraft.common.message;

import me.blockcraft.common.config.YamlFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** The plugin modules of the repository, their sources and the messages they bundle, for the tests that read them. */
final class MessageFiles {

    static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
    static final Path API = ROOT.resolve("paper/api");

    private MessageFiles() {
    }

    static List<Path> modules() throws IOException {
        var modules = new ArrayList<Path>();
        for (String platform : List.of("paper", "velocity")) {
            try (Stream<Path> children = Files.list(ROOT.resolve(platform))) {
                children.filter(child -> Files.isDirectory(child.resolve("src/main/java"))).forEach(modules::add);
            }
        }
        return modules;
    }

    static List<Path> sources(Path module) throws IOException {
        try (Stream<Path> files = Files.walk(module.resolve("src/main/java"))) {
            return files.filter(file -> file.toString().endsWith(".java")).toList();
        }
    }

    /** Every bundled message by its dotted key; a list's lines are joined with newlines. */
    static Map<String, String> bundled(Path module) {
        Path resources = module.resolve("src/main/resources");
        if (!Files.isDirectory(resources)) {
            return Map.of();
        }
        try (Stream<Path> files = Files.walk(resources)) {
            var messages = new HashMap<String, String>();
            for (Path file : files.filter(file -> file.getFileName().toString().equals("messages.yml")).toList()) {
                try (InputStream in = Files.newInputStream(file)) {
                    messages.putAll(YamlFile.flatten(YamlFile.load(in)));
                }
            }
            return messages;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
