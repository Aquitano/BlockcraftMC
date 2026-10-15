package me.blockcraft.common.message;

import me.blockcraft.common.config.YamlFile;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

/**
 * Every key a plugin asks its {@link Messages} for must exist in that module's bundled {@code messages.yml}, or in
 * BlockcraftAPI's for a Paper plugin. A key an api helper reads from the messages a game hands it must exist in
 * every module that uses the helper.
 */
public class MessageKeysTest {

    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final Path API = ROOT.resolve("paper/api");
    private static final Pattern LOOKUP = Pattern.compile("(?i)messages(?:\\(\\))?\\.(?:get|getLines|pick|raw|has)\\(\\s*\"([^\"]+)\"\\s*[,)]");
    private static final Pattern PACKAGE = Pattern.compile("^package\\s+([\\w.]+);", Pattern.MULTILINE);

    @Test
    public void everyRequestedKeyIsBundled() throws IOException {
        var missing = new ArrayList<String>();
        for (Path module : modules()) {
            Set<String> own = bundledKeys(module);
            Set<String> allowed = new HashSet<>(own);
            if (module.startsWith(ROOT.resolve("paper"))) {
                allowed.addAll(bundledKeys(API));
            }
            for (Path source : sources(module)) {
                String code = Files.readString(source);
                Matcher matcher = LOOKUP.matcher(code);
                while (matcher.find()) {
                    String key = matcher.group(1);
                    if (allowed.contains(key)) {
                        continue;
                    }
                    if (module.equals(API)) {
                        usersOf(source, code).stream()
                                .filter(user -> !bundledKeys(user).contains(key))
                                .forEach(user -> missing.add(ROOT.relativize(user) + " lacks " + key + " for " + source.getFileName()));
                    } else {
                        missing.add(ROOT.relativize(source) + ": " + key);
                    }
                }
            }
        }
        assertEquals(String.join("\n", missing), List.of(), missing);
    }

    private static List<Path> modules() throws IOException {
        var modules = new ArrayList<Path>();
        for (String platform : List.of("paper", "velocity")) {
            try (Stream<Path> children = Files.list(ROOT.resolve(platform))) {
                children.filter(child -> Files.isDirectory(child.resolve("src/main/java"))).forEach(modules::add);
            }
        }
        return modules;
    }

    private static List<Path> sources(Path module) throws IOException {
        try (Stream<Path> files = Files.walk(module.resolve("src/main/java"))) {
            return files.filter(file -> file.toString().endsWith(".java")).toList();
        }
    }

    private static Set<String> bundledKeys(Path module) {
        Path resources = module.resolve("src/main/resources");
        if (!Files.isDirectory(resources)) {
            return Set.of();
        }
        try (Stream<Path> files = Files.walk(resources)) {
            var keys = new HashSet<String>();
            for (Path file : files.filter(file -> file.getFileName().toString().equals("messages.yml")).toList()) {
                try (InputStream in = Files.newInputStream(file)) {
                    keys.addAll(YamlFile.flatten(YamlFile.load(in)).keySet());
                }
            }
            return keys;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The modules whose sources import the class in {@code source}. */
    private static List<Path> usersOf(Path source, String code) throws IOException {
        Matcher matcher = PACKAGE.matcher(code);
        if (!matcher.find()) {
            return List.of();
        }
        String importLine = "import " + matcher.group(1) + "." + source.getFileName().toString().replace(".java", "") + ";";
        var users = new ArrayList<Path>();
        for (Path module : modules()) {
            if (module.equals(API)) {
                continue;
            }
            for (Path file : sources(module)) {
                if (Files.readString(file).contains(importLine)) {
                    users.add(module);
                    break;
                }
            }
        }
        return users;
    }
}
