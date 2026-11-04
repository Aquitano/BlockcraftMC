package me.blockcraft.system.command.staff;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.DurationText;
import me.blockcraft.common.moderation.PlayerAddressRepository;
import me.blockcraft.common.moderation.PlayerAddressRepository.Alt;
import me.blockcraft.system.PlayerLookup;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.time.Duration;
import java.time.Instant;

/** The accounts that logged in from one of the player's addresses within {@code alts.retention-days}. */
public final class AltsCommand {

    private final CommandSupport support;
    private final PlayerLookup lookup;
    private final PlayerAddressRepository addresses;
    private final Duration retention;
    private final Messages messages;
    private final DurationText durations;

    public AltsCommand(CommandSupport support, PlayerLookup lookup, PlayerAddressRepository addresses, Duration retention, Messages messages) {
        this.support = support;
        this.lookup = lookup;
        this.addresses = addresses;
        this.retention = retention;
        this.messages = messages;
        this.durations = new DurationText(messages);
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("alts")
                .requires(CommandSupport.permission(SystemPermissions.ALTS))
                .executes(support.usage("alts.usage"))
                .then(support.playerArgument("player")
                        .executes(support.run((source, context) -> alts(source, StringArgumentType.getString(context, "player"))))));
    }

    private void alts(CommandSource source, String name) {
        var target = lookup.findVisible(source, name, messages.get("player-unknown", Placeholder.unparsed("player", name)));
        if (target.isEmpty()) {
            return;
        }
        var player = Placeholder.unparsed("player", target.get().name());
        var days = Formatter.number("days", retention.toDays());
        var alts = addresses.alts(target.get().uuid(), retention);
        if (alts.isEmpty()) {
            source.sendMessage(messages.get("alts.none", player, days));
            return;
        }
        source.sendMessage(messages.get("alts.header", player, days, Formatter.number("count", alts.size())));
        alts.forEach(alt -> source.sendMessage(entry(alt)));
    }

    private Component entry(Alt alt) {
        var name = Placeholder.unparsed("player", alt.name());
        return messages.get("alts.entry", name,
                        Placeholder.unparsed("time", durations.format(Duration.between(alt.lastSeen(), Instant.now()))),
                        Placeholder.component("ban-flag", alt.banned() ? messages.get("alts.banned") : Component.empty()),
                        Placeholder.component("mute-flag", alt.muted() ? messages.get("alts.muted") : Component.empty()))
                .clickEvent(ClickEvent.runCommand("/whois " + alt.name()))
                .hoverEvent(HoverEvent.showText(messages.get("alts.hover", name)));
    }
}
