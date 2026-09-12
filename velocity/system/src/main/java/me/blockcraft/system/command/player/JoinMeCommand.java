package me.blockcraft.system.command.player;

import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Invites the whole network to the sender's server; limited per day unless the player may skip the delay. */
public final class JoinMeCommand {

    private final CommandSupport support;
    private final ProxyServer proxy;
    private final SystemConfig config;
    private final Messages messages;
    private final Map<UUID, Integer> usesToday = new ConcurrentHashMap<>();

    public JoinMeCommand(CommandSupport support, ProxyServer proxy, SystemConfig config, Messages messages) {
        this.support = support;
        this.proxy = proxy;
        this.config = config;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("joinme")
                .requires(CommandSupport.permission(SystemPermissions.JOIN_ME))
                .executes(support.asPlayer((player, context) -> announce(player)))
                .then(BrigadierCommand.literalArgumentBuilder("clear")
                        .requires(CommandSupport.permission(SystemPermissions.JOIN_ME_CLEAR))
                        .executes(support.run((source, context) -> {
                            resetDailyUses();
                            source.sendMessage(messages.get("joinme.cleared"));
                        }))));
    }

    public void resetDailyUses() {
        usesToday.clear();
    }

    private void announce(Player player) {
        if (!player.hasPermission(SystemPermissions.JOIN_ME_NO_DELAY)) {
            int used = usesToday.merge(player.getUniqueId(), 1, Integer::sum);
            if (used > config.joinMe().dailyLimit()) {
                player.sendMessage(messages.get("joinme.limit"));
                return;
            }
        }
        String server = CommandSupport.currentServer(player);
        var frame = messages.get("joinme.frame");
        var blank = Component.space();
        var lines = List.of(
                frame,
                blank,
                messages.get("joinme.game." + gameKey(server), Placeholder.unparsed("player", player.getUsername())),
                blank,
                messages.get("joinme.click")
                        .clickEvent(ClickEvent.runCommand("/serverjoin " + server))
                        .hoverEvent(HoverEvent.showText(messages.get("joinme.click-hover"))),
                blank,
                frame);
        for (Player all : proxy.getAllPlayers()) {
            lines.forEach(all::sendMessage);
        }
    }

    private String gameKey(String server) {
        return config.joinMe().gamesByServerPrefix().entrySet().stream()
                .filter(entry -> server.startsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("default");
    }
}
