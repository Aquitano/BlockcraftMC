package me.blockcraft.system.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.proxy.ProxyPreShutdownEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.FreezeRepository;
import me.blockcraft.system.PlayerLookup.Target;
import me.blockcraft.system.StaffButtons;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.moderation.ModerationActions;
import me.blockcraft.system.moderation.PunishmentService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;

/**
 * Applies {@code freeze.quit-punishment} to a player who left the network while frozen (a {@code bc_frozen} row),
 * in the name of whoever froze them. The grace period forgives a reconnect and lets a restart's disconnects pass.
 */
public final class FreezeQuitListener {

    private final Object plugin;
    private final ProxyServer proxy;
    private final FreezeRepository frozen;
    private final PunishmentService punishments;
    private final ModerationActions moderation;
    private final SystemConfig.Freeze config;
    private final Messages messages;
    private final StaffButtons buttons;
    private final Logger logger;
    private volatile boolean shuttingDown;

    public FreezeQuitListener(Object plugin, ProxyServer proxy, FreezeRepository frozen, PunishmentService punishments,
                              ModerationActions moderation, SystemConfig.Freeze config, Messages messages, StaffButtons buttons,
                              Logger logger) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.frozen = frozen;
        this.punishments = punishments;
        this.moderation = moderation;
        this.config = config;
        this.messages = messages;
        this.buttons = buttons;
        this.logger = logger;
    }

    @Subscribe
    public void onPreShutdown(ProxyPreShutdownEvent event) {
        shuttingDown = true;
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        if (config.quitPunishment() == 0 || event.getLoginStatus() != DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN) {
            return;
        }
        Player player = event.getPlayer();
        proxy.getScheduler().buildTask(plugin, () -> punishIfGone(player.getUniqueId(), player.getUsername()))
                .delay(config.quitGrace())
                .schedule();
    }

    private void punishIfGone(UUID uuid, String name) {
        if (shuttingDown || proxy.getPlayer(uuid).isPresent()) {
            return;
        }
        try {
            var issuer = frozen.issuer(uuid);
            var target = new Target(uuid, name, Optional.empty());
            if (issuer.isEmpty() || punishments.activeBan(target).isPresent()) {
                return;
            }
            if (!moderation.automatic(target, config.quitPunishment(), issuer.get())) {
                logger.warn("{} left while frozen; /strafe {} was not applied (unknown number, protected account or already punished)",
                        name, config.quitPunishment());
                return;
            }
            var notice = messages.get("freeze.quit-punished", Placeholder.unparsed("player", name));
            for (Player staff : proxy.getAllPlayers()) {
                if (staff.hasPermission(SystemPermissions.TEAM)) {
                    staff.sendMessage(notice.append(buttons.of(staff, name, StaffButtons.Button.WHOIS)));
                }
            }
        } catch (DatabaseException e) {
            logger.warn("Could not punish {} for leaving while frozen", name, e);
        }
    }
}
