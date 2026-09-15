package me.blockcraft.system.command.moderation;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.DurationText;
import me.blockcraft.common.moderation.Punishment;
import me.blockcraft.common.moderation.PunishmentType;
import me.blockcraft.system.PlayerLookup;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.moderation.PunishmentService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Every ban and mute a player ever collected, newest first, capped at {@code history.limit}. */
public final class HistoryCommand {

    private final CommandSupport support;
    private final PlayerLookup lookup;
    private final PunishmentService punishments;
    private final SystemConfig config;
    private final Messages messages;
    private final DurationText durations;
    private final DateTimeFormatter dates;

    public HistoryCommand(CommandSupport support, PlayerLookup lookup, PunishmentService punishments,
                          SystemConfig config, Messages messages) {
        this.support = support;
        this.lookup = lookup;
        this.punishments = punishments;
        this.config = config;
        this.messages = messages;
        this.durations = new DurationText(messages);
        this.dates = DateTimeFormatter.ofPattern(messages.raw("history.date-format")).withZone(ZoneId.systemDefault());
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("history")
                .requires(CommandSupport.permission(SystemPermissions.HISTORY))
                .executes(support.usage("history.usage"))
                .then(support.playerArgument("player")
                        .executes(support.run((source, context) -> history(source, StringArgumentType.getString(context, "player"))))));
    }

    private void history(CommandSource source, String name) {
        var target = lookup.find(name);
        if (target.isEmpty()) {
            source.sendMessage(messages.get("player-unknown", Placeholder.unparsed("player", name)));
            return;
        }
        var history = punishments.history(target.get());
        var player = Placeholder.unparsed("player", target.get().name());
        if (history.entries().isEmpty()) {
            source.sendMessage(messages.get("history.empty", player));
            return;
        }
        int limit = config.historyLimit();
        source.sendMessage(messages.get("history.header", player,
                Formatter.number("bans", history.bans()), Formatter.number("mutes", history.mutes())));
        history.entries().stream().limit(limit)
                .forEach(entry -> source.sendMessage(messages.get("history.entry", placeholders(entry))));
        source.sendMessage(messages.get("history.count",
                Formatter.number("shown", Math.min(limit, history.entries().size())),
                Formatter.number("total", history.entries().size())));
    }

    private TagResolver placeholders(Punishment punishment) {
        return TagResolver.resolver(
                Placeholder.component("type", type(punishment.type())),
                Placeholder.unparsed("date", dates.format(punishment.createdAt())),
                Placeholder.unparsed("issuer", punishment.issuer()),
                Placeholder.unparsed("reason", punishment.reason()),
                Placeholder.component("duration", duration(punishment)),
                Placeholder.component("revoked", revoked(punishment)));
    }

    private Component type(PunishmentType type) {
        return switch (type) {
            case BAN -> messages.get("history.type-ban");
            case MUTE -> messages.get("history.type-mute");
        };
    }

    private Component duration(Punishment punishment) {
        return punishment.expiresAt()
                .<Component>map(end -> Component.text(durations.format(Duration.between(punishment.createdAt(), end))))
                .orElseGet(() -> messages.get("duration.permanent"));
    }

    private Component revoked(Punishment punishment) {
        return punishment.revokedBy()
                .map(revoker -> messages.get("history.revoked", Placeholder.unparsed("revoker", revoker)))
                .orElseGet(Component::empty);
    }
}
