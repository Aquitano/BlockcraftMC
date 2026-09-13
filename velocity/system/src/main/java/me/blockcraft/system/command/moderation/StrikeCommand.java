package me.blockcraft.system.command.moderation;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.StrikeRepository;
import me.blockcraft.system.PlayerLookup.Target;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.moderation.PunishmentService;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.Optional;

public final class StrikeCommand {

    private final CommandSupport support;
    private final ProxyServer proxy;
    private final StrikeRepository strikes;
    private final PunishmentService punishments;
    private final SystemConfig config;
    private final Messages messages;

    public StrikeCommand(CommandSupport support, ProxyServer proxy, StrikeRepository strikes, PunishmentService punishments,
                         SystemConfig config, Messages messages) {
        this.support = support;
        this.proxy = proxy;
        this.strikes = strikes;
        this.punishments = punishments;
        this.config = config;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("strike")
                .requires(CommandSupport.permission(SystemPermissions.STRIKE))
                .executes(support.usage("strike.usage"))
                .then(support.playerArgument("player")
                        .executes(support.usage("strike.usage"))
                        .then(BrigadierCommand.requiredArgumentBuilder("reason", StringArgumentType.greedyString())
                                .executes(support.run((source, context) -> strike(source,
                                        StringArgumentType.getString(context, "player"),
                                        StringArgumentType.getString(context, "reason")))))));
    }

    private void strike(CommandSource source, String name, String reason) {
        var target = proxy.getPlayer(name);
        if (target.isEmpty()) {
            source.sendMessage(messages.get("strike.offline"));
            return;
        }
        var player = target.get();
        if (!support.mayModerate(source, player.getUniqueId())) {
            return;
        }
        if (!player.hasPermission(SystemPermissions.STRIKES)) {
            source.sendMessage(messages.get("strike.target-needs-permission",
                    Placeholder.unparsed("player", player.getUsername()), Placeholder.unparsed("permission", SystemPermissions.STRIKES)));
            return;
        }
        strikes.add(player.getUniqueId(), reason, CommandSupport.senderName(source));
        int count = strikes.count(player.getUniqueId());
        var placeholders = TagResolver.resolver(Placeholder.unparsed("player", player.getUsername()), Formatter.number("count", count));
        player.sendMessage(messages.get("strike.received", placeholders));
        source.sendMessage(messages.get("strike.done", placeholders));
        if (count >= config.maxStrikes()) {
            punishments.ban(new Target(player.getUniqueId(), player.getUsername(), Optional.of(player)),
                    messages.raw("strike.ban-reason"), messages.raw("strike.ban-issuer"), Optional.empty());
            source.sendMessage(messages.get("strike.max-reached", placeholders));
        }
    }
}
