package me.blockcraft.bedwars.shop;

import me.blockcraft.api.sound.Sounds;
import me.blockcraft.bedwars.perk.Perks;
import me.blockcraft.bedwars.resource.Resource;
import me.blockcraft.bedwars.team.TeamColor;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

/** The round's shop: what a click on an offer costs and brings, settled on a copy of the buyer's inventory first. */
public final class Shop {

    private final ShopCatalog catalog;
    private final Messages messages;
    private final Perks perks;
    private final Function<Player, Optional<TeamColor>> teamOf;

    public Shop(ShopCatalog catalog, Messages messages, Perks perks, Function<Player, Optional<TeamColor>> teamOf) {
        this.catalog = catalog;
        this.messages = messages;
        this.perks = perks;
        this.teamOf = teamOf;
    }

    public void open(Player player) {
        new ShopMenu(this, messages, catalog, player).open(player);
    }

    public ShopCatalog catalog() {
        return catalog;
    }

    /** What a purchase would leave the buyer with, or why it cannot happen. */
    sealed interface Purchase {
        record Done(ItemStack[] contents) implements Purchase {
        }

        record Short(Resource currency) implements Purchase {
        }

        record NoRoom() implements Purchase {
        }
    }

    /** {@code hotbar} is the number key pressed over the offer, -1 for none; it buys exactly one into that slot. */
    Purchase plan(Player player, Offer offer, boolean shift, int hotbar) {
        var contents = Arrays.stream(player.getInventory().getStorageContents())
                .map(stack -> stack == null ? null : stack.clone())
                .toArray(ItemStack[]::new);
        var amounts = Arrays.stream(contents)
                .mapToInt(stack -> stack != null && stack.getType() == offer.currency().material() ? stack.getAmount() : 0)
                .toArray();
        var payment = Payment.take(amounts, offer.price(), shift && hotbar < 0 ? offer.shiftTimes() : 1);
        if (payment.isEmpty()) {
            return new Purchase.Short(offer.currency());
        }
        var left = payment.get().amounts();
        for (int slot = 0; slot < contents.length; slot++) {
            if (amounts[slot] != left[slot]) {
                contents[slot] = left[slot] == 0 ? null : contents[slot].asQuantity(left[slot]);
            }
        }
        var item = item(player, offer);
        boolean fits = hotbar >= 0
                ? Delivery.toSlot(contents, item, hotbar)
                : Delivery.add(contents, item, payment.get().times());
        return fits ? new Purchase.Done(contents) : new Purchase.NoRoom();
    }

    Sounds buy(Player player, Offer offer, boolean shift, int hotbar) {
        return switch (plan(player, offer, shift, hotbar)) {
            case Purchase.Done done -> {
                player.getInventory().setStorageContents(done.contents());
                yield Sounds.MENU_CLICK;
            }
            case Purchase.Short shortOf -> {
                player.sendMessage(messages.get("shop.not-enough",
                        Placeholder.component("resource", shortOf.currency().displayName(messages))));
                yield Sounds.MENU_DENY;
            }
            case Purchase.NoRoom ignored -> {
                player.sendMessage(messages.get("shop.no-room"));
                yield Sounds.MENU_DENY;
            }
        };
    }

    public ItemStack item(Player buyer, Offer offer) {
        return offer.create(messages, teamOf.apply(buyer), perks.stick(buyer));
    }

    ItemStack display(Player buyer, Offer offer) {
        return offer.display(messages, teamOf.apply(buyer), perks.stick(buyer));
    }
}
