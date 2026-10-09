package me.blockcraft.system.moderation;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.DurationText;
import me.blockcraft.common.moderation.Punishment;
import me.blockcraft.common.moderation.PunishmentRepository;
import me.blockcraft.common.moderation.PunishmentType;
import me.blockcraft.common.player.PlayerReference;
import me.blockcraft.system.PlayerLookup.Target;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.SystemPermissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Applies punishments the way the old proxy did: store, kick the target if online, tell the staff. */
public final class PunishmentService {

    public record History(List<Punishment> entries, int bans, int mutes) {
    }

    private final ProxyServer proxy;
    private final PunishmentRepository punishments;
    private final Messages messages;
    private final DurationText durations;
    private final SystemConfig.Network network;

    public PunishmentService(ProxyServer proxy, PunishmentRepository punishments, Messages messages, SystemConfig.Network network) {
        this.proxy = proxy;
        this.punishments = punishments;
        this.messages = messages;
        this.durations = new DurationText(messages);
        this.network = network;
    }

    public Optional<Punishment> activeBan(Target target) {
        return punishments.activeBan(target.uuid());
    }

    public Optional<Punishment> activeMute(Target target) {
        return punishments.activeMute(target.uuid());
    }

    /** Every ban and mute of one player in one list, newest first, next to the per-type counts. */
    public History history(Target target) {
        var bans = punishments.history(target.uuid(), PunishmentType.BAN);
        var mutes = punishments.history(target.uuid(), PunishmentType.MUTE);
        return new History(
                Stream.concat(bans.stream(), mutes.stream())
                        .sorted(Comparator.comparing(Punishment::createdAt).reversed())
                        .toList(),
                bans.size(), mutes.size());
    }

    public void ban(Target target, String reason, PlayerReference issuer, Optional<Duration> duration) {
        punishments.ban(target.uuid(), reason, issuer, duration);
        punishments.activeBan(target.uuid()).ifPresent(ban -> {
            target.online().ifPresent(player -> player.disconnect(banScreen(ban)));
            var placeholders = placeholders(target.name(), ban);
            notify(SystemPermissions.KICK, "ban.notify", placeholders);
        });
    }

    public void mute(Target target, String reason, PlayerReference issuer, Optional<Duration> duration) {
        punishments.mute(target.uuid(), reason, issuer, duration);
        punishments.activeMute(target.uuid()).ifPresent(mute -> {
            target.online().ifPresent(player -> player.sendMessage(muteScreen(mute)));
            notify(SystemPermissions.TEAM, "mute.notify", placeholders(target.name(), mute));
        });
    }

    public void revoke(Target target, PunishmentType type, PlayerReference issuer) {
        punishments.revoke(target.uuid(), type, issuer);
        var placeholders = TagResolver.resolver(Placeholder.unparsed("player", target.name()), Placeholder.unparsed("issuer", issuer.name()));
        switch (type) {
            case BAN -> notify(SystemPermissions.KICK, "unban.notify", placeholders);
            case MUTE -> notify(SystemPermissions.TEAM, "unmute.notify", placeholders);
        }
    }

    /** Ends the active punishment without telling the staff, for a /strafe that replaces it right away. */
    public void revokeQuietly(Target target, PunishmentType type, PlayerReference issuer) {
        punishments.revoke(target.uuid(), type, issuer);
    }

    public Component banScreen(Punishment ban) {
        return screen(ban.isPermanent() ? "ban.screen-permanent" : "ban.screen-temporary", ban);
    }

    public Component muteScreen(Punishment mute) {
        return screen(mute.isPermanent() ? "mute.screen-permanent" : "mute.screen-temporary", mute);
    }

    public Component remaining(Punishment punishment) {
        return punishment.remaining()
                .map(left -> messages.get("duration.remaining", Placeholder.unparsed("time", durations.format(left))))
                .orElseGet(() -> messages.get("duration.permanent"));
    }

    public TagResolver placeholders(String name, Punishment punishment) {
        return TagResolver.resolver(
                Placeholder.unparsed("player", name),
                Placeholder.unparsed("reason", punishment.reason()),
                Placeholder.unparsed("issuer", punishment.issuer().name()),
                Placeholder.component("remaining", remaining(punishment)),
                Placeholder.unparsed("ip", network.ip()),
                Placeholder.unparsed("website", network.website()),
                Placeholder.unparsed("teamspeak", network.teamspeak()));
    }

    private Component screen(String key, Punishment punishment) {
        return Component.join(JoinConfiguration.newlines(), messages.getLines(key, placeholders("", punishment)));
    }

    private void notify(String permission, String key, TagResolver... placeholders) {
        var lines = messages.getLines(key, placeholders);
        for (Player staff : proxy.getAllPlayers()) {
            if (staff.hasPermission(permission)) {
                lines.forEach(staff::sendMessage);
            }
        }
    }
}
