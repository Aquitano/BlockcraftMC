package me.blockcraft.system.command.moderation;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.StrikeRepository;
import me.blockcraft.system.PlayerLookup;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class ShowStrikesCommand {

    private final CommandSupport support;
    private final PlayerLookup lookup;
    private final StrikeList strikeList;
    private final Messages messages;

    public ShowStrikesCommand(CommandSupport support, PlayerLookup lookup, StrikeRepository strikes, Messages messages) {
        this.support = support;
        this.lookup = lookup;
        this.strikeList = new StrikeList(strikes, messages);
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("showstrikes")
                .requires(CommandSupport.permission(SystemPermissions.STRIKE))
                .executes(support.usage("showstrikes.usage"))
                .then(support.playerArgument("player")
                        .executes(support.run((source, context) -> show(source, StringArgumentType.getString(context, "player"))))));
    }

    private void show(CommandSource source, String name) {
        lookup.find(name).ifPresentOrElse(
                target -> strikeList.send(source, target.name(), target.uuid()),
                () -> source.sendMessage(messages.get("player-unknown", Placeholder.unparsed("player", name))));
    }
}
