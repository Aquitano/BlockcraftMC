package me.blockcraft.sternenfront.appearance;

import me.blockcraft.sternenfront.bot.BotSnapshot.Point;

import java.util.List;
import java.util.UUID;

public record RigSnapshot(String type, Point previous, double stride, List<UUID> viewers, List<Part> parts) {
    public record Part(String name, UUID id, Point position, float yaw, float pitch, List<Float> translation,
                       List<Float> leftRotation, List<Float> rightRotation, List<Float> scale,
                       int teleportTicks, int interpolationTicks, int interpolationDelay) { }
}
