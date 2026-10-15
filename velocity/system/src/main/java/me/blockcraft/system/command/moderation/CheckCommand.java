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

public final class CheckCommand {

    private final CommandSupport support;
    private final PlayerLookup lookup;
    private final PunishmentService punishments;
    private final Messages messages;

    public CheckCommand(CommandSupport support, PlayerLookup lookup, PunishmentService punishments, Messages messages) {
        this.support = support;
        this.lookup = lookup;
        this.punishments = punishments;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("check")
                .requires(CommandSupport.permission(SystemPermissions.CHECK))
                .executes(support.usage("check.usage"))
                .then(support.playerArgument("player")
                        .executes(support.run((source, context) -> check(source, StringArgumentType.getString(context, "player"))))));
    }

    private void check(CommandSource source, String name) {
        var target = lookup.find(name);
        if (target.isEmpty()) {
            source.sendMessage(messages.get("player-unknown", Placeholder.unparsed("player", name)));
            return;
        }
        punishments.activeBan(target.get()).ifPresentOrElse(
                ban -> messages.getLines("check.banned", punishments.placeholders(target.get().name(), ban)).forEach(source::sendMessage),
                () -> messages.getLines("check.not-banned").forEach(source::sendMessage));
        punishments.activeMute(target.get()).ifPresentOrElse(
                mute -> messages.getLines("check.muted", punishments.placeholders(target.get().name(), mute)).forEach(source::sendMessage),
                () -> messages.getLines("check.not-muted").forEach(source::sendMessage));
    }
}
