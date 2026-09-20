package me.blockcraft.api.pack;

import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.PackStatus;
import me.blockcraft.common.network.ProxyChannel;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
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
    private final Map<UUID, PackStatus> statuses = new HashMap<>();

    public ResourcePacks(Messages messages) {
        this.messages = messages;
    }

    public boolean applied(Player player) {
        return status(player) == PackStatus.APPLIED;
    }

    public PackStatus status(Player player) {
        return statuses.getOrDefault(player.getUniqueId(), PackStatus.UNAVAILABLE);
    }

    public void retry(Player player) {
        ProxyMessaging.requestPack(JavaPlugin.getProvidingPlugin(ResourcePacks.class), player);
    }

    /** Resolves {@code <icon:name>} the way this player's client can draw it. */
    public TagResolver icons(Player player) {
        return Icon.resolver(applied(player), messages);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(ProxyChannel.NAME)) {
            return;
        }
        PackStatus state;
        try (var in = new DataInputStream(new ByteArrayInputStream(message))) {
            if (!in.readUTF().equals(ProxyChannel.PACK)) {
                return;
            }
            boolean wearing = in.readBoolean();
            state = PackStatus.valueOf(in.readUTF());
            if (wearing != (state == PackStatus.APPLIED) || in.available() != 0) {
                return;
            }
        } catch (IOException | IllegalArgumentException malformed) {
            return;
        }
        boolean previouslyApplied = applied(player);
        statuses.put(player.getUniqueId(), state);
        boolean wearing = state == PackStatus.APPLIED;
        if (wearing == previouslyApplied) {
            return;
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
        statuses.remove(event.getPlayer().getUniqueId());
    }
}
