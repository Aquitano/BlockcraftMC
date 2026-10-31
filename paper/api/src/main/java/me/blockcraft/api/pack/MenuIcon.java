package me.blockcraft.api.pack;

import me.blockcraft.api.item.Items;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The pictures a menu or a hotbar can show on an item: the pack's {@code items/<key>.json} model for clients
 * wearing it, the vanilla item everyone else sees, decided in one place. An item built here carries its icon twice:
 * as custom_model_data, which the pack's override of the vanilla item answers, so a held hotbar item or a display
 * shows right to every viewer; and as a tag, so {@link #style} can also put the model on directly in a menu, which
 * only its one viewer sees.
 */
public enum MenuIcon {

    BACK(Material.ARROW),
    CLOSE(Material.BARRIER),
    PREVIOUS(Material.ARROW),
    NEXT(Material.SPECTRAL_ARROW),
    PAGE(Material.PAPER),
    REFRESH(Material.SUNFLOWER),
    LOADING(Material.CLOCK),
    EMPTY_LIST(Material.COBWEB),
    TOOL_CLOSE(Material.BARRIER),
    TOOL_SHOP(Material.GOLD_INGOT),
    TOOL_HOME(Material.ENDER_EYE),
    TOOL_SETTINGS(Material.COMPARATOR),
    TOOL_COSMETICS(Material.ENDER_CHEST),
    TOOL_PROFILE(Material.PLAYER_HEAD),
    TOOL_HEADS(Material.PLAYER_HEAD),
    TOOL_SHIRTS(Material.LEATHER_CHESTPLATE),
    TOOL_HATS(Material.GOLDEN_HELMET),
    TOOL_FRIENDS(Material.PLAYER_HEAD),
    TOOL_REQUESTS(Material.PAPER),
    TOOL_ADD_FRIEND(Material.WRITABLE_BOOK),
    PREMIUM(Material.GLISTERING_MELON_SLICE),
    QUICKSG(Material.IRON_SWORD),
    EMERALDRUSH(Material.EMERALD),
    DUELL(Material.SHIELD),
    BEDWARS(Material.RED_BED),
    MLGRUSH(Material.STICK),
    ZOMBIES(Material.ZOMBIE_HEAD),
    CORES(Material.BEACON),
    TEAM(Material.WHITE_BANNER),
    ENDERGAMES(Material.ENDER_PEARL),
    TTT(Material.REDSTONE),
    TTT_SHOP(Material.EMERALD),
    SKYWARS(Material.FEATHER),
    ARCHIVE(Material.CLOCK),
    ARCHIVE_ENTRANCE(Material.COMPASS),
    ARCHIVE_POINT(Material.ENDER_PEARL),
    ARCHIVE_NOTE(Material.PAPER),
    GADGETS(Material.FISHING_ROD),
    KILL_EFFECTS(Material.IRON_SWORD),
    WIN_EFFECTS(Material.FIREWORK_ROCKET),
    TRAILS(Material.SNOWBALL),
    CHAT_TAGS(Material.NAME_TAG),
    LOBBIES(Material.NETHER_STAR),
    ADVENT(Material.CHEST),
    RECAP(Material.WRITABLE_BOOK),
    ACHIEVEMENTS(Material.NETHER_STAR),
    PREMIUM_AREA(Material.FIREWORK_ROCKET),
    PETS(Material.PIG_SPAWN_EGG),
    GLOBAL_STATS(Material.PLAYER_HEAD),
    ENCHANTING(Material.ENCHANTING_TABLE),
    VOTE(Material.PAPER),
    JUMP(Material.DIAMOND_BOOTS),
    START(Material.DIAMOND),
    STAFF_RANDOM(Material.COMPASS),
    STAFF_FREEZE(Material.PACKED_ICE),
    STAFF_INVSEE(Material.CHEST),
    STAFF_REPORTS(Material.BOOK),
    STAFF_INFO(Material.SPYGLASS),
    STAFF_SPECTATE(Material.ENDER_EYE),
    SPECTATE_PLAYERS(Material.COMPASS),
    SPECTATE_PREVIOUS(Material.ARROW),
    SPECTATE_NEXT(Material.SPECTRAL_ARROW);

    /** Draws nothing at all; the outer slots of a toolbar button wear it so the whole bar answers hover and click. */
    public static final Key EMPTY_MODEL = Key.key("blockcraft", "empty");

    /** The dark tooltip behind every menu item's name, {@code gui/sprites/tooltip/menu_*} in the pack. */
    public static final Key TOOLTIP_STYLE = Key.key("blockcraft", "menu");

    private static final NamespacedKey MARKER = NamespacedKey.fromString("blockcraft:menu_icon");

    private final Material fallback;

    MenuIcon(Material fallback) {
        this.fallback = fallback;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Key model() {
        return Key.key("blockcraft", key());
    }

    public static Optional<MenuIcon> byKey(String key) {
        return Arrays.stream(values()).filter(icon -> icon.key().equals(key)).findFirst();
    }

    public Material fallback() {
        return fallback;
    }

    /** The custom_model_data the pack's override of the {@code fallback} item selects this icon by. */
    public String modelData() {
        return "blockcraft:icon/" + key();
    }

    public Items.Builder item() {
        return mark(Items.of(fallback));
    }

    /**
     * Marks an item built elsewhere, such as a player's own head, so the pack draws this icon over it. Outside a
     * menu that only works on the {@code fallback} material, whose override knows the icon.
     */
    public Items.Builder mark(Items.Builder item) {
        return item.hideAttributes().modelData(modelData())
                .edit(meta -> meta.getPersistentDataContainer().set(MARKER, PersistentDataType.STRING, key()));
    }

    public static Optional<Key> modelOf(ItemStack item) {
        if (!item.hasItemMeta()) {
            return Optional.empty();
        }
        return Optional.ofNullable(item.getItemMeta().getPersistentDataContainer().get(MARKER, PersistentDataType.STRING))
                .map(key -> Key.key("blockcraft", key));
    }

    /**
     * Puts the pack's model on an item that carries an icon, or takes it off again, as the viewer's client needs;
     * false when the item carries none.
     */
    public static boolean style(ItemStack item, boolean packApplied) {
        var model = modelOf(item);
        if (model.isEmpty()) {
            return false;
        }
        if (packApplied) {
            Items.of(item).model(model.get());
        } else {
            Items.of(item).noModel();
        }
        return true;
    }
}
