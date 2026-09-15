package me.blockcraft.api.dialog;

import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogRegistryEntry;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.event.WritableRegistry;
import io.papermc.paper.registry.keys.DialogKeys;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;

/**
 * The network menu behind the pause screen's button and the quick-actions key. It is a registry dialog, so it is
 * built once at bootstrap and its buttons can only name what they open; the click arrives as
 * {@link PlayerCustomClickEvent} and the plugin opens the per-player screen from there.
 */
public final class ServerMenu implements Listener {

    public static final TypedKey<Dialog> KEY = DialogKeys.create(Key.key("blockcraft", "server_menu"));

    private static final Key SETTINGS = Key.key("blockcraft", "settings");
    private static final Key PROFILE = Key.key("blockcraft", "profile");
    private static final Key HUB = Key.key("blockcraft", "hub");
    private static final int COLUMNS = 1;

    private final Plugin plugin;

    public ServerMenu(Plugin plugin) {
        this.plugin = plugin;
    }

    public static void register(WritableRegistry<Dialog, DialogRegistryEntry.Builder> registry, Messages messages) {
        registry.register(KEY, builder -> builder
                .base(DialogBase.builder(messages.get("server-menu.title")).build())
                .type(DialogType.multiAction(List.of(
                                entry(messages.get("server-menu.settings"), SETTINGS),
                                entry(messages.get("server-menu.profile"), PROFILE),
                                entry(messages.get("server-menu.hub"), HUB)))
                        .columns(COLUMNS)
                        .exitAction(Dialogs.button(messages.get("menu.close")))
                        .build()));
    }

    private static ActionButton entry(Component label, Key opens) {
        return ActionButton.builder(label).action(DialogAction.customClick(opens, null)).build();
    }

    @EventHandler
    public void onClick(PlayerCustomClickEvent event) {
        if (!(event.getCommonConnection() instanceof PlayerGameConnection connection)) {
            return;
        }
        var player = connection.getPlayer();
        var clicked = event.getIdentifier();
        if (clicked.equals(SETTINGS)) {
            new SettingsDialog(player, Optional.empty()).open(player);
        } else if (clicked.equals(PROFILE)) {
            BlockcraftApi.get().profiles().open(player, Optional.empty());
        } else if (clicked.equals(HUB)) {
            ProxyMessaging.hub(plugin, player);
        }
    }
}
