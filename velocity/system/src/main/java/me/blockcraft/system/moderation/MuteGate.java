package me.blockcraft.system.moderation;

import com.velocitypowered.api.proxy.Player;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.PunishmentRepository;
import org.slf4j.Logger;

/**
 * Mute enforcement for the chat that never reaches a backend (/msg, /r, /pc, /support); Paper's MuteListener
 * covers public chat. Velocity runs commands off the network threads, so the lookup may block.
 */
public final class MuteGate {

    private final PunishmentRepository punishments;
    private final PunishmentService service;
    private final Messages messages;
    private final Logger logger;

    public MuteGate(PunishmentRepository punishments, PunishmentService service, Messages messages, Logger logger) {
        this.punishments = punishments;
        this.service = service;
        this.messages = messages;
        this.logger = logger;
    }

    /** True when the player may write; otherwise they have been told why. A database error holds the message back. */
    public boolean allows(Player player) {
        try {
            var mute = punishments.activeMute(player.getUniqueId());
            if (mute.isEmpty()) {
                return true;
            }
            player.sendMessage(service.muteScreen(mute.get()));
            return false;
        } catch (DatabaseException e) {
            logger.error("Could not check {} for a mute, holding the message back", player.getUsername(), e);
            player.sendMessage(messages.get("mute.check-failed"));
            return false;
        }
    }
}
