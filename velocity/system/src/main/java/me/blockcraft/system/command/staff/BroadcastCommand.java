package me.blockcraft.system.command.staff;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class BroadcastCommand {

    private final CommandSupport support;
    private final ProxyServer proxy;
    private final Messages messages;

    public BroadcastCommand(CommandSupport support, ProxyServer proxy, Messages messages) {
        this.support = support;
        this.proxy = proxy;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("bc")
                .requires(CommandSupport.permission(SystemPermissions.BROADCAST))
                .executes(support.usage("bc.usage"))
                .then(BrigadierCommand.requiredArgumentBuilder("message", StringArgumentType.greedyString())
                        .executes(support.run((source, context) -> {
                            var text = LegacyComponentSerializer.legacyAmpersand().deserialize(StringArgumentType.getString(context, "message"));
                            var lines = messages.getLines("bc.format", Placeholder.component("message", text));
                            for (Player player : proxy.getAllPlayers()) {
                                lines.forEach(player::sendMessage);
                            }
                        }))));
    }
}
