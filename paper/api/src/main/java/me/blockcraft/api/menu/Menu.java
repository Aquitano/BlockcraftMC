package me.blockcraft.api.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.pack.UiFont;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.settings.PlayerSetting;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.BitSet;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A chest GUI identified by its holder instead of its title. Clicks inside it are cancelled and routed
 * to {@link #onClick} by {@link MenuListener}; clicks in the player's own inventory are cancelled too.
 * <p>
 * The inventory itself is created when the menu opens, because its look depends on the viewer: a client wearing
 * the network's resource pack sees the pack's panel behind the slots, the pack's tooltip and model on every item
 * built from a {@link MenuIcon} (the two buttons every menu shares, {@link #backButton} and {@link #closeButton},
 * among them), and a {@link #toolbar} of three wide buttons across the first row; everyone else sees vanilla items
 * and empty slots filled with panes, whose clicks are swallowed.
 * <p>
 * {@link #open} and {@link #close} run on the next tick because Bukkit forbids opening or closing an
 * inventory from inside {@link InventoryClickEvent}; the client would otherwise receive the cancel resync
 * for the old window after the new one opened and show ghost items.
 */
public abstract class Menu implements InventoryHolder {

    private static final Plugin API = JavaPlugin.getProvidingPlugin(Menu.class);
    private static final int COLUMNS = 9;

    /** One of the three wide buttons of the toolbar row; each spans three slots. */
    public enum ToolbarButton {
        LEFT(0), MIDDLE(3), RIGHT(6);

        private final int firstSlot;

        ToolbarButton(int firstSlot) {
            this.firstSlot = firstSlot;
        }

        int iconSlot() {
            return firstSlot + 1;
        }

        boolean covers(int slot) {
            return slot >= firstSlot && slot < firstSlot + 3;
        }
    }

    private record ToolbarEntry(ItemStack icon, Consumer<Player> action) {
    }

    private final int rows;
    private final Component title;
    private final ItemStack[] items;
    private final Map<ToolbarButton, ToolbarEntry> toolbar = new EnumMap<>(ToolbarButton.class);
    private final BitSet fillerSlots = new BitSet();
    private Inventory inventory;
    private boolean packApplied;
    private final BitSet informational = new BitSet();
    private int backSlot = -1;
    private int closeSlot = -1;
    private Consumer<Player> backAction;

    protected Menu(int rows, Component title) {
        this.rows = rows;
        this.title = title;
        this.items = new ItemStack[rows * COLUMNS];
    }

    protected abstract void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event);

    protected Sounds clickSound(int slot, InventoryClickEvent event) {
        return Sounds.MENU_CLICK;
    }

    protected boolean isEnabled(Player player, int slot, InventoryClickEvent event) {
        return true;
    }

    protected void onClose(Player player, InventoryCloseEvent event) {
    }

    protected void onSettingChanged(Player player, PlayerSetting setting) {
    }

    /** Menus whose layout lives off the gaps between items turn the filler off. */
    protected boolean fillEmptySlots() {
        return true;
    }

    protected void set(int slot, ItemStack item) {
        informational.clear(slot);
        if (Objects.equals(items[slot], item)) {
            return;
        }
        items[slot] = item == null ? null : item.clone();
        if (inventory != null) {
            var rendered = renderSlot(slot);
            if (!Objects.equals(inventory.getItem(slot), rendered)) {
                inventory.setItem(slot, rendered);
            }
        }
    }

    protected void info(int slot, ItemStack item) {
        set(slot, item);
        informational.set(slot);
    }

    /**
     * A wide button in the first row: {@code icon}, built from a {@link MenuIcon}, sits in its middle slot and the
     * whole button answers the click. The pack draws the row as three coloured bars; without it, the icon is an
     * ordinary item between panes.
     */
    protected void toolbar(ToolbarButton button, ItemStack icon, Consumer<Player> action) {
        toolbar.put(button, new ToolbarEntry(icon, action));
    }

    protected void fill(ItemStack item) {
        for (int slot = 0; slot < items.length; slot++) {
            if (items[slot] == null) {
                set(slot, item);
            }
        }
    }

    /** The way back to the menu that opened this one; the parent is reopened lazily because it may load first. */
    protected void backButton(int slot, Consumer<Player> previous) {
        backSlot = slot;
        backAction = previous;
    }

    protected void closeButton(int slot) {
        closeSlot = slot;
    }

    /** Close as one of the wide buttons; top-level menus put it on the left. */
    protected void closeButton(ToolbarButton button) {
        toolbar(button, button(MenuIcon.TOOL_CLOSE, "menu.close"), Menu::close);
    }

    public void open(Player player) {
        open(player, ignored -> { });
    }

    protected final void open(Player player, Consumer<Player> afterOpen) {
        var tasks = BlockcraftApi.get().playerTasks();
        tasks.invalidate(player);
        var current = tasks.currentScreen(player);
        Bukkit.getScheduler().runTask(API, () -> {
            if (!current.getAsBoolean()) {
                return;
            }
            boolean opening = !(player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu);
            show(player);
            if (player.getOpenInventory().getTopInventory() != inventory) {
                return;
            }
            if (opening) {
                Sounds.MENU_OPEN.play(player);
            }
            afterOpen.accept(player);
        });
    }

    protected final void showLoading(Player player) {
        show(player);
    }

    private void show(Player player) {
        boolean wearing = BlockcraftApi.get().resourcePack().applied(player);
        if (inventory == null || wearing != packApplied) {
            build(wearing);
        }
        if (player.getOpenInventory().getTopInventory() != inventory) {
            player.openInventory(inventory);
        }
    }

    void restyle(Player player) {
        if (packApplied == BlockcraftApi.get().resourcePack().applied(player)) {
            return;
        }
        var current = BlockcraftApi.get().playerTasks().currentScreen(player);
        Bukkit.getScheduler().runTask(API, () -> {
            if (current.getAsBoolean()) {
                show(player);
            }
        });
    }

    public static void close(Player player) {
        close(player, ignored -> { });
    }

    public static void close(Player player, Consumer<Player> afterClose) {
        var tasks = BlockcraftApi.get().playerTasks();
        tasks.invalidate(player);
        var current = tasks.currentScreen(player);
        Bukkit.getScheduler().runTask(API, () -> {
            if (current.getAsBoolean()) {
                player.closeInventory();
                afterClose.accept(player);
            }
        });
    }

    @Override
    public final Inventory getInventory() {
        if (inventory == null) {
            build(false);
        }
        return inventory;
    }

    void handleClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (fillerSlots.get(slot) || informational.get(slot) || !isEnabled(player, slot, event)) {
            return;
        }
        for (var button : toolbar.entrySet()) {
            if (button.getKey().covers(slot)) {
                Sounds.MENU_CLICK.play(player);
                button.getValue().action().accept(player);
                return;
            }
        }
        if (slot == backSlot) {
            Sounds.MENU_CLICK.play(player);
            backAction.accept(player);
        } else if (slot == closeSlot) {
            Sounds.MENU_CLICK.play(player);
            close(player);
        } else {
            clickSound(slot, event).play(player);
            onClick(player, slot, clicked, event);
        }
    }

    void handleClose(Player player, InventoryCloseEvent event) {
        onClose(player, event);
    }

    private void build(boolean packApplied) {
        this.packApplied = packApplied;
        inventory = Bukkit.createInventory(this, items.length,
                packApplied ? UiFont.menuTitle(rows, !toolbar.isEmpty(), title) : title);
        if (backSlot >= 0) {
            items[backSlot] = button(MenuIcon.BACK, "menu.back");
        }
        if (closeSlot >= 0) {
            items[closeSlot] = button(MenuIcon.CLOSE, "menu.close");
        }
        toolbar.forEach((button, entry) -> items[button.iconSlot()] = entry.icon());
        for (int slot = 0; slot < items.length; slot++) {
            inventory.setItem(slot, renderSlot(slot));
        }
    }

    private ItemStack renderSlot(int slot) {
        fillerSlots.clear(slot);
        ItemStack item = items[slot];
        if (packApplied) {
            for (var entry : toolbar.entrySet()) {
                if (entry.getKey().covers(slot) && slot != entry.getKey().iconSlot()) {
                    return Items.of(entry.getValue().icon().clone()).model(MenuIcon.EMPTY_MODEL)
                            .tooltipStyle(MenuIcon.TOOLTIP_STYLE).build();
                }
            }
        }
        if (item == null) {
            fillerSlots.set(slot);
            return fillEmptySlots() && !packApplied
                    ? Items.of(Material.GRAY_STAINED_GLASS_PANE)
                        .name(BlockcraftApi.get().messages().get("menu.filler")).build()
                    : null;
        }
        var rendered = item.clone();
        MenuIcon.style(rendered, packApplied);
        if (packApplied) {
            Items.of(rendered).tooltipStyle(MenuIcon.TOOLTIP_STYLE);
        }
        return rendered;
    }

    private static ItemStack button(MenuIcon icon, String messageKey) {
        return icon.item().name(BlockcraftApi.get().messages().get(messageKey)).build();
    }
}
