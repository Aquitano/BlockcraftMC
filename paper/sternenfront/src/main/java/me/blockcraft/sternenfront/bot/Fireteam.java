package me.blockcraft.sternenfront.bot;

import me.blockcraft.sternenfront.game.Faction;

import org.bukkit.Location;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** What one fireteam goes for together: its members by number, leader first, and the post and route they share. */
final class Fireteam {

    final int id;
    final Faction faction;
    final List<Integer> members;
    int objective = -1;
    int route;
    int leader = -1;
    boolean assaulting;
    boolean regrouping;
    long waitingSince = -1;
    /** Where the team last knew an enemy to be while any member still remembers one. */
    Location threat;
    long contactSince = -1;
    int flanker = -1;
    final Bounding bounding = new Bounding();
    /** Where the leader walked, newest first, a step apart. */
    final Deque<Location> trail = new ArrayDeque<>();
    private int avoided = -1;
    private long avoidedUntil;

    Fireteam(int id, Faction faction, List<Integer> members) {
        this.id = id;
        this.faction = faction;
        this.members = members;
    }

    int element(int number) {
        return members.indexOf(number) % 2;
    }

    void avoid(int post, long until) {
        avoided = post;
        avoidedUntil = until;
    }

    boolean avoiding(int post, long tick) {
        return post == avoided && tick < avoidedUntil;
    }

    /** The point {@code distance} blocks back along where the leader walked, so a column keeps to walkable ground. */
    static Location behind(Location leader, Iterable<Location> trail, double distance) {
        var reached = leader;
        double walked = 0;
        for (var step : trail) {
            walked += step.distance(reached);
            reached = step;
            if (walked >= distance) {
                break;
            }
        }
        return reached.clone();
    }
}
