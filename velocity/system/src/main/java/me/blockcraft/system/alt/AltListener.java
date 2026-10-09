package me.blockcraft.system.alt;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.ResultedEvent.ComponentResult;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.PlayerAddressRepository;
import me.blockcraft.system.SystemConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.slf4j.Logger;

/**
 * Remembers the hashed address of every authenticated login, refused ones included, so a banned account's attempts
 * still link it to the alt it comes back with. With {@code alts.block-banned-alts} it also refuses a login from an
 * address a banned account used, failing closed like the ban check.
 */
public final class AltListener {

    /** After LoginListener, so a login it already refused is only recorded. */
    private static final short AFTER_LOGIN_GATE = -1;

    private final PlayerAddressRepository addresses;
    private final AddressHasher hasher;
    private final SystemConfig.Alts config;
    private final Messages messages;
    private final Logger logger;

    public AltListener(PlayerAddressRepository addresses, AddressHasher hasher, SystemConfig.Alts config, Messages messages, Logger logger) {
        this.addresses = addresses;
        this.hasher = hasher;
        this.config = config;
        this.messages = messages;
        this.logger = logger;
    }

    @Subscribe(priority = AFTER_LOGIN_GATE)
    public EventTask onLogin(LoginEvent event) {
        var player = event.getPlayer();
        var address = hasher.hash(player.getRemoteAddress().getAddress());
        return EventTask.async(() -> {
            boolean gate = config.blockBannedAlts() && event.getResult().isAllowed();
            try {
                addresses.record(player.getUniqueId(), address);
                if (gate && addresses.sharedWithBannedAccount(player.getUniqueId(), address, config.retention())) {
                    logger.info("Refused {}: their address was used by a banned account", player.getUsername());
                    event.setResult(ComponentResult.denied(screen("alts.blocked")));
                }
            } catch (DatabaseException e) {
                if (gate) {
                    logger.error("Could not check {} for banned alts, refusing the login", player.getUsername(), e);
                    event.setResult(ComponentResult.denied(screen("login.error")));
                } else {
                    logger.warn("Could not record the address hash of {}", player.getUsername(), e);
                }
            }
        });
    }

    public void purgeExpired() {
        try {
            int removed = addresses.purge(config.retention());
            logger.info("Purged {} address hashes older than {} days", removed, config.retention().toDays());
        } catch (DatabaseException e) {
            logger.warn("Could not purge expired address hashes", e);
        }
    }

    private Component screen(String key) {
        return Component.join(JoinConfiguration.newlines(), messages.getLines(key));
    }
}
