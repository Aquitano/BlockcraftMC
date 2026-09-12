package me.blockcraft.system.command.player;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import me.blockcraft.system.LobbyConnector;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.network.ServerDirectory;

/** Joins a game by group name; the proxy picks the instance. */
public final class PlayCommand {

    private final CommandSupport support;
    private final LobbyConnector lobby;
    private final ServerDirectory directory;

    public PlayCommand(CommandSupport support, LobbyConnector lobby, ServerDirectory directory) {
        this.support = support;
        this.lobby = lobby;
        this.directory = directory;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("play")
                .executes(support.usage("play.usage"))
                .then(BrigadierCommand.requiredArgumentBuilder("game", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            directory.groups().stream().filter(group -> group.startsWith(builder.getRemaining())).forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(support.asPlayer((player, context) -> lobby.play(player, StringArgumentType.getString(context, "game"))))));
    }
}
