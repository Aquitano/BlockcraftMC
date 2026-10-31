package me.blockcraft.system.command.staff;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Edits the default and random MOTD lines; they keep the legacy "&" colour codes staff type in-game. */
public final class MotdCommand {

    private final CommandSupport support;
    private final SystemConfig config;
    private final Messages messages;

    public MotdCommand(CommandSupport support, SystemConfig config, Messages messages) {
        this.support = support;
        this.config = config;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("motd")
                .requires(CommandSupport.permission(SystemPermissions.MOTD))
                .executes(support.run((source, context) -> usage(source)))
                .then(BrigadierCommand.literalArgumentBuilder("list").executes(support.run((source, context) -> list(source))))
                .then(BrigadierCommand.literalArgumentBuilder("clear").executes(support.run((source, context) ->
                        source.sendMessage(messages.get("motd.cleared", Formatter.number("count", config.clearRandomMotds()))))))
                .then(BrigadierCommand.literalArgumentBuilder("setdefault")
                        .executes(support.run((source, context) -> usage(source)))
                        .then(BrigadierCommand.requiredArgumentBuilder("motd", StringArgumentType.greedyString())
                                .executes(support.run((source, context) -> {
                                    String motd = StringArgumentType.getString(context, "motd");
                                    config.setDefaultMotd(motd);
                                    source.sendMessage(messages.get("motd.default-set", motd(motd)));
                                }))))
                .then(BrigadierCommand.literalArgumentBuilder("add")
                        .executes(support.run((source, context) -> usage(source)))
                        .then(BrigadierCommand.requiredArgumentBuilder("motd", StringArgumentType.greedyString())
                                .executes(support.run((source, context) -> {
                                    String motd = StringArgumentType.getString(context, "motd");
                                    config.addRandomMotd(motd);
                                    source.sendMessage(messages.get("motd.added", motd(motd)));
                                }))))
                .then(remove("del"))
                .then(remove("remove")));
    }

    private LiteralArgumentBuilder<CommandSource> remove(String literal) {
        return BrigadierCommand.literalArgumentBuilder(literal)
                .executes(support.usage("motd.del-usage"))
                .then(BrigadierCommand.requiredArgumentBuilder("number", IntegerArgumentType.integer())
                        .executes(support.run((source, context) -> {
                            int number = IntegerArgumentType.getInteger(context, "number");
                            int size = config.randomMotds().size();
                            if (number < 1 || number > size) {
                                source.sendMessage(messages.get("motd.del-range", Formatter.number("max", size)));
                                return;
                            }
                            source.sendMessage(messages.get("motd.removed", motd(config.removeRandomMotd(number - 1))));
                        })));
    }

    private void usage(CommandSource source) {
        messages.getLines("motd.usage").forEach(source::sendMessage);
    }

    private void list(CommandSource source) {
        source.sendMessage(messages.get("motd.list-header"));
        source.sendMessage(messages.get("motd.list-default", motd(config.defaultMotd())));
        var random = config.randomMotds();
        if (random.isEmpty()) {
            source.sendMessage(messages.get("motd.list-empty"));
        }
        for (int i = 0; i < random.size(); i++) {
            source.sendMessage(messages.get("motd.list-entry", Formatter.number("number", i + 1), motd(random.get(i))));
        }
    }

    private static TagResolver motd(String legacy) {
        return Placeholder.component("motd", LegacyComponentSerializer.legacyAmpersand().deserialize(legacy));
    }
}
