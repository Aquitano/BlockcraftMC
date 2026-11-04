package me.blockcraft.system.command.staff;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.ProxySettings;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;

/** Network-wide slow mode; the Paper servers enforce it from bc_settings next to their own chat cooldown. */
public final class ChatSlowCommand {

    private final CommandSupport support;
    private final ProxyServer proxy;
    private final ProxySettings settings;
    private final Messages messages;

    public ChatSlowCommand(CommandSupport support, ProxyServer proxy, ProxySettings settings, Messages messages) {
        this.support = support;
        this.proxy = proxy;
        this.settings = settings;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("chatslow")
                .requires(CommandSupport.permission(SystemPermissions.CHAT_SLOW))
                .executes(support.usage("chatslow.usage"))
                .then(BrigadierCommand.literalArgumentBuilder("off")
                        .executes(support.run((source, context) -> {
                            settings.setChatSlowSeconds(0);
                            proxy.sendMessage(messages.get("chatslow.disabled"));
                        })))
                .then(BrigadierCommand.requiredArgumentBuilder("seconds", IntegerArgumentType.integer(1))
                        .executes(support.run((source, context) -> {
                            int seconds = IntegerArgumentType.getInteger(context, "seconds");
                            settings.setChatSlowSeconds(seconds);
                            proxy.sendMessage(messages.get("chatslow.enabled", Formatter.number("seconds", seconds)));
                        }))));
    }
}
