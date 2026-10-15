package me.blockcraft.system.command.staff;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.StaffAction;
import me.blockcraft.common.moderation.StaffActionRepository;
import me.blockcraft.common.player.PlayerReference;
import me.blockcraft.system.PlayerLookup;
import me.blockcraft.system.StaffButtons;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** {@code /stafflog <player> [page]}: what staff did by or to the player on any server, newest first. */
public final class StaffLogCommand {

    private final CommandSupport support;
    private final PlayerLookup lookup;
    private final StaffActionRepository actions;
    private final SystemConfig config;
    private final Messages messages;
    private final StaffButtons buttons;
    private final DateTimeFormatter dates;

    public StaffLogCommand(CommandSupport support, PlayerLookup lookup, StaffActionRepository actions, SystemConfig config,
                           Messages messages, StaffButtons buttons) {
        this.support = support;
        this.lookup = lookup;
        this.actions = actions;
        this.config = config;
        this.messages = messages;
        this.buttons = buttons;
        this.dates = DateTimeFormatter.ofPattern(messages.raw("stafflog.date-format")).withZone(ZoneId.systemDefault());
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("stafflog")
                .requires(CommandSupport.permission(SystemPermissions.STAFF_LOG))
                .executes(support.usage("stafflog.usage"))
                .then(support.playerArgument("player")
                        .executes(support.run((source, context) -> show(source, StringArgumentType.getString(context, "player"), 1)))
                        .then(BrigadierCommand.requiredArgumentBuilder("page", IntegerArgumentType.integer(1))
                                .executes(support.run((source, context) -> show(source, StringArgumentType.getString(context, "player"),
                                        IntegerArgumentType.getInteger(context, "page")))))));
    }

    private void show(CommandSource source, String name, int page) {
        var target = lookup.find(name);
        if (target.isEmpty()) {
            source.sendMessage(messages.get("player-unknown", Placeholder.unparsed("player", name)));
            return;
        }
        var player = Placeholder.unparsed("player", target.get().name());
        int size = config.staffLogPageSize();
        var log = actions.involving(target.get().uuid(), page - 1, size);
        if (log.total() == 0) {
            source.sendMessage(messages.get("stafflog.empty", player));
            return;
        }
        int pages = (log.total() + size - 1) / size;
        if (log.entries().isEmpty()) {
            source.sendMessage(messages.get("stafflog.no-page", Formatter.number("pages", pages)));
            return;
        }
        source.sendMessage(messages.get("stafflog.header", player, Formatter.number("page", page), Formatter.number("pages", pages),
                Formatter.number("total", log.total())));
        log.entries().forEach(entry -> source.sendMessage(entry(entry)));
        if (page < pages) {
            source.sendMessage(messages.get("stafflog.next", player, Formatter.number("next", page + 1)));
        }
    }

    private Component entry(StaffAction action) {
        return messages.get("stafflog.entry",
                Placeholder.unparsed("date", dates.format(action.createdAt())),
                Placeholder.component("issuer", name(action.issuer())),
                Placeholder.component("action", label(action.action())),
                Placeholder.component("target", action.target()
                        .map(target -> messages.get("stafflog.target", Placeholder.component("target", name(target))))
                        .orElseGet(Component::empty)),
                Placeholder.component("detail", action.detail()
                        .map(detail -> messages.get("stafflog.detail", Placeholder.unparsed("detail", detail)))
                        .orElseGet(Component::empty)),
                Placeholder.unparsed("server", action.server()));
    }

    /** The console has no account to look up. */
    private Component name(PlayerReference player) {
        return player.uuid().isPresent() ? buttons.name(player.name()) : Component.text(player.name());
    }

    /** A key without a label, from an old row or a newer server, shows as it is. */
    private Component label(String action) {
        String key = "stafflog.labels." + action;
        return messages.has(key) ? messages.get(key) : Component.text(action);
    }
}
