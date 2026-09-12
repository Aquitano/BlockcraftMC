package me.blockcraft.system.command.moderation;

import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.AdLogRepository;
import me.blockcraft.system.PlayerLookup;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/** Lists the chat messages the Paper-side advertisement filter blocked. */
public final class WerbungCommand {

    private static final int MAX_LISTED = 18;

    private final CommandSupport support;
    private final PlayerLookup lookup;
    private final AdLogRepository adLog;
    private final Messages messages;

    public WerbungCommand(CommandSupport support, PlayerLookup lookup, AdLogRepository adLog, Messages messages) {
        this.support = support;
        this.lookup = lookup;
        this.adLog = adLog;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("werbung")
                .requires(CommandSupport.permission(SystemPermissions.WERBUNG))
                .executes(support.run((source, context) -> list(source)))
                .then(BrigadierCommand.literalArgumentBuilder("clear").executes(support.run((source, context) -> clear(source))))
                .then(BrigadierCommand.literalArgumentBuilder("reset").executes(support.run((source, context) -> clear(source)))));
    }

    private void list(CommandSource source) {
        var entries = adLog.list();
        if (entries.size() > MAX_LISTED) {
            source.sendMessage(messages.get("werbung.too-many"));
            return;
        }
        source.sendMessage(messages.get("werbung.header", Formatter.number("count", entries.size())));
        for (var entry : entries) {
            source.sendMessage(messages.get("werbung.entry",
                    Placeholder.unparsed("player", lookup.nameOf(entry.player()).orElse(entry.player().toString())),
                    Placeholder.unparsed("server", entry.server()),
                    Placeholder.unparsed("message", entry.message())));
        }
    }

    private void clear(CommandSource source) {
        source.sendMessage(messages.get("werbung.cleared", Formatter.number("count", adLog.count())));
        adLog.clear();
    }
}
