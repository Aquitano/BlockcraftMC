package me.blockcraft.lobby.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.lobby.LobbyPermissions;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

final class ExtrasMenu extends Menu {

    private static final int GADGETS_SLOT = 0;
    private static final int KILL_EFFECTS_SLOT = 1;
    private static final int WIN_EFFECTS_SLOT = 2;
    private static final int PREMIUM_SLOT = 4;
    private static final int TAGS_SLOT = 6;
    private static final int LOBBIES_SLOT = 7;

    private final Menus menus;

    ExtrasMenu(Menus menus) {
        super(1, menus.messages().get("extras.title"));
        this.menus = menus;
        var messages = menus.messages();
        set(GADGETS_SLOT, Items.of(Material.FISHING_ROD).name(messages.get("extras.gadgets")).lore(messages.get("extras.gadgets-lore")).hideAttributes().build());
        set(KILL_EFFECTS_SLOT, Items.of(Material.IRON_SWORD).name(messages.get("extras.kill-effects")).lore(messages.get("extras.kill-effects-lore")).hideAttributes().build());
        set(WIN_EFFECTS_SLOT, Items.of(Material.FIREWORK_ROCKET).name(messages.get("extras.win-effects")).lore(messages.get("extras.win-effects-lore")).build());
        set(PREMIUM_SLOT, Items.of(Material.GLISTERING_MELON_SLICE).name(messages.get("extras.premium")).lore(messages.get("extras.premium-lore")).build());
        set(TAGS_SLOT, Items.of(Material.NAME_TAG).name(messages.get("extras.tags")).lore(messages.get("extras.tags-lore")).build());
        set(LOBBIES_SLOT, Items.of(Material.NETHER_STAR).name(messages.get("extras.lobbies")).lore(messages.get("extras.lobbies-lore")).build());
        fill(Items.of(Material.BLACK_STAINED_GLASS_PANE).name(messages.get("extras.filler")).build());
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        switch (slot) {
            case GADGETS_SLOT -> menus.openGadgets(player);
            case KILL_EFFECTS_SLOT -> menus.openKillEffects(player);
            case WIN_EFFECTS_SLOT -> menus.openWinEffects(player);
            case PREMIUM_SLOT -> {
                if (player.hasPermission(LobbyPermissions.PREMIUM)) {
                    menus.openPremium(player);
                } else {
                    close(player);
                    menus.messages().getLines("premium.required").forEach(player::sendMessage);
                }
            }
            case TAGS_SLOT -> menus.openChatTags(player);
            case LOBBIES_SLOT -> menus.openLobbies(player);
            default -> close(player);
        }
    }
}
