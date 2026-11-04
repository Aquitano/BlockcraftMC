package me.blockcraft.ttt.shop;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

/** A time bomb's wires side by side; cutting the right one defuses it, any other sets it off. */
final class DefuseMenu extends Menu {

    enum Wire {
        RED(Material.RED_WOOL), BLUE(Material.BLUE_WOOL), YELLOW(Material.YELLOW_WOOL), GREEN(Material.LIME_WOOL),
        WHITE(Material.WHITE_WOOL), BLACK(Material.BLACK_WOOL), PURPLE(Material.PURPLE_WOOL);

        private final Material material;

        Wire(Material material) {
            this.material = material;
        }

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        static List<Wire> first(int count) {
            return Arrays.stream(values()).limit(count).toList();
        }
    }

    private final Map<Integer, Wire> slots = new HashMap<>();
    private final BiConsumer<Player, Wire> cut;

    DefuseMenu(Messages messages, List<Wire> wires, BiConsumer<Player, Wire> cut) {
        super(2, messages.get("shop.time-bomb.title"));
        this.cut = cut;
        int first = (9 - wires.size()) / 2;
        for (int index = 0; index < wires.size(); index++) {
            var wire = wires.get(index);
            slots.put(first + index, wire);
            set(first + index, Items.of(wire.material)
                    .name(messages.get("shop.time-bomb.wire", Placeholder.component("wire", messages.get("shop.time-bomb.wires." + wire.key()))))
                    .lore(messages.get("shop.time-bomb.cut"))
                    .build());
        }
        closeButton();
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        var wire = slots.get(slot);
        if (wire != null) {
            close(player);
            cut.accept(player, wire);
        }
    }
}
