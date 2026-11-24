package me.blockcraft.api.game.rush;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * The second row is the player's hotbar: a click picks a piece of the kit, the next click puts it into that slot and
 * saves the order. The host game's messages hold the texts under {@code prefix}, such as {@code layout.title}.
 */
public final class RushLayoutMenu extends Menu {

    private static final int HOTBAR_ROW = 9;
    private static final int RESET_SLOT = 22;

    private final RushKit kit;
    private final Messages messages;
    private final String prefix;
    private final Player player;
    private RushLayout layout;
    private Optional<RushLayout.Item> selected = Optional.empty();

    /** Opened from a hotbar item, so it closes. */
    public RushLayoutMenu(RushKit kit, Messages messages, String prefix, Player player) {
        this(kit, messages, prefix, player, Optional.empty());
    }

    /** Opened from another menu, to which {@code back} leads. */
    public RushLayoutMenu(RushKit kit, Messages messages, String prefix, Player player, Consumer<Player> back) {
        this(kit, messages, prefix, player, Optional.of(back));
    }

    private RushLayoutMenu(RushKit kit, Messages messages, String prefix, Player player, Optional<Consumer<Player>> back) {
        super(3, messages.get(prefix + "title"));
        this.kit = kit;
        this.messages = messages;
        this.prefix = prefix;
        this.player = player;
        this.layout = RushLayout.of(player);
        draw();
        set(RESET_SLOT, MenuIcon.REFRESH.item().name(messages.get(prefix + "reset")).build());
        back.ifPresentOrElse(this::backButton, this::closeButton);
    }

    private void draw() {
        for (int slot = 0; slot < RushLayout.SLOTS; slot++) {
            var item = layout.at(slot);
            set(HOTBAR_ROW + slot, item.isPresent() ? piece(item.get()) : empty(slot));
        }
    }

    private ItemStack piece(RushLayout.Item item) {
        var stack = Items.of(kit.preview(player, item)).hideAttributes();
        if (selected.filter(item::equals).isPresent()) {
            return stack.glow().lore(messages.get(prefix + "selected")).build();
        }
        return stack.lore(messages.get(prefix + (selected.isPresent() ? "place" : "select"))).build();
    }

    private ItemStack empty(int slot) {
        var stack = Items.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE).name(messages.get(prefix + "empty", Formatter.number("slot", slot + 1)));
        return (selected.isPresent() ? stack.lore(messages.get(prefix + "place")) : stack).build();
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot == RESET_SLOT) {
            apply(RushLayout.DEFAULT);
            return;
        }
        int hotbar = slot - HOTBAR_ROW;
        if (hotbar < 0 || hotbar >= RushLayout.SLOTS) {
            return;
        }
        var item = layout.at(hotbar);
        if (selected.isEmpty() || item.equals(selected)) {
            selected = item.equals(selected) ? Optional.empty() : item;
            draw();
            return;
        }
        apply(layout.move(selected.get(), hotbar));
    }

    private void apply(RushLayout next) {
        layout = next;
        selected = Optional.empty();
        next.save(player);
        draw();
    }
}
