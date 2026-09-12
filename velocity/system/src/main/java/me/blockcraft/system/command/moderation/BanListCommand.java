package me.blockcraft.system.command.moderation;

import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.PunishmentRepository;
import me.blockcraft.common.moderation.PunishmentType;
import me.blockcraft.system.PlayerLookup;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class BanListCommand {

    private final CommandSupport support;
    private final PlayerLookup lookup;
    private final PunishmentRepository punishments;
    private final Messages messages;

    public BanListCommand(CommandSupport support, PlayerLookup lookup, PunishmentRepository punishments, Messages messages) {
        this.support = support;
        this.lookup = lookup;
        this.punishments = punishments;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("banlist")
                .requires(CommandSupport.permission(SystemPermissions.BAN_LIST))
                .executes(support.run((source, context) -> list(source))));
    }

    private void list(CommandSource source) {
        var bans = punishments.listActive(PunishmentType.BAN);
        if (bans.isEmpty()) {
            source.sendMessage(messages.get("banlist.empty"));
            return;
        }
        source.sendMessage(messages.get("banlist.header"));
        for (var ban : bans) {
            String name = lookup.nameOf(ban.player()).orElse(ban.player().toString());
            source.sendMessage(messages.get("banlist.entry", Placeholder.unparsed("player", name)));
        }
        source.sendMessage(messages.get("banlist.count", Formatter.number("count", bans.size())));
    }
}
