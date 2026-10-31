package me.blockcraft.lobby.konami;

import org.bukkit.Input;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** A button of the code on the movement keys: B is sneak and A is jump. */
public enum KonamiKey {
    UP(Input::isForward),
    DOWN(Input::isBackward),
    LEFT(Input::isLeft),
    RIGHT(Input::isRight),
    B(Input::isSneak),
    A(Input::isJump);

    private final Predicate<Input> held;

    KonamiKey(Predicate<Input> held) {
        this.held = held;
    }

    /** The keys {@code now} holds that {@code before} did not. */
    public static List<KonamiKey> pressed(Input before, Input now) {
        return Arrays.stream(values()).filter(key -> (before == null || !key.held.test(before)) && key.held.test(now)).toList();
    }

    public String messageKey() {
        return "konami.key." + name().toLowerCase(Locale.ROOT);
    }
}
