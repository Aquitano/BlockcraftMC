package me.blockcraft.endergames.game;

import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** Two different players drawn at random for a swap. */
record SwapPairs<T>(T first, T second) {

    static <T> Optional<SwapPairs<T>> pick(List<T> candidates, RandomGenerator random) {
        if (candidates.size() < 2) {
            return Optional.empty();
        }
        int first = random.nextInt(candidates.size());
        int second = random.nextInt(candidates.size() - 1);
        if (second >= first) {
            second++;
        }
        return Optional.of(new SwapPairs<>(candidates.get(first), candidates.get(second)));
    }
}
