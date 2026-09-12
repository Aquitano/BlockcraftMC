package me.blockcraft.system.command.moderation;

import com.velocitypowered.api.command.CommandSource;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.StrikeRepository;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.UUID;

/** The strike overview shared by /strikes and /showstrikes. */
final class StrikeList {

    private final StrikeRepository strikes;
    private final Messages messages;

    StrikeList(StrikeRepository strikes, Messages messages) {
        this.strikes = strikes;
        this.messages = messages;
    }

    void send(CommandSource viewer, String name, UUID player) {
        var list = strikes.list(player);
        viewer.sendMessage(messages.get("strikes.header", Placeholder.unparsed("player", name)));
        viewer.sendMessage(messages.get("strikes.count", Formatter.number("count", list.size())));
        for (int i = 0; i < list.size(); i++) {
            viewer.sendMessage(messages.get("strikes.entry",
                    Formatter.number("number", i + 1),
                    Placeholder.unparsed("reason", list.get(i).reason()),
                    Placeholder.unparsed("issuer", list.get(i).issuer())));
        }
        viewer.sendMessage(messages.get("strikes.footer"));
    }
}
