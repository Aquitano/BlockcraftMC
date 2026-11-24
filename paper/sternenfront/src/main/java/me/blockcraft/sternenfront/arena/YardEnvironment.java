package me.blockcraft.sternenfront.arena;

import me.blockcraft.api.pack.PackModel;
import org.bukkit.Location;

import java.util.List;
import java.util.Optional;

public record YardEnvironment(List<Location> steam, List<Location> sparks, Optional<Crane> crane, List<Prop> props,
                              List<SeaSupport> sea) {

    public static final YardEnvironment EMPTY = new YardEnvironment(List.of(), List.of(), Optional.empty(), List.of(), List.of());

    public record Crane(Location from, Location to) { }

    public record Prop(PackModel model, Location at, float scale) { }

    public record SeaSupport(Location at, double radius) { }
}
