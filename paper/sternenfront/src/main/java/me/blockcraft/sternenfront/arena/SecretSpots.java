package me.blockcraft.sternenfront.arena;

import org.bukkit.Location;
import org.bukkit.util.BoundingBox;

import java.util.List;
import java.util.Optional;

/**
 * Where the map hides its secrets: the five kyber crystals in finding order, the loop the droid Brummi waddles round,
 * the dune that wakes the sand beast, the cantina's music box, the shipyard receiver and the two courses.
 */
public record SecretSpots(List<Location> crystals, List<Location> droidPath, Optional<Dune> dune,
                          Optional<Location> musicBox, Optional<Course> course, Optional<Location> receiver,
                          Optional<Course> inspectionCourse) {

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
