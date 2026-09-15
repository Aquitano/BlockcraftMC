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

public final class UnmuteCommand {

    private final CommandSupport support;
    private final PlayerLookup lookup;
    private final PunishmentService punishments;
    private final Messages messages;

    public UnmuteCommand(CommandSupport support, PlayerLookup lookup, PunishmentService punishments, Messages messages) {
        this.support = support;
        this.lookup = lookup;
        this.punishments = punishments;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("unmute")
                .requires(CommandSupport.permission(SystemPermissions.MUTE))
                .executes(support.usage("unmute.usage"))
                .then(support.playerArgument("player")
                        .executes(support.run((source, context) -> unmute(source, StringArgumentType.getString(context, "player"))))));
    }

    private void unmute(CommandSource source, String name) {
        var target = lookup.find(name);
        if (target.isEmpty()) {
            source.sendMessage(messages.get("player-unknown", Placeholder.unparsed("player", name)));
            return;
        }
        if (punishments.activeMute(target.get()).isEmpty()) {
            source.sendMessage(messages.get("unmute.not-muted"));
            return;
        }
        source.sendMessage(messages.get("unmute.done"));
        punishments.unmute(target.get(), CommandSupport.sender(source));
    }
}
