package me.blockcraft.system.command.moderation;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.PlayerVisibility;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class KickCommand {

    private final CommandSupport support;
    private final ProxyServer proxy;
    private final PlayerVisibility visibility;
    private final SystemConfig config;
    private final Messages messages;

    public KickCommand(CommandSupport support, ProxyServer proxy, PlayerVisibility visibility, SystemConfig config, Messages messages) {
        this.support = support;
        this.proxy = proxy;
        this.visibility = visibility;
        this.config = config;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("kick")
                .requires(CommandSupport.permission(SystemPermissions.KICK))
                .executes(support.usage("kick.usage"))
                .then(support.playerArgument("player")
                        .executes(support.usage("kick.usage"))
                        .then(BrigadierCommand.requiredArgumentBuilder("reason", StringArgumentType.greedyString())
                                .executes(support.run((source, context) -> kick(source,
                                        StringArgumentType.getString(context, "player"),
                                        StringArgumentType.getString(context, "reason")))))));
    }

    private void kick(CommandSource source, String name, String reason) {
        var target = visibility.find(source, name);
        if (target.isEmpty()) {
            source.sendMessage(messages.get("kick.offline"));
            return;
        }
        if (!support.mayModerate(source, target.get().getUniqueId())) {
            return;
        }
        source.sendMessage(messages.get("kick.done"));
        var placeholders = TagResolver.resolver(
                Placeholder.unparsed("player", target.get().getUsername()),
                Placeholder.unparsed("issuer", CommandSupport.senderName(source)),
                Placeholder.unparsed("ip", config.network().ip()),
                Placeholder.unparsed("reason", reason));
        var notification = messages.getLines("kick.notify", placeholders);
        for (Player staff : proxy.getAllPlayers()) {
            if (staff.hasPermission(SystemPermissions.KICK)) {
                notification.forEach(staff::sendMessage);
            }
        }
        target.get().disconnect(Component.join(JoinConfiguration.newlines(), messages.getLines("kick.screen", placeholders)));
    }
}
