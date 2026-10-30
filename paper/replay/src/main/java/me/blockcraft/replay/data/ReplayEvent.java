package me.blockcraft.replay.data;

import java.util.List;
import java.util.UUID;

public sealed interface ReplayEvent {

    int tick();

    /** Something recorded that moves: a participant or another entity. */
    sealed interface Body<T extends Body<T>> permits Actor, Prop {
        UUID id();
        UUID world();
        double x();
        double y();
        double z();
        float yaw();
        float pitch();

        T at(double x, double y, double z, float yaw, float pitch);
    }

    /**
     * One sample: where the participants and the entities around them are, the entities seen for the first time and
     * what everyone did since the previous sample.
     */
    record Frame(int tick, List<Actor> actors, List<Prop> props, List<Appearance> appearances,
                 List<Animation> animations) implements ReplayEvent {
        public Frame {
            actors = List.copyOf(actors);
            props = List.copyOf(props);
            appearances = List.copyOf(appearances);
            animations = List.copyOf(animations);
        }

        public Frame(int tick, List<Actor> actors) {
            this(tick, actors, List.of(), List.of(), List.of());
        }
    }

    record Actor(UUID id, UUID world, double x, double y, double z, float yaw, float pitch,
                 String pose, List<byte[]> equipment) implements Body<Actor> {
        @Override
        public Actor at(double x, double y, double z, float yaw, float pitch) {
            return new Actor(id, world, x, y, z, yaw, pitch, pose, equipment);
        }
    }

    /** A mob, projectile or primed TNT near the participants. */
    record Prop(UUID id, UUID world, double x, double y, double z, float yaw, float pitch) implements Body<Prop> {
        @Override
        public Prop at(double x, double y, double z, float yaw, float pitch) {
            return new Prop(id, world, x, y, z, yaw, pitch);
        }
    }

    /** How a prop looked when it was first seen, as Minecraft entity data. */
    record Appearance(UUID id, String entity) { }

    record Animation(UUID subject, Motion motion) { }

    enum Motion { SWING_MAIN_HAND, SWING_OFF_HAND, HURT, DEATH, EXPLOSION, SHOT }

    record Chunk(int tick, UUID world, int x, int z, int minY, int height,
                 List<String> palette, int[] blocks) implements ReplayEvent { }

    record Blocks(int tick, List<Block> blocks) implements ReplayEvent {
        public Blocks {
            blocks = List.copyOf(blocks);
        }
    }

    record Block(UUID world, int x, int y, int z, String data) { }

    /** A recorded world's time of day from this tick on. */
    record Daytime(int tick, UUID world, long time) implements ReplayEvent { }

    /**
     * An entity that mostly stays where it is, such as a display or a dropped item, as it shows from this tick on.
     * {@code entity} is its Minecraft entity data, empty while it shows what it showed before; {@code transformation}
     * is a display's translation, left rotation, scale and right rotation, which it eases into over
     * {@code interpolation} ticks, and empty for anything else.
     */
    record Fixture(int tick, UUID id, UUID world, double x, double y, double z, float yaw, float pitch, String entity,
                   float[] transformation, int interpolation) implements ReplayEvent {
        public static final int TRANSFORMATION_SIZE = 14;
    }

    /** A fixture is gone from this tick on. */
    record FixtureGone(int tick, UUID id) implements ReplayEvent { }
}
