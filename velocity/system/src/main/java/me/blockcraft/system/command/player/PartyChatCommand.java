package me.blockcraft.system.command.player;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.party.PartyManager;

public final class PartyChatCommand {

    private final CommandSupport support;
    private final PartyManager parties;
    private final Messages messages;

    public PartyChatCommand(CommandSupport support, PartyManager parties, Messages messages) {
        this.support = support;
        this.parties = parties;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("pc")
                .executes(support.asPlayer((player, context) -> player.sendMessage(messages.get(
                        parties.isInParty(player.getUniqueId()) ? "party.chat-usage" : "party.not-in-party"))))
                .then(BrigadierCommand.requiredArgumentBuilder("message", StringArgumentType.greedyString())
                        .executes(support.asPlayer((player, context) -> {
                            String message = StringArgumentType.getString(context, "message").trim();
                            if (message.isEmpty()) {
                                player.sendMessage(messages.get("party.chat-empty"));
                                return;
                            }
                            parties.chat(player, message);
                        }))));
    }
}
