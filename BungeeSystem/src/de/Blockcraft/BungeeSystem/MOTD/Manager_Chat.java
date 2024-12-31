package de.Blockcraft.BungeeSystem.MOTD;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Listener;

public class Manager_Chat
        implements Listener {
    public static final Boolean globalMute = false;
    public static Boolean wartungen = false;

    public static TextComponent getComponent(String text1, String popup, String text2, ClickEvent onClickEvent, String onHoverText) {
        if (text1 == null) {
            text1 = "";
        }
        TextComponent message = new TextComponent(text1);
        TextComponent popupMessage = new TextComponent(ChatColor.RESET + popup);
        if (onClickEvent != null) {
            popupMessage.setClickEvent(onClickEvent);
        }
        if (onHoverText != null) {
            popupMessage.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(onHoverText).create()));
        }
        message.addExtra(popupMessage);
        message.addExtra(new TextComponent(text2));
        return message;
    }

    @SuppressWarnings("deprecation")
    public static String getMessage(CommandSender cs, String Message) {
        if (cs instanceof ProxiedPlayer && globalMute) {
            if (!cs.hasPermission("bs.globalmute")) {
                cs.sendMessage(ChatColor.RED + "\u00a73Dir wird es derzeit verweigert, im \u00a7eChat \u00a73zu schreiben. \u00a7eGlobalMute \u00a73ist aktiv.");
            }
            return null;
        }
        return Message;
    }
}

