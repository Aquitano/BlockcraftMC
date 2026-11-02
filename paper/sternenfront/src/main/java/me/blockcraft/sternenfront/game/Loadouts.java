package me.blockcraft.sternenfront.game;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.pack.PackModel;
import me.blockcraft.common.message.Messages;
import me.blockcraft.sternenfront.config.SternenfrontConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What a deployed player carries: the class's blaster in the first slot, its abilities next to it, the faction's
 * helmet and leather in the faction's colour. Armour gives no protection, so a class's health alone decides how many
 * bolts it takes.
 */
public final class Loadouts {

    private static final NamespacedKey BLADE_DAMAGE = new NamespacedKey("sternenfront", "blade_damage");
    private static final NamespacedKey BLADE_SPEED = new NamespacedKey("sternenfront", "blade_speed");
    private static final double PLAYER_BASE_DAMAGE = 1;
    private static final double SWORD_SPEED = -2.4;
    private static final double TO_HEARTS = 2;

    private final Messages messages;
    private final SternenfrontConfig config;

    public Loadouts(Messages messages, SternenfrontConfig config) {
        this.messages = messages;
        this.config = config;
    }

    public void equip(Player player, Faction faction, CombatClass combatClass) {
        var inventory = player.getInventory();
        inventory.clear();
        inventory.setItem(0, blaster(combatClass.blaster()));
        for (int index = 0; index < combatClass.abilities().size(); index++) {
            var ability = combatClass.abilities().get(index);
            inventory.setItem(index + 1, ability(ability, index));
            player.setCooldown(ability.material(), 0);
        }
        dress(player, faction);
        setMaxHealth(player, config.health(combatClass));
    }

    /** The champion's hotbar: the blade alone, with the helmet and leather they deployed in. */
    public void arm(Player champion) {
        var inventory = champion.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            inventory.setItem(slot, null);
        }
        inventory.setItem(0, blade());
        inventory.setHeldItemSlot(0);
        setMaxHealth(champion, config.relic().health());
    }

    public ItemStack blaster(Blaster blaster) {
        var stats = config.stats(blaster);
        var lore = new ArrayList<>(messages.getLines("blaster." + blaster.key() + ".lore"));
        lore.add(Component.empty());
        lore.addAll(messages.getLines("blaster.stats",
                Formatter.number("damage", stats.damage() * stats.bolts() / TO_HEARTS),
                Formatter.number("range", Math.round(stats.range()))));
        return blaster.build(blaster.model().item().name(messages.get("blaster." + blaster.key() + ".name")).lore(lore)
                .hideAttributes().edit(meta -> meta.setUnbreakable(true)));
    }

    public ItemStack ability(Ability ability, int index) {
        var lore = new ArrayList<>(messages.getLines("ability." + ability.key() + ".lore"));
        lore.add(Component.empty());
        lore.add(messages.get(index == 0 ? "ability.first-key" : "ability.second-key"));
        return ability.build(ability.model().item().name(messages.get("ability." + ability.key() + ".name")).lore(lore)
                .hideAttributes());
    }

    public ItemStack blade() {
        var item = PackModel.STERNENFRONT_RELIC_BLADE.item().name(messages.get("relic.name"))
                .lore(messages.getLines("relic.lore")).hideAttributes().edit(meta -> meta.setUnbreakable(true)).build();
        item.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.itemAttributes()
                .addModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(BLADE_DAMAGE,
                        config.relic().damage() - PLAYER_BASE_DAMAGE, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND))
                .addModifier(Attribute.ATTACK_SPEED, new AttributeModifier(BLADE_SPEED,
                        SWORD_SPEED, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND))
                .build());
        return SternenfrontItem.BLADE.tag(item);
    }

    /** The item that reopens the deploy menu while a dead player chooses. */
    public ItemStack deployItem() {
        return SternenfrontItem.DEPLOY.build(MenuIcon.DEPLOY.item().name(messages.get("deploy.item")));
    }

    /** A class as the menus show it: its blaster, named after the class, with what it carries. */
    public ItemStack card(CombatClass combatClass, boolean selected, List<Component> hint) {
        var stats = config.stats(combatClass.blaster());
        var lore = new ArrayList<>(messages.getLines("class." + combatClass.key() + ".lore"));
        lore.add(Component.empty());
        lore.add(messages.get("class.blaster",
                Placeholder.component("blaster", messages.get("blaster." + combatClass.blaster().key() + ".name")),
                Formatter.number("damage", stats.damage() * stats.bolts() / TO_HEARTS)));
        lore.add(messages.get("class.health", Formatter.number("hearts", config.health(combatClass) / TO_HEARTS)));
        combatClass.abilities().forEach(ability -> lore.add(messages.get("class.ability",
                Placeholder.component("ability", messages.get("ability." + ability.key() + ".name")))));
        lore.add(Component.empty());
        lore.addAll(hint);
        var builder = combatClass.blaster().model().item().name(messages.get("class." + combatClass.key() + ".name"))
                .lore(lore).hideAttributes();
        if (selected) {
            builder.glow();
        }
        return builder.build();
    }

    private void dress(Player player, Faction faction) {
        var inventory = player.getInventory();
        var helmet = faction == Faction.KOLONIEN ? PackModel.STERNENFRONT_HELMET_KOLONIEN : PackModel.STERNENFRONT_HELMET_HEGEMONIE;
        inventory.setHelmet(helmet.item().name(messages.get("faction." + faction.key() + ".helmet")).build());
        inventory.setChestplate(armour(Material.LEATHER_CHESTPLATE, faction));
        inventory.setLeggings(armour(Material.LEATHER_LEGGINGS, faction));
        inventory.setBoots(armour(Material.LEATHER_BOOTS, faction));
    }

    /** The leather a lobby player wears once they picked a faction, so the others see whom they fight for. */
    public void dressForLobby(Player player, Faction faction) {
        player.getInventory().setChestplate(armour(Material.LEATHER_CHESTPLATE, faction));
    }

    private static ItemStack armour(Material material, Faction faction) {
        var item = Items.of(material).color(faction.armour()).hideAttributes().edit(meta -> meta.setUnbreakable(true)).build();
        item.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.itemAttributes().build());
        return item;
    }

    private static void setMaxHealth(Player player, double health) {
        var maxHealth = Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH));
        maxHealth.setBaseValue(health);
        player.setHealth(health);
    }
}
