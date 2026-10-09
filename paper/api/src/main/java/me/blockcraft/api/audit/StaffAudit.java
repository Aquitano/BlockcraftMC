package me.blockcraft.api.audit;

import me.blockcraft.api.player.RealNames;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.StaffActionRepository;
import me.blockcraft.common.player.PlayerReference;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The staff audit log: every call writes one row to {@code bc_staff_actions} off the main thread, and the actions
 * named in {@code audit.notify} also reach every online player holding {@code audit.notify-permission}. An action is
 * a short key such as {@code gamemode.creative} or {@code invsee.edit}; names are the real account names.
 */
public final class StaffAudit {

    private static final PlayerReference CONSOLE = PlayerReference.named("Console");

    public record Notices(Set<String> actions, String permission) {

        public static Notices from(Map<String, Object> section) {
            var actions = section.get("notify") instanceof List<?> keys
                    ? keys.stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet())
                    : Set.<String>of();
            return new Notices(actions, section.get("notify-permission") instanceof String node ? node : "blockcraft.audit.notify");
        }
    }

    private final Plugin plugin;
    private final StaffActionRepository repository;
    private final RealNames realNames;
    private final String server;
    private final Notices notices;
    private final Messages messages;

    public StaffAudit(Plugin plugin, StaffActionRepository repository, RealNames realNames, String server, Notices notices,
                      Messages messages) {
        this.plugin = plugin;
        this.repository = repository;
        this.realNames = realNames;
        this.server = server;
        this.notices = notices;
        this.messages = messages;
    }

    public void record(CommandSender issuer, String action) {
        record(issuer, action, Optional.empty(), Optional.empty());
    }

    public void record(CommandSender issuer, String action, String detail) {
        record(issuer, action, Optional.empty(), Optional.of(detail));
    }

    public void record(CommandSender issuer, String action, Player target) {
        record(issuer, action, Optional.of(target), Optional.empty());
    }

    public void record(CommandSender issuer, String action, Player target, String detail) {
        record(issuer, action, Optional.of(target), Optional.of(detail));
    }

    private void record(CommandSender issuer, String action, Optional<Player> target, Optional<String> detail) {
        var issuerReference = reference(issuer);
        var targetReference = target.map(this::reference);
        Tasks.async(plugin, () -> repository.record(issuerReference, action, targetReference, detail, server));
        if (notices.actions().contains(action)) {
            notifyStaff(issuer, target, messages.get("audit.notice",
                    Placeholder.unparsed("issuer", issuerReference.name()),
                    Placeholder.unparsed("action", action),
                    Placeholder.component("target", targetReference
                            .map(reference -> messages.get("audit.notice-target", Placeholder.unparsed("target", reference.name())))
                            .orElseGet(Component::empty)),
                    Placeholder.component("detail", detail
                            .map(text -> messages.get("audit.notice-detail", Placeholder.unparsed("detail", text)))
                            .orElseGet(Component::empty)),
                    Placeholder.unparsed("server", server)));
        }
    }

    private PlayerReference reference(CommandSender sender) {
        return sender instanceof Player player ? reference(player) : CONSOLE;
    }

    private PlayerReference reference(Player player) {
        return PlayerReference.known(player.getUniqueId(), realNames.of(player));
    }

    /** A plugin message needs a connection to travel on, so the console's notices ride on whoever is online. */
    private void notifyStaff(CommandSender issuer, Optional<Player> target, Component notice) {
        Stream.concat(Stream.of(issuer).filter(Player.class::isInstance).map(Player.class::cast),
                        Stream.concat(target.stream().filter(Player::isOnline), Bukkit.getOnlinePlayers().stream()))
                .findFirst()
                .ifPresent(carrier -> ProxyMessaging.notifyStaff(plugin, carrier, notices.permission(), notice));
    }
}
