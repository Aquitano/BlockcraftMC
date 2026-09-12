package me.blockcraft.system.command.moderation;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.WarnRepository;
import me.blockcraft.system.PlayerLookup;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class WarnsCommand {

    private static final int MAX_LISTED = 18;

    private final CommandSupport support;
    private final PlayerLookup lookup;
    private final WarnRepository warns;
    private final Messages messages;

    public WarnsCommand(CommandSupport support, PlayerLookup lookup, WarnRepository warns, Messages messages) {
        this.support = support;
        this.lookup = lookup;
        this.warns = warns;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("warns")
                .requires(CommandSupport.permission(SystemPermissions.WARNS))
                .executes(support.usage("warns.usage"))
                .then(confirmClear("clear"))
                .then(confirmClear("reset"))
                .then(BrigadierCommand.literalArgumentBuilder("clear*")
                        .executes(support.run((source, context) -> clearAll(source)))
                        .then(support.playerArgument("player")
                                .executes(support.run((source, context) -> clear(source, StringArgumentType.getString(context, "player"))))))
                .then(BrigadierCommand.literalArgumentBuilder("get")
                        .executes(support.usage("warns.usage"))
                        .then(support.playerArgument("player")
                                .executes(support.run((source, context) -> get(source, StringArgumentType.getString(context, "player"))))))
                .then(BrigadierCommand.literalArgumentBuilder("list")
                        .executes(support.run((source, context) -> list(source)))));
    }

    private LiteralArgumentBuilder<CommandSource> confirmClear(String literal) {
        return BrigadierCommand.literalArgumentBuilder(literal)
                .executes(support.run((source, context) -> source.sendMessage(messages.get("warns.clear-all-confirm")
                        .clickEvent(ClickEvent.runCommand("/warns clear*"))
                        .hoverEvent(HoverEvent.showText(messages.get("warns.clear-hover"))))))
                .then(support.playerArgument("player")
                        .executes(support.run((source, context) -> {
                            String player = StringArgumentType.getString(context, "player");
                            source.sendMessage(messages.get("warns.clear-confirm", Placeholder.unparsed("player", player))
                                    .clickEvent(ClickEvent.runCommand("/warns clear* " + player))
                                    .hoverEvent(HoverEvent.showText(messages.get("warns.clear-hover"))));
                        })));
    }

    private void clearAll(CommandSource source) {
        warns.clearAll();
        source.sendMessage(messages.get("warns.cleared-all"));
    }

    private void clear(CommandSource source, String name) {
        lookup.find(name).ifPresentOrElse(target -> {
            int removed = warns.clear(target.uuid());
            source.sendMessage(messages.get("warns.cleared", Placeholder.unparsed("player", target.name()), Formatter.number("count", removed)));
        }, () -> source.sendMessage(messages.get("player-unknown", Placeholder.unparsed("player", name))));
    }

    private void get(CommandSource source, String name) {
        lookup.find(name).ifPresentOrElse(
                target -> source.sendMessage(messages.get("warns.get",
                        Placeholder.unparsed("player", target.name()), Formatter.number("count", warns.count(target.uuid())))),
                () -> source.sendMessage(messages.get("player-unknown", Placeholder.unparsed("player", name))));
    }

    private void list(CommandSource source) {
        var counts = warns.counts();
        if (counts.size() > MAX_LISTED) {
            source.sendMessage(messages.get("warns.too-many"));
            return;
        }
        source.sendMessage(messages.get("warns.list-header", Formatter.number("count", counts.size())));
        for (var entry : counts) {
            source.sendMessage(messages.get("warns.list-entry",
                    Placeholder.unparsed("player", entry.name()), Formatter.number("count", entry.count())));
        }
    }
}
