package me.blockcraft.reportsmenu.util;

import me.blockcraft.reportsmenu.Main;
import net.minecraft.server.v1_8_R3.*;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;

public class AnvilGUI {
    private Player player;
    private AnvilClickEventHandler handler;
    private HashMap<AnvilSlot, ItemStack> items;
    private Inventory inv;
    private Listener listener;

    public AnvilGUI(final Player player, final AnvilClickEventHandler handler) {
        this.items = new HashMap<>();
        this.player = player;
        this.handler = handler;
        this.listener = new Listener() {
            @EventHandler
            public void onInventoryClick(final InventoryClickEvent event) {
                if (event.getWhoClicked() instanceof Player) {
                    final Player clicker = (Player) event.getWhoClicked();
                    if (event.getInventory().equals(inv)) {
                        event.setCancelled(true);
                        final ItemStack item = event.getCurrentItem();
                        final int slot = event.getRawSlot();
                        String name = "";
                        if (item != null && item.hasItemMeta()) {
                            final ItemMeta meta = item.getItemMeta();
                            if (meta.hasDisplayName()) {
                                name = meta.getDisplayName();
                            }
                        }
                        final AnvilClickEvent clickEvent = new AnvilClickEvent(AnvilSlot.bySlot(slot), name);
                        handler.onAnvilClick(clickEvent);
                        if (clickEvent.getWillClose()) {
                            event.getWhoClicked().closeInventory();
                        }
                        if (clickEvent.getWillDestroy()) {
                            AnvilGUI.this.destroy();
                        }
                    }
                }
            }

            @EventHandler
            public void onInventoryClose(final InventoryCloseEvent event) {
                if (event.getPlayer() instanceof Player) {
                    final Player player = (Player) event.getPlayer();
                    final Inventory inv = event.getInventory();
                    if (inv.equals(AnvilGUI.this.inv)) {
                        inv.clear();
                        AnvilGUI.this.destroy();
                    }
                }
            }

            @EventHandler
            public void onPlayerQuit(final PlayerQuitEvent event) {
                if (event.getPlayer().equals(AnvilGUI.this.getPlayer())) {
                    AnvilGUI.this.destroy();
                }
            }
        };
        Bukkit.getPluginManager().registerEvents(this.listener, Main.getInstance());
    }

    public Player getPlayer() {
        return this.player;
    }

    public void setSlot(final AnvilSlot slot, final ItemStack item) {
        this.items.put(slot, item);
    }

    public void open() {
        final EntityPlayer p = ((CraftPlayer) this.player).getHandle();
        final AnvilContainer container = new AnvilContainer(p);
        this.inv = container.getBukkitView().getTopInventory();
        for (final AnvilSlot slot : this.items.keySet()) {
            this.inv.setItem(slot.getSlot(), this.items.get(slot));
        }
        final int c = p.nextContainerCounter();
        p.playerConnection.sendPacket(new PacketPlayOutOpenWindow(c, "minecraft:anvil", new ChatMessage("Repair"), 0));
        p.activeContainer = container;
        p.activeContainer.windowId = c;
        p.activeContainer.addSlotListener(p);
    }

    public void destroy() {
        this.player = null;
        this.handler = null;
        this.items = null;
        HandlerList.unregisterAll(this.listener);
        this.listener = null;
    }

    public enum AnvilSlot {
        INPUT_LEFT("INPUT_LEFT", 0, 0),
        INPUT_RIGHT("INPUT_RIGHT", 1, 1),
        OUTPUT("OUTPUT", 2, 2);

        private final int slot;

        AnvilSlot(final String name, final int ordinal, final int slot) {
            this.slot = slot;
        }

        public static AnvilSlot bySlot(final int slot) {
            AnvilSlot[] values;
            for (int length = (values = values()).length, i = 0; i < length; ++i) {
                final AnvilSlot anvilSlot = values[i];
                if (anvilSlot.getSlot() == slot) {
                    return anvilSlot;
                }
            }
            return null;
        }

        public int getSlot() {
            return this.slot;
        }
    }

    public interface AnvilClickEventHandler {
        void onAnvilClick(final AnvilClickEvent p0);
    }

    private static class AnvilContainer extends ContainerAnvil {
        public AnvilContainer(final EntityHuman entity) {
            super(entity.inventory, entity.world, new BlockPosition(0, 0, 0), entity);
        }

        public boolean a(final EntityHuman entityhuman) {
            return true;
        }
    }

    public static class AnvilClickEvent {
        private final AnvilSlot slot;
        private final String name;
        private boolean close;
        private boolean destroy;

        public AnvilClickEvent(final AnvilSlot slot, final String name) {
            this.close = true;
            this.destroy = true;
            this.slot = slot;
            this.name = name;
        }

        public AnvilSlot getSlot() {
            return this.slot;
        }

        public String getName() {
            return this.name;
        }

        public boolean getWillClose() {
            return this.close;
        }

        public void setWillClose(final boolean close) {
            this.close = close;
        }

        public boolean getWillDestroy() {
            return this.destroy;
        }

        public void setWillDestroy(final boolean destroy) {
            this.destroy = destroy;
        }
    }
}
