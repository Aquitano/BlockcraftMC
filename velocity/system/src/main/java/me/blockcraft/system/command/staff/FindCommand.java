package me.blockcraft.system.command.staff;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.PlayerLookup;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class FindCommand {

    private final CommandSupport support;
    private final PlayerLookup lookup;
    private final Messages messages;

    public FindCommand(CommandSupport support, PlayerLookup lookup, Messages messages) {
        this.support = support;
        this.lookup = lookup;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("find")
                .requires(CommandSupport.permission(SystemPermissions.FIND))
                .executes(support.usage("find.usage"))
                .then(support.playerArgument("player")
                        .executes(support.run((source, context) -> lookup.online(source, StringArgumentType.getString(context, "player"), messages.get("find.offline"))
                                .ifPresent(target -> source.sendMessage(messages.get("find.found",
                                        Placeholder.unparsed("server", CommandSupport.currentServer(target)))))))));
    }
}
