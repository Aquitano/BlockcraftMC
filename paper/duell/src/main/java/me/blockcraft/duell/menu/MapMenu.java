package me.blockcraft.duell.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.message.Messages;
import me.blockcraft.duell.game.Duel;
import me.blockcraft.duell.game.DuelMode;
import me.blockcraft.duell.game.DuelVote;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;

/** The playable maps of one mode and the choice to leave the map to chance. */
final class MapMenu extends Menu {

    private static final int COLUMNS = 9;
    private static final int PER_ROW = 7;

    private final Duel duel;
    private final Messages messages;
    private final DuelMode mode;
    private final Map<Integer, OptionalInt> slots = new HashMap<>();

    MapMenu(Duel duel, Messages messages, DuelMode mode, Player viewer) {
        this(duel, messages, mode, viewer, options(duel.playableMaps(mode)));
    }

    private MapMenu(Duel duel, Messages messages, DuelMode mode, Player viewer, List<OptionalInt> options) {
        super(rows(options.size()), messages.get("menu.maps.title",
                Placeholder.component("mode", messages.get("mode." + mode.configKey()))));
        this.duel = duel;
        this.messages = messages;
        this.mode = mode;
        backButton(player -> new ModeMenu(duel, messages, player).open(player));
        if (options.isEmpty()) {
            info(COLUMNS + 4, MenuIcon.EMPTY_LIST.item().name(messages.get("menu.maps.unavailable"))
                    .lore(messages.get("menu.maps.unavailable-lore")).build());
        }
        var own = duel.votes().choiceOf(viewer.getUniqueId());
        for (int index = 0; index < options.size(); index++) {
            var map = options.get(index);
            int slot = COLUMNS * (1 + index / PER_ROW) + 1 + index % PER_ROW;
            slots.put(slot, map);
            set(slot, icon(map, own.equals(Optional.of(new DuelVote.Choice(mode, map)))));
        }
    }

    private static List<OptionalInt> options(List<Integer> playable) {
        return playable.isEmpty() ? List.of() : Stream.concat(Stream.of(OptionalInt.empty()),
                playable.stream().map(OptionalInt::of)).toList();
    }

    private static int rows(int options) {
        return 2 + (options + PER_ROW - 1) / PER_ROW;
    }

    private ItemStack icon(OptionalInt map, boolean chosen) {
        var lore = new ArrayList<Component>();
        Items.Builder icon;
        if (map.isPresent()) {
            icon = Items.of(Material.FILLED_MAP)
                    .name(messages.get("menu.maps.map", Placeholder.unparsed("map", duel.mapName(mode, map.getAsInt()))));
            lore.add(messages.get("menu.vote.votes", Formatter.number("votes", duel.votes().votes(mode, map.getAsInt()))));
        } else {
            icon = Items.of(Material.ENDER_PEARL).name(messages.get("menu.maps.random"));
            lore.add(messages.get("menu.maps.random-lore"));
        }
        if (chosen) {
            lore.add(messages.get("menu.vote.own"));
            icon.glow();
        }
        lore.add(messages.get("menu.maps.choose"));
        return icon.lore(lore).hideAttributes().build();
    }

    @Override
    public void open(Player player) {
        super.open(player, () -> duel.isLobby() && duel.isParticipant(player));
    }

    @Override
    protected boolean fillEmptySlots() {
        return false;
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        var map = slots.get(slot);
        if (map == null) {
            return;
        }
        close(player);
        if (!duel.vote(player, new DuelVote.Choice(mode, map))) {
            player.sendMessage(messages.get("vote.closed"));
        }
    }
}
