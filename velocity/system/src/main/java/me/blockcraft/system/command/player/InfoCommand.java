package me.blockcraft.system.command.player;

import com.velocitypowered.api.command.BrigadierCommand;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.command.CommandSupport;

/** One of the configured info commands: it prints its {@code info.<name>} lines and nothing else. */
public final class InfoCommand {

    private InfoCommand() {
    }

    public static String messageKey(String name) {
        return "info." + name;
    }

    public static BrigadierCommand create(CommandSupport support, String name, Messages messages) {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder(name)
                .executes(support.run((source, context) -> messages.getLines(messageKey(name)).forEach(source::sendMessage))));
    }
}
