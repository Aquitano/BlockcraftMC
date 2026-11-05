package me.blockcraft.sternenfront.arena;

import org.bukkit.Location;

import java.util.List;
import java.util.Optional;

public record YardEnvironment(List<Location> steam, List<Location> sparks, Optional<Crane> crane) {

    public static final YardEnvironment EMPTY = new YardEnvironment(List.of(), List.of(), Optional.empty());

    public record Crane(Location from, Location to) { }
}
