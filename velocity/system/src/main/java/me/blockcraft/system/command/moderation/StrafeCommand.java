package me.blockcraft.system.command.moderation;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.PunishmentType;
import me.blockcraft.system.PlayerLookup;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.moderation.PunishmentPreset;
import me.blockcraft.system.moderation.PunishmentService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.Random;

public final class StrafeCommand {

    private final CommandSupport support;
    private final PlayerLookup lookup;
    private final PunishmentService punishments;
    private final SystemConfig config;
    private final Messages messages;
    private final Random random = new Random();

    public StrafeCommand(CommandSupport support, PlayerLookup lookup, PunishmentService punishments, SystemConfig config,
                         Messages messages) {
        this.support = support;
        this.lookup = lookup;
        this.punishments = punishments;
        this.config = config;
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
        PunishmentPreset preset = config.punishments().get(number);
        if (preset == null) {
            source.sendMessage(messages.get("strafe.unknown-number"));
            return;
        }
        if (config.isUnbannable(name)) {
            source.sendMessage(messages.get("strafe.unbannable"));
            return;
        }
        var target = lookup.find(name);
        if (target.isEmpty()) {
            source.sendMessage(messages.get("player-unknown", Placeholder.unparsed("player", name)));
            return;
        }
        String issuer = CommandSupport.senderName(source);
        var existing = preset.type() == PunishmentType.BAN ? punishments.activeBan(target.get()) : punishments.activeMute(target.get());
        if (existing.isPresent()) {
            source.sendMessage(messages.get("strafe.overwrite"));
            punishments.revokeQuietly(target.get(), preset.type(), issuer);
        }
        String reason = preset.reason() + " #" + banId();
        switch (preset.type()) {
            case BAN -> punishments.ban(target.get(), reason, issuer, preset.duration());
            case MUTE -> punishments.mute(target.get(), reason, issuer, preset.duration());
        }
        source.sendMessage(messages.get("strafe.done"));
    }

    /** Four digits, no two equal neighbours, so staff can quote a punishment unambiguously. */
    private String banId() {
        var id = new StringBuilder();
        int previous = -1;
        for (int i = 0; i < 4; i++) {
            int digit;
            do {
                digit = random.nextInt(10);
            } while (digit == previous);
            id.append(digit);
            previous = digit;
        }
        return id.toString();
    }
}
