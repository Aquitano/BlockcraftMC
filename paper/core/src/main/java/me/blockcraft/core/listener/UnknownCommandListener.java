package me.blockcraft.core.listener;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.command.UnknownCommandEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Paper raises {@link UnknownCommandEvent} for every command it cannot parse, a typo in the arguments of a real one
 * included. A command the player was never sent gets the network's own line: staff learn that it exists but is not
 * theirs to use, everyone else that it does not exist, as vanilla says too. One they can see keeps Minecraft's
 * explanation of what is wrong with it. All lead with the error icon.
 */
public final class UnknownCommandListener implements Listener {

    private final Messages messages;
    private final Map<UUID, Set<String>> visible = new HashMap<>();

    public UnknownCommandListener(Messages messages) {
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommandsSent(PlayerCommandSendEvent event) {
        visible.put(event.getPlayer().getUniqueId(), Set.copyOf(event.getCommands()));
    }

    @EventHandler
    public void onUnknownCommand(UnknownCommandEvent event) {
        if (!(event.getSender() instanceof Player player)) {
            return;
        }
        var icons = BlockcraftApi.get().resourcePack().icons(player);
        String label = event.getCommandLine().strip().replaceFirst("^/", "").split("\\s+", 2)[0]
                .toLowerCase(Locale.ROOT);
        var reason = event.message();
        if (reason != null && visible.getOrDefault(player.getUniqueId(), Set.of()).contains(label)) {
            event.message(messages.get("unknown-command.invalid", icons, Placeholder.component("reason", reason)));
        } else if (player.hasPermission(ChatPermissions.TEAM) && Bukkit.getCommandMap().getCommand(label) != null) {
            event.message(messages.get("unknown-command.forbidden", icons, Placeholder.unparsed("command", label)));
        } else {
            event.message(messages.get("unknown-command.missing", icons, Placeholder.unparsed("command", label)));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        visible.remove(event.getPlayer().getUniqueId());
    }
}
