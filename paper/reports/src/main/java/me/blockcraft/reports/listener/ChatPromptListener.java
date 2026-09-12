package me.blockcraft.reports.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.blockcraft.reports.menu.ReportMenus;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ChatPromptListener implements Listener {

    private final ReportMenus menus;

    public ChatPromptListener(ReportMenus menus) {
        this.menus = menus;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (menus.answerStatusPrompt(event.getPlayer(), text)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        menus.forgetStatusPrompt(event.getPlayer());
    }
}
