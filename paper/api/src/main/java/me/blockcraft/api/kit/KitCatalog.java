package me.blockcraft.api.kit;

import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/** A game's kits with their prices from config.yml's {@code kits.<key>}; a kit priced at 0 everyone owns. */
public final class KitCatalog<K extends Enum<K> & UnlockableKit> {

    private final Class<K> type;
    private final Map<K, Integer> prices;

    private KitCatalog(Class<K> type, Map<K, Integer> prices) {
        this.type = type;
        this.prices = prices;
    }

    /** A kit the file leaves out costs {@code defaultPrice}. */
    public static <K extends Enum<K> & UnlockableKit> KitCatalog<K> from(ConfigurationSection kits, Class<K> type,
                                                                        ToIntFunction<K> defaultPrice) {
        var prices = new EnumMap<K, Integer>(type);
        for (var kit : type.getEnumConstants()) {
            prices.put(kit, Math.max(0, kits.getInt(kit.key(), defaultPrice.applyAsInt(kit))));
        }
        return new KitCatalog<>(type, prices);
    }

    public List<K> kits() {
        return List.of(type.getEnumConstants());
    }

    public int price(K kit) {
        return prices.get(kit);
    }

    public boolean free(K kit) {
        return price(kit) == 0;
    }

    /** The free kits and those whose unlock is among {@code unlocked}. */
    public Set<K> owned(Set<String> unlocked) {
        var owned = EnumSet.noneOf(type);
        kits().stream().filter(kit -> free(kit) || unlocked.contains(kit.item())).forEach(owned::add);
        return owned;
    }
}
