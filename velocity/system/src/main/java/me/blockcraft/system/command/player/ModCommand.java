package me.blockcraft.system.command.player;

import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;

/** Tells a team member how many team members are online. */
public final class ModCommand {

    private final CommandSupport support;
    private final ProxyServer proxy;
    private final Messages messages;

    public ModCommand(CommandSupport support, ProxyServer proxy, Messages messages) {
        this.support = support;
        this.proxy = proxy;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("mod")
                .requires(CommandSupport.permission(SystemPermissions.TEAM))
                .executes(support.asPlayer((player, context) -> {
                    long online = proxy.getAllPlayers().stream().filter(other -> other.hasPermission(SystemPermissions.TEAM)).count();
                    player.sendMessage(messages.get("mod", Formatter.number("count", online)));
                })));
    }
}
