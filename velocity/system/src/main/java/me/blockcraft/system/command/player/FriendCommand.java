package me.blockcraft.system.command.player;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.friends.FriendManager;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;

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
                .then(withName("add", friends::add, support.playerArgument("player")))
                .then(withName("accept", friends::accept, relatedNames(friends::requestNames)))
                .then(withName("deny", friends::deny, relatedNames(friends::requestNames)))
                .then(withName("remove", friends::remove, relatedNames(friends::friendNames)))
                .then(withName("jump", friends::jump, relatedNames(friends::onlineFriendNames))));
    }

    /** {@code /friends} is the list alone, which is what players type most. */
    public BrigadierCommand list() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("friends")
                .requires(CommandSupport.permission(SystemPermissions.FRIENDS))
                .executes(support.asPlayer((player, context) -> friends.list(player))));
    }

    private LiteralArgumentBuilder<CommandSource> withName(String literal, BiConsumer<Player, String> action,
                                                         RequiredArgumentBuilder<CommandSource, String> argument) {
        return BrigadierCommand.literalArgumentBuilder(literal)
                .executes(support.usage("friends.usage-" + literal))
                .then(argument.executes(support.asPlayer((player, context) ->
                        action.accept(player, StringArgumentType.getString(context, "player")))));
    }

    private RequiredArgumentBuilder<CommandSource, String> relatedNames(Function<Player, List<String>> names) {
        return BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                .suggests((context, builder) -> {
                    if (!(context.getSource() instanceof Player player)) {
                        return builder.buildFuture();
                    }
                    return CompletableFuture.supplyAsync(() -> {
                        String typed = builder.getRemainingLowerCase();
                        names.apply(player).stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(typed)).forEach(builder::suggest);
                        return builder.build();
                    }, Thread::startVirtualThread);
                });
    }

    private void help(Player player) {
        messages.getLines("friends.help").forEach(player::sendMessage);
    }
}
