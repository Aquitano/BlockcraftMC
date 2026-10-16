package me.blockcraft.api.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.world.MapBundle;
import me.blockcraft.api.world.WorldTemplate;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

/** {@code /map} shows the map this server runs; {@code /map save} publishes the running world to its template. */
public final class MapCommand {

    private MapCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Plugin plugin, MapBundle map, Messages messages) {
        return Commands.literal("map")
                .requires(source -> source.getSender().hasPermission("blockcraft.map"))
                .executes(context -> {
                    messages.getLines("map.info", tags(map)).forEach(context.getSource().getSender()::sendMessage);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("save").executes(context -> {
                    save(plugin, map, messages, context.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                }))
                .build();
    }

    private static void save(Plugin plugin, MapBundle map, Messages messages, CommandSender sender) {
        if (map.template().isEmpty()) {
            sender.sendMessage(messages.get("map.no-template"));
            return;
        }
        boolean started = map.save(plugin,
                () -> sender.sendMessage(messages.get("map.saved", tags(map))),
                () -> sender.sendMessage(messages.get("map.failed")));
        sender.sendMessage(messages.get(started ? "map.saving" : "map.busy"));
    }

    private static TagResolver tags(MapBundle map) {
        return TagResolver.resolver(
                Placeholder.unparsed("map", map.name()),
                Placeholder.unparsed("builders", map.builders().isEmpty() ? "-" : String.join(", ", map.builders())),
                Placeholder.unparsed("template", map.template().map(WorldTemplate::directory).map(String::valueOf).orElse("-")));
    }
}
