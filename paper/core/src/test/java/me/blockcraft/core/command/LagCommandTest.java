package me.blockcraft.core.command;

import io.papermc.paper.plugin.configuration.PluginMeta;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class LagCommandTest {

    @Test
    public void requiredDependentsIncludeProvidedNamesWithoutDuplicatesOrSelf() throws InvalidDescriptionException {
        var target = plugin("Core", "provides: [LegacyCore]\ndepend: [Core]\n");
        var plugins = List.of(target,
                plugin("Zombies", "depend: [core, LegacyCore]\n"),
                plugin("Lobby", "depend: [legacycore]\n"),
                plugin("Unrelated", "depend: [Other]\n"));

        assertEquals(List.of("Lobby", "Zombies"), LagCommand.dependents(target, plugins, PluginMeta::getPluginDependencies));
    }

    @Test
    public void optionalDependentsStaySeparateFromRequiredAndLoadBefore() throws InvalidDescriptionException {
        var target = plugin("Core", "");
        var plugins = List.of(target,
                plugin("Required", "depend: [Core]\n"),
                plugin("Optional", "softdepend: [Core]\n"),
                plugin("Ordered", "loadbefore: [Core]\n"));

        assertEquals(List.of("Required"), LagCommand.dependents(target, plugins, PluginMeta::getPluginDependencies));
        assertEquals(List.of("Optional"), LagCommand.dependents(target, plugins, PluginMeta::getPluginSoftDependencies));
    }

    private static PluginMeta plugin(String name, String dependencies) throws InvalidDescriptionException {
        return new PluginDescriptionFile(new StringReader("name: " + name + "\nversion: 1\nmain: example.Plugin\n" + dependencies));
    }
}
