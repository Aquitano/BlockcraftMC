package me.blockcraft.sternenfront.game;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.pack.MenuState;
import me.blockcraft.api.pack.PackModel;
import me.blockcraft.common.message.Messages;
import me.blockcraft.sternenfront.config.SternenfrontConfig;
import me.blockcraft.sternenfront.hero.Sabers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What a deployed player carries: the class's blaster in the first slot, the faction's
 * helmet and leather in the faction's colour. Armour gives no protection, so a class's health alone decides how many
 * bolts it takes.
 */
public final class Loadouts {

    /** Heroes have far more health than a row of hearts, and more rows would cover the action bar. */
    private static final double HEARTS_SHOWN = 20;

    private static final NamespacedKey BLADE_DAMAGE = new NamespacedKey("sternenfront", "blade_damage");
    private static final NamespacedKey BLADE_SPEED = new NamespacedKey("sternenfront", "blade_speed");
    private static final NamespacedKey CLASS_SPEED = new NamespacedKey("sternenfront", "class_speed");
    private static final NamespacedKey CLASS_SCALE = new NamespacedKey("sternenfront", "class_scale");
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
        inventory.setItem(0, blaster(combatClass.blaster(faction)));
        combatClass.abilities(faction).forEach(ability -> player.setCooldown(ability.material(), 0));
        dress(player, faction, Optional.empty());
        var body = config.stats(faction, combatClass);
        fit(player, body.speed(), body.scale());
        health(player, body.health());
    }

    /** The champion's hotbar: the blade alone, with the helmet and leather they deployed in. */
    public void arm(Player champion) {
        var inventory = champion.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            inventory.setItem(slot, null);
        }
        inventory.setItem(0, blade());
        inventory.setHeldItemSlot(0);
        health(champion, config.relic().health());
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

    /** An ability as the class menu's read-out shows it, with the key it sits on. */
    public ItemStack abilityCard(Faction faction, Ability ability, int index) {
        var lore = new ArrayList<>(messages.getLines(ability.messageKey(faction) + ".lore"));
        lore.add(Component.empty());
        lore.add(messages.get(index == 0 ? "class.first-key" : "class.second-key"));
        return ability.model().item().name(messages.get(ability.messageKey(faction) + ".name")).lore(lore)
                .hideAttributes().build();
    }

    public ItemStack blade() {
        var item = PackModel.STERNENFRONT_RELIC_BLADE.item().name(messages.get("relic.name"))
                .lore(messages.getLines("relic.lore")).hideAttributes().edit(meta -> meta.setUnbreakable(true)).build();
        Sabers.allowGuard(item);
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

    /** Deployment has no separate equipment row, so its cards include the class's numbers. */
    public ItemStack card(Faction faction, CombatClass combatClass, boolean selected, List<Component> hint) {
        var lore = new ArrayList<>(messages.getLines(combatClass.messageKey(faction) + ".lore"));
        lore.add(Component.empty());
        lore.addAll(statLines(faction, combatClass));
        combatClass.abilities(faction).forEach(ability -> lore.add(messages.get("class.ability",
                Placeholder.component("ability", messages.get(ability.messageKey(faction) + ".name")))));
        lore.add(Component.empty());
        lore.addAll(hint);
        return classCard(faction, combatClass, selected, lore);
    }

    public ItemStack roleCard(Faction faction, CombatClass combatClass, boolean selected, List<Component> hint) {
        var lore = new ArrayList<>(messages.getLines(combatClass.messageKey(faction) + ".lore"));
        lore.add(Component.empty());
        lore.addAll(hint);
        return classCard(faction, combatClass, selected, lore);
    }

    private ItemStack classCard(Faction faction, CombatClass combatClass, boolean selected, List<Component> lore) {
        var builder = combatClass.blaster(faction).model().item().name(messages.get(combatClass.messageKey(faction) + ".name"))
                .lore(lore).hideAttributes();
        if (selected) {
            builder.glow().state(MenuState.ACTIVE);
        }
        return builder.build();
    }

    /** The class's numbers as the class menu's read-out shows them. */
    public ItemStack statsCard(Faction faction, CombatClass combatClass) {
        return MenuIcon.GLOBAL_STATS.item().name(messages.get("class.stats")).lore(statLines(faction, combatClass)).build();
    }

    private List<Component> statLines(Faction faction, CombatClass combatClass) {
        var body = config.stats(faction, combatClass);
        var lines = new ArrayList<>(blasterStats(combatClass.blaster(faction)));
        lines.add(messages.get("class.health", Formatter.number("hearts", body.health() / TO_HEARTS)));
        if (body.speed() != 1) {
            lines.add(messages.get("class.speed", Formatter.number("percent", Math.round(body.speed() * 100))));
        }
        if (body.scale() != 1) {
            lines.add(messages.get("class.size", Formatter.number("percent", Math.round(body.scale() * 100))));
        }
        return lines;
    }

    public List<Component> blasterStats(Blaster blaster) {
        var stats = config.stats(blaster);
        return List.of(messages.get("class.blaster",
                        Placeholder.component("blaster", messages.get("blaster." + blaster.key() + ".name")),
                        Formatter.number("damage", stats.damage() * stats.bolts() / TO_HEARTS)),
                messages.get("class.range", Formatter.number("range", Math.round(stats.range()))));
    }

    /** The faction's leather, and on the head {@code head} if a hero wears their own, else the faction's helmet. */
    public void dress(Player player, Faction faction, Optional<PackModel> head) {
        var inventory = player.getInventory();
        var helmet = head.orElse(faction == Faction.KOLONIEN ? PackModel.STERNENFRONT_HELMET_KOLONIEN : PackModel.STERNENFRONT_HELMET_HEGEMONIE);
        inventory.setHelmet(helmet.item().name(messages.get(head.isPresent() ? "hero.head" : "faction." + faction.key() + ".helmet")).build());
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

    /** Transient, so a player who quits mid-round takes neither their class's size nor its pace along in their saved data. */
    public static void fit(LivingEntity body, double speed, double scale) {
        natural(body);
        AttributeScale.apply(body, Attribute.MOVEMENT_SPEED, CLASS_SPEED, speed - 1);
        AttributeScale.apply(body, Attribute.SCALE, CLASS_SCALE, scale - 1);
    }

    /** Whatever {@link #fit} did, undone: the size and pace a hero, a champion and anyone between rounds has. */
    public static void natural(LivingEntity body) {
        AttributeScale.remove(body, Attribute.MOVEMENT_SPEED, CLASS_SPEED);
        AttributeScale.remove(body, Attribute.SCALE, CLASS_SCALE);
    }

    public void health(Player player, double health) {
        var maxHealth = Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH));
        maxHealth.setBaseValue(health);
        player.setHealth(health);
        player.setHealthScale(HEARTS_SHOWN);
    }
}
