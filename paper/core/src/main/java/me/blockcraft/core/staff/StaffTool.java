package me.blockcraft.core.staff;

import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/** The staff mode hotbar, each tool recognised by its tag rather than by the slot it sits in. */
enum StaffTool implements HotbarItem {
    RANDOM(0, MenuIcon.STAFF_RANDOM),
    FREEZE(1, MenuIcon.STAFF_FREEZE),
    INVSEE(2, MenuIcon.STAFF_INVSEE),
    INFO(3, MenuIcon.STAFF_INFO),
    SPECTATE(4, MenuIcon.STAFF_SPECTATE),
    REPORTS(6, MenuIcon.STAFF_REPORTS),
    LEAVE(8, MenuIcon.TOOL_CLOSE);

    private final int slot;
    private final MenuIcon icon;

    StaffTool(int slot, MenuIcon icon) {
        this.slot = slot;
        this.icon = icon;
    }

    static Optional<StaffTool> of(ItemStack stack) {
        return HotbarItem.of(stack, StaffTool.class);
    }

    static void giveAll(Player player, Messages messages) {
        for (var tool : values()) {
            player.getInventory().setItem(tool.slot, tool.build(player, tool.icon.item().name(tool.name(messages))));
        }
    }

    private Component name(Messages messages) {
        return switch (this) {
            case RANDOM -> messages.get("staff.item.random");
            case FREEZE -> messages.get("staff.item.freeze");
            case INVSEE -> messages.get("staff.item.invsee");
            case INFO -> messages.get("staff.item.info");
            case SPECTATE -> messages.get("staff.item.spectate");
            case REPORTS -> messages.get("staff.item.reports");
            case LEAVE -> messages.get("staff.item.leave");
        };
    }
}
