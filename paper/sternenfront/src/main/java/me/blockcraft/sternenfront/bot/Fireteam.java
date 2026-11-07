package me.blockcraft.sternenfront.bot;

import me.blockcraft.sternenfront.game.Faction;

import java.util.List;

/** What one fireteam goes for together: its members by number, leader first, and the post and route they share. */
final class Fireteam {

    final int id;
    final Faction faction;
    final List<Integer> members;
    int objective = -1;
    int route;
    private int avoided = -1;
    private long avoidedUntil;

    Fireteam(int id, Faction faction, List<Integer> members) {
        this.id = id;
        this.faction = faction;
        this.members = members;
    }

    void avoid(int post, long until) {
        avoided = post;
        avoidedUntil = until;
    }

    boolean avoiding(int post, long tick) {
        return post == avoided && tick < avoidedUntil;
    }
}
