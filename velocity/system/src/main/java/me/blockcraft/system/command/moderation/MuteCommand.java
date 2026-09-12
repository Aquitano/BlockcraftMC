package me.blockcraft.system.command.moderation;

import com.velocitypowered.api.command.BrigadierCommand;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;

public final class MuteCommand {

    private final CommandSupport support;

    public MuteCommand(CommandSupport support) {
        this.support = support;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("mute")
                .requires(CommandSupport.permission(SystemPermissions.MUTE))
                .executes(support.usage("mute.use-strafe")));
    }
}
