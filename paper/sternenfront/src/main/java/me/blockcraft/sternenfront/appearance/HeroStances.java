package me.blockcraft.sternenfront.appearance;

import me.blockcraft.sternenfront.combat.Stance;

import java.util.Optional;

/** How a bladed hero's arms and blade sit in each stance, in radians, and how many ticks the body takes to get there. */
final class HeroStances {

    private static final float READY_TILT = .35f;
    private static final float SWING_TURN = 1f;
    private static final int EASE = 3;
    private static final int CAST_EASE = 2;

    /**
     * {@code lift} raises an arm forward from hanging, replacing the body's resting lift; {@code turn} swings an arm or
     * the blade toward the body's left; {@code bladeTilt} tips the blade forward and {@code bladeX} moves it leftward.
     */
    record Limbs(float rightLift, float rightTurn, float leftLift, float leftTurn,
                 float bladeTilt, float bladeTurn, float bladeRoll, float bladeX, float bladeY, int ease) { }

    private HeroStances() { }

    /** {@code rest} is the body's own arm lift, which a fighter without a stance keeps. */
    static Limbs of(Optional<Stance> stance, float rest) {
        return stance.map(shown -> of(shown, rest)).orElseGet(() -> new Limbs(rest, 0, rest, 0, 0, 0, 0, 0, 0, 1));
    }

    private static Limbs of(Stance stance, float rest) {
        return switch (stance) {
            case Stance.Still still -> switch (still) {
                case READY -> new Limbs(rest, 0, rest, 0, READY_TILT, 0, 0, 0, 0, EASE);
                case GUARD -> new Limbs(-1.4f, .5f, -1.3f, -.6f, .1f, .25f, -.8f, .17f, .12f, EASE);
                case CAST -> new Limbs(rest, 0, -1.55f, -.1f, READY_TILT, 0, 0, 0, 0, CAST_EASE);
            };
            case Stance.Swing swing -> {
                boolean windUp = swing.elapsed() == 0;
                float turn = (swing.fromLeft() == windUp ? 1 : -1) * SWING_TURN;
                yield new Limbs(-1.45f, turn, rest, 0, 1.45f, turn, 0, 0, 0, windUp ? 1 : Math.max(1, swing.ticks() - 1));
            }
        };
    }
}
