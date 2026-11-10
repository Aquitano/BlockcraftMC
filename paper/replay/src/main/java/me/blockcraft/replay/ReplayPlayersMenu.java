package me.blockcraft.replay;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.ListMenu;
import me.blockcraft.common.message.Messages;
import me.blockcraft.replay.data.ReplayHeader;
import me.blockcraft.replay.playback.PlaybackManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** The players on screen in the recording, one head each; a click takes the viewer over their shoulder. */
final class ReplayPlayersMenu extends ListMenu<ReplayHeader.Participant> {

    private final PlaybackManager playback;
    private final Messages messages;

    ReplayPlayersMenu(PlaybackManager playback, Messages messages, List<ReplayHeader.Participant> participants) {
        super(rowsFor(participants.size()), messages.get("players.title"));
        this.playback = playback;
        this.messages = messages;
        closeButton();
        show(participants);
    }

    @Override
    protected ItemStack icon(ReplayHeader.Participant participant) {
        var name = Placeholder.unparsed("player", participant.name());
        return Items.head(participant.id(), participant.name())
                .name(messages.get("players.entry", name))
                .lore(messages.get("players.hint", name))
                .build();
    }

    @Override
    protected Component empty() {
        return messages.get("players.empty");
    }

    @Override
    protected void onSelect(Player player, ReplayHeader.Participant participant, InventoryClickEvent event) {
        close(player, closed -> {
            if (!playback.focus(closed, participant.id())) {
                closed.sendMessage(messages.get("players.gone", Placeholder.unparsed("player", participant.name())));
            }
        });
    }
}
