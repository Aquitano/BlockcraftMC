package me.blockcraft.system.command.player;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.system.AfkPlayers;
import me.blockcraft.system.LiveSession;
import me.blockcraft.system.PlayerChoices;
import me.blockcraft.system.PlayerLookup;
import me.blockcraft.system.PlayerVisibility;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.ignore.IgnoreList;
import me.blockcraft.system.moderation.PrivateChatGate;
import me.blockcraft.system.rank.ProxyRanks;
import me.blockcraft.common.sound.FeedbackSound;
import me.blockcraft.system.pack.NetworkPack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Private messages across the network: /msg starts a conversation, /r answers whoever wrote last. */
public final class MessageCommand {

    private final CommandSupport support;
    private final ProxyServer proxy;
    private final PlayerVisibility visibility;
    private final PlayerLookup lookup;
    private final PlayerChoices choices;
    private final IgnoreList ignores;
    private final AfkPlayers afk;
    private final PrivateChatGate gate;
    private final Messages messages;
    private final Map<UUID, UUID> lastPartner = new ConcurrentHashMap<>();

    public MessageCommand(CommandSupport support, ProxyServer proxy, PlayerVisibility visibility, PlayerLookup lookup,
                          PlayerChoices choices, IgnoreList ignores, AfkPlayers afk, PrivateChatGate gate, Messages messages) {
        this.support = support;
        this.proxy = proxy;
        this.visibility = visibility;
        this.lookup = lookup;
        this.choices = choices;
        this.ignores = ignores;
        this.afk = afk;
        this.gate = gate;
        this.messages = messages;
    }

    public BrigadierCommand msg() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("msg")
                .requires(CommandSupport.permission(SystemPermissions.MSG))
                .executes(support.usage("msg.usage"))
                .then(support.playerArgument("player")
                        .executes(support.usage("msg.usage"))
                        .then(BrigadierCommand.requiredArgumentBuilder("message", StringArgumentType.greedyString())
                                .executes(support.asPlayer((sender, context) ->
                                        lookup.online(sender, StringArgumentType.getString(context, "player"), messages.get("msg.offline"))
                                                .ifPresent(target -> deliver(sender, target, StringArgumentType.getString(context, "message"))))))));
    }

    public BrigadierCommand reply() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("r")
                .requires(CommandSupport.permission(SystemPermissions.MSG))
                .executes(support.usage("msg.reply-usage"))
                .then(BrigadierCommand.requiredArgumentBuilder("message", StringArgumentType.greedyString())
                        .executes(support.asPlayer((sender, context) -> {
                            Optional.ofNullable(lastPartner.get(sender.getUniqueId())).flatMap(proxy::getPlayer).ifPresentOrElse(
                                    online -> deliver(sender, online, StringArgumentType.getString(context, "message")),
                                    () -> sender.sendMessage(messages.get("msg.nobody-to-answer")));
                        }))));
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        if (LiveSession.endedBy(event)) {
            lastPartner.remove(event.getPlayer().getUniqueId());
        }
    }

    private void deliver(Player sender, Player target, String text) {
        if (!visibility.canSee(sender, target)) {
            sender.sendMessage(messages.get("msg.offline"));
            return;
        }
        if (target.equals(sender)) {
            sender.sendMessage(messages.pick("msg.self"));
            return;
        }
        boolean refused = !choices.enabled(target, PlayerSetting.PRIVATE_MESSAGES) || ignores.ignores(target, sender);
        if (refused && !sender.hasPermission(SystemPermissions.MSG_BYPASS)) {
            sender.sendMessage(messages.get("msg.disabled", Placeholder.unparsed("player", visibility.shownName(target))));
            return;
        }
        if (!gate.allows(sender, text)) {
            return;
        }
        var resolvers = TagResolver.resolver(
                Placeholder.component("sender", ProxyRanks.displayName(sender, visibility.nick(sender))),
                Placeholder.component("target", ProxyRanks.displayName(target, visibility.nick(target))),
                Placeholder.component("message", LegacyComponentSerializer.legacyAmpersand().deserialize(text)));
        target.sendMessage(messages.get("msg.to-target", resolvers));
        if (choices.enabled(target, PlayerSetting.SOUNDS)) {
            target.playSound(FeedbackSound.NOTIFY.sound(NetworkPack.applied(target)));
        }
        sender.sendMessage(messages.get("msg.to-sender", resolvers));
        if (afk.isAfk(target) && !visibility.isVanished(target)) {
            sender.sendMessage(messages.get("msg.afk", Placeholder.unparsed("player", visibility.shownName(target))));
        }
        lastPartner.put(sender.getUniqueId(), target.getUniqueId());
        lastPartner.put(target.getUniqueId(), sender.getUniqueId());
    }
}
