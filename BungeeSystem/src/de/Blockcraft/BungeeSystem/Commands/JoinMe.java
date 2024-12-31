package de.Blockcraft.BungeeSystem.Commands;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.MessageUtil;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class JoinMe
        extends Command {
    public static final ConcurrentMap<ProxiedPlayer, Integer> used = new ConcurrentHashMap<>();

    public JoinMe() {
        super("joinme");
    }

    public static String getJMsg(ProxiedPlayer p) {
        if (p.getServer().getInfo().getName().startsWith(Data.KFFApref)) {
            return ChatColor.DARK_GRAY + "» " + "\u00a76" + p.getName() + "\u00a77 spielt \u00a7eKnockbackFFA\u00a77!";
        } else if (p.getServer().getInfo().getName().startsWith("1vs1") || p.getServer().getInfo().getName().startsWith("NoHitDelay") || p.getServer().getInfo().getName().startsWith(Data.SoupPvPdef)) {
            return ChatColor.DARK_GRAY + "» " + "\u00a76" + p.getName() + "\u00a77 spielt \u00a7ePVP\u00a77!";
        } else if (p.getServer().getInfo().getName().startsWith(Data.BWpref)) {
            return ChatColor.DARK_GRAY + "» " + "\u00a76" + p.getName() + "\u00a77 spielt \u00a7eBedwars\u00a77!";
        } else if (p.getServer().getInfo().getName().startsWith(Data.SWpref)) {
            return ChatColor.DARK_GRAY + "» " + "\u00a76" + p.getName() + "\u00a77 spielt \u00a7eSkywars\u00a77!";
        } else if (p.getServer().getInfo().getName().startsWith(Data.RushDef)) {
            return ChatColor.DARK_GRAY + "» " + "\u00a76" + p.getName() + "\u00a77 spielt \u00a7eTradewars\u00a77!";
        } else if (p.getServer().getInfo().getName().equals(Data.BauServerdef)) {
            return ChatColor.DARK_GRAY + "» " + "\u00a76" + p.getName() + "\u00a77 spielt auf dem \u00a7eBauserver\u00a77!";
        } else {
            return ChatColor.DARK_GRAY + "» " + "\u00a76" + p.getName() + "\u00a77 spielt jetzt!";
        }
    }

    @SuppressWarnings("deprecation")
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            ProxiedPlayer p = (ProxiedPlayer) sender;
            if (p.hasPermission("bs.joinme")) {
                if (!p.hasPermission("bs.joinme.use.nodelay")) {
                    if (!used.containsKey(p)) {
                        used.put(p, 0);
                    }
                    if (used.get(p) < 6) {
                        for (ProxiedPlayer all : ProxyServer.getInstance().getPlayers()) {
                            TextComponent msg2 = new TextComponent("\u00a78» \u00a77KLICKE HIER, um den \u00a7eServer \u00a77zu \u00a7ebetreten\u00a77!");
                            msg2.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("\u00a7cVerbinden").create()));
                            msg2.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/serverjoin " + p.getServer().getInfo().getName()));
                            all.sendMessage("\u00a76\u00a7k|||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||");
                            all.sendMessage(" ");

                            all.sendMessage(getJMsg(p));

                            all.sendMessage(" ");
                            all.sendMessage(msg2);
                            all.sendMessage(" ");
                            all.sendMessage("\u00a76\u00a7k|||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||");
                        }
                    } else {
                        MessageUtil.sendPlayerMessage(p, "\u00a7cDu hast diesen Befehl schon zu oft benutzt.");
                    }
                } else {
                    for (ProxiedPlayer all : ProxyServer.getInstance().getPlayers()) {
                        TextComponent msg2 = new TextComponent("\u00a78» \u00a77KLICKE HIER, um den \u00a7eServer \u00a77zu \u00a7ebetreten\u00a77!");
                        msg2.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("\u00a7cVerbinden").create()));
                        msg2.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/serverjoin " + p.getServer().getInfo().getName()));
                        all.sendMessage("\u00a76\u00a7k|||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||");
                        all.sendMessage(" ");
                        all.sendMessage(getJMsg(p));
                        all.sendMessage(" ");
                        all.sendMessage(msg2);
                        all.sendMessage(" ");
                        all.sendMessage("\u00a76\u00a7k|||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||");
                    }
                }
            }
        } else if (args.length == 1 && args[0].equalsIgnoreCase("clear") && sender.hasPermission("bs.joinme.clear")) {
            used.clear();
            MessageUtil.sendPlayerMessage(sender, "\u00a7cDu hast alle JoinMe Einträge geleert!");
        } else {
            MessageUtil.sendPlayerMessage(sender, Data.NO_PERM);
        }
    }

}

