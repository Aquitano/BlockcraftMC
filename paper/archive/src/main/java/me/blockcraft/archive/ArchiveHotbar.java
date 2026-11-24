package me.blockcraft.archive;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.archive.command.NoteCommand;
import me.blockcraft.archive.menu.ArchiveMenus;
import me.blockcraft.common.message.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;

/**
 * The guide to the exhibits in the first slot, a note for staff in the second, the way back to the lobby in the last.
 */
final class ArchiveHotbar implements Listener {

    private static final int GUIDE_SLOT = 0;
    private static final int NOTE_SLOT = 1;
    private static final int HUB_SLOT = 8;

    enum Item implements HotbarItem {
        GUIDE,
        NOTE,
        HUB
    }

    private final Plugin plugin;
    private final Messages messages;
    private final ArchiveMenus menus;
    private final Consumer<Player> writeNote;

    ArchiveHotbar(Plugin plugin, Messages messages, ArchiveMenus menus, Consumer<Player> writeNote) {
        this.plugin = plugin;
        this.messages = messages;
        this.menus = menus;
        this.writeNote = writeNote;
    }

    void give(Player player) {
        var inventory = player.getInventory();
        inventory.setItem(GUIDE_SLOT, Item.GUIDE.build(MenuIcon.ARCHIVE.item()
                .name(messages.get("hotbar.guide")).lore(messages.getLines("hotbar.guide-lore"))));
        if (player.hasPermission(NoteCommand.PERMISSION)) {
            inventory.setItem(NOTE_SLOT, Item.NOTE.build(MenuIcon.ARCHIVE_NOTE.item()
                    .name(messages.get("hotbar.note")).lore(messages.getLines("hotbar.note-lore"))));
        } else if (HotbarItem.of(inventory.getItem(NOTE_SLOT), Item.class).orElse(null) == Item.NOTE) {
            inventory.setItem(NOTE_SLOT, null);
        }
        inventory.setItem(HUB_SLOT, Item.HUB.build(MenuIcon.TOOL_HOME.item()
                .name(BlockcraftApi.get().messages().get("hotbar.hub"))));
        inventory.setHeldItemSlot(GUIDE_SLOT);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || !(event.getAction().isRightClick() || event.getAction().isLeftClick())) {
            return;
        }
        var player = event.getPlayer();
        HotbarItem.of(event.getItem(), Item.class).ifPresent(item -> {
            if (item != Item.NOTE && !event.getAction().isRightClick()) {
                return;
            }
            event.setCancelled(true);
            switch (item) {
                case GUIDE -> menus.openSections(player);
                case NOTE -> {
                    if (player.hasPermission(NoteCommand.PERMISSION)) {
                        Sounds.MENU_CLICK.play(player);
                        writeNote.accept(player);
                    }
                }
                case HUB -> ProxyMessaging.hub(plugin, player);
            }
        });
    }
}
