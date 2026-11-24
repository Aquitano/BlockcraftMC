package me.blockcraft.zombies.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.zombies.game.Weapon;
import me.blockcraft.zombies.game.ZombiesGame;
import me.blockcraft.zombies.menu.StationScreen.Art;
import me.blockcraft.zombies.menu.StationScreen.Layout;
import me.blockcraft.zombies.menu.StationScreen.Tone;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;

/**
 * A buyer with two guns clicks the one the offered gun takes the place of; leaving the menu buys nothing. With the pack
 * the offered gun's card stands on top, arrows lead from it to the cards of the two carried guns below, and a click
 * anywhere on one of those replaces it; without it, a chest row holds the offered gun and the two carried ones.
 */
public final class ReplaceWeaponMenu extends Menu {

    private static final int OFFERED = 0;
    private static final int FIRST_CARRIED = 3;

    private final ZombiesGame game;
    private final Player player;
    private final Weapon offered;
    private final boolean cards;
    private final Map<Integer, Weapon> carried = new HashMap<>();
    private final Consumer<Weapon> onChoice;

    public ReplaceWeaponMenu(ZombiesGame game, Player player, Weapon offered, Consumer<Weapon> onChoice) {
        boolean cards = BlockcraftApi.get().resourcePack().applied(player);
        super(cards ? Layout.SWAP.rows : 1, game.messages().get("shop.replace-title"));
        this.game = game;
        this.player = player;
        this.offered = offered;
        this.cards = cards;
        this.onChoice = onChoice;
        var messages = game.messages();
        var name = Placeholder.component("weapon", messages.get("weapon." + offered.key()));
        int offeredSlot = cards ? Layout.SWAP.cards.getFirst() : OFFERED;
        area(offeredSlot);
        info(offeredSlot, offered.model().item().name(messages.get("weapon.name", name))
                .lore(messages.get("shop.replace-body", name)).hideAttributes().build());
        int index = 0;
        for (var owned : game.survivor(player).weapons().keySet()) {
            int slot = cards ? Layout.SWAP.cards.get(1 + index) : FIRST_CARRIED + 2 * index;
            carried.put(slot, owned);
            area(slot);
            set(slot, owned.model().item().name(game.weapons().displayName(player, owned))
                    .lore(messages.get("shop.replace-choice", name)).hideAttributes().build());
            index++;
        }
        closeButton();
    }

    private void area(int slot) {
        if (cards) {
            area(slot, StationScreen.CARD_COLUMNS, StationScreen.CARD_ROWS);
        }
    }

    /** Sends the exit back to the menu the swap was opened from instead of closing. */
    public ReplaceWeaponMenu returningTo(Consumer<Player> previous) {
        backButton(previous);
        return this;
    }

    @Override
    protected Optional<Component> screen() {
        if (!cards) {
            return Optional.empty();
        }
        var messages = game.messages();
        var shown = new ArrayList<StationScreen.Card>();
        shown.add(card(offered, false, messages.get("shop.state.new"), Tone.BUY));
        carried.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> shown.add(card(entry.getValue(), true, messages.get("shop.state.replace"), Tone.REPLACE)));
        return Optional.of(StationScreen.render(Layout.SWAP, Art.AMMO, StationScreen.capitals(messages.get("shop.replace-heading")),
                game.survivor(player).gold(), List.copyOf(shown)));
    }

    /** A carried gun shows the damage its upgrade gives it. */
    private StationScreen.Card card(Weapon weapon, boolean carried, Component state, Tone tone) {
        var stats = game.config().guns().get(weapon);
        var ammo = game.survivor(player).weapons().get(weapon);
        double damage = stats.damage() * stats.pellets() * (carried && ammo != null && ammo.upgraded() ? stats.upgradeMultiplier() : 1);
        return new StationScreen.Card(Art.of(weapon), StationScreen.name(game.messages(), weapon),
                StationScreen.stats(damage, stats.clip()), OptionalInt.empty(), StationScreen.capitals(state), tone);
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
