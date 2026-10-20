package me.blockcraft.system.command.moderation;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.DurationText;
import me.blockcraft.common.moderation.PunishmentType;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.moderation.ModerationActions;
import me.blockcraft.system.moderation.PunishmentPreset;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.Map;


public final class StrafeCommand {

    private final CommandSupport support;
    private final ModerationActions actions;
    private final SystemConfig config;
    private final Messages messages;
    private final DurationText durations;

    public StrafeCommand(CommandSupport support, ModerationActions actions, SystemConfig config, Messages messages) {
        this.support = support;
        this.actions = actions;
        this.config = config;
        this.messages = messages;
        this.durations = new DurationText(messages);
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("strafe")
                .requires(CommandSupport.permission(SystemPermissions.BAN).or(CommandSupport.permission(SystemPermissions.MUTE)))
                .executes(support.run((source, context) -> list(source)))
                .then(support.playerArgument("player")
                        .executes(support.run((source, context) -> list(source)))
                        .then(BrigadierCommand.requiredArgumentBuilder("number", IntegerArgumentType.integer(1))
                                .executes(support.run((source, context) -> punish(source,
                                        StringArgumentType.getString(context, "player"),
                                        IntegerArgumentType.getInteger(context, "number")))))));
    }

    private void punish(CommandSource source, String name, int number) {
        support.withReference(source, name, target -> actions.preset(source, target, number));
    }

    private void list(CommandSource source) {
        source.sendMessage(messages.get("strafe.list-header"));
        config.punishments().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> source.sendMessage(entry(entry.getKey(), entry.getValue())));
    }

    private Component entry(int number, PunishmentPreset preset) {
        return messages.get("strafe.list-entry",
                Formatter.number("number", number),
                Placeholder.component("type", messages.get(preset.type() == PunishmentType.BAN ? "history.type-ban" : "history.type-mute")),
                Placeholder.unparsed("reason", preset.reason()),
                Placeholder.component("duration", preset.duration()
                        .<Component>map(duration -> Component.text(durations.format(duration)))
                        .orElseGet(() -> messages.get("duration.permanent"))));
    }
}
