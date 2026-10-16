package me.blockcraft.system.command.moderation;

import me.blockcraft.system.moderation.ModerationActions;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;


public final class StrafeCommand {

    private final CommandSupport support;
    private final ModerationActions actions;
    private final Messages messages;

    public StrafeCommand(CommandSupport support, ModerationActions actions, Messages messages) {
        this.support = support;
        this.actions = actions;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("strafe")
                .requires(CommandSupport.permission(SystemPermissions.BAN))
                .executes(support.run((source, context) -> messages.getLines("strafe.list").forEach(source::sendMessage)))
                .then(support.playerArgument("player")
                        .executes(support.run((source, context) -> messages.getLines("strafe.list").forEach(source::sendMessage)))
                        .then(BrigadierCommand.requiredArgumentBuilder("number", IntegerArgumentType.integer(1))
                                .executes(support.run((source, context) -> punish(source,
                                        StringArgumentType.getString(context, "player"),
                                        IntegerArgumentType.getInteger(context, "number")))))));
    }

    private void punish(CommandSource source, String name, int number) {
        support.withReference(source, name, target -> actions.preset(source, target, number));
    }
}
