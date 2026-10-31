package me.blockcraft.common.network.operation;

import me.blockcraft.common.moderation.PunishmentType;
import me.blockcraft.common.player.PlayerReference;

import java.util.Objects;

/** What a backend may ask the proxy to do on a player's behalf; every field is validated before it is sent. */
public sealed interface NetworkRequest {

    int MAX_TEXT_LENGTH = 200;

    record SubmitReport(String target, String reason) implements NetworkRequest {

        public SubmitReport {
            if (!PlayerReference.isName(target)) {
                throw new IllegalArgumentException("Invalid player name");
            }
            requireText(reason);
        }
    }

    record TimedPunishment(PlayerReference target, PunishmentType type, long seconds, String reason) implements NetworkRequest {

        public TimedPunishment {
            Objects.requireNonNull(target);
            Objects.requireNonNull(type);
            if (seconds <= 0 || seconds > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid duration");
            }
            requireText(reason);
        }
    }

    record PresetPunishment(PlayerReference target, int number) implements NetworkRequest {

        public PresetPunishment {
            Objects.requireNonNull(target);
            if (number < 1) {
                throw new IllegalArgumentException("Invalid preset");
            }
        }
    }

    static boolean isText(String text) {
        return text != null && !text.isBlank() && text.length() <= MAX_TEXT_LENGTH
                && text.chars().noneMatch(Character::isISOControl);
    }

    private static void requireText(String text) {
        if (!isText(text)) {
            throw new IllegalArgumentException("Invalid reason");
        }
    }
}
