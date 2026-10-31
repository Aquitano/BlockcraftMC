package me.blockcraft.bedwars.shop;

import me.blockcraft.api.item.Items;
import me.blockcraft.bedwars.item.SpecialItem;
import me.blockcraft.bedwars.perk.StickSkin;
import me.blockcraft.bedwars.resource.Resource;
import me.blockcraft.bedwars.team.TeamColor;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.Map;
import java.util.Optional;

/**
 * One thing the shop sells and what it costs. The item is built for its buyer, whose team may colour it and whose
 * skin the stick wears. {@code shiftTimes} is how often a shift-click buys it at most.
 */
public record Offer(String key, Material material, int amount, Resource currency, int price, int shiftTimes,
                    Map<Enchantment, Integer> enchantments, Optional<PotionType> potion, int damage,
                    Coloring coloring, Optional<SpecialItem> special, boolean stick) {

    /** How the buyer's team colours the item: not at all, as its stained glass, or as the dye of leather armour. */
    public enum Coloring {
        NONE,
        GLASS,
        DYE
    }

    public Offer {
        enchantments = Map.copyOf(enchantments);
    }

    public ItemStack create(Messages messages, Optional<TeamColor> team, StickSkin skin) {
        var type = coloring == Coloring.GLASS ? team.map(TeamColor::glass).orElse(material) : material;
        var item = Items.of(type, amount).name(messages.get("shop.items." + key)).edit(meta -> {
            enchantments.forEach((enchantment, level) -> meta.addEnchant(enchantment, level, true));
            if (coloring == Coloring.DYE && meta instanceof LeatherArmorMeta leather) {
                team.ifPresent(color -> leather.setColor(color.dye().getColor()));
            }
            if (meta instanceof PotionMeta potionMeta) {
                potion.ifPresent(potionMeta::setBasePotionType);
            }
            if (damage > 0 && meta instanceof Damageable damageable) {
                damageable.setDamage(damage);
            }
        }).build();
        special.ifPresent(tag -> tag.tag(item));
        return stick ? skin.skin(StickSkin.tag(item)) : item;
    }

    /** The item as the shop shows it: what the buyer would get, with the price below. */
    ItemStack display(Messages messages, Optional<TeamColor> team, StickSkin skin) {
        return Items.of(create(messages, team, skin)).lore(Component.empty(), currency.amount(messages, price)).build();
    }
}
