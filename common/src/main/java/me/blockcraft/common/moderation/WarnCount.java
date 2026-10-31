package me.blockcraft.common.moderation;

import java.util.UUID;

public record WarnCount(UUID player, String name, int count) {
}
