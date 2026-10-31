package me.blockcraft.lobby.activity;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDate;
import java.time.Month;
import java.util.Set;
import java.util.function.Consumer;

final class AdventMenu extends Menu {

    private final SeasonalEvents events;
    private final LocalDate day;
    private final Consumer<Player> back;

    AdventMenu(SeasonalEvents events, Messages messages, LocalDate day, Set<String> owned, Consumer<Player> back) {
        super(4, messages.get("seasonal.advent-title"));
        this.events = events;
        this.day = day;
        this.back = back;
        for (int door = 1; door <= 24; door++) {
            boolean claimed = owned.contains("calendar.advent." + day.getYear() + "." + door);
            boolean today = day.getMonth() == Month.DECEMBER && day.getDayOfMonth() == door;
            var item = Items.of(claimed ? Material.PAPER : today ? Material.CHEST : Material.IRON_DOOR)
                    .name(messages.get("seasonal.door", Formatter.number("day", door)))
                    .lore(messages.get(claimed ? "seasonal.advent-claimed" : today ? "seasonal.advent-open" : "seasonal.advent-closed")).build();
            if (today && !claimed) {
                set(door - 1, item);
            } else {
                info(door - 1, item);
            }
        }
        backButton(back);
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot >= 0 && slot < 24 && day.getDayOfMonth() == slot + 1) {
            events.claimAdvent(player, day, back);
        }
    }
}
