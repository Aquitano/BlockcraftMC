package me.blockcraft.system.command.player;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.PlayerVisibility;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.party.PartyManager;

public final class PartyCommand {

    private final CommandSupport support;
    private final PlayerVisibility visibility;
    private final PartyManager parties;
    private final Messages messages;

    public PartyCommand(CommandSupport support, PlayerVisibility visibility, PartyManager parties, Messages messages) {
        this.support = support;
        this.visibility = visibility;
        this.parties = parties;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("party")
                .executes(support.asPlayer((player, context) -> help(player)))
                .then(BrigadierCommand.literalArgumentBuilder("help").executes(support.asPlayer((player, context) -> help(player))))
                .then(BrigadierCommand.literalArgumentBuilder("create").executes(support.asPlayer((player, context) -> parties.create(player))))
                .then(BrigadierCommand.literalArgumentBuilder("list").executes(support.asPlayer((player, context) -> parties.list(player))))
                .then(BrigadierCommand.literalArgumentBuilder("leave").executes(support.asPlayer((player, context) -> parties.leave(player))))
                .then(BrigadierCommand.literalArgumentBuilder("accept").executes(support.asPlayer((player, context) -> parties.accept(player))))
                .then(BrigadierCommand.literalArgumentBuilder("join")
                        .executes(support.usage("party.join-usage"))
                        .then(support.playerArgument("leader")
                                .executes(support.asPlayer((player, context) -> join(player, StringArgumentType.getString(context, "leader"))))))
                .then(BrigadierCommand.literalArgumentBuilder("invite")
                        .executes(support.asPlayer((player, context) -> {
                            if (player.hasPermission(SystemPermissions.PARTY_INVITE)) {
                                player.sendMessage(messages.get("party.invite-usage"));
                            } else {
                                player.sendMessage(messages.get("party.invite-no-permission"));
                            }
                        }))
                        .then(support.playerArgument("player")
                                .executes(support.asPlayer((player, context) -> invite(player, StringArgumentType.getString(context, "player"))))))
                .then(BrigadierCommand.literalArgumentBuilder("kick")
                        .executes(support.asPlayer((player, context) -> {
                            if (player.hasPermission(SystemPermissions.PARTY_KICK)) {
                                player.sendMessage(messages.get("party.kick-usage"));
                            } else {
                                player.sendMessage(messages.get("party.kick-no-permission"));
                            }
                        }))
                        .then(support.playerArgument("player")
                                .executes(support.asPlayer((player, context) -> kick(player, StringArgumentType.getString(context, "player"))))))
                .then(BrigadierCommand.literalArgumentBuilder("promote")
                        .executes(support.usage("party.promote-usage"))
                        .then(memberArgument()
                                .executes(support.asPlayer((player, context) -> promote(player, StringArgumentType.getString(context, "player")))))));
    }

    /** Only party members can be promoted, so the suggestions come from the party instead of the tab list. */
    private RequiredArgumentBuilder<CommandSource, String> memberArgument() {
        return BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                .suggests((context, builder) -> {
                    if (context.getSource() instanceof Player player) {
                        String typed = builder.getRemaining().toLowerCase();
                        parties.memberNames(player.getUniqueId())
                                .filter(shown -> shown.toLowerCase().startsWith(typed))
                                .forEach(builder::suggest);
                    }
                    return builder.buildFuture();
                });
    }

    private void help(Player player) {
        messages.getLines("party.help").forEach(player::sendMessage);
    }

    private void join(Player player, String leaderName) {
        var leader = visibility.find(player, leaderName);
        if (leader.isEmpty()) {
            player.sendMessage(messages.get("party.target-offline"));
            return;
        }
        parties.requestInvite(player, leader.get());
    }

    private void invite(Player inviter, String inviteeName) {
        if (!inviter.hasPermission(SystemPermissions.PARTY_INVITE)) {
            inviter.sendMessage(messages.get("party.invite-no-permission"));
            return;
        }
        var invitee = visibility.find(inviter, inviteeName);
        if (invitee.isEmpty()) {
            inviter.sendMessage(messages.get("party.target-offline"));
            return;
        }
        parties.invite(inviter, invitee.get());
    }

    private void promote(Player leader, String memberName) {
        var member = visibility.find(leader, memberName);
        if (member.isEmpty()) {
            leader.sendMessage(messages.get("party.target-offline"));
            return;
        }
        parties.promote(leader, member.get());
    }

    private void kick(Player leader, String memberName) {
        if (!leader.hasPermission(SystemPermissions.PARTY_KICK)) {
            leader.sendMessage(messages.get("party.kick-no-permission"));
            return;
        }
        var member = visibility.find(leader, memberName);
        if (member.isEmpty()) {
            leader.sendMessage(messages.get("party.target-offline"));
            return;
        }
        parties.kick(leader, member.get());
    }
}
