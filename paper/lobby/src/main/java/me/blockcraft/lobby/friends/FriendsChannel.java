package me.blockcraft.lobby.friends;

import com.google.common.io.ByteStreams;
import me.blockcraft.common.network.ProxyChannel;
import me.blockcraft.lobby.menu.Menus;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.ArrayList;
import java.util.List;

/**
 * The proxy's answer to a friends request. Only the proxy knows who is online and on which server, so the menu
 * is drawn from what comes back rather than from the database.
 */
public final class FriendsChannel implements PluginMessageListener {

    private final Menus menus;

    public FriendsChannel(Menus menus) {
        this.menus = menus;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        var in = ByteStreams.newDataInput(message);
        if (!channel.equals(ProxyChannel.NAME) || !in.readUTF().equals(ProxyChannel.FRIENDS)) {
            return;
        }
        var friends = new ArrayList<FriendEntry>();
        for (int count = in.readInt(); count > 0; count--) {
            friends.add(new FriendEntry(in.readUTF(), in.readBoolean(), in.readUTF()));
        }
        var requests = new ArrayList<String>();
        for (int count = in.readInt(); count > 0; count--) {
            requests.add(in.readUTF());
        }
        menus.openFriends(player, List.copyOf(friends), List.copyOf(requests));
    }
}
