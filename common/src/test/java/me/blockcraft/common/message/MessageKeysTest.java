package me.blockcraft.common.message;

import me.blockcraft.common.config.YamlFile;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;

/**
 * Every key a plugin asks its {@link Messages} for must exist in that module's bundled {@code messages.yml}, or in
 * BlockcraftAPI's for a Paper plugin. A key an api helper reads from the messages a game hands it must exist in
 * every module that uses the helper.
 */
public class MessageKeysTest {

    private static final Path ROOT = MessageFiles.ROOT;
    private static final Path API = MessageFiles.API;
    private static final Pattern LOOKUP = Pattern.compile("(?i)messages(?:\\(\\))?\\.(?:get|getLines|pick|raw|has)\\(\\s*\"([^\"]+)\"\\s*[,)]");
    private static final Pattern PACKAGE = Pattern.compile("^package\\s+([\\w.]+);", Pattern.MULTILINE);
    private static final Pattern PLACEHOLDER = Pattern.compile(
            "(?:Placeholder\\.(?:unparsed|parsed|component|styling)|Formatter\\.(?:number|date|choice|booleanChoice|joining))\\(\\s*\"([\\w-]+)\"");

    @Test
    public void everyRequestedKeyIsBundled() throws IOException {
        var missing = new ArrayList<String>();
        for (Path module : MessageFiles.modules()) {
            Set<String> own = MessageFiles.bundled(module).keySet();
            Set<String> allowed = new HashSet<>(own);
            if (module.startsWith(ROOT.resolve("paper"))) {
                allowed.addAll(MessageFiles.bundled(API).keySet());
            }
            for (Path source : MessageFiles.sources(module)) {
                String code = Files.readString(source);
                Matcher matcher = LOOKUP.matcher(code);
                while (matcher.find()) {
                    String key = matcher.group(1);
                    if (allowed.contains(key)) {
                        continue;
                    }
                    if (module.equals(API)) {
                        usersOf(source, code).stream()
                                .filter(user -> !MessageFiles.bundled(user).keySet().contains(key))
                                .forEach(user -> missing.add(ROOT.relativize(user) + " lacks " + key + " for " + source.getFileName()));
                    } else {
                        missing.add(ROOT.relativize(source) + ": " + key);
                    }
                }
            }
        }
        assertEquals(String.join("\n", missing), List.of(), missing);
    }

    /** The network's tags resolve before a message's own placeholders, so a placeholder named like one never shows. */
    @Test
    public void noPlaceholderIsNamedLikeANetworkTag() throws IOException {
        var networkTags = new HashSet<String>();
        for (Path module : MessageFiles.modules()) {
            MessageFiles.bundled(module).keySet().stream()
                    .filter(key -> key.startsWith("palette."))
                    .forEach(key -> networkTags.add(key.substring("palette.".length())));
        }
        try (var config = Files.newInputStream(API.resolve("src/main/resources/config.yml"))) {
            networkTags.addAll(YamlFile.section(YamlFile.load(config), "network").keySet());
        }
        var shadowed = new ArrayList<String>();
        for (Path module : MessageFiles.modules()) {
            for (Path source : MessageFiles.sources(module)) {
                PLACEHOLDER.matcher(Files.readString(source)).results()
                        .map(match -> match.group(1))
                        .filter(networkTags::contains)
                        .forEach(name -> shadowed.add(ROOT.relativize(source) + ": <" + name + ">"));
            }
        }
        assertEquals(String.join("\n", shadowed), List.of(), shadowed);
    }

    /** The modules whose sources import the class in {@code source}. */
    private static List<Path> usersOf(Path source, String code) throws IOException {
        Matcher matcher = PACKAGE.matcher(code);
        if (!matcher.find()) {
            return List.of();
        }
        String importLine = "import " + matcher.group(1) + "." + source.getFileName().toString().replace(".java", "") + ";";
        var users = new ArrayList<Path>();
        for (Path module : MessageFiles.modules()) {
            if (module.equals(API)) {
                continue;
            }
            for (Path file : MessageFiles.sources(module)) {
                if (Files.readString(file).contains(importLine)) {
                    users.add(module);
                    break;
                }
            }
        }
        return users;
    }
}
