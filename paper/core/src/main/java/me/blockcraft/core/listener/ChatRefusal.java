package me.blockcraft.core.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.Plugin;

import java.util.List;

/** Drops a chat message and tells its sender why, with the deny sound; the event is async, so the sound waits for the main thread. */
final class ChatRefusal {

    private ChatRefusal() {
    }

    static void refuse(Plugin plugin, AsyncChatEvent event, Component reason) {
        refuse(plugin, event, List.of(reason));
    }

    static void refuse(Plugin plugin, AsyncChatEvent event, List<Component> reason) {
        var player = event.getPlayer();
        event.setCancelled(true);
        reason.forEach(player::sendMessage);
        Tasks.sync(plugin, () -> Sounds.MENU_DENY.play(player));
    }
}
