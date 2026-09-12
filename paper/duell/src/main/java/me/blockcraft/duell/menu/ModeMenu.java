package me.blockcraft.duell.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.message.Messages;
import me.blockcraft.duell.game.Duel;
import me.blockcraft.duell.game.DuelMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public final class ModeMenu extends Menu {

    private static final Map<Integer, DuelMode> SLOTS = Map.of(0, DuelMode.VS, 1, DuelMode.SG, 2, DuelMode.SUPERJUMP);
    private static final Map<DuelMode, Material> ICONS = Map.of(
            DuelMode.VS, Material.IRON_SWORD,
            DuelMode.SG, Material.CHAINMAIL_CHESTPLATE,
            DuelMode.SUPERJUMP, Material.GOLDEN_BOOTS);
    private static final int SLOT_VOTED = 4;

    private final Duel duel;
    private final Messages messages;

    public ModeMenu(Duel duel, Messages messages) {
        super(1, messages.get("menu.modes.title"));
        this.duel = duel;
        this.messages = messages;
        if (duel.voted()) {
            set(SLOT_VOTED, Items.of(Material.BARRIER)
                    .name(messages.get("menu.modes.voted"))
                    .lore(messages.get("menu.modes.voted-lore"))
                    .build());
            return;
        }
        SLOTS.forEach((slot, mode) -> set(slot, Items.of(ICONS.get(mode))
                .name(messages.get("menu.modes." + mode.configKey()))
                .lore(messages.get("menu.modes." + mode.configKey() + "-lore"))
                .build()));
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        close(player);
        var mode = SLOTS.get(slot);
        if (mode != null && !duel.vote(mode)) {
            player.sendMessage(messages.get("vote.already-ended"));
        }
    }
}
