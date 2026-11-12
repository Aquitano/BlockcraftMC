package me.blockcraft.sternenfront.game;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.pack.PackModel;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;

/**
 * The blasters, one per class and one per gun hero, each a vanilla hoe that the pack draws as its {@link PackModel}.
 * The speeder's cannon is here only so its bolts and kill lines name it: nobody holds it, it borrows a model, and its
 * stats come from {@code speeders.cannon}.
 */
public enum Blaster implements HotbarItem {
    A280(PackModel.STERNENFRONT_A280, PackModel.STERNENFRONT_A280_FIRING, PackModel.STERNENFRONT_A280_LOWERED,
            SoundCue.BLASTER_CARBINE),
    E11D(PackModel.STERNENFRONT_E11D, PackModel.STERNENFRONT_E11D_FIRING, PackModel.STERNENFRONT_E11D_LOWERED,
            SoundCue.BLASTER_CARBINE),
    DC17M(PackModel.STERNENFRONT_DC17M, PackModel.STERNENFRONT_DC17M_FIRING, PackModel.STERNENFRONT_DC17M_LOWERED,
            SoundCue.BLASTER_CARBINE),
    CARBINE(PackModel.STERNENFRONT_CARBINE, PackModel.STERNENFRONT_CARBINE_FIRING, PackModel.STERNENFRONT_CARBINE_LOWERED,
            SoundCue.BLASTER_CARBINE),
    REPEATER(PackModel.STERNENFRONT_REPEATER, PackModel.STERNENFRONT_REPEATER_FIRING, PackModel.STERNENFRONT_REPEATER_LOWERED,
            SoundCue.BLASTER_REPEATER),
    LONG_RIFLE(PackModel.STERNENFRONT_LONG_RIFLE, PackModel.STERNENFRONT_LONG_RIFLE_FIRING, PackModel.STERNENFRONT_LONG_RIFLE_LOWERED,
            SoundCue.BLASTER_LONG_RIFLE),
    PISTOL(PackModel.STERNENFRONT_PISTOL, PackModel.STERNENFRONT_PISTOL_FIRING, PackModel.STERNENFRONT_PISTOL_LOWERED,
            SoundCue.BLASTER_PISTOL),
    TWIN_PISTOL(PackModel.STERNENFRONT_TWIN_PISTOL, PackModel.STERNENFRONT_TWIN_PISTOL_FIRING, PackModel.STERNENFRONT_TWIN_PISTOL_LOWERED,
            SoundCue.BLASTER_TWIN_PISTOL),
    HEAVY_RIFLE(PackModel.STERNENFRONT_HEAVY_RIFLE, PackModel.STERNENFRONT_HEAVY_RIFLE_FIRING, PackModel.STERNENFRONT_HEAVY_RIFLE_LOWERED,
            SoundCue.BLASTER_HEAVY_RIFLE),
    E5(PackModel.STERNENFRONT_E5, PackModel.STERNENFRONT_E5_FIRING, PackModel.STERNENFRONT_E5_LOWERED,
            SoundCue.BLASTER_CARBINE),
    WRIST_BLASTER(PackModel.STERNENFRONT_WRIST_BLASTER, PackModel.STERNENFRONT_WRIST_BLASTER_FIRING, PackModel.STERNENFRONT_WRIST_BLASTER_LOWERED,
            SoundCue.BLASTER_REPEATER),
    E5S(PackModel.STERNENFRONT_E5S, PackModel.STERNENFRONT_E5S_FIRING, PackModel.STERNENFRONT_E5S_LOWERED,
            SoundCue.BLASTER_LONG_RIFLE),
    SE14(PackModel.STERNENFRONT_SE14, PackModel.STERNENFRONT_SE14_FIRING, PackModel.STERNENFRONT_SE14_LOWERED,
            SoundCue.BLASTER_PISTOL),
    WESTAR_PISTOLS(PackModel.STERNENFRONT_WESTAR_PISTOLS, PackModel.STERNENFRONT_WESTAR_PISTOLS_FIRING, PackModel.STERNENFRONT_WESTAR_PISTOLS_LOWERED,
            SoundCue.BLASTER_TWIN_PISTOL),
    ARC_REPEATER(PackModel.STERNENFRONT_REPEATER, PackModel.STERNENFRONT_REPEATER_FIRING, PackModel.STERNENFRONT_REPEATER_LOWERED,
            SoundCue.BLASTER_REPEATER),
    COMMANDO_CARBINE(PackModel.STERNENFRONT_E5, PackModel.STERNENFRONT_E5_FIRING, PackModel.STERNENFRONT_E5_LOWERED,
            SoundCue.BLASTER_CARBINE),
    SPEEDER_CANNON(PackModel.STERNENFRONT_HEAVY_RIFLE, PackModel.STERNENFRONT_HEAVY_RIFLE_FIRING, PackModel.STERNENFRONT_HEAVY_RIFLE_LOWERED,
            SoundCue.BLASTER_SPEEDER_CANNON);

    /** Whether a held trigger keeps pulling, or every shot takes its own click. */
    public enum FireMode {
        AUTOMATIC, SINGLE;

        public static FireMode of(String name, FireMode fallback) {
            return name == null ? fallback : switch (name.toLowerCase(Locale.ROOT)) {
                case "automatic" -> AUTOMATIC;
                case "single" -> SINGLE;
                default -> throw new IllegalArgumentException("Unknown fire mode " + name + ", expected automatic or single");
            };
        }
    }

    /**
     * {@code bolts} leave the muzzle {@code burstTicks} apart for one pull of the trigger; {@code speed} is in blocks
     * per tick, {@code spread} the largest sideways deviation per block.
     */
    public record Stats(double damage, double headshot, double heat, int cooldownTicks, int bolts, int burstTicks,
                        double spread, double speed, double range, FireMode mode) {

        public Stats scaled(double damageScale, boolean headshots) {
            return new Stats(damage * damageScale, headshots ? headshot : 1, heat, cooldownTicks, bolts, burstTicks, spread, speed,
                    range, mode);
        }
    }

    /** How a held blaster is drawn: ready, kicked back by a shot, or lowered in a sprint. */
    public enum Pose { READY, FIRING, LOWERED }

    private static final NamespacedKey GRIP = new NamespacedKey("sternenfront", "grip");

    private final PackModel model;
    private final PackModel firingModel;
    private final PackModel loweredModel;
    private final SoundCue shotSound;

    Blaster(PackModel model, PackModel firingModel, PackModel loweredModel, SoundCue shotSound) {
        this.model = model;
        this.firingModel = firingModel;
        this.loweredModel = loweredModel;
        this.shotSound = shotSound;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public PackModel model() {
        return model;
    }

    public Blaster profile() {
        return switch (this) {
            case E5, COMMANDO_CARBINE, A280, E11D, DC17M -> CARBINE;
            case WRIST_BLASTER, ARC_REPEATER -> REPEATER;
            case E5S -> LONG_RIFLE;
            case SE14 -> PISTOL;
            case WESTAR_PISTOLS -> TWIN_PISTOL;
            default -> this;
        };
    }

    public boolean isLongRifle() {
        return profile() == LONG_RIFLE;
    }

    /** A rapid-firing heavy blaster, the Z-6 or one built on it, whose bolts land hard. */
    public boolean repeater() {
        return profile() == REPEATER;
    }

    public boolean dual() {
        return profile() == TWIN_PISTOL;
    }

    public PackModel model(Pose pose) {
        return switch (pose) {
            case READY -> model;
            case FIRING -> firingModel;
            case LOWERED -> loweredModel;
        };
    }

    public SoundCue shotSound() {
        return shotSound;
    }

    /**
     * Changes {@code item} without changing how it looks. A client breaks its trigger block anew when the held item
     * changes, and that start tells the server of a hold the client carried on unannounced.
     */
    public static void regrip(ItemStack item) {
        item.editPersistentDataContainer(data -> data.set(GRIP, PersistentDataType.BOOLEAN,
                !data.getOrDefault(GRIP, PersistentDataType.BOOLEAN, false)));
    }

    /** Draws {@code item} in {@code pose} with its vents in {@code glow}; false when it already looked so. */
    public boolean updateLook(ItemStack item, Pose pose, HeatGlow glow) {
        var key = model(pose).key();
        var current = item.getData(DataComponentTypes.CUSTOM_MODEL_DATA);
        if (current != null && current.strings().equals(List.of(key)) && current.colors().equals(List.of(glow.colour()))) {
            return false;
        }
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString(key).addColor(glow.colour()).build());
        return true;
    }
}
