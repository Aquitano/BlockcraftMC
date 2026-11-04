package me.blockcraft.system.command.staff;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class TeamChatCommand {

    private final CommandSupport support;
    private final ProxyServer proxy;
    private final Messages messages;

    public TeamChatCommand(CommandSupport support, ProxyServer proxy, Messages messages) {
        this.support = support;
        this.proxy = proxy;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("tc")
                .requires(CommandSupport.permission(SystemPermissions.TEAM_CHAT))
                .executes(support.asPlayer((player, context) -> listMembers(player)))
                .then(BrigadierCommand.requiredArgumentBuilder("message", StringArgumentType.greedyString())
                        .executes(support.asPlayer((player, context) -> send(player, StringArgumentType.getString(context, "message"))))));
    }

    private void listMembers(Player player) {
        player.sendMessage(messages.get("tc.online-header"));
        for (Player member : proxy.getAllPlayers()) {
            if (member.hasPermission(SystemPermissions.TEAM_CHAT)) {
                player.sendMessage(messages.get("tc.online-entry",
                        Placeholder.unparsed("player", member.getUsername()),
                        Placeholder.unparsed("server", CommandSupport.currentServer(member))));
            }
        }
        player.sendMessage(messages.get("tc.usage"));
    }

    private void send(Player sender, String message) {
        var line = messages.get("tc.format",
                Placeholder.unparsed("player", sender.getUsername()),
                Placeholder.unparsed("server", CommandSupport.currentServer(sender)),
                Placeholder.unparsed("message", message));
        for (Player member : proxy.getAllPlayers()) {
            if (member.hasPermission(SystemPermissions.TEAM_CHAT)) {
                member.sendMessage(line);
            }
        }
    }
}
