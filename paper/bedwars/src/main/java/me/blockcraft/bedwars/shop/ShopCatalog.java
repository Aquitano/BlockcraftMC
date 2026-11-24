package me.blockcraft.bedwars.shop;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.blockcraft.bedwars.item.SpecialItem;
import me.blockcraft.bedwars.resource.Resource;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionType;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The shop's categories and offers, read from the {@code shop} list of config.yml. Every entry of a category's
 * {@code offers} is an offer, or {@code spacer} for an empty slot between them.
 */
public record ShopCatalog(List<Category> categories) {

    private static final String SPACER = "spacer";

    /** A tab of the shop; an empty offer is a gap in its row. */
    public record Category(String key, Material icon, List<Optional<Offer>> offers) {
    }

    public static ShopCatalog parse(List<Map<?, ?>> section) {
        return new ShopCatalog(section.stream().map(ShopCatalog::category).toList());
    }

    /**
     * The shop's own item for a block players placed and broke again, so it stacks with what they bought; empty
     * for blocks the shop does not sell as they are.
     */
    public Optional<Offer> plainBlock(Material material) {
        return categories.stream()
                .flatMap(category -> category.offers().stream().flatMap(Optional::stream))
                .filter(offer -> offer.material() == material && offer.coloring() == Offer.Coloring.NONE
                        && offer.special().isEmpty() && !offer.stick())
                .findFirst();
    }

    private static Category category(Map<?, ?> values) {
        String key = text(values, "category");
        var offers = values.get("offers") instanceof List<?> list ? list : List.of();
        return new Category(key, material(text(values, "icon")), offers.stream().map(entry -> {
            if (SPACER.equals(entry)) {
                return Optional.<Offer>empty();
            }
            if (entry instanceof Map<?, ?> offer) {
                return Optional.of(offer(offer));
            }
            throw new IllegalArgumentException("Shop category " + key + " has an entry that is neither an offer nor a spacer: " + entry);
        }).toList());
    }

    private static Offer offer(Map<?, ?> values) {
        String[] price = text(values, "price").trim().split("\\s+");
        if (price.length != 2) {
            throw new IllegalArgumentException("A price reads like '3 iron', not '" + values.get("price") + "'");
        }
        var currency = Resource.byKey(price[1]).orElseThrow(() -> new IllegalArgumentException("Unknown resource " + price[1]));
        var enchantments = new HashMap<Enchantment, Integer>();
        if (values.get("enchantments") instanceof Map<?, ?> listed) {
            var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
            listed.forEach((name, level) -> enchantments.put(registry.getOrThrow(Key.key(String.valueOf(name))),
                    ((Number) level).intValue()));
        }
        return new Offer(
                text(values, "item"),
                material(text(values, "material")),
                number(values, "amount", 1),
                currency,
                Integer.parseInt(price[0]),
                number(values, "shift", 1),
                enchantments,
                Optional.ofNullable(values.get("potion")).map(potion -> PotionType.valueOf(upper(potion))),
                number(values, "damage", 0),
                Optional.ofNullable(values.get("color")).map(color -> Offer.Coloring.valueOf(upper(color))).orElse(Offer.Coloring.NONE),
                Optional.ofNullable(values.get("special")).map(special -> SpecialItem.byKey(String.valueOf(special))
                        .orElseThrow(() -> new IllegalArgumentException("Unknown special item " + special))),
                Boolean.TRUE.equals(values.get("stick")));
    }

    private static String text(Map<?, ?> values, String key) {
        if (values.get(key) == null) {
            throw new IllegalArgumentException("Shop entry " + values + " misses '" + key + "'");
        }
        return String.valueOf(values.get(key));
    }

    private static int number(Map<?, ?> values, String key, int fallback) {
        return values.get(key) instanceof Number value ? value.intValue() : fallback;
    }

    private static Material material(String name) {
        var material = Material.matchMaterial(name);
        if (material == null || !material.isItem()) {
            throw new IllegalArgumentException("Unknown item " + name);
        }
        return material;
    }

    private static String upper(Object value) {
        return String.valueOf(value).toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
