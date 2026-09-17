package me.blockcraft.api.pack;

import com.google.common.io.ByteStreams;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.ProxyChannel;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Which clients wear the network's resource pack. The proxy offers it and is the only one to hear the client's
 * answer, so it tells every backend the player arrives on; each answer restyles the {@link MenuIcon} items the
 * player already holds and is re-announced as a {@link PlayerResourcePackStatusEvent}, so a screen drawn before
 * it came can redraw itself.
 */
public final class ResourcePacks implements PluginMessageListener, Listener {

    private static final UUID ID = UUID.nameUUIDFromBytes("blockcraft:pack".getBytes(StandardCharsets.UTF_8));

    private final Messages messages;
    private final Set<UUID> applied = new HashSet<>();

    public ResourcePacks(Messages messages) {
        this.messages = messages;
    }

    public boolean applied(Player player) {
        return applied.contains(player.getUniqueId());
    }

    /** Resolves {@code <icon:name>} the way this player's client can draw it. */
    public TagResolver icons(Player player) {
        return Icon.resolver(applied(player), messages);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        var in = ByteStreams.newDataInput(message);
        if (!channel.equals(ProxyChannel.NAME) || !in.readUTF().equals(ProxyChannel.PACK)) {
            return;
        }
        boolean wearing = in.readBoolean();
        if (wearing) {
            applied.add(player.getUniqueId());
        } else {
            applied.remove(player.getUniqueId());
        }
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            var item = inventory.getItem(slot);
            if (item != null && MenuIcon.style(item, wearing)) {
                inventory.setItem(slot, item);
            }
        }
        new PlayerResourcePackStatusEvent(player, ID, wearing
                ? PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED
                : PlayerResourcePackStatusEvent.Status.DECLINED).callEvent();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        applied.remove(event.getPlayer().getUniqueId());
    }
}
