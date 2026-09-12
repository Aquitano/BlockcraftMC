package me.blockcraft.system.command.moderation;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.StrikeRepository;
import me.blockcraft.system.PlayerLookup;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class DelStrikeCommand {

    private final CommandSupport support;
    private final PlayerLookup lookup;
    private final StrikeRepository strikes;
    private final Messages messages;

    public DelStrikeCommand(CommandSupport support, PlayerLookup lookup, StrikeRepository strikes, Messages messages) {
        this.support = support;
        this.lookup = lookup;
        this.strikes = strikes;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("delstrike")
                .requires(CommandSupport.permission(SystemPermissions.STRIKE))
                .executes(support.usage("delstrike.usage"))
                .then(support.playerArgument("player")
                        .executes(support.run((source, context) -> remove(source, StringArgumentType.getString(context, "player"))))));
    }

    private void remove(CommandSource source, String name) {
        var target = lookup.find(name);
        if (target.isEmpty()) {
            source.sendMessage(messages.get("player-unknown", Placeholder.unparsed("player", name)));
            return;
        }
        int before = strikes.count(target.get().uuid());
        if (before == 0) {
            source.sendMessage(messages.get("delstrike.none"));
            return;
        }
        strikes.removeLatest(target.get().uuid());
        var placeholders = TagResolver.resolver(
                Placeholder.unparsed("player", target.get().name()),
                Formatter.number("number", before),
                Formatter.number("remaining", before - 1));
        source.sendMessage(messages.get("delstrike.done", placeholders));
        source.sendMessage(messages.get("delstrike.remaining", placeholders));
        target.get().online().ifPresent(player -> {
            player.sendMessage(messages.get("delstrike.received", placeholders));
            player.sendMessage(messages.get("delstrike.remaining", placeholders));
        });
    }
}
