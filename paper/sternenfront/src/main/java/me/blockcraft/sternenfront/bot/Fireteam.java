package me.blockcraft.sternenfront.bot;

import me.blockcraft.sternenfront.game.Faction;

import org.bukkit.Location;
import org.bukkit.util.Vector;

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
    /** Where a member's killer stood, which the team treats as contact until {@code alarmUntil}. */
    Location alarm;
    long alarmUntil;
    int flanker = -1;
    long fallBackUntil;
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

    enum Place { IN_COLUMN, AHEAD, STRAGGLING, AWAY }

    /**
     * Where a member stands against its leader on the way to {@code objective}: a member that has further to go is
     * behind, however the route bends, and only one in the gap between {@code near} and {@code away} holds the team up.
     */
    static Place place(Vector leader, Vector member, Vector objective, double spacing, double near, double away) {
        double gap = leader.distance(member);
        double lag = left(member, objective) - left(leader, objective);
        if (gap >= away) {
            return Place.AWAY;
        }
        if (lag < -spacing) {
            return Place.AHEAD;
        }
        return lag > 0 && gap > near ? Place.STRAGGLING : Place.IN_COLUMN;
    }

    /**
     * How fast a member walks to its place {@code gap} blocks away: the leader's pace once there, so it neither
     * overruns the place nor stops and starts, and up to a run when it has fallen a few places behind.
     */
    static double followPace(double gap, double spacing, double walk, double sprint) {
        return Math.clamp(walk + (sprint - walk) * (gap - spacing) / (2 * spacing), walk, sprint);
    }

    static double left(Vector at, Vector objective) {
        return Math.hypot(at.getX() - objective.getX(), at.getZ() - objective.getZ());
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
