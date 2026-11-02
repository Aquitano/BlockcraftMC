package me.blockcraft.sternenfront.game;

import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.pack.PackModel;

import java.util.Locale;

/** The four blasters, one per class, each a vanilla hoe that the pack draws as its {@link PackModel}. */
public enum Blaster implements HotbarItem {
    CARBINE(PackModel.STERNENFRONT_CARBINE),
    REPEATER(PackModel.STERNENFRONT_REPEATER),
    LONG_RIFLE(PackModel.STERNENFRONT_LONG_RIFLE),
    PISTOL(PackModel.STERNENFRONT_PISTOL);

    /**
     * {@code bolts} leave the muzzle {@code burstTicks} apart for one pull of the trigger; {@code speed} is in blocks
     * per tick, {@code spread} the largest sideways deviation per block, {@code sound} a sound key played at
     * {@code pitch}.
     */
    public record Stats(double damage, double headshot, double heat, int cooldownTicks, int bolts, int burstTicks,
                        double spread, double speed, double range, String sound, float pitch) {
    }

    private final PackModel model;

    Blaster(PackModel model) {
        this.model = model;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public PackModel model() {
        return model;
    }
}
