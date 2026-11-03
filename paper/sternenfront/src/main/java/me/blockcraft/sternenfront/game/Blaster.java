package me.blockcraft.sternenfront.game;

import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.pack.PackModel;

import java.util.Locale;

/** The blasters, one per class and one per gun hero, each a vanilla hoe that the pack draws as its {@link PackModel}. */
public enum Blaster implements HotbarItem {
    CARBINE(PackModel.STERNENFRONT_CARBINE),
    REPEATER(PackModel.STERNENFRONT_REPEATER),
    LONG_RIFLE(PackModel.STERNENFRONT_LONG_RIFLE),
    PISTOL(PackModel.STERNENFRONT_PISTOL),
    TWIN_PISTOL(PackModel.STERNENFRONT_TWIN_PISTOL),
    HEAVY_RIFLE(PackModel.STERNENFRONT_HEAVY_RIFLE);

    /**
     * {@code bolts} leave the muzzle {@code burstTicks} apart for one pull of the trigger; {@code speed} is in blocks
     * per tick, {@code spread} the largest sideways deviation per block.
     */
    public record Stats(double damage, double headshot, double heat, int cooldownTicks, int bolts, int burstTicks,
                        double spread, double speed, double range) {
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

    public PackModel firingModel() {
        return switch (this) {
            case CARBINE -> PackModel.STERNENFRONT_CARBINE_FIRING;
            case REPEATER -> PackModel.STERNENFRONT_REPEATER_FIRING;
            case LONG_RIFLE -> PackModel.STERNENFRONT_LONG_RIFLE_FIRING;
            case PISTOL -> PackModel.STERNENFRONT_PISTOL_FIRING;
            case TWIN_PISTOL -> PackModel.STERNENFRONT_TWIN_PISTOL_FIRING;
            case HEAVY_RIFLE -> PackModel.STERNENFRONT_HEAVY_RIFLE_FIRING;
        };
    }
}
