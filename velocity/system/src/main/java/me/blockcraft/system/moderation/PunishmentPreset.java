package me.blockcraft.system.moderation;

import me.blockcraft.common.moderation.PunishmentType;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/** One numbered entry of the /strafe catalogue from config.yml. */
public record PunishmentPreset(PunishmentType type, String reason, Optional<Duration> duration) {

    public static PunishmentPreset fromYaml(Map<String, Object> section) {
        return new PunishmentPreset(
                PunishmentType.valueOf(String.valueOf(section.get("type")).toUpperCase()),
                String.valueOf(section.get("reason")),
                Durations.parse(String.valueOf(section.getOrDefault("duration", "permanent"))));
    }
}
