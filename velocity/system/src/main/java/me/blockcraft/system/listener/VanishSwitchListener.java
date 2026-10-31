package me.blockcraft.system.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.moderation.VanishRepository;
import me.blockcraft.system.PlayerVisibility;
import org.slf4j.Logger;

import java.time.Duration;

/**
 * Staff mode ends the vanish it started when the player leaves a server, but the message that tells the proxy goes
 * through the connection the switch already dropped. {@code bc_vanished} is written all the same, so the proxy
 * reads it again once the player is on the new server.
 */
public final class VanishSwitchListener {

    /** The old server only notices the player left on its next tick, and writes the table after that. */
    private static final Duration WRITE_GRACE = Duration.ofSeconds(2);

    private final Object plugin;
    private final ProxyServer proxy;
    private final VanishRepository vanish;
    private final PlayerVisibility visibility;
    private final Logger logger;

    public VanishSwitchListener(Object plugin, ProxyServer proxy, VanishRepository vanish, PlayerVisibility visibility,
                                Logger logger) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.vanish = vanish;
        this.visibility = visibility;
        this.logger = logger;
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        if (event.getPreviousServer() == null) {
            return;
        }
        var player = event.getPlayer();
        proxy.getScheduler().buildTask(plugin, () -> sync(player)).delay(WRITE_GRACE).schedule();
    }

    private void sync(Player player) {
        if (!player.isActive()) {
            return;
        }
        try {
            visibility.setVanished(player, vanish.isVanished(player.getUniqueId()));
        } catch (DatabaseException e) {
            logger.warn("Could not read the vanish state of {}", player.getUniqueId(), e);
        }
    }
}
