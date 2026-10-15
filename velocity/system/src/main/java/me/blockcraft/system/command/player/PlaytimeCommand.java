package me.blockcraft.system.command.player;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.DurationText;
import me.blockcraft.common.moderation.PlayerRepository;
import me.blockcraft.system.PlayerLookup;
import me.blockcraft.system.Playtime;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class PlaytimeCommand {

    private final CommandSupport support;
    private final PlayerLookup lookup;
    private final PlayerRepository players;
    private final Playtime playtime;
    private final Messages messages;
    private final DurationText durations;

    public PlaytimeCommand(CommandSupport support, PlayerLookup lookup, PlayerRepository players, Playtime playtime, Messages messages) {
        this.support = support;
        this.lookup = lookup;
        this.players = players;
        this.playtime = playtime;
        this.messages = messages;
        this.durations = new DurationText(messages);
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("playtime")
                .requires(CommandSupport.permission(SystemPermissions.PLAYTIME))
                .executes(support.asPlayer((player, context) -> show(player, player.getUsername())))
                .then(support.playerArgument("player")
                        .requires(CommandSupport.permission(SystemPermissions.PLAYTIME_OTHERS))
                        .executes(support.run((source, context) -> show(source, StringArgumentType.getString(context, "player"))))));
    }

    private void show(CommandSource source, String name) {
        var known = lookup.find(name).flatMap(target -> players.byUuid(target.uuid()));
        known.ifPresentOrElse(
                player -> source.sendMessage(messages.get("playtime.total",
                        Placeholder.unparsed("player", player.name()),
                        Placeholder.unparsed("time", durations.format(playtime.total(player))))),
                () -> source.sendMessage(messages.get("player-unknown", Placeholder.unparsed("player", name))));
    }
}
