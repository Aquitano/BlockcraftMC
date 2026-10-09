package me.blockcraft.system.command.moderation;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.PunishmentRepository;
import me.blockcraft.common.moderation.PunishmentType;
import me.blockcraft.common.player.PlayerReference;
import me.blockcraft.system.PlayerLookup;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.moderation.Durations;
import me.blockcraft.system.moderation.ModerationActions;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The four commands each punishment type has, named after it: {@code /ban} only points to {@code /strafe},
 * {@code /tempban}, {@code /unban} and {@code /banlist}; the same for mutes.
 */
public final class PunishmentCommands {

    private final CommandSupport support;
    private final PlayerLookup lookup;
    private final ModerationActions actions;
    private final PunishmentRepository punishments;
    private final Messages messages;

    public PunishmentCommands(CommandSupport support, PlayerLookup lookup, ModerationActions actions, PunishmentRepository punishments,
                              Messages messages) {
        this.support = support;
        this.lookup = lookup;
        this.actions = actions;
        this.punishments = punishments;
        this.messages = messages;
    }

    public List<BrigadierCommand> create(PunishmentType type) {
        String name = type.name().toLowerCase(Locale.ROOT);
        String permission = switch (type) {
            case BAN -> SystemPermissions.BAN;
            case MUTE -> SystemPermissions.MUTE;
        };
        String listPermission = switch (type) {
            case BAN -> SystemPermissions.BAN_LIST;
            case MUTE -> SystemPermissions.MUTE_LIST;
        };
        return List.of(redirect(name, permission), timed(type, "temp" + name, permission), revoke(type, "un" + name, permission),
                list(type, name + "list", listPermission));
    }

    private BrigadierCommand redirect(String name, String permission) {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder(name)
                .requires(CommandSupport.permission(permission))
                .executes(support.usage(name + ".use-strafe"))
                .then(BrigadierCommand.requiredArgumentBuilder("ignored", StringArgumentType.greedyString())
                        .executes(support.usage(name + ".use-strafe"))));
    }

    private BrigadierCommand timed(PunishmentType type, String name, String permission) {
        String usage = name + ".usage";
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder(name)
                .requires(CommandSupport.permission(permission))
                .executes(support.usage(usage))
                .then(support.playerArgument("player")
                        .executes(support.usage(usage))
                        .then(BrigadierCommand.requiredArgumentBuilder("duration", StringArgumentType.word())
                                .executes(support.usage(usage))
                                .then(BrigadierCommand.requiredArgumentBuilder("reason", StringArgumentType.greedyString())
                                        .executes(support.run((source, context) -> withReference(source,
                                                StringArgumentType.getString(context, "player"),
                                                target -> actions.timed(source, target, type,
                                                        Durations.timed(StringArgumentType.getString(context, "duration")),
                                                        StringArgumentType.getString(context, "reason")))))))));
    }

    private BrigadierCommand revoke(PunishmentType type, String name, String permission) {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder(name)
                .requires(CommandSupport.permission(permission))
                .executes(support.usage(name + ".usage"))
                .then(support.playerArgument("player")
                        .executes(support.run((source, context) -> withReference(source, StringArgumentType.getString(context, "player"),
                                target -> actions.revoke(source, target, type))))));
    }

    private BrigadierCommand list(PunishmentType type, String name, String permission) {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder(name)
                .requires(CommandSupport.permission(permission))
                .executes(support.run((source, context) -> {
                    var active = punishments.listActive(type);
                    if (active.isEmpty()) {
                        source.sendMessage(messages.get(name + ".empty"));
                        return;
                    }
                    source.sendMessage(messages.get(name + ".header"));
                    for (var punishment : active) {
                        String player = lookup.nameOf(punishment.player()).orElse(punishment.player().toString());
                        source.sendMessage(messages.get(name + ".entry", Placeholder.unparsed("player", player)));
                    }
                    source.sendMessage(messages.get(name + ".count", Formatter.number("count", active.size())));
                })));
    }

    /** Brigadier words also allow characters no Minecraft name has. */
    private void withReference(CommandSource source, String name, Consumer<PlayerReference> action) {
        if (PlayerReference.isName(name)) {
            action.accept(PlayerReference.named(name));
        } else {
            source.sendMessage(messages.get("player-unknown", Placeholder.unparsed("player", name)));
        }
    }
}
