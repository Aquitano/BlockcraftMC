package me.blockcraft.system.command.staff;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class FindCommand {

    private final CommandSupport support;
    private final ProxyServer proxy;
    private final Messages messages;

    public FindCommand(CommandSupport support, ProxyServer proxy, Messages messages) {
        this.support = support;
        this.proxy = proxy;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("find")
                .requires(CommandSupport.permission(SystemPermissions.FIND))
                .executes(support.usage("find.usage"))
                .then(support.playerArgument("player")
                        .executes(support.run((source, context) -> proxy.getPlayer(StringArgumentType.getString(context, "player"))
                                .ifPresentOrElse(
                                        target -> source.sendMessage(messages.get("find.found",
                                                Placeholder.unparsed("server", CommandSupport.currentServer(target)))),
                                        () -> source.sendMessage(messages.get("find.offline")))))));
    }
}
