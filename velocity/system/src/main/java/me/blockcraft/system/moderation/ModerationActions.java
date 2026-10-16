package me.blockcraft.system.moderation;

import com.velocitypowered.api.command.CommandSource;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.PunishmentType;
import me.blockcraft.common.network.operation.NetworkRequest;
import me.blockcraft.common.network.operation.OperationResult;
import me.blockcraft.common.player.PlayerReference;
import me.blockcraft.system.PlayerLookup;
import me.blockcraft.system.PlayerLookup.Target;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.time.Duration;
import java.util.Optional;
import java.util.Random;

/**
 * The punishments both the proxy commands and the backends' requests apply, with the permission, rank and
 * protected-account checks in one place; the source is told what happened, the caller gets the result.
 */
public final class ModerationActions {

    private final CommandSupport support;
    private final PlayerLookup lookup;
    private final PunishmentService punishments;
    private final SystemConfig config;
    private final Messages messages;
    private final Random random = new Random();

    public ModerationActions(CommandSupport support, PlayerLookup lookup, PunishmentService punishments, SystemConfig config,
                             Messages messages) {
        this.support = support;
        this.lookup = lookup;
        this.punishments = punishments;
        this.config = config;
        this.messages = messages;
    }

    public OperationResult timed(CommandSource source, PlayerReference reference, PunishmentType type, Optional<Duration> duration,
                                 String reason) {
        boolean ban = type == PunishmentType.BAN;
        String key = ban ? "tempban" : "tempmute";
        if (!permitted(source, ban ? SystemPermissions.BAN : SystemPermissions.MUTE)) {
            return OperationResult.REJECTED;
        }
        if (duration.filter(value -> !value.isNegative() && !value.isZero()).isEmpty()) {
            messages.getLines(key + ".units").forEach(source::sendMessage);
            return OperationResult.REJECTED;
        }
        if (!NetworkRequest.isText(reason)) {
            source.sendMessage(messages.get(key + ".usage"));
            return OperationResult.REJECTED;
        }
        var target = lookup.find(source, reference, messages.get("player-unknown", Placeholder.unparsed("player", reference.name())));
        if (target.isEmpty()) {
            return OperationResult.REJECTED;
        }
        if (ban && config.isUnbannable(target.get().name())) {
            source.sendMessage(messages.get("tempban.unbannable"));
            return OperationResult.REJECTED;
        }
        if (!support.mayModerate(source, target.get().uuid())) {
            return OperationResult.REJECTED;
        }
        var active = ban ? punishments.activeBan(target.get()) : punishments.activeMute(target.get());
        if (active.isPresent()) {
            source.sendMessage(messages.get(key + (ban ? ".already-banned" : ".already-muted")));
            return OperationResult.REJECTED;
        }
        return store(source, () -> {
            if (ban) {
                punishments.ban(target.get(), reason, CommandSupport.sender(source), duration);
            } else {
                punishments.mute(target.get(), reason, CommandSupport.sender(source), duration);
            }
            source.sendMessage(messages.get(key + ".done", Placeholder.unparsed("player", target.get().name())));
        });
    }

    public OperationResult preset(CommandSource source, PlayerReference reference, int number) {
        if (!permitted(source, SystemPermissions.BAN)) {
            return OperationResult.REJECTED;
        }
        PunishmentPreset preset = config.punishments().get(number);
        if (preset == null) {
            source.sendMessage(messages.get("strafe.unknown-number"));
            return OperationResult.REJECTED;
        }
        var target = lookup.find(source, reference, messages.get("player-unknown", Placeholder.unparsed("player", reference.name())));
        if (target.isEmpty()) {
            return OperationResult.REJECTED;
        }
        if (config.isUnbannable(reference.name()) || config.isUnbannable(target.get().name())) {
            source.sendMessage(messages.get("strafe.unbannable"));
            return OperationResult.REJECTED;
        }
        if (!support.mayModerate(source, target.get().uuid())) {
            return OperationResult.REJECTED;
        }
        var issuer = CommandSupport.sender(source);
        String reason = preset.reason() + " #" + banId();
        return store(source, () -> {
            var existing = preset.type() == PunishmentType.BAN ? punishments.activeBan(target.get()) : punishments.activeMute(target.get());
            if (existing.isPresent()) {
                source.sendMessage(messages.get("strafe.overwrite"));
                punishments.revokeQuietly(target.get(), preset.type(), issuer);
            }
            switch (preset.type()) {
                case BAN -> punishments.ban(target.get(), reason, issuer, preset.duration());
                case MUTE -> punishments.mute(target.get(), reason, issuer, preset.duration());
            }
            source.sendMessage(messages.get("strafe.done"));
        });
    }

    /**
     * A catalogue punishment the network applies on its own in the name of {@code issuer}, with no source to answer.
     * Unlike {@code /strafe} it leaves an active punishment of the same type standing; returns whether it applied one.
     */
    public boolean automatic(Target target, int number, PlayerReference issuer) {
        PunishmentPreset preset = config.punishments().get(number);
        if (preset == null || config.isUnbannable(target.name())) {
            return false;
        }
        var existing = preset.type() == PunishmentType.BAN ? punishments.activeBan(target) : punishments.activeMute(target);
        if (existing.isPresent()) {
            return false;
        }
        String reason = preset.reason() + " #" + banId();
        switch (preset.type()) {
            case BAN -> punishments.ban(target, reason, issuer, preset.duration());
            case MUTE -> punishments.mute(target, reason, issuer, preset.duration());
        }
        return true;
    }

    public OperationResult revoke(CommandSource source, PlayerReference reference, PunishmentType type) {
        boolean ban = type == PunishmentType.BAN;
        String key = ban ? "unban" : "unmute";
        if (!permitted(source, ban ? SystemPermissions.BAN : SystemPermissions.MUTE)) {
            return OperationResult.REJECTED;
        }
        var target = lookup.find(source, reference, messages.get("player-unknown", Placeholder.unparsed("player", reference.name())));
        if (target.isEmpty()) {
            return OperationResult.REJECTED;
        }
        var active = ban ? punishments.activeBan(target.get()) : punishments.activeMute(target.get());
        if (active.isEmpty()) {
            source.sendMessage(messages.get(key + (ban ? ".not-banned" : ".not-muted")));
            return OperationResult.REJECTED;
        }
        return store(source, () -> {
            punishments.revoke(target.get(), type, CommandSupport.sender(source));
            source.sendMessage(messages.get(key + ".done"));
        });
    }

    /** The database is the only thing that can still fail here; the source hears about it once, whichever way it asked. */
    private OperationResult store(CommandSource source, Runnable write) {
        try {
            write.run();
            return OperationResult.APPLIED;
        } catch (DatabaseException e) {
            source.sendMessage(messages.get("moderation.error"));
            return OperationResult.FAILED;
        }
    }

    private boolean permitted(CommandSource source, String permission) {
        if (source.hasPermission(permission)) {
            return true;
        }
        source.sendMessage(messages.get("no-permission"));
        return false;
    }

    /** Four digits, no two equal neighbours, so staff can quote a punishment unambiguously. */
    private String banId() {
        var id = new StringBuilder();
        int previous = -1;
        for (int i = 0; i < 4; i++) {
            int digit;
            do {
                digit = random.nextInt(10);
            } while (digit == previous);
            id.append(digit);
            previous = digit;
        }
        return id.toString();
    }
}
