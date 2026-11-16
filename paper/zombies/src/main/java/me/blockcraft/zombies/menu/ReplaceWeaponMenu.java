package me.blockcraft.zombies.menu;

import me.blockcraft.api.menu.Menu;
import me.blockcraft.zombies.game.Weapon;
import me.blockcraft.zombies.game.ZombiesGame;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** A buyer with two guns clicks the one the offered gun takes the place of; leaving the menu buys nothing. */
public final class ReplaceWeaponMenu extends Menu {

    private static final int OFFERED = 0;
    private static final int FIRST_CARRIED = 3;

    private final Map<Integer, Weapon> carried = new HashMap<>();
    private final Consumer<Weapon> onChoice;

    public ReplaceWeaponMenu(ZombiesGame game, Player player, Weapon offered, Consumer<Weapon> onChoice) {
        super(1, game.messages().get("shop.replace-title"));
        this.onChoice = onChoice;
        var messages = game.messages();
        var name = Placeholder.component("weapon", messages.get("weapon." + offered.key()));
        info(OFFERED, offered.model().item().name(messages.get("weapon.name", name))
                .lore(messages.get("shop.replace-body", name)).hideAttributes().build());
        int slot = FIRST_CARRIED;
        for (var owned : game.survivor(player).weapons().keySet()) {
            carried.put(slot, owned);
            set(slot, owned.model().item().name(game.weapons().displayName(player, owned))
                    .lore(messages.get("shop.replace-choice", name)).hideAttributes().build());
            slot += 2;
        }
        closeButton();
    }

    /** Sends the exit back to the menu the swap was opened from instead of closing. */
    public ReplaceWeaponMenu returningTo(Consumer<Player> previous) {
        backButton(previous);
        return this;
    }

    @Override
    protected boolean automaticClickSound(int slot, InventoryClickEvent event) {
        return false;
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        var weapon = carried.get(slot);
        if (weapon != null) {
            onChoice.accept(weapon);
        }
    }
}
