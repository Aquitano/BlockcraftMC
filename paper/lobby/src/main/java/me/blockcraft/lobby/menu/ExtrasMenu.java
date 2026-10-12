package me.blockcraft.lobby.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.lobby.LobbyPermissions;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/** The toolbar closes or crosses over to the profile and the shop; the cosmetics sit in the row below. */
final class ExtrasMenu extends Menu {

    private static final int GADGETS_SLOT = 9;
    private static final int KILL_EFFECTS_SLOT = 10;
    private static final int WIN_EFFECTS_SLOT = 11;
    private static final int TRAILS_SLOT = 12;
    private static final int PREMIUM_SLOT = 13;
    private static final int TAGS_SLOT = 15;
    private static final int LOBBIES_SLOT = 16;

    private final Menus menus;

    ExtrasMenu(Menus menus, Player viewer) {
        super(2, menus.messages().get("extras.title"));
        this.menus = menus;
        var messages = menus.messages();
        closeButton(ToolbarButton.LEFT);
        toolbar(ToolbarButton.MIDDLE, MenuIcon.TOOL_PROFILE.mark(Items.head(viewer.getUniqueId(), BlockcraftApi.get().realName(viewer)))
                .name(messages.get("extras.profile")).lore(messages.get("extras.profile-lore")).build(), menus::openProfile);
        toolbar(ToolbarButton.RIGHT, MenuIcon.TOOL_SHOP.item()
                .name(messages.get("extras.shop")).lore(messages.get("extras.shop-lore")).build(), menus::openShop);
        set(GADGETS_SLOT, Items.of(Material.FISHING_ROD).name(messages.get("extras.gadgets")).lore(messages.get("extras.gadgets-lore")).hideAttributes().build());
        set(KILL_EFFECTS_SLOT, Items.of(Material.IRON_SWORD).name(messages.get("extras.kill-effects")).lore(messages.get("extras.kill-effects-lore")).hideAttributes().build());
        set(WIN_EFFECTS_SLOT, Items.of(Material.FIREWORK_ROCKET).name(messages.get("extras.win-effects")).lore(messages.get("extras.win-effects-lore")).build());
        set(TRAILS_SLOT, Items.of(Material.SNOWBALL).name(messages.get("premium-menu.trails")).lore(messages.get("premium-menu.trails-lore")).build());
        set(PREMIUM_SLOT, MenuIcon.PREMIUM.item().name(messages.get("extras.premium")).lore(messages.get("extras.premium-lore")).build());
        set(TAGS_SLOT, Items.of(Material.NAME_TAG).name(messages.get("extras.tags")).lore(messages.get("extras.tags-lore")).build());
        set(LOBBIES_SLOT, Items.of(Material.NETHER_STAR).name(messages.get("extras.lobbies")).lore(messages.get("extras.lobbies-lore")).build());
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        switch (slot) {
            case GADGETS_SLOT -> menus.openGadgets(player);
            case KILL_EFFECTS_SLOT -> menus.openKillEffects(player);
            case WIN_EFFECTS_SLOT -> menus.openWinEffects(player);
            case TRAILS_SLOT -> menus.openTrails(player);
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
