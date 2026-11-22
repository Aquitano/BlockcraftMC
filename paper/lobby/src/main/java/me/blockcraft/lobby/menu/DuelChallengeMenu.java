package me.blockcraft.lobby.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.common.duel.DuelRules;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.ProxyMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;

/** The rules a challenger sets before the invitation goes out; the Duell server draws the map. */
public final class DuelChallengeMenu extends Menu {

    private static final String RUSH = "mlgrush";
    private static final int MODE = 11;
    private static final int SERIES = 13;
    private static final int COMBAT = 15;
    private static final int SEND = 22;

    private final Plugin plugin;
    private final Messages messages;
    private final ProxyMessage.DuelSetup setup;
    private final List<String> modes;
    private int mode;
    private boolean legacy = true;
    private int bestOf = 1;
    private boolean submitted;

    public DuelChallengeMenu(Plugin plugin, Messages messages, ProxyMessage.DuelSetup setup, List<String> modes) {
        super(4, messages.get("duel-challenge.title", Placeholder.unparsed("player", setup.name())));
        this.plugin = plugin;
        this.messages = messages;
        this.setup = setup;
        this.modes = modes;
        toolbar(ToolbarButton.LEFT, MenuIcon.BACK.item().name(messages.get("duel-challenge.cancel")).build(), this::cancel);
        toolbar(ToolbarButton.MIDDLE, Items.head(setup.opponent(), setup.name()).name(messages.get("duel-challenge.opponent",
                Placeholder.unparsed("player", setup.name()))).build(), ignored -> { });
        draw();
    }

    private String mode() {
        return modes.get(mode);
    }

    private void draw() {
        set(MODE, Items.of(Material.DIAMOND_SWORD).name(messages.get("duel-challenge.mode",
                Placeholder.component("mode", messages.get("duel-challenge.modes." + mode())))).lore(messages.get("duel-challenge.toggle")).build());
        set(SERIES, Items.of(Material.PAPER, bestOf).name(messages.get("duel-challenge.series", Formatter.number("games", bestOf)))
                .lore(messages.get("duel-challenge.toggle")).build());
        set(COMBAT, Items.of(legacy ? Material.IRON_SWORD : Material.SHIELD)
                .name(messages.get(legacy ? "duel-challenge.legacy" : "duel-challenge.modern"))
                .lore(messages.get(mode().equals(RUSH) ? "duel-challenge.legacy-only" : "duel-challenge.toggle")).build());
        set(SEND, Items.of(Material.LIME_DYE).name(messages.get("duel-challenge.send")).build());
    }

    private void cancel(Player player) {
        submitted = true;
        Menu.close(player);
        ProxyMessaging.send(plugin, player, new ProxyMessage.DuelCancel(setup.ticket()));
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack item, InventoryClickEvent event) {
        if (submitted) return;
        switch (slot) {
            case MODE -> {
                mode = (mode + 1) % modes.size();
                if (mode().equals(RUSH)) legacy = true;
                draw();
            }
            case SERIES -> {
                bestOf = bestOf == 1 ? 3 : 1;
                draw();
            }
            case COMBAT -> {
                if (!mode().equals(RUSH)) legacy = !legacy;
                draw();
            }
            case SEND -> {
                submitted = true;
                Menu.close(player);
                ProxyMessaging.send(plugin, player, new ProxyMessage.DuelInvitation(setup.ticket(), new DuelRules(mode(), legacy, bestOf)));
            }
            default -> { }
        }
    }
}
