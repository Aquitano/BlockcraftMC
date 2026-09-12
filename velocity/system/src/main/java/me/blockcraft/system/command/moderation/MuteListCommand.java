package me.blockcraft.system.command.moderation;

import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.PunishmentRepository;
import me.blockcraft.common.moderation.PunishmentType;
import me.blockcraft.system.PlayerLookup;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class MuteListCommand {

    private final CommandSupport support;
    private final PlayerLookup lookup;
    private final PunishmentRepository punishments;
    private final Messages messages;

    public MuteListCommand(CommandSupport support, PlayerLookup lookup, PunishmentRepository punishments, Messages messages) {
        this.support = support;
        this.lookup = lookup;
        this.punishments = punishments;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("mutelist")
                .requires(CommandSupport.permission(SystemPermissions.MUTE_LIST))
                .executes(support.run((source, context) -> list(source))));
    }

    private void list(CommandSource source) {
        var mutes = punishments.listActive(PunishmentType.MUTE);
        if (mutes.isEmpty()) {
            source.sendMessage(messages.get("mutelist.empty"));
            return;
        }
        source.sendMessage(messages.get("mutelist.header"));
        for (var mute : mutes) {
            String name = lookup.nameOf(mute.player()).orElse(mute.player().toString());
            source.sendMessage(messages.get("mutelist.entry", Placeholder.unparsed("player", name)));
        }
    }
}
