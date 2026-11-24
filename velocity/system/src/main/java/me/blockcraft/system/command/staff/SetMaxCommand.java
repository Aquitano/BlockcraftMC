package me.blockcraft.system.command.staff;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;

public final class SetMaxCommand {

    private final CommandSupport support;
    private final SystemConfig config;
    private final Messages messages;

    public SetMaxCommand(CommandSupport support, SystemConfig config, Messages messages) {
        this.support = support;
        this.config = config;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("setmax")
                .requires(CommandSupport.permission(SystemPermissions.SET_MAX))
                .executes(support.run((source, context) ->
                        source.sendMessage(messages.get("setmax.current", Formatter.number("max", config.maxPlayers())))))
                .then(BrigadierCommand.requiredArgumentBuilder("max", IntegerArgumentType.integer(0))
                        .executes(support.run((source, context) -> {
                            int max = IntegerArgumentType.getInteger(context, "max");
                            config.setMaxPlayers(max);
                            source.sendMessage(messages.get("setmax.done", Formatter.number("max", max)));
                        }))));
    }
}
