package me.blockcraft.api.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.world.MapBundle;
import me.blockcraft.api.world.WorldTemplate;
import me.blockcraft.api.world.WaitingLobby;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/** {@code /map} shows the map this server runs; {@code /map save} publishes the running world to its template. */
public final class MapCommand {

    private MapCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Plugin plugin, MapBundle map, Optional<WaitingLobby> waiting, Messages messages) {
        return Commands.literal("map")
                .requires(source -> source.getSender().hasPermission("blockcraft.map"))
                .executes(context -> {
                    var sender = context.getSource().getSender();
                    messages.getLines("map.info", tags(map, current(waiting, sender))).forEach(sender::sendMessage);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("save").executes(context -> {
                    save(plugin, map, waiting, messages, context.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                }))
                .build();
    }

    private static Optional<WaitingLobby> current(Optional<WaitingLobby> waiting, CommandSender sender) {
        return waiting.filter(lobby -> sender instanceof Player player && player.getWorld().equals(lobby.world()));
    }

    private static void save(Plugin plugin, MapBundle map, Optional<WaitingLobby> waiting, Messages messages, CommandSender sender) {
        var lobby = current(waiting, sender);
        if (map.template().isEmpty() && lobby.isEmpty()) {
            sender.sendMessage(messages.get("map.no-template"));
            return;
        }
        try {
            if (lobby.isPresent()) {
                lobby.orElseThrow().save();
            } else {
                map.save();
            }
            sender.sendMessage(messages.get("map.saved", tags(map, lobby)));
        } catch (RuntimeException e) {
            plugin.getSLF4JLogger().error("Could not publish the map", e);
            sender.sendMessage(messages.get("map.failed"));
        }
    }

    private static TagResolver tags(MapBundle map, Optional<WaitingLobby> waiting) {
        return TagResolver.resolver(
                Placeholder.unparsed("map", waiting.map(lobby -> lobby.directory().getFileName().toString()).orElse(map.name())),
                Placeholder.unparsed("builders", waiting.isPresent() || map.builders().isEmpty() ? "-" : String.join(", ", map.builders())),
                Placeholder.unparsed("template", waiting.map(WaitingLobby::directory).or(() -> map.template().map(WorldTemplate::directory))
                        .map(String::valueOf).orElse("-")));
    }
}
