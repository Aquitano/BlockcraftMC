package me.blockcraft.api;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.tags.DialogTagKeys;
import me.blockcraft.api.dialog.ServerMenu;

import java.util.List;

/** Registers the network menu dialog and hangs it into the pause screen and the quick-actions key. */
public final class BlockcraftApiBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {
        var messages = ApiResources.load(context.getDataDirectory(), name -> getClass().getResourceAsStream("/" + name)).messages();
        var lifecycle = context.getLifecycleManager();
        lifecycle.registerEventHandler(RegistryEvents.DIALOG.compose(), event -> ServerMenu.register(event.registry(), messages));
        lifecycle.registerEventHandler(LifecycleEvents.TAGS.postFlatten(RegistryKey.DIALOG), event -> {
            event.registrar().addToTag(DialogTagKeys.PAUSE_SCREEN_ADDITIONS, List.of(ServerMenu.KEY));
            event.registrar().addToTag(DialogTagKeys.QUICK_ACTIONS, List.of(ServerMenu.KEY));
        });
    }
}
