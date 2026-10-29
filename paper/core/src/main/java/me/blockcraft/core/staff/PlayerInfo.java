package me.blockcraft.core.staff;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.rank.Ranks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.DurationText;
import me.blockcraft.common.moderation.Punishment;
import me.blockcraft.common.moderation.PunishmentRepository;
import me.blockcraft.common.report.ReportRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.ArrayList;
import java.util.function.BooleanSupplier;

/** What the info tool tells staff about a player: real rank, ping, client, open reports and active punishments. */
final class PlayerInfo {

    private record Standing(int openReports, Optional<Punishment> ban, Optional<Punishment> mute) {
    }

    private final ReportRepository reports;
    private final PunishmentRepository punishments;
    private final Messages messages;
    private final DurationText durations;

    PlayerInfo(ReportRepository reports, PunishmentRepository punishments, Messages messages) {
        this.reports = reports;
        this.punishments = punishments;
        this.messages = messages;
        this.durations = new DurationText(messages);
    }

    void show(Player staff, Player target, BooleanSupplier eligible) {
        var uuid = target.getUniqueId();
        String name = BlockcraftApi.get().realName(target);
        var header = messages.get("staff.info.header", Placeholder.unparsed("player", name),
                Placeholder.unparsed("rank", Ranks.group(target)),
                Formatter.number("ping", target.getPing()),
                Placeholder.unparsed("client", Optional.ofNullable(target.getClientBrandName()).orElse("?")));
        BlockcraftApi.get().playerTasks().latest(staff, () -> new Standing(reports.countByReported(uuid, name), punishments.activeBan(uuid), punishments.activeMute(uuid)),
                standing -> {
                    if (!eligible.getAsBoolean()) {
                        return;
                    }
                    var lines = new ArrayList<Component>();
                    lines.add(header);
                    lines.add(messages.get("staff.info.reports", Formatter.number("count", standing.openReports())));
                    standing.ban().ifPresent(ban -> lines.add(messages.get("staff.info.ban", details(ban))));
                    standing.mute().ifPresent(mute -> lines.add(messages.get("staff.info.mute", details(mute))));
                    if (standing.ban().isEmpty() && standing.mute().isEmpty()) {
                        lines.add(messages.get("staff.info.clean"));
                    }
                    new PlayerInfoDialog(messages.get("staff.info.title", Placeholder.unparsed("player", name)), lines)
                            .open(staff, eligible);
                });
    }

    private TagResolver details(Punishment punishment) {
        return TagResolver.resolver(
                Placeholder.unparsed("reason", punishment.reason()),
                Placeholder.component("remaining", punishment.remaining()
                        .<Component>map(left -> Component.text(durations.format(left)))
                        .orElseGet(() -> messages.get("duration.permanent"))));
    }
}
