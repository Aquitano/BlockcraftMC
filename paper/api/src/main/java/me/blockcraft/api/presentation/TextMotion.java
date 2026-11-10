package me.blockcraft.api.presentation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

public enum TextMotion {
    FLOAT, SHAKE, PULSE, RAINBOW, SHINE;

    public Component apply(Component text) {
        return text.color(TextColor.color(231, ordinal() + 1, 197));
    }
}
