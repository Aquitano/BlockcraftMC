package me.blockcraft.core.nick;

import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class NickListener implements Listener {

    private final Nicks nicks;
    private final Messages messages;

    public NickListener(Nicks nicks, Messages messages) {
        this.nicks = nicks;
        this.messages = messages;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        nicks.applyStored(player, nick -> player.sendMessage(messages.get("nick.still-active", Placeholder.unparsed("nick", nick))));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        nicks.forget(event.getPlayer());
    }
}
