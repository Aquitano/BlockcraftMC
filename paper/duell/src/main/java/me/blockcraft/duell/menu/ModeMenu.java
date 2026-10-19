package me.blockcraft.duell.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.message.Messages;
import me.blockcraft.duell.game.Duel;
import me.blockcraft.duell.game.DuelMode;
import me.blockcraft.duell.game.DuelVote;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

public final class ModeMenu extends Menu {

    private static final Map<Integer, DuelMode> SLOTS = Map.of(11, DuelMode.VS, 13, DuelMode.SG, 15, DuelMode.SUPERJUMP);
    private static final Map<DuelMode, Material> ICONS = Map.of(
            DuelMode.VS, Material.IRON_SWORD,
            DuelMode.SG, Material.CHAINMAIL_CHESTPLATE,
            DuelMode.SUPERJUMP, Material.GOLDEN_BOOTS);

    private final Duel duel;
    private final Messages messages;

    public ModeMenu(Duel duel, Messages messages, Player viewer) {
        super(3, messages.get("menu.modes.title"));
        this.duel = duel;
        this.messages = messages;
        closeButton();
        var own = duel.votes().choiceOf(viewer.getUniqueId()).map(DuelVote.Choice::mode);
        SLOTS.forEach((slot, mode) -> {
            boolean chosen = own.equals(Optional.of(mode));
            var lore = new ArrayList<Component>();
            lore.add(messages.get("menu.modes." + mode.configKey() + "-lore"));
            lore.add(messages.get("menu.vote.votes", Formatter.number("votes", duel.votes().votes(mode))));
            if (chosen) {
                lore.add(messages.get("menu.vote.own"));
            }
            lore.add(messages.get("menu.modes.choose"));
            var icon = Items.of(ICONS.get(mode))
                    .name(messages.get("menu.modes." + mode.configKey()))
                    .lore(lore)
                    .hideAttributes();
            if (chosen) {
                icon.glow();
            }
            set(slot, icon.build());
        });
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
        var mode = SLOTS.get(slot);
        if (mode != null) {
            new MapMenu(duel, messages, mode, player).open(player);
        }
    }
}
