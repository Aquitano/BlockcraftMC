package me.blockcraft.system.command.player;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.friends.FriendManager;

import java.util.function.BiConsumer;

public final class FriendCommand {

    private final CommandSupport support;
    private final FriendManager friends;
    private final Messages messages;

    public FriendCommand(CommandSupport support, FriendManager friends, Messages messages) {
        this.support = support;
        this.friends = friends;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("friend")
                .requires(CommandSupport.permission(SystemPermissions.FRIENDS))
                .executes(support.asPlayer((player, context) -> help(player)))
                .then(BrigadierCommand.literalArgumentBuilder("help").executes(support.asPlayer((player, context) -> help(player))))
                .then(BrigadierCommand.literalArgumentBuilder("list").executes(support.asPlayer((player, context) -> friends.list(player))))
                .then(withName("add", friends::add))
                .then(withName("accept", friends::accept))
                .then(withName("deny", friends::deny))
                .then(withName("remove", friends::remove))
                .then(withName("jump", friends::jump)));
    }

    /** {@code /friends} is the list alone, which is what players type most. */
    public BrigadierCommand list() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("friends")
                .requires(CommandSupport.permission(SystemPermissions.FRIENDS))
                .executes(support.asPlayer((player, context) -> friends.list(player))));
    }

    private LiteralArgumentBuilder<CommandSource> withName(String literal, BiConsumer<Player, String> action) {
        return BrigadierCommand.literalArgumentBuilder(literal)
                .executes(support.usage("friends.usage-" + literal))
                .then(support.playerArgument("player").executes(support.asPlayer((player, context) ->
                        action.accept(player, StringArgumentType.getString(context, "player")))));
    }

    private void help(Player player) {
        messages.getLines("friends.help").forEach(player::sendMessage);
    }
}
