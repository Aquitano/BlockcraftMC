package de.Blockcraft.Warns;

import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import static de.Blockcraft.BungeeSystem.Data.PREFIX;

public class MessageListener {
    private static final MessageListener instance = new MessageListener();

    public static MessageListener getInstance() {
        return instance;
    }

    public void sendMessage(ProxiedPlayer p, MessageType type, String[] messages) {
        int n = messages.length;
        int n2 = 0;
        while (n2 < n) {
            String msg2 = messages[n2];
            p.sendMessage(new TextComponent(PREFIX + msg2));
            ++n2;
        }
    }

    public void sendMessage(MessageType type, String[] messages) {
        int n = messages.length;
        int n2 = 0;
        while (n2 < n) {
            String msg2 = messages[n2];
            System.out.println(PREFIX + msg2);
            ++n2;
        }
    }
}

