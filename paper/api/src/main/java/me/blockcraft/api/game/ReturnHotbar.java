package me.blockcraft.api.game;

import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The hotbar of the wait between a round's result and the return to the lobby: another round in the first slot and the
 * lobby in the last, where the lobby and spectator hotbars keep their way out too. Nothing else stays in the hotbar,
 * so whatever the game or the spectators held there no longer answers.
 */
final class ReturnHotbar implements Listener {

    private static final int HOTBAR_SIZE = 9;

    enum ReturnItem implements HotbarItem {
        AGAIN(0, MenuIcon.REFRESH, "summary.again"),
        LOBBY(8, MenuIcon.TOOL_HOME, "summary.lobby");

        private final int slot;
        private final MenuIcon icon;
        private final String messageKey;

        ReturnItem(int slot, MenuIcon icon, String messageKey) {
            this.slot = slot;
            this.icon = icon;
            this.messageKey = messageKey;
        }
    }

    private final Plugin plugin;
    private final Messages messages;
    private final BooleanSupplier open;
    private final Consumer<Player> playAgain;

    /** Another round is offered only while {@code open} holds. */
    ReturnHotbar(Plugin plugin, Messages messages, BooleanSupplier open, Consumer<Player> playAgain) {
        this.plugin = plugin;
        this.messages = messages;
        this.open = open;
        this.playAgain = playAgain;
    }

    void give(Player player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            inventory.setItem(slot, null);
        }
        for (var item : ReturnItem.values()) {
            inventory.setItem(item.slot, item.build(item.icon.item().name(messages.get(item.messageKey))));
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) {
            return;
        }
        var player = event.getPlayer();
        HotbarItem.of(event.getItem(), ReturnItem.class).ifPresent(item -> {
            event.setCancelled(true);
            switch (item) {
                case AGAIN -> {
                    if (open.getAsBoolean()) {
                        Sounds.MENU_CLICK.play(player);
                        playAgain.accept(player);
                    }
                }
                case LOBBY -> {
                    Sounds.MENU_CLICK.play(player);
                    ProxyMessaging.hub(plugin, player);
                }
            }
        });
    }
}
