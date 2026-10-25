package me.blockcraft.zombies.game;

import java.util.Locale;

public enum EnemyKind {
    WALKER, RUNNER, TANK, EXPLODER, HELLHOUND, INFERNO;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
