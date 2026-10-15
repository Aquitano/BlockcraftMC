package me.blockcraft.system.command.player;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.DurationText;
import me.blockcraft.common.moderation.PlayerRepository;
import me.blockcraft.system.PlayerVisibility;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.time.Duration;
import java.time.Instant;

/** Online players are shown as the viewer would see them; anyone else is looked up by account name only. */
public final class SeenCommand {

    private final CommandSupport support;
    private final PlayerVisibility visibility;
    private final PlayerRepository players;
    private final Messages messages;
    private final DurationText durations;

    public SeenCommand(CommandSupport support, PlayerVisibility visibility, PlayerRepository players, Messages messages) {
        this.support = support;
        this.visibility = visibility;
        this.players = players;
        this.messages = messages;
        this.durations = new DurationText(messages);
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("seen")
                .requires(CommandSupport.permission(SystemPermissions.SEEN))
                .executes(support.usage("seen.usage"))
                .then(support.playerArgument("player")
                        .executes(support.run((source, context) -> seen(source, StringArgumentType.getString(context, "player"))))));
    }

    private void seen(CommandSource source, String name) {
        var online = visibility.find(source, name);
        if (online.isPresent()) {
            source.sendMessage(messages.get("seen.online",
                    Placeholder.unparsed("player", visibility.shownName(online.get())),
                    Placeholder.unparsed("server", CommandSupport.currentServer(online.get()))));
            return;
        }
        players.byName(name).ifPresentOrElse(
                known -> source.sendMessage(messages.get("seen.offline",
                        Placeholder.unparsed("player", known.name()),
                        Placeholder.unparsed("time", durations.format(Duration.between(known.lastSeen(), Instant.now()))))),
                () -> source.sendMessage(messages.get("player-unknown", Placeholder.unparsed("player", name))));
    }
}
