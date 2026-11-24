package me.blockcraft.api.game;

import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public record Stats(int kills, int deaths, int wins, int losses, int points) {

    public static final Stats EMPTY = new Stats(0, 0, 0, 0, 0);

    /** Kills per death rounded to two places, or "-" without a death. */
    public String kdr() {
        return deaths == 0 ? "-" : String.valueOf(Math.round((double) kills / deaths * 100.0) / 100.0);
    }

    /** The tags every stats line and stats item is written with. */
    public TagResolver placeholders() {
        return TagResolver.resolver(
                Formatter.number("kills", kills),
                Formatter.number("deaths", deaths),
                Formatter.number("wins", wins),
                Formatter.number("losses", losses),
                Formatter.number("points", points),
                Placeholder.unparsed("kdr", kdr()));
    }
}
