package me.blockcraft.api.elo;

import me.blockcraft.common.elo.EloEntry;
import me.blockcraft.common.elo.EloMatch;
import me.blockcraft.common.elo.EloRating;
import me.blockcraft.common.elo.EloRepository;
import me.blockcraft.common.elo.Season;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ranked 1vs1 ratings for the running season and the skill ladder they map onto. Every read and write here
 * blocks on the database; a new season is nothing but a new key, so a rating starts over by itself in the first
 * week of January, April, July and October.
 */
public final class Elo {

    private final EloRepository repository;
    private final List<SkillGroup> ladder;
    private final Messages messages;

    public Elo(EloRepository repository, List<SkillGroup> ladder, Messages messages) {
        if (ladder.isEmpty()) {
            throw new IllegalArgumentException("elo.groups needs at least one group");
        }
        this.repository = repository;
        this.ladder = ladder;
        this.messages = messages;
    }

    public Season season() {
        return Season.current();
    }

    public EloRating rating(UUID player) {
        return repository.get(player, season());
    }

    public Optional<EloRating> ratingByName(String name) {
        return repository.byName(name, season());
    }

    public List<EloEntry> top(int limit) {
        return repository.top(season(), limit);
    }

    public EloMatch settle(UUID winner, String winnerName, UUID loser, String loserName) {
        return repository.settle(winner, winnerName, loser, loserName, season());
    }

    public EloRating fresh() {
        return EloRating.fresh(repository.formula());
    }

    /** The highest rung the rating reaches; below the lowest rung is still the lowest rung. */
    public SkillGroup group(int rating) {
        SkillGroup reached = ladder.getFirst();
        for (var group : ladder) {
            if (rating >= group.minRating()) {
                reached = group;
            }
        }
        return reached;
    }

    public Component groupName(SkillGroup group) {
        return messages.get("elo.groups." + group.key());
    }

    public Component groupName(int rating) {
        return groupName(group(rating));
    }

    /** {@code <rating>} and the coloured {@code <group>} it sits in. */
    public TagResolver placeholders(EloRating rating) {
        return TagResolver.resolver(
                Formatter.number("rating", rating.rating()),
                Placeholder.component("group", groupName(rating.rating())));
    }
}
