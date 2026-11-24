package me.blockcraft.common.config;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class YamlFileTest {

    @Test
    public void anOverrideOfAKeyTheDefaultLacksIsNamedDownToItsFirstMissingLevel() {
        var defaults = Map.of("motd", Map.of("default", "", "random", List.of()), "max-players", 100);
        var overrides = Map.of("motd", Map.of("default", "Hi", "rotating", List.of("a")), "lobby", Map.of("spawn", 1),
                "max-players", 50);

        assertEquals(List.of("lobby", "motd.rotating"), YamlFile.unknownKeys(defaults, overrides).stream().sorted().toList());
    }
}
