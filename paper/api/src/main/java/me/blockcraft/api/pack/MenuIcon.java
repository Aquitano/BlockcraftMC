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
 * wearing it, the vanilla item everyone else sees, decided in one place. An item built here carries its icon, so
 * {@link #style} can draw it right for its viewer wherever it ends up.
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
    TOOL_FRIENDS(Material.PLAYER_HEAD),
    TOOL_REQUESTS(Material.PAPER),
    TOOL_ADD_FRIEND(Material.WRITABLE_BOOK),
    PREMIUM(Material.GLISTERING_MELON_SLICE),
    QUICKSG(Material.IRON_SWORD),
    EMERALDRUSH(Material.EMERALD),
    DUELL(Material.SHIELD),
    BEDWARS(Material.RED_BED),
    MLGRUSH(Material.STICK),
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

    public Items.Builder item() {
        return mark(Items.of(fallback));
    }

    /** Marks an item built elsewhere, such as a player's own head, so the pack draws this icon over it. */
    public Items.Builder mark(Items.Builder item) {
        return item.hideAttributes()
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
