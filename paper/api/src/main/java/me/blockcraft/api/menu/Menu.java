package me.blockcraft.api.menu;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.pack.MenuState;
import me.blockcraft.api.pack.MenuTile;
import me.blockcraft.api.pack.UiFont;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.settings.PlayerSetting;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.BitSet;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/**
 * A chest GUI identified by its holder instead of its title. Clicks inside it are cancelled and routed
 * to {@link #onClick} by {@link MenuListener}; clicks in the player's own inventory are cancelled too, unless the
 * menu {@link #locksPlayerInventory() leaves it to the game}.
 * <p>
 * A menu may also borrow the player's own inventory while it is open: its slots then go on below the chest's, in
 * the client's order, the hotbar last, and the player's items come back when it closes, however it closes.
 * <p>
 * The inventory itself is created when the menu opens, because its look depends on the viewer: a client wearing
 * the network's resource pack sees the pack's panel behind the slots, the pack's tooltip and model on every item
 * built from a {@link MenuIcon} (the exit every menu shares, {@link #backButton} or {@link #closeButton} in the last
 * slot, among them), and a {@link #toolbar} of three wide buttons across the first row; everyone else sees vanilla
 * items and empty slots filled with panes, whose clicks are swallowed.
 * <p>
 * {@link #open} and {@link #close} run on the next tick because Bukkit forbids opening or closing an
 * inventory from inside {@link InventoryClickEvent}; the client would otherwise receive the cancel resync
 * for the old window after the new one opened and show ghost items.
 */
public abstract class Menu implements InventoryHolder {

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

        public boolean covers(int slot) {
            return slot >= firstSlot && slot < firstSlot + 3;
        }
    }

    private record ToolbarEntry(ItemStack icon, Consumer<Player> action) {
    }

    private record Exit(MenuIcon icon, String messageKey, Consumer<Player> action) {
    }

    /** Slots that answer as one, the first holding the item; the bottom-right one shows its state. */
    private record Area(int slot, int columns, int rows) {
        boolean covers(int candidate) {
            return candidate >= 0 && candidate / COLUMNS >= slot / COLUMNS && candidate / COLUMNS < slot / COLUMNS + rows
                    && candidate % COLUMNS >= slot % COLUMNS && candidate % COLUMNS < slot % COLUMNS + columns;
        }

        IntStream slots() {
            return IntStream.range(0, rows).flatMap(row -> IntStream.range(0, columns).map(column -> slot + row * COLUMNS + column));
        }

        int badgeSlot() {
            return slot + (rows - 1) * COLUMNS + columns - 1;
        }

        int iconSlot() {
            return slot + columns / 2;
        }
    }

    private final int rows;
    private final Component title;
    private final ItemStack[] items;
    private final Map<ToolbarButton, ToolbarEntry> toolbar = new EnumMap<>(ToolbarButton.class);
    private final Map<Integer, MenuTile.Placement> tiles = new LinkedHashMap<>();
    private final Map<Integer, Area> areas = new LinkedHashMap<>();
    private final boolean borrowing;
    private Player borrower;
    private Loan<ItemStack> loan;
    private final BitSet fillerSlots = new BitSet();
    private Inventory inventory;
    private boolean packApplied;
    private final BitSet informational = new BitSet();
    private Exit exit;
    private int page;
    private int pages;

    protected Menu(int rows, Component title) {
        this(rows, title, false);
    }

    /** {@code borrowing}: the menu also draws on the player's own inventory while it is open, as one player's menu. */
    protected Menu(int rows, Component title, boolean borrowing) {
        this.rows = rows;
        this.title = title;
        this.borrowing = borrowing;
        this.items = new ItemStack[rows * COLUMNS + (borrowing ? PlayerSlots.SIZE : 0)];
    }

    protected abstract void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event);

    protected Sounds clickSound(int slot, InventoryClickEvent event) {
        return Sounds.MENU_CLICK;
    }

    /** Purchase actions choose their feedback after the attempt instead of playing a click beforehand. */
    protected boolean automaticClickSound(int slot, InventoryClickEvent event) {
        return true;
    }

    /** The clicks that reach {@link #onClick}; a menu that does more on a shift-click or a number key takes those too. */
    protected boolean accepts(ClickType click) {
        return click == ClickType.LEFT || click == ClickType.RIGHT;
    }

    protected boolean isEnabled(Player player, int slot, InventoryClickEvent event) {
        return true;
    }

    protected void onSettingChanged(Player player, PlayerSetting setting) {
    }

    /**
     * A menu opened mid-game, like a shop, lets the player rearrange their own inventory under the game's rules;
     * only moves that reach into the menu stay blocked.
     */
    protected boolean locksPlayerInventory() {
        return true;
    }

    /** A picture of the menu's own that the pack draws instead of its panel, the plain title being for a client without it. */
    protected Optional<Component> screen() {
        return Optional.empty();
    }

    boolean borrowsPlayerInventory() {
        return borrowing;
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
            var area = areas.get(slot);
            var slots = area == null ? IntStream.of(slot) : area.slots();
            slots.forEach(this::show);
        }
    }

    private void show(int slot) {
        var rendered = renderSlot(slot);
        if (slot >= size()) {
            if (loan != null && borrower.getOpenInventory().getTopInventory() == inventory) {
                loan.lend(slot - size(), PlayerSlots.mark(rendered));
            }
        } else if (!Objects.equals(inventory.getItem(slot), rendered)) {
            inventory.setItem(slot, rendered);
        }
    }

    /** Slots that answer and show their state as one, with no picture of their own. */
    protected void area(int slot, int columns, int rows) {
        var area = new Area(slot, columns, rows);
        if (inventory != null || slot % COLUMNS + columns > COLUMNS || area.slots().anyMatch(covered -> covered >= items.length
                || items[covered] != null || areaAt(covered) != null)) {
            throw new IllegalArgumentException("Area overlaps another control or exceeds this menu: " + area);
        }
        areas.put(slot, area);
    }

    protected void tile(int slot, MenuTile art, MenuTile.Shape shape, ItemStack item) {
        var tile = new MenuTile.Placement(slot, art, shape);
        if (inventory != null || tile.slots().anyMatch(covered -> covered >= items.length || items[covered] != null
                || tiles.values().stream().anyMatch(other -> other.covers(covered))
                || toolbar.keySet().stream().anyMatch(button -> button.covers(covered)))) {
            throw new IllegalArgumentException("Tile overlaps another control or exceeds this menu: " + tile);
        }
        tiles.put(slot, tile);
        areas.put(slot, new Area(slot, shape.columns(), shape.rows()));
        set(slot, item);
    }

    protected void info(int slot, ItemStack item) {
        set(slot, item);
        informational.set(slot);
    }

    /**
     * A wide button in the first row: {@code icon}, built from a {@link MenuIcon}, sits in its middle slot and the
     * whole button answers the click. The pack draws the row as three coloured bars; without it, the icon is an
     * ordinary item between panes. A button set again once the menu is open changes in place.
     */
    protected void toolbar(ToolbarButton button, ItemStack icon, Consumer<Player> action) {
        toolbar.put(button, new ToolbarEntry(icon, action));
        if (inventory == null) {
            return;
        }
        items[button.iconSlot()] = icon;
        for (int slot = button.firstSlot; slot < button.firstSlot + 3; slot++) {
            inventory.setItem(slot, renderSlot(slot));
        }
    }

    boolean hasToolbar() {
        return !toolbar.isEmpty();
    }

    protected void fill(ItemStack item) {
        for (int slot = 0; slot < size(); slot++) {
            if (items[slot] == null) {
                set(slot, item);
            }
        }
    }

    /**
     * The way back to the menu that opened this one, in the last slot; the parent is reopened lazily because it may
     * load first.
     */
    protected void backButton(Consumer<Player> previous) {
        exit = new Exit(MenuIcon.BACK, "menu.back", previous);
    }

    /** Close in the last slot, for a menu no other menu leads to. */
    protected void closeButton() {
        exit = new Exit(MenuIcon.CLOSE, "menu.close", Menu::close);
    }

    /** Close as one of the wide buttons; top-level menus put it on the left. */
    protected void closeButton(ToolbarButton button) {
        toolbar(button, button(MenuIcon.TOOL_CLOSE, "menu.close"), Menu::close);
    }

    public void open(Player player) {
        open(player, ignored -> { });
    }

    public void open(Player player, BooleanSupplier eligible) {
        open(player, eligible, ignored -> { });
    }

    protected final void open(Player player, Consumer<Player> afterOpen) {
        open(player, () -> true, afterOpen);
    }

    private void open(Player player, BooleanSupplier eligible, Consumer<Player> afterOpen) {
        BlockcraftApi.get().playerTasks().navigate(player, eligible, opened -> {
            boolean opening = !(opened.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu);
            show(opened);
            if (opened.getOpenInventory().getTopInventory() != inventory) {
                return;
            }
            if (opening) {
                Sounds.MENU_OPEN.play(opened);
            }
            afterOpen.accept(opened);
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
        if (borrowing && loan == null && player.getOpenInventory().getTopInventory() == inventory) {
            borrower = player;
            loan = new Loan<>(new PlayerSlots(player.getInventory()), PlayerSlots::lent);
            for (int slot = size(); slot < items.length; slot++) {
                show(slot);
            }
        }
    }

    /** Gives the player's own items back, as soon as the menu stops drawing on them. */
    void giveBack() {
        if (loan != null) {
            loan.giveBack();
            loan = null;
        }
    }

    /** What the player had in the slots this menu borrowed, before it gives them back. */
    List<ItemStack> lentFrom() {
        return loan == null ? List.of() : loan.own().stream().map(ItemStack::clone).toList();
    }

    /**
     * Shows {@code count} pages, turned from the middle of the last row, and keeps the current page in range; a turn
     * calls {@link #pageTurned}, which draws the new page.
     */
    void paginate(int count) {
        var messages = BlockcraftApi.get().messages();
        pages = Math.max(1, count);
        page = Math.min(page, pages - 1);
        set(pagerSlot(-1), page > 0 ? button(MenuIcon.PREVIOUS, "menu.previous") : null);
        info(pagerSlot(0), pages > 1 ? MenuIcon.PAGE.item().name(messages.get("menu.page",
                Formatter.number("page", page + 1), Formatter.number("pages", pages))).build() : null);
        set(pagerSlot(1), page + 1 < pages ? button(MenuIcon.NEXT, "menu.next") : null);
    }

    void hidePager() {
        set(pagerSlot(-1), null);
        set(pagerSlot(0), null);
        set(pagerSlot(1), null);
    }

    void firstPage() {
        page = 0;
    }

    int page() {
        return page;
    }

    void pageTurned(Player player) {
    }

    private int pagerSlot(int offset) {
        return size() - COLUMNS + COLUMNS / 2 + offset;
    }

    private int exitSlot() {
        return size() - 1;
    }

    private int size() {
        return rows * COLUMNS;
    }

    void restyle(Player player) {
        if (packApplied == BlockcraftApi.get().resourcePack().applied(player)) {
            return;
        }
        BlockcraftApi.get().playerTasks().update(player, this::show);
    }

    public static void close(Player player) {
        close(player, ignored -> { });
    }

    public static void close(Player player, Consumer<Player> afterClose) {
        BlockcraftApi.get().playerTasks().navigate(player, closed -> {
            closed.closeInventory();
            afterClose.accept(closed);
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
        var area = areaAt(slot);
        if (area != null) {
            slot = area.slot();
            clicked = items[slot];
        }
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
        if (exit != null && slot == exitSlot()) {
            Sounds.MENU_CLICK.play(player);
            exit.action().accept(player);
        } else if (pages > 0 && (slot == pagerSlot(-1) || slot == pagerSlot(1))) {
            Sounds.MENU_CLICK.play(player);
            page = Math.clamp(page + (slot < pagerSlot(0) ? -1 : 1), 0, pages - 1);
            pageTurned(player);
        } else {
            if (automaticClickSound(slot, event)) {
                clickSound(slot, event).play(player);
            }
            onClick(player, slot, clicked, event);
        }
    }

    private void build(boolean packApplied) {
        this.packApplied = packApplied;
        Component shownTitle = title;
        var screen = screen();
        if (packApplied && screen.isPresent()) {
            shownTitle = screen.get();
        } else if (packApplied) {
            shownTitle = UiFont.menuTitle(rows, hasToolbar(), hasToolbar() && !tiles.isEmpty(), Component.empty());
            for (var tile : tiles.values()) {
                shownTitle = shownTitle.append(UiFont.tile(tile, BlockcraftApi.get().messages().get("tiles." + tile.tile().key())));
            }
            shownTitle = shownTitle.append(title);
        }
        inventory = Bukkit.createInventory(this, size(), shownTitle);
        if (exit != null) {
            items[exitSlot()] = button(exit.icon(), exit.messageKey());
        }
        toolbar.forEach((button, entry) -> items[button.iconSlot()] = entry.icon());
        for (int slot = 0; slot < size(); slot++) {
            inventory.setItem(slot, renderSlot(slot));
        }
    }

    private ItemStack renderSlot(int slot) {
        fillerSlots.clear(slot);
        ItemStack item = items[slot];
        if (item == null && slot >= size()) {
            fillerSlots.set(slot);
            return placeholder();
        }
        var tile = areaAt(slot);
        if (tile != null) {
            item = items[tile.slot()];
            if (item != null && packApplied) {
                var state = MenuState.of(item);
                var rendered = Items.of(item.clone()).model(slot == tile.badgeSlot()
                        ? state.map(MenuState::model).orElse(MenuIcon.EMPTY_MODEL) : MenuIcon.EMPTY_MODEL)
                        .tooltipStyle(MenuIcon.TOOLTIP_STYLE).build();
                MenuState.style(rendered);
                return rendered;
            }
            if (item != null && slot != tile.iconSlot()) {
                return Items.of(Material.GRAY_STAINED_GLASS_PANE).name(item.getItemMeta().displayName())
                        .lore(item.getItemMeta().lore() == null ? List.of() : item.getItemMeta().lore()).build();
            }
        }
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
            MenuState.style(rendered);
        }
        return rendered;
    }

    /**
     * What an empty borrowed slot holds: nothing to see with the pack, a filler without it. A slot something else empties
     * meanwhile, as a reset does, then no longer looks untouched, so it is not given back.
     */
    private ItemStack placeholder() {
        if (!packApplied) {
            return Items.of(Material.GRAY_STAINED_GLASS_PANE).name(BlockcraftApi.get().messages().get("menu.filler")).build();
        }
        var item = Items.of(Material.GRAY_STAINED_GLASS_PANE).model(MenuIcon.EMPTY_MODEL).name(Component.empty()).build();
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
        return item;
    }

    private Area areaAt(int slot) {
        return areas.values().stream().filter(area -> area.covers(slot)).findFirst().orElse(null);
    }

    private static ItemStack button(MenuIcon icon, String messageKey) {
        return icon.item().name(BlockcraftApi.get().messages().get(messageKey)).build();
    }
}
