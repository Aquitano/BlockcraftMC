package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.common.anticheat.AnticheatEvidenceRepository;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.PlayerRepository;
import me.blockcraft.common.player.PlayerReference;
import me.blockcraft.core.staff.GrimAlerts;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class AnticheatCommand {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM. HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    private AnticheatCommand() { }

    public static LiteralCommandNode<CommandSourceStack> create(AnticheatEvidenceRepository repository,
                                                               PlayerRepository players, int days, Messages messages) {
        return Commands.literal("anticheat")
                .requires(source -> source.getSender() instanceof Player && source.getSender().hasPermission(GrimAlerts.NOTIFY_PERMISSION))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("anticheat.history.usage"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("history")
                        .executes(context -> show(context.getSource().getPlayerOrThrow(), Optional.empty(), repository, players, days, messages))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(context -> show(context.getSource().getPlayerOrThrow(),
                                        Optional.of(StringArgumentType.getString(context, "player")), repository, players, days, messages))))
                .build();
    }

    private static int show(Player staff, Optional<String> target, AnticheatEvidenceRepository repository,
                            PlayerRepository players, int days, Messages messages) {
        BlockcraftApi.get().playerTasks().latest(staff, () -> {
            if (target.isEmpty()) {
                return Optional.of(repository.latest(Optional.empty(), days, 20));
            }
            var id = resolve(target.get(), players);
            return id.map(player -> repository.latest(Optional.of(player), days, 20));
        }, rows -> {
            if (!staff.hasPermission(GrimAlerts.NOTIFY_PERMISSION)) {
                return;
            }
            rows.ifPresentOrElse(entries -> send(staff, entries, days, messages),
                    () -> staff.sendMessage(messages.get("anticheat.history.unknown")));
        }, ignored -> { }, ignored -> { });
        return Command.SINGLE_SUCCESS;
    }

    private static Optional<UUID> resolve(String target, PlayerRepository players) {
        try {
            return Optional.of(UUID.fromString(target));
        } catch (IllegalArgumentException invalidUuid) {
            return PlayerReference.isName(target) ? players.byName(target).map(player -> player.uuid()) : Optional.empty();
        }
    }

    private static void send(Player staff, List<AnticheatEvidenceRepository.Entry> entries, int days, Messages messages) {
        staff.sendMessage(messages.get("anticheat.history.header", Formatter.number("days", days)));
        if (entries.isEmpty()) {
            staff.sendMessage(messages.get("anticheat.history.empty"));
        }
        for (var entry : entries) {
            var evidence = entry.evidence();
            var line = messages.get("anticheat.history.entry", Formatter.number("id", entry.id()),
                            Placeholder.unparsed("time", TIME.format(evidence.recordedAt())),
                            Placeholder.unparsed("player", evidence.name()), Placeholder.unparsed("server", evidence.server()),
                            Placeholder.unparsed("check", evidence.check()), Placeholder.unparsed("vl", evidence.violations()))
                    .hoverEvent(HoverEvent.showText(messages.get("anticheat.history.details",
                            Placeholder.unparsed("uuid", evidence.player().toString()),
                            Placeholder.unparsed("description", evidence.description()),
                            Placeholder.unparsed("verbose", evidence.verbose()))));
            if (evidence.replay().isPresent()) {
                var replay = evidence.replay().get();
                boolean playable = entry.replayPublished() && staff.hasPermission("blockcraft.replay.use")
                        && staff.hasPermission("blockcraft.replay.staff") && Bukkit.getPluginManager().isPluginEnabled("BlockcraftReplay");
                var reference = messages.get(playable ? "anticheat.history.replay" : "anticheat.history.replay-unavailable",
                                Formatter.number("seconds", replay.tick() / 20), Placeholder.unparsed("replay", replay.id().toString()))
                        .hoverEvent(HoverEvent.showText(messages.get("anticheat.history.replay-details",
                                Formatter.number("seconds", replay.tick() / 20), Placeholder.unparsed("replay", replay.id().toString()))));
                if (playable) {
                    reference = reference.clickEvent(ClickEvent.runCommand("/replay play " + replay.id()));
                }
                line = line.append(Component.space()).append(reference);
            }
            staff.sendMessage(line);
        }
        staff.sendMessage(messages.get("anticheat.history.footer"));
    }
}
