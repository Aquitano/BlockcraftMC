package me.blockcraft.system.command.staff;

import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.DurationText;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.common.network.ServerRepository;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class ServersCommand {

    private final CommandSupport support;
    private final ServerRepository servers;
    private final SystemConfig config;
    private final Messages messages;
    private final DurationText durations;

    public ServersCommand(CommandSupport support, ServerRepository servers, SystemConfig config, Messages messages) {
        this.support = support;
        this.servers = servers;
        this.config = config;
        this.messages = messages;
        this.durations = new DurationText(messages);
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("servers")
                .requires(CommandSupport.permission(SystemPermissions.TEAM))
                .executes(support.run((source, context) -> list(source))));
    }

    private void list(CommandSource source) {
        var entries = servers.all();
        if (entries.isEmpty()) {
            source.sendMessage(messages.get("servers.empty"));
            return;
        }
        source.sendMessage(messages.get("servers.header", Formatter.number("count", entries.size())));
        for (var entry : entries) {
            boolean stale = entry.heartbeatAge().toSeconds() >= config.discovery().staleSeconds();
            source.sendMessage(messages.get(stale ? "servers.entry-stale" : "servers.entry",
                    Placeholder.unparsed("server", entry.server().name()),
                    Placeholder.unparsed("group", entry.server().group()),
                    Placeholder.unparsed("state", entry.server().status().state().name()),
                    Formatter.number("online", entry.server().status().online()),
                    Formatter.number("max", entry.server().status().maxPlayers()),
                    Placeholder.unparsed("age", durations.format(entry.heartbeatAge()))));
        }
    }
}
