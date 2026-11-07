package me.blockcraft.sternenfront.game;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.pack.PackModel;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * The blasters, one per class and one per gun hero, each a vanilla hoe that the pack draws as its {@link PackModel}.
 * The speeder's cannon is here only so its bolts and kill lines name it: nobody holds it, it borrows a model, and its
 * stats come from {@code speeders.cannon}.
 */
public enum Blaster implements HotbarItem {
    CARBINE(PackModel.STERNENFRONT_CARBINE),
    REPEATER(PackModel.STERNENFRONT_REPEATER),
    LONG_RIFLE(PackModel.STERNENFRONT_LONG_RIFLE),
    PISTOL(PackModel.STERNENFRONT_PISTOL),
    TWIN_PISTOL(PackModel.STERNENFRONT_TWIN_PISTOL),
    HEAVY_RIFLE(PackModel.STERNENFRONT_HEAVY_RIFLE),
    E5(PackModel.STERNENFRONT_E5),
    WRIST_BLASTER(PackModel.STERNENFRONT_WRIST_BLASTER),
    E5S(PackModel.STERNENFRONT_E5S),
    SE14(PackModel.STERNENFRONT_SE14),
    WESTAR_PISTOLS(PackModel.STERNENFRONT_WESTAR_PISTOLS),
    ARC_REPEATER(PackModel.STERNENFRONT_REPEATER),
    COMMANDO_CARBINE(PackModel.STERNENFRONT_E5),
    SPEEDER_CANNON(PackModel.STERNENFRONT_HEAVY_RIFLE);

    /**
     * {@code bolts} leave the muzzle {@code burstTicks} apart for one pull of the trigger; {@code speed} is in blocks
     * per tick, {@code spread} the largest sideways deviation per block.
     */
    public record Stats(double damage, double headshot, double heat, int cooldownTicks, int bolts, int burstTicks,
                        double spread, double speed, double range) {
    }

    /** How a held blaster is drawn: ready, or kicked back by a shot. */
    public enum Pose { READY, FIRING }

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

    public Blaster profile() {
        return switch (this) {
            case E5, COMMANDO_CARBINE -> CARBINE;
            case WRIST_BLASTER, ARC_REPEATER -> REPEATER;
            case E5S -> LONG_RIFLE;
            case SE14 -> PISTOL;
            case WESTAR_PISTOLS -> TWIN_PISTOL;
            default -> this;
        };
    }

    public boolean precision() {
        return profile() == LONG_RIFLE;
    }

    public boolean dual() {
        return profile() == TWIN_PISTOL;
    }

    public PackModel model(Pose pose) {
        return switch (pose) {
            case READY -> model;
            case FIRING -> firingModel();
        };
    }

    /** Draws {@code item} in {@code pose} with its vents in {@code glow}; false when it already looked so. */
    public boolean show(ItemStack item, Pose pose, HeatGlow glow) {
        var key = model(pose).key();
        var current = item.getData(DataComponentTypes.CUSTOM_MODEL_DATA);
        if (current != null && current.strings().equals(List.of(key)) && current.colors().equals(List.of(glow.colour()))) {
            return false;
        }
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString(key).addColor(glow.colour()).build());
        return true;
    }

    private PackModel firingModel() {
        return switch (this) {
            case CARBINE -> PackModel.STERNENFRONT_CARBINE_FIRING;
            case REPEATER, ARC_REPEATER -> PackModel.STERNENFRONT_REPEATER_FIRING;
            case LONG_RIFLE -> PackModel.STERNENFRONT_LONG_RIFLE_FIRING;
            case PISTOL -> PackModel.STERNENFRONT_PISTOL_FIRING;
            case TWIN_PISTOL -> PackModel.STERNENFRONT_TWIN_PISTOL_FIRING;
            case HEAVY_RIFLE, SPEEDER_CANNON -> PackModel.STERNENFRONT_HEAVY_RIFLE_FIRING;
            case E5, COMMANDO_CARBINE -> PackModel.STERNENFRONT_E5_FIRING;
            case WRIST_BLASTER -> PackModel.STERNENFRONT_WRIST_BLASTER_FIRING;
            case E5S -> PackModel.STERNENFRONT_E5S_FIRING;
            case SE14 -> PackModel.STERNENFRONT_SE14_FIRING;
            case WESTAR_PISTOLS -> PackModel.STERNENFRONT_WESTAR_PISTOLS_FIRING;
        };
    }
}
