package me.blockcraft.system.command.player;

import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.proxy.Player;
import me.blockcraft.common.discord.DiscordLinkRepository;
import me.blockcraft.common.discord.DiscordLinkRepository.Link;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.security.SecureRandom;
import java.time.Duration;

/** The Discord invite, and the code a player sends to the Discord bot to link the two accounts. */
public final class DiscordCommand {

    private static final String CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 6;

    private final CommandSupport support;
    private final DiscordLinkRepository links;
    private final SystemConfig config;
    private final Messages messages;
    private final SecureRandom random = new SecureRandom();

    public DiscordCommand(CommandSupport support, DiscordLinkRepository links, SystemConfig config, Messages messages) {
        this.support = support;
        this.links = links;
        this.config = config;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("discord")
                .requires(CommandSupport.permission(SystemPermissions.DISCORD))
                .executes(support.asPlayer((player, context) -> info(player)))
                .then(BrigadierCommand.literalArgumentBuilder("link")
                        .executes(support.asPlayer((player, context) -> link(player))))
                .then(BrigadierCommand.literalArgumentBuilder("unlink")
                        .executes(support.asPlayer((player, context) -> unlink(player)))));
    }

    private void info(Player player) {
        player.sendMessage(messages.get("discord.invite", Placeholder.unparsed("discord", config.network().discord())));
        var link = links.get(player.getUniqueId());
        var discordId = link.flatMap(Link::discordId);
        var pendingCode = link.flatMap(Link::pendingCode);
        if (discordId.isPresent()) {
            player.sendMessage(messages.get("discord.status-linked", Placeholder.unparsed("id", discordId.get())));
        } else if (pendingCode.isPresent()) {
            player.sendMessage(messages.get("discord.status-pending", Placeholder.unparsed("code", pendingCode.get())));
        } else {
            player.sendMessage(messages.get("discord.status-unlinked"));
        }
    }

    private void link(Player player) {
        if (links.get(player.getUniqueId()).map(Link::isLinked).orElse(false)) {
            player.sendMessage(messages.get("discord.already-linked"));
            return;
        }
        int minutes = config.discordCodeMinutes();
        var code = newCode();
        links.storeCode(player.getUniqueId(), code, Duration.ofMinutes(minutes));
        player.sendMessage(messages.get("discord.code",
                Placeholder.unparsed("code", code),
                Placeholder.unparsed("minutes", String.valueOf(minutes))));
    }

    private void unlink(Player player) {
        if (links.unlink(player.getUniqueId())) {
            player.sendMessage(messages.get("discord.unlinked"));
        } else {
            player.sendMessage(messages.get("discord.not-linked"));
        }
    }

    /** Guessing a live code would link a stranger's Discord account, so the code comes from a secure source. */
    private String newCode() {
        var code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }
}
