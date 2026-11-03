package me.blockcraft.sternenfront.arena;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

import java.util.List;

/**
 * Where the map hides its secrets: the five kyber crystals in finding order, the loop the droid Brummi waddles round,
 * the dune that wakes the sand beast, the cantina's music box and the Kessel-Lauf.
 */
public record SecretSpots(List<Location> crystals, List<Location> droidPath, Dune dune, Block musicBox, Course course) {

    /** The top of the dune and how far from it a player still stands on it. */
    public record Dune(Location top, double radius) {
    }

    /**
     * The Kessel-Lauf. {@code gates} are the x planes a runner crosses in order, from the start through the
     * checkpoints to the finish. Inside {@code lane} a runner is on the course; feet below {@code pitY} fell into the
     * pit. {@code board} is where the best times float.
     */
    public record Course(BoundingBox lane, double pitY, List<Double> gates, Location board) {
    }
}
