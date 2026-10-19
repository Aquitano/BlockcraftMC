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

    private static final int COLUMNS = 9;

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
        if (notice.isPresent()) {
            hidePager();
        } else {
            paginate((entries.size() + perPage() - 1) / perPage());
        }
        var centered = notice.or(() -> entries.isEmpty()
                ? Optional.of(new Notice(MenuIcon.EMPTY_LIST.item().name(empty()).build(), false))
                : Optional.empty());
        for (int slot = firstSlot(); slot < footer(); slot++) {
            var entry = entryAt(slot);
            if (slot == center() && centered.isPresent()) {
                draw(slot, centered.get().item(), centered.get().retry());
            } else if (entry.isPresent()) {
                draw(slot, icon(entry.get()), selectable(entry.get()));
            } else {
                set(slot, null);
            }
        }
        if (refreshable) {
            draw(footer(), MenuIcon.REFRESH.item().name(BlockcraftApi.get().messages().get("menu.refresh")).build(),
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
        var entry = entryAt(slot);
        if (entry.isPresent()) {
            onSelect(player, entry.get(), event);
        } else if (refreshable && slot == footer() || slot == center() && notice.filter(Notice::retry).isPresent()) {
            reload(player);
        }
    }

    private Optional<T> entryAt(int slot) {
        int index = page() * perPage() + slot - firstSlot();
        return notice.isEmpty() && slot >= firstSlot() && slot < footer() && index < entries.size()
                ? Optional.of(entries.get(index)) : Optional.empty();
    }

    private int firstSlot() {
        return hasToolbar() ? COLUMNS : 0;
    }

    private int footer() {
        return (rows - 1) * COLUMNS;
    }

    private int perPage() {
        return footer() - firstSlot();
    }

    private int center() {
        return firstSlot() + (perPage() / COLUMNS - 1) / 2 * COLUMNS + COLUMNS / 2;
    }
}
