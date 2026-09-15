package me.blockcraft.system.command.moderation;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.PlayerLookup;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.moderation.Durations;
import me.blockcraft.system.moderation.PunishmentService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class TempMuteCommand {

    private final CommandSupport support;
    private final PlayerLookup lookup;
    private final PunishmentService punishments;
    private final Messages messages;

    public TempMuteCommand(CommandSupport support, PlayerLookup lookup, PunishmentService punishments, Messages messages) {
        this.support = support;
        this.lookup = lookup;
        this.punishments = punishments;
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
        var target = lookup.find(name);
        if (target.isEmpty()) {
            source.sendMessage(messages.get("player-unknown", Placeholder.unparsed("player", name)));
            return;
        }
        if (!support.mayModerate(source, target.get().uuid())) {
            return;
        }
        if (punishments.activeMute(target.get()).isPresent()) {
            source.sendMessage(messages.get("tempmute.already-muted"));
            return;
        }
        var duration = Durations.timed(spec);
        if (duration.isEmpty()) {
            messages.getLines("tempmute.units").forEach(source::sendMessage);
            return;
        }
        source.sendMessage(messages.get("tempmute.done", Placeholder.unparsed("player", target.get().name())));
        punishments.mute(target.get(), reason, CommandSupport.senderName(source), duration);
    }
}
