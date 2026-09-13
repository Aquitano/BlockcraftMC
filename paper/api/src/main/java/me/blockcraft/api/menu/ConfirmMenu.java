package me.blockcraft.api.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/** Yes/no in front of a purchase; the item in the middle is what the player is about to buy, price and all. */
public final class ConfirmMenu extends Menu {

    private static final int ACCEPT = 2;
    private static final int SUBJECT = 4;
    private static final int CANCEL = 6;

    private final Consumer<Player> onAccept;
    private final Consumer<Player> onCancel;

    public ConfirmMenu(ItemStack subject, Consumer<Player> onAccept, Consumer<Player> onCancel) {
        super(1, BlockcraftApi.get().messages().get("menu.confirm.title"));
        this.onAccept = onAccept;
        this.onCancel = onCancel;
        var messages = BlockcraftApi.get().messages();
        set(ACCEPT, Items.of(Material.LIME_STAINED_GLASS_PANE).name(messages.get("menu.confirm.accept")).build());
        set(SUBJECT, subject);
        set(CANCEL, Items.of(Material.RED_STAINED_GLASS_PANE).name(messages.get("menu.confirm.cancel")).build());
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        switch (slot) {
            case ACCEPT -> {
                close(player);
                onAccept.accept(player);
            }
            case CANCEL -> onCancel.accept(player);
            default -> {
            }
        }
    }
}
