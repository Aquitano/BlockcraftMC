package me.blockcraft.api.game.rush;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.common.settings.PlayerSetting;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Optional;

/** The hotbar slot each piece of the MLG Rush kit takes, the player's own order being their {@link PlayerSetting#MLGRUSH_LAYOUT}. */
public record RushLayout(int stick, int blocks, int pickaxe) {

    public enum Item { STICK, BLOCKS, PICKAXE }

    public static final int SLOTS = 9;
    public static final RushLayout DEFAULT = new RushLayout(0, 1, 2);

    public RushLayout {
        if (Arrays.stream(new int[] {stick, blocks, pickaxe}).anyMatch(slot -> slot < 0 || slot >= SLOTS)
                || stick == blocks || blocks == pickaxe || stick == pickaxe) {
            throw new IllegalArgumentException("A rush layout needs three different hotbar slots, not " + stick + "," + blocks + "," + pickaxe);
        }
    }

    public static RushLayout of(Player player) {
        return parse(BlockcraftApi.get().settings().get(player, PlayerSetting.MLGRUSH_LAYOUT));
    }

    static RushLayout parse(Optional<String> stored) {
        var parts = stored.map(value -> value.split(",")).filter(split -> split.length == 3);
        if (parts.isEmpty()) {
            return DEFAULT;
        }
        try {
            return new RushLayout(Integer.parseInt(parts.get()[0]), Integer.parseInt(parts.get()[1]), Integer.parseInt(parts.get()[2]));
        } catch (IllegalArgumentException invalid) {
            return DEFAULT;
        }
    }

    public void save(Player player) {
        BlockcraftApi.get().settings().set(player, PlayerSetting.MLGRUSH_LAYOUT, serialize());
    }

    String serialize() {
        return stick + "," + blocks + "," + pickaxe;
    }

    public int slot(Item item) {
        return switch (item) {
            case STICK -> stick;
            case BLOCKS -> blocks;
            case PICKAXE -> pickaxe;
        };
    }

    public Optional<Item> at(int slot) {
        return Arrays.stream(Item.values()).filter(item -> slot(item) == slot).findFirst();
    }

    /** Puts {@code item} into {@code slot}; whatever sat there takes the item's old slot. */
    public RushLayout move(Item item, int slot) {
        int[] slots = {stick, blocks, pickaxe};
        int from = slots[item.ordinal()];
        at(slot).ifPresent(displaced -> slots[displaced.ordinal()] = from);
        slots[item.ordinal()] = slot;
        return new RushLayout(slots[0], slots[1], slots[2]);
    }
}
