package me.blockcraft.system.command.player;

import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.proxy.Player;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class YouTuberCommand {

    private final CommandSupport support;
    private final SystemConfig config;
    private final Messages messages;

    public YouTuberCommand(CommandSupport support, SystemConfig config, Messages messages) {
        this.support = support;
        this.config = config;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("youtuber")
                .executes(support.asPlayer((player, context) -> send(player, "youtuber.info")))
                .then(BrigadierCommand.literalArgumentBuilder("250")
                        .executes(support.asPlayer((player, context) -> send(player, "youtuber.requirements-250"))))
                .then(BrigadierCommand.literalArgumentBuilder("500")
                        .executes(support.asPlayer((player, context) -> send(player, "youtuber.requirements-500")))));
    }

    private void send(Player player, String key) {
        messages.getLines(key, Placeholder.unparsed("website", config.network().website())).forEach(player::sendMessage);
    }
}
