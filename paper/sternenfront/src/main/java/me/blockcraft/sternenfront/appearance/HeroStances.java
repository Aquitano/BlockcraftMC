package me.blockcraft.sternenfront.appearance;

import me.blockcraft.sternenfront.combat.Stance;

import java.util.Optional;

/** How a bladed hero's arms and blade sit in each stance, in radians, and how many ticks the body takes to get there. */
final class HeroStances {

    private static final float READY_TILT = .35f;
    private static final float SWING_TURN = 1f;
    private static final int BLEND_TICKS = 3;
    private static final int CAST_BLEND_TICKS = 2;

    /**
     * {@code lift} raises an arm forward from hanging, replacing the body's resting lift; {@code turn} swings an arm or
     * the blade toward the body's left; {@code bladeTilt} tips the blade forward and {@code bladeX} moves it leftward.
     */
    record Limbs(float rightLift, float rightTurn, float leftLift, float leftTurn,
                 float bladeTilt, float bladeTurn, float bladeRoll, float bladeX, float bladeY, int blendTicks) { }

    private HeroStances() { }

    /** {@code rest} is the body's own arm lift, which a fighter without a stance keeps. */
    static Limbs of(Optional<Stance> stance, float rest) {
        return stance.map(shown -> inStance(shown, rest)).orElseGet(() -> new Limbs(rest, 0, rest, 0, 0, 0, 0, 0, 0, 1));
    }

    private static Limbs inStance(Stance stance, float rest) {
        return switch (stance) {
            case Stance.Still still -> switch (still) {
                case READY -> new Limbs(rest, 0, rest, 0, READY_TILT, 0, 0, 0, 0, BLEND_TICKS);
                case GUARD -> new Limbs(-1.4f, .5f, -1.3f, -.6f, .1f, .25f, -.8f, .17f, .12f, BLEND_TICKS);
                case WIND_UP -> new Limbs(rest, 0, .6f, .35f, READY_TILT, 0, 0, 0, 0, BLEND_TICKS);
                case CAST -> new Limbs(rest, 0, -1.55f, -.1f, READY_TILT, 0, 0, 0, 0, CAST_BLEND_TICKS);
            };
            case Stance.Swing swing -> {
                boolean windUp = swing.elapsed() == 0;
                float turn = (swing.fromLeft() == windUp ? 1 : -1) * SWING_TURN;
                yield new Limbs(-1.45f, turn, rest, 0, 1.45f, turn, 0, 0, 0, windUp ? 1 : Math.max(1, swing.ticks() - 1));
            }
        };
    }
}
