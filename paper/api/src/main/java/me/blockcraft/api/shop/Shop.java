package me.blockcraft.api.shop;

import me.blockcraft.api.coins.CoinsRepository;

import java.util.UUID;

/** Coin purchases of {@link Unlocks} items; blocking, so call it through {@code Tasks.async}. */
public final class Shop {

    public enum Outcome {
        BOUGHT,
        ALREADY_OWNED,
        NOT_ENOUGH_COINS
    }

    /** {@code balance} is the coins after the purchase, or the unchanged balance when nothing was bought. */
    public record Purchase(Outcome outcome, int balance) {

        public boolean bought() {
            return outcome == Outcome.BOUGHT;
        }
    }

    private final CoinsRepository coins;
    private final Unlocks unlocks;

    public Shop(CoinsRepository coins, Unlocks unlocks) {
        this.coins = coins;
        this.unlocks = unlocks;
    }

    /**
     * The unlock row is claimed first: of two clicks in the same tick only one insert succeeds, so a player is
     * never charged twice. A failed withdrawal gives the row back.
     */
    public Purchase buy(UUID player, String name, String item, int price) {
        if (!unlocks.unlock(player, item)) {
            return new Purchase(Outcome.ALREADY_OWNED, coins.get(player));
        }
        if (!coins.withdraw(player, name, price)) {
            unlocks.revoke(player, item);
            return new Purchase(Outcome.NOT_ENOUGH_COINS, coins.get(player));
        }
        return new Purchase(Outcome.BOUGHT, coins.get(player));
    }
}
