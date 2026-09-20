package me.blockcraft.system.command.player;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.system.PlayerChoices;
import me.blockcraft.system.PlayerVisibility;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.moderation.MuteGate;
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
    private final PlayerChoices choices;
    private final MuteGate muteGate;
    private final Messages messages;
    private final Map<UUID, UUID> lastPartner = new ConcurrentHashMap<>();

    public MessageCommand(CommandSupport support, ProxyServer proxy, PlayerVisibility visibility, PlayerChoices choices,
                          MuteGate muteGate, Messages messages) {
        this.support = support;
        this.proxy = proxy;
        this.visibility = visibility;
        this.choices = choices;
        this.muteGate = muteGate;
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
                                        visibility.find(sender, StringArgumentType.getString(context, "player")).ifPresentOrElse(
                                                target -> deliver(sender, target, StringArgumentType.getString(context, "message")),
                                                () -> sender.sendMessage(messages.get("msg.offline"))))))));
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
        lastPartner.remove(event.getPlayer().getUniqueId());
    }

    private void deliver(Player sender, Player target, String text) {
        if (target.equals(sender)) {
            sender.sendMessage(messages.get("msg.self"));
            return;
        }
        if (!choices.enabled(target, PlayerSetting.PRIVATE_MESSAGES) && !sender.hasPermission(SystemPermissions.MSG_BYPASS)) {
            sender.sendMessage(messages.get("msg.disabled", Placeholder.unparsed("player", visibility.shownName(target))));
            return;
        }
        if (!muteGate.allows(sender)) {
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
        lastPartner.put(sender.getUniqueId(), target.getUniqueId());
        lastPartner.put(target.getUniqueId(), sender.getUniqueId());
    }
}
