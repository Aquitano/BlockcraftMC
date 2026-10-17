package me.blockcraft.api;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.tags.DialogTagKeys;
import me.blockcraft.api.dialog.ServerMenu;
import me.blockcraft.api.world.WorldTemplate;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/**
 * Runs before the server loads its world: restores the map from its template when one is configured, and registers
 * the network menu dialog into the pause screen and the quick-actions key.
 */
public final class BlockcraftApiBootstrap implements PluginBootstrap {

    private static final Path SERVER_PROPERTIES = Path.of("server.properties");
    private static final String DEFAULT_LEVEL_NAME = "world";

    @Override
    public void bootstrap(BootstrapContext context) {
        var resources = ApiResources.load(context.getDataDirectory(), name -> getClass().getResourceAsStream("/" + name));
        WorldTemplate.configured(resources.config()).ifPresent(template -> restore(template, context.getLogger()));
        var messages = resources.messages();
        var lifecycle = context.getLifecycleManager();
        lifecycle.registerEventHandler(RegistryEvents.DIALOG.compose(), event -> ServerMenu.register(event.registry(), messages));
        lifecycle.registerEventHandler(LifecycleEvents.TAGS.postFlatten(RegistryKey.DIALOG), event -> {
            event.registrar().addToTag(DialogTagKeys.PAUSE_SCREEN_ADDITIONS, List.of(ServerMenu.KEY));
            event.registrar().addToTag(DialogTagKeys.QUICK_ACTIONS, List.of(ServerMenu.KEY));
        });
    }

    private static void restore(WorldTemplate template, ComponentLogger logger) {
        Path world = Path.of(levelName()).toAbsolutePath();
        template.recover();
        if (!template.exists()) {
            logger.warn("World template {} holds no map yet, keeping {} as it is; build the map and run /map save", template.directory(), world);
            return;
        }
        template.restore(world);
        logger.info("Restored {} from {}", world, template.directory());
    }

    private static String levelName() {
        if (Files.notExists(SERVER_PROPERTIES)) {
            return DEFAULT_LEVEL_NAME;
        }
        var properties = new Properties();
        try (InputStream in = Files.newInputStream(SERVER_PROPERTIES)) {
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + SERVER_PROPERTIES, e);
        }
        return properties.getProperty("level-name", DEFAULT_LEVEL_NAME);
    }
}
