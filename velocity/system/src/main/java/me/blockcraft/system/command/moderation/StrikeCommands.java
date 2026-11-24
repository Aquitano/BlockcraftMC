package me.blockcraft.system.command.moderation;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.StrikeRepository;
import me.blockcraft.common.player.PlayerReference;
import me.blockcraft.system.PlayerLookup;
import me.blockcraft.system.PlayerLookup.Target;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.moderation.PunishmentService;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Strikes are the team's own warnings: {@code /strike} hands one out and bans at {@code max-strikes},
 * {@code /strikes} shows a team member their own, {@code /showstrikes} and {@code /delstrike} anyone's.
 */
public final class StrikeCommands {

    private final CommandSupport support;
    private final PlayerLookup lookup;
    private final StrikeRepository strikes;
    private final PunishmentService punishments;
    private final SystemConfig config;
    private final Messages messages;

    public StrikeCommands(CommandSupport support, PlayerLookup lookup, StrikeRepository strikes,
                          PunishmentService punishments, SystemConfig config, Messages messages) {
        this.support = support;
        this.lookup = lookup;
        this.strikes = strikes;
        this.punishments = punishments;
        this.config = config;
        this.messages = messages;
    }

    public List<BrigadierCommand> create() {
        return List.of(strike(), own(), byName("showstrikes", this::show), byName("delstrike", this::remove));
    }

    private BrigadierCommand strike() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("strike")
                .requires(CommandSupport.permission(SystemPermissions.STRIKE))
                .executes(support.usage("strike.usage"))
                .then(support.playerArgument("player")
                        .executes(support.usage("strike.usage"))
                        .then(BrigadierCommand.requiredArgumentBuilder("reason", StringArgumentType.greedyString())
                                .executes(support.run((source, context) -> strike(source,
                                        StringArgumentType.getString(context, "player"),
                                        StringArgumentType.getString(context, "reason")))))));
    }

    private BrigadierCommand own() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("strikes")
                .executes(support.asPlayer((player, context) -> {
                    if (player.hasPermission(SystemPermissions.STRIKES)) {
                        send(player, player.getUsername(), player.getUniqueId());
                    } else {
                        player.sendMessage(messages.get("strikes.not-collected"));
                    }
                })));
    }

    private BrigadierCommand byName(String name, BiConsumer<CommandSource, Target> action) {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder(name)
                .requires(CommandSupport.permission(SystemPermissions.STRIKE))
                .executes(support.usage(name + ".usage"))
                .then(support.playerArgument("player")
                        .executes(support.run((source, context) -> {
                            String player = StringArgumentType.getString(context, "player");
                            lookup.find(source, player, messages.get("player-unknown", Placeholder.unparsed("player", player)))
                                    .ifPresent(target -> action.accept(source, target));
                        }))));
    }

    private void strike(CommandSource source, String name, String reason) {
        var target = lookup.online(source, name, messages.get("strike.offline"));
        if (target.isEmpty()) {
            return;
        }
        var player = target.get();
        if (!support.mayModerate(source, player.getUniqueId())) {
            return;
        }
        if (!player.hasPermission(SystemPermissions.STRIKES)) {
            source.sendMessage(messages.get("strike.target-needs-permission",
                    Placeholder.unparsed("player", player.getUsername()), Placeholder.unparsed("permission", SystemPermissions.STRIKES)));
            return;
        }
        strikes.add(player.getUniqueId(), reason, CommandSupport.sender(source));
        int count = strikes.count(player.getUniqueId());
        var placeholders = TagResolver.resolver(Placeholder.unparsed("player", player.getUsername()), Formatter.number("count", count));
        player.sendMessage(messages.get("strike.received", placeholders));
        source.sendMessage(messages.get("strike.done", placeholders));
        if (count >= config.maxStrikes()) {
            punishments.ban(new Target(player.getUniqueId(), player.getUsername(), Optional.of(player)),
                    messages.raw("strike.ban-reason"), PlayerReference.named(messages.raw("strike.ban-issuer")), Optional.empty());
            source.sendMessage(messages.get("strike.max-reached", placeholders));
        }
    }

    private void show(CommandSource source, Target target) {
        send(source, target.name(), target.uuid());
    }

    private void remove(CommandSource source, Target target) {
        if (!support.mayModerate(source, target.uuid())) {
            return;
        }
        var list = strikes.list(target.uuid());
        if (list.isEmpty()) {
            source.sendMessage(messages.get("delstrike.none"));
            return;
        }
        if (!support.mayOverrule(source, list.getLast().issuer())) {
            return;
        }
        int before = list.size();
        strikes.removeLatest(target.uuid());
        var placeholders = TagResolver.resolver(
                Placeholder.unparsed("player", target.name()),
                Formatter.number("number", before),
                Formatter.number("remaining", before - 1));
        source.sendMessage(messages.get("delstrike.done", placeholders));
        source.sendMessage(messages.get("delstrike.remaining", placeholders));
        target.online().ifPresent(player -> {
            player.sendMessage(messages.get("delstrike.received", placeholders));
            player.sendMessage(messages.get("delstrike.remaining", placeholders));
        });
    }

    private void send(CommandSource viewer, String name, UUID player) {
        var list = strikes.list(player);
        viewer.sendMessage(messages.get("strikes.header", Placeholder.unparsed("player", name)));
        viewer.sendMessage(messages.get("strikes.count", Formatter.number("count", list.size())));
        for (int i = 0; i < list.size(); i++) {
            viewer.sendMessage(messages.get("strikes.entry",
                    Formatter.number("number", i + 1),
                    Placeholder.unparsed("reason", list.get(i).reason()),
                    Placeholder.unparsed("issuer", list.get(i).issuer().name())));
        }
        viewer.sendMessage(messages.get("strikes.footer"));
    }
}
