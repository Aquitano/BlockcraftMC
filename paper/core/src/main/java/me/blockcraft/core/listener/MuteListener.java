package me.blockcraft.core.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.DurationText;
import me.blockcraft.common.moderation.Punishment;
import me.blockcraft.common.moderation.PunishmentRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * Blocks chat for players with an active proxy mute; the event is already async, so the lookup may block.
 * When the database cannot answer, the message is held back rather than let a muted player through.
 */
public final class MuteListener implements Listener {

    private final Plugin plugin;
    private final PunishmentRepository punishments;
    private final Messages messages;
    private final DurationText durations;

    public MuteListener(Plugin plugin, PunishmentRepository punishments, Messages messages) {
        this.plugin = plugin;
        this.punishments = punishments;
        this.messages = messages;
        this.durations = new DurationText(messages);
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        try {
            punishments.activeMute(event.getPlayer().getUniqueId()).ifPresent(mute -> {
                event.setCancelled(true);
                messages.getLines("mute.blocked",
                        Placeholder.unparsed("reason", mute.reason()),
                        Placeholder.component("remaining", remaining(mute))).forEach(event.getPlayer()::sendMessage);
            });
        } catch (DatabaseException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not check " + event.getPlayer().getName() + " for a mute, holding the message back", e);
            event.setCancelled(true);
            event.getPlayer().sendMessage(messages.get("mute.check-failed"));
        }
    }

    private Component remaining(Punishment mute) {
        return mute.remaining()
                .map(left -> messages.get("duration.remaining", Placeholder.unparsed("time", durations.format(left))))
                .orElseGet(() -> messages.get("duration.permanent"));
    }
}
