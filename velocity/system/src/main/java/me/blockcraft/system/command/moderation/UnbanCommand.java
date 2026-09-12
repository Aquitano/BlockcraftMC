package me.blockcraft.system.command.moderation;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.PlayerLookup;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.moderation.PunishmentService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class UnbanCommand {

    private final CommandSupport support;
    private final PlayerLookup lookup;
    private final PunishmentService punishments;
    private final Messages messages;

    public UnbanCommand(CommandSupport support, PlayerLookup lookup, PunishmentService punishments, Messages messages) {
        this.support = support;
        this.lookup = lookup;
        this.punishments = punishments;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("unban")
                .requires(CommandSupport.permission(SystemPermissions.BAN))
                .executes(support.usage("unban.usage"))
                .then(support.playerArgument("player")
                        .executes(support.run((source, context) -> unban(source, StringArgumentType.getString(context, "player"))))));
    }

    private void unban(CommandSource source, String name) {
        var target = lookup.find(name);
        if (target.isEmpty()) {
            source.sendMessage(messages.get("player-unknown", Placeholder.unparsed("player", name)));
            return;
        }
        if (punishments.activeBan(target.get()).isEmpty()) {
            source.sendMessage(messages.get("unban.not-banned"));
            return;
        }
        source.sendMessage(messages.get("unban.done"));
        punishments.unban(target.get(), CommandSupport.senderName(source));
    }
}
