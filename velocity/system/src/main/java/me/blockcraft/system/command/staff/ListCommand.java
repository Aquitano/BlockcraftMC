package me.blockcraft.system.command.staff;

import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.PlayerVisibility;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class ListCommand {

    private final CommandSupport support;
    private final PlayerVisibility visibility;
    private final SystemConfig config;
    private final Messages messages;

    public ListCommand(CommandSupport support, PlayerVisibility visibility, SystemConfig config, Messages messages) {
        this.support = support;
        this.visibility = visibility;
        this.config = config;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("list")
                .executes(support.asPlayer((player, context) -> {
                    long onServer = player.getCurrentServer().map(ServerConnection::getServer)
                            .map(server -> server.getPlayersConnected().stream().filter(other -> visibility.canSee(player, other)).count())
                            .orElse(0L);
                    var placeholders = TagResolver.resolver(
                            Placeholder.unparsed("ip", config.network().ip()),
                            Placeholder.unparsed("server", CommandSupport.currentServer(player)),
                            Formatter.number("network", visibility.countFor(player)),
                            Formatter.number("count", onServer));
                    messages.getLines("list.lines", placeholders).forEach(player::sendMessage);
                })));
    }
}
