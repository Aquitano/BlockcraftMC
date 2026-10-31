package me.blockcraft.api.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.pack.MenuIcon;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Entries page by page between the toolbar, when the menu has one, and the last row, which turns the pages and holds
 * the exit and, for a list that can go stale, a refresh button in its first slot. While the entries load, after
 * loading failed or when there are none, one item in the middle of the page says so; clicking the failed one loads
 * again.
 */
public abstract class ListMenu<T> extends Menu {

    private record Notice(ItemStack item, boolean retry) {
    }

    private final int rows;
    private List<T> entries = List.of();
    private Optional<Notice> notice = Optional.empty();
    private boolean refreshable;

    protected ListMenu(int rows, Component title) {
        super(rows, title);
        this.rows = rows;
    }

    /** As many rows as {@code entries} need, plus the last row, up to a full chest. */
    protected static int rowsFor(int entries) {
        return ListLayout.rowsFor(entries);
    }

    /** The first slot of the last row, free for one of the menu's own buttons unless the list has a refresh button. */
    protected final int lastRowSlot() {
        return layout().lastRow();
    }

    protected abstract ItemStack icon(T entry);

    protected abstract void onSelect(Player player, T entry, InventoryClickEvent event);

    /** An entry that only informs, such as the server the player is already on, takes no click. */
    protected boolean selectable(T entry) {
        return true;
    }

    protected Component empty() {
        return BlockcraftApi.get().messages().get("menu.empty");
    }

    /** Loads the entries again, for the refresh button and the failed notice. */
    protected void reload(Player player) {
    }

    /** A click on one of the menu's own buttons outside the entries, such as one it put in the last row. */
    protected void onButton(Player player, int slot, InventoryClickEvent event) {
    }

    protected final void refreshButton() {
        refreshable = true;
    }

    protected final void loading() {
        showNotice(new Notice(MenuIcon.LOADING.item()
                .name(BlockcraftApi.get().messages().get("menu.load.pending")).build(), false));
    }

    protected final void failed() {
        var messages = BlockcraftApi.get().messages();
        showNotice(new Notice(MenuIcon.REFRESH.item()
                .name(messages.get("menu.load.failed")).lore(messages.get("menu.load.retry")).build(), true));
    }

    /** The viewer may not see this list; {@code reason} says why. */
    protected final void unavailable(Component reason) {
        showNotice(new Notice(MenuIcon.EMPTY_LIST.item().name(reason).build(), false));
    }

    /** The same list again, fresh: the viewer stays on their page as far as the list still reaches. */
    protected final void show(List<T> entries) {
        this.entries = List.copyOf(entries);
        notice = Optional.empty();
        render();
    }

    /** Another list, from its first page. */
    protected final void showFromStart(List<T> entries) {
        firstPage();
        show(entries);
    }

    /** Stands in for the list until the next {@link #show} without losing the page the viewer was on. */
    private void showNotice(Notice notice) {
        this.notice = Optional.of(notice);
        render();
    }

    @Override
    void pageTurned(Player player) {
        render();
    }

    private void render() {
        var layout = layout();
        if (notice.isPresent()) {
            hidePager();
        } else {
            paginate(layout.pages(entries.size()));
        }
        var centered = notice.or(() -> entries.isEmpty()
                ? Optional.of(new Notice(MenuIcon.EMPTY_LIST.item().name(empty()).build(), false))
                : Optional.empty());
        for (int slot = layout.firstSlot(); slot < layout.lastRow(); slot++) {
            var entry = entryAt(slot);
            if (slot == layout.center() && centered.isPresent()) {
                draw(slot, centered.get().item(), centered.get().retry());
            } else if (entry.isPresent()) {
                draw(slot, icon(entry.get()), selectable(entry.get()));
            } else {
                set(slot, null);
            }
        }
        if (refreshable) {
            draw(layout.lastRow(), MenuIcon.REFRESH.item().name(BlockcraftApi.get().messages().get("menu.refresh")).build(),
                    notice.map(Notice::retry).orElse(true));
        }
    }

    private void draw(int slot, ItemStack item, boolean clickable) {
        if (clickable) {
            set(slot, item);
        } else {
            info(slot, item);
        }
    }

    @Override
    protected final void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        var layout = layout();
        var entry = entryAt(slot);
        if (entry.isPresent()) {
            onSelect(player, entry.get(), event);
        } else if (refreshable && slot == layout.lastRow()
                || slot == layout.center() && notice.filter(Notice::retry).isPresent()) {
            reload(player);
        } else {
            onButton(player, slot, event);
        }
    }

    private Optional<T> entryAt(int slot) {
        return notice.isPresent() ? Optional.empty() : layout().index(page(), slot).stream()
                .filter(index -> index < entries.size())
                .mapToObj(entries::get)
                .findFirst();
    }

    private ListLayout layout() {
        return new ListLayout(rows, hasToolbar());
    }
}
