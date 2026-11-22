package me.blockcraft.api.kit;

import me.blockcraft.api.config.BundledDefaults;
import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A game's kits with their prices from config.yml's {@code kits.<key>}; a kit priced at 0 everyone owns. */
public final class KitCatalog<K extends Enum<K> & UnlockableKit> {

    private final Class<K> type;
    private final Map<K, Integer> prices;

    private KitCatalog(Class<K> type, Map<K, Integer> prices) {
        this.type = type;
        this.prices = prices;
    }

    /** {@code kits} prices every kit of {@code type} by its key. */
    public static <K extends Enum<K> & UnlockableKit> KitCatalog<K> from(ConfigurationSection kits, Class<K> type) {
        var prices = new EnumMap<K, Integer>(type);
        var config = BundledDefaults.strict(kits);
        for (var kit : type.getEnumConstants()) {
            prices.put(kit, Math.max(0, config.integer(kit.key())));
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
