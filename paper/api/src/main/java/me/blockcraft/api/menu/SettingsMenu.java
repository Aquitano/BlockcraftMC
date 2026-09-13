package me.blockcraft.api.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.settings.PlayerSetting;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** One item per on/off setting; a click flips it and redraws that item in the open menu. */
public final class SettingsMenu extends Menu {

    private static final List<PlayerSetting> TOGGLES = PlayerSetting.toggles();
    private static final int BUTTON_SLOT = 8;

    private final Player viewer;

    /** {@code back} is the menu this was opened from, or empty when the hotbar item opened it. */
    public SettingsMenu(Player viewer, Optional<Consumer<Player>> back) {
        super(1, BlockcraftApi.get().messages().get("settings.title"));
        this.viewer = viewer;
        for (int index = 0; index < TOGGLES.size(); index++) {
            set(slot(index), toggleItem(TOGGLES.get(index)));
        }
        back.ifPresentOrElse(previous -> backButton(BUTTON_SLOT, previous), () -> closeButton(BUTTON_SLOT));
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        int index = slot - slot(0);
        if (index < 0 || index >= TOGGLES.size()) {
            return;
        }
        var setting = TOGGLES.get(index);
        BlockcraftApi.get().settings().toggle(player, setting);
        set(slot, toggleItem(setting));
        // Menu's own click sound was swallowed when this very click turned the sounds back on.
        Sounds.MENU_CLICK.play(player);
    }

    private ItemStack toggleItem(PlayerSetting setting) {
        var messages = BlockcraftApi.get().messages();
        boolean on = BlockcraftApi.get().settings().enabled(viewer, setting);
        return Items.of(on ? Material.LIME_DYE : Material.GRAY_DYE)
                .name(messages.get("settings." + setting.key()))
                .lore(messages.get(on ? "settings.enabled" : "settings.disabled"))
                .build();
    }

    private static int slot(int index) {
        return (BUTTON_SLOT - TOGGLES.size()) / 2 + index;
    }
}
