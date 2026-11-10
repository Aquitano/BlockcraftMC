package me.blockcraft.bedwars.shop;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * How the shop takes a price from the slots holding its resource, as the original did: the smallest stacks go
 * first, so a purchase frees as many slots as it can, and whatever is left of the last stack it touched stays in
 * that slot. With shift held a click buys as often as the resources and the offer's limit allow.
 */
final class Payment {

    /** {@code amounts} after the purchase, slot by slot, and how often the price was paid. */
    record Result(int times, int[] amounts) {
    }

    private Payment() {
    }

    /** {@code amounts} holds the resource's count per slot, zero for slots without it. Empty if not even one is paid. */
    static Optional<Result> take(int[] amounts, int price, int maxTimes) {
        var remaining = amounts.clone();
        var slots = IntStream.range(0, amounts.length)
                .filter(slot -> amounts[slot] > 0)
                .boxed()
                .sorted(Comparator.comparingInt(slot -> amounts[slot]))
                .toList();
        int found = 0;
        int times = 0;
        int last = -1;
        for (int slot : slots) {
            found += remaining[slot];
            remaining[slot] = 0;
            last = slot;
            while (found >= price && times < maxTimes) {
                found -= price;
                times++;
            }
            if (times >= maxTimes) {
                break;
            }
        }
        if (times == 0) {
            return Optional.empty();
        }
        if (found > 0) {
            remaining[last] = found;
        }
        return Optional.of(new Result(times, remaining));
    }
}
