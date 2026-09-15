package me.blockcraft.system.command.moderation;

import me.blockcraft.common.player.PlayerReference;
import me.blockcraft.system.moderation.ModerationActions;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.moderation.Durations;

public final class TempMuteCommand {

    private final CommandSupport support;
    private final ModerationActions actions;
    private final Messages messages;

    public TempMuteCommand(CommandSupport support, ModerationActions actions, Messages messages) {
        this.support = support;
        this.actions = actions;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("tempmute")
                .requires(CommandSupport.permission(SystemPermissions.MUTE))
                .executes(support.usage("tempmute.usage"))
                .then(support.playerArgument("player")
                        .executes(support.usage("tempmute.usage"))
                        .then(BrigadierCommand.requiredArgumentBuilder("duration", StringArgumentType.word())
                                .executes(support.usage("tempmute.usage"))
                                .then(BrigadierCommand.requiredArgumentBuilder("reason", StringArgumentType.greedyString())
                                        .executes(support.run((source, context) -> mute(source,
                                                StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "duration"),
                                                StringArgumentType.getString(context, "reason"))))))));
    }

    private void mute(CommandSource source, String name, String spec, String reason) {
        actions.timed(source, PlayerReference.named(name),
                me.blockcraft.common.moderation.PunishmentType.MUTE, Durations.timed(spec), reason);
    }
}
