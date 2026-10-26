package me.blockcraft.api.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.proxy.ProxyMessaging;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The waiting lobby's hotbar, the same in every game: the game's own choices in the first two slots (kits, perks,
 * the vote), the player's stats in the middle, /start for staff and the way back to the hub at the end. A choice
 * that is unavailable, such as a vote that has closed, is left out until {@link #refreshChoices} finds it available
 * again.
 */
public final class LobbyHotbar implements Listener {

    private static final int MAX_CHOICES = 2;
    private static final int STATS_SLOT = 4;
    private static final int START_SLOT = 7;
    private static final int HUB_SLOT = 8;

    public record Choice(MenuIcon icon, Component name, BooleanSupplier available, Consumer<Player> open) {

        public Choice(MenuIcon icon, Component name, Consumer<Player> open) {
            this(icon, name, () -> true, open);
        }
    }

    enum LobbyItem implements HotbarItem {
        CHOICE,
        SECOND_CHOICE,
        STATS,
        START,
        HUB
    }

    private final Plugin plugin;
    private final List<Choice> choices;
    private final StatsMenus stats;
    private final LobbyCountdown countdown;

    /** At most two {@code choices}, in the order of their slots. */
    public LobbyHotbar(Plugin plugin, List<Choice> choices, StatsMenus stats, LobbyCountdown countdown) {
        if (choices.size() > MAX_CHOICES) {
            throw new IllegalArgumentException("The lobby hotbar has room for two choices, not " + choices.size());
        }
        this.plugin = plugin;
        this.choices = List.copyOf(choices);
        this.stats = stats;
        this.countdown = countdown;
    }

    public void give(Player player) {
        var messages = BlockcraftApi.get().messages();
        var inventory = player.getInventory();
        refreshChoices(player);
        inventory.setItem(STATS_SLOT, LobbyItem.STATS.build(
                MenuIcon.TOOL_PROFILE.mark(Items.head(player.getUniqueId(), BlockcraftApi.get().realName(player)))
                        .name(messages.get("hotbar.stats"))));
        if (player.hasPermission(GamePermissions.START)) {
            inventory.setItem(START_SLOT, LobbyItem.START.build(MenuIcon.START.item().name(messages.get("hotbar.start"))));
        }
        giveHub(player);
    }

    /** Only the way back to the hub, for a game whose players wait for the restart in its lobby. */
    public void giveHub(Player player) {
        player.getInventory().setItem(HUB_SLOT, LobbyItem.HUB.build(
                MenuIcon.TOOL_HOME.item().name(BlockcraftApi.get().messages().get("hotbar.hub"))));
    }

    /** Puts the choices that are available into their slots and takes the others away. */
    public void refreshChoices(Player player) {
        for (int slot = 0; slot < choices.size(); slot++) {
            var choice = choices.get(slot);
            player.getInventory().setItem(slot, choice.available().getAsBoolean()
                    ? LobbyItem.values()[slot].build(choice.icon().item().name(choice.name()))
                    : null);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) {
            return;
        }
        var player = event.getPlayer();
        HotbarItem.of(event.getItem(), LobbyItem.class).ifPresent(item -> {
            event.setCancelled(true);
            switch (item) {
                case CHOICE, SECOND_CHOICE -> open(player, choices.get(item.ordinal()));
                case STATS -> stats.open(player);
                case START -> {
                    if (player.hasPermission(GamePermissions.START)) {
                        countdown.forceStart(player);
                    }
                }
                case HUB -> ProxyMessaging.hub(plugin, player);
            }
        });
    }

    private static void open(Player player, Choice choice) {
        if (choice.available().getAsBoolean()) {
            choice.open().accept(player);
        }
    }
}
