package me.blockcraft.system.command.moderation;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.PlayerLookup;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.moderation.Durations;
import me.blockcraft.system.moderation.PunishmentService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class TempBanCommand {

    private final CommandSupport support;
    private final PlayerLookup lookup;
    private final PunishmentService punishments;
    private final SystemConfig config;
    private final Messages messages;

    public TempBanCommand(CommandSupport support, PlayerLookup lookup, PunishmentService punishments, SystemConfig config,
                          Messages messages) {
        this.support = support;
        this.lookup = lookup;
        this.punishments = punishments;
        this.config = config;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("tempban")
                .requires(CommandSupport.permission(SystemPermissions.BAN))
                .executes(support.usage("tempban.usage"))
                .then(support.playerArgument("player")
                        .executes(support.usage("tempban.usage"))
                        .then(BrigadierCommand.requiredArgumentBuilder("duration", StringArgumentType.word())
                                .executes(support.usage("tempban.usage"))
                                .then(BrigadierCommand.requiredArgumentBuilder("reason", StringArgumentType.greedyString())
                                        .executes(support.run((source, context) -> ban(source,
                                                StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "duration"),
                                                StringArgumentType.getString(context, "reason"))))))));
    }

    private void ban(CommandSource source, String name, String spec, String reason) {
        if (config.isUnbannable(name)) {
            source.sendMessage(messages.get("tempban.unbannable"));
            return;
        }
        var target = lookup.find(name);
        if (target.isEmpty()) {
            source.sendMessage(messages.get("player-unknown", Placeholder.unparsed("player", name)));
            return;
        }
        if (!support.mayModerate(source, target.get().uuid())) {
            return;
        }
        if (punishments.activeBan(target.get()).isPresent()) {
            source.sendMessage(messages.get("tempban.already-banned"));
            return;
        }
        var duration = Durations.timed(spec);
        if (duration.isEmpty()) {
            messages.getLines("tempban.units").forEach(source::sendMessage);
            return;
        }
        source.sendMessage(messages.get("tempban.done", Placeholder.unparsed("player", target.get().name())));
        punishments.ban(target.get(), reason, CommandSupport.senderName(source), duration);
    }
}
