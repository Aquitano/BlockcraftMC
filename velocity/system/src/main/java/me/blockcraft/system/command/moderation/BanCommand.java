package me.blockcraft.system.command.moderation;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;

/** Bans are issued through /strafe or /tempban; this only points there (the /warn click action still runs it). */
public final class BanCommand {

    private final CommandSupport support;

    public BanCommand(CommandSupport support) {
        this.support = support;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("ban")
                .requires(CommandSupport.permission(SystemPermissions.BAN))
                .executes(support.usage("ban.use-strafe"))
                .then(BrigadierCommand.requiredArgumentBuilder("ignored", StringArgumentType.greedyString())
                        .executes(support.usage("ban.use-strafe"))));
    }
}
