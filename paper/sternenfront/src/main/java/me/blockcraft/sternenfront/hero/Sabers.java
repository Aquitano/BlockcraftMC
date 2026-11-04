package me.blockcraft.sternenfront.hero;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.pack.PackModel;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.sternenfront.game.SoundCue;
import me.blockcraft.sternenfront.game.SternenfrontItem;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The lightsabers: a hilt that ignites a moment after it is drawn, a blade in the hero's colour (white for whoever
 * found every kyber crystal), the hum it makes while it is held, and the swing it makes when it strikes.
 */
public final class Sabers {

    public static final String WHITE_UNLOCK = "sternenfront.saber.white";

    private static final NamespacedKey DAMAGE = new NamespacedKey("sternenfront", "saber_damage");
    private static final NamespacedKey SPEED = new NamespacedKey("sternenfront", "saber_speed");
    private static final NamespacedKey LIT = new NamespacedKey("sternenfront", "saber_lit");
    private static final double PLAYER_BASE_DAMAGE = 1;
    private static final double SWORD_SPEED = -2.4;
    private static final int SABER_SLOT = 0;
    private static final float HUM_VOLUME = 0.5f;

    private final Plugin plugin;
    private final Messages messages;
    private final HeroConfig.Saber config;
    private final Set<UUID> white = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Ignition> ignitions = new HashMap<>();
    private final Map<UUID, Long> swings = new HashMap<>();

    private record Ignition(Hero hero, long at) { }

    public Sabers(Plugin plugin, Messages messages, HeroConfig.Saber config) {
        this.plugin = plugin;
        this.messages = messages;
        this.config = config;
    }

    /** Reads whether the player found every kyber crystal, off the main thread; the next blade they draw shows it. */
    public void refresh(Player player) {
        var id = player.getUniqueId();
        Tasks.async(plugin, () -> BlockcraftApi.get().unlocks().owns(id, WHITE_UNLOCK),
                owns -> {
                    if (owns) {
                        white.add(id);
                    } else {
                        white.remove(id);
                    }
                });
    }

    /** The hero's saber, its blade lit in their colour or still in the hilt. */
    public ItemStack item(Player owner, Hero hero, boolean lit) {
        if (!(hero.weapon() instanceof Hero.Weapon.Saber saber)) {
            throw new IllegalArgumentException(hero + " carries no lightsaber");
        }
        var model = lit ? saber.lit() : saber.off();
        var item = model.item().name(messages.get("hero." + hero.key() + ".weapon"))
                .lore(messages.getLines("hero.saber-lore")).hideAttributes().edit(meta -> meta.setUnbreakable(true)).build();
        if (lit) {
            tint(item, model, colour(owner, hero));
        }
        item.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.itemAttributes()
                .addModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(DAMAGE, (lit ? config.damage() : 0) - PLAYER_BASE_DAMAGE,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND))
                .addModifier(Attribute.ATTACK_SPEED, new AttributeModifier(SPEED, SWORD_SPEED,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND))
                .build());
        item.editMeta(meta -> meta.getPersistentDataContainer().set(LIT, PersistentDataType.BOOLEAN, lit));
        allowGuard(item);
        return SternenfrontItem.SABER.tag(item);
    }

    public static void allowGuard(ItemStack item) {
        item.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable()
                .consumeSeconds(3600).animation(ItemUseAnimation.BLOCK)
                .hasConsumeParticles(false).build());
    }

    /** The pack draws a tinted model's blade in the first colour of its custom_model_data. */
    public static void tint(ItemStack item, PackModel model, Color colour) {
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString(model.key()).addColor(colour).build());
    }

    public Color colour(Player owner, Hero hero) {
        if (hero == Hero.CHAMPION) {
            return config.relic();
        }
        if (white.contains(owner.getUniqueId())) {
            return config.white();
        }
        return hero == Hero.SITH ? config.sith() : config.jedi();
    }

    /** Hands the hilt over; it ignites {@code igniteTicks} later. */
    public void draw(Player player, Hero hero, long tick) {
        player.getInventory().setItem(SABER_SLOT, item(player, hero, false));
        ignitions.put(player.getUniqueId(), new Ignition(hero, tick + config.igniteTicks()));
    }

    /** Puts the hilt back into the hand without a blade, as while the saber flies. */
    public void unlit(Player player, Hero hero) {
        ignitions.remove(player.getUniqueId());
        player.getInventory().setItem(SABER_SLOT, item(player, hero, false));
    }

    public void relight(Player player, Hero hero) {
        player.getInventory().setItem(SABER_SLOT, item(player, hero, true));
        SoundCue.SABER_IGNITE.play(player.getLocation(), 1f);
    }

    /** Ignites what is due and hums for every saber hero who holds a lit blade. */
    public void tick(long tick, Map<Player, Hero> sabers) {
        ignitions.entrySet().removeIf(entry -> {
            if (tick < entry.getValue().at()) {
                return false;
            }
            var player = Bukkit.getPlayer(entry.getKey());
            if (player != null && sabers.get(player) == entry.getValue().hero() && isSaber(player.getInventory().getItem(SABER_SLOT))) {
                relight(player, entry.getValue().hero());
            }
            return true;
        });
        if (tick % config.humTicks() != 0) {
            return;
        }
        sabers.keySet().stream().filter(player -> isLit(player.getInventory().getItemInMainHand()))
                .forEach(player -> SoundCue.SABER_HUM.play(player.getLocation(), HUM_VOLUME));
    }

    public static boolean isSaber(ItemStack item) {
        return HotbarItem.of(item, SternenfrontItem.class).filter(SternenfrontItem.SABER::equals).isPresent();
    }

    public static boolean isLit(ItemStack item) {
        return isSaber(item) && Boolean.TRUE.equals(item.getPersistentDataContainer().get(LIT, PersistentDataType.BOOLEAN));
    }

    public void swing(Player player, long tick) {
        if (!isLit(player.getInventory().getItemInMainHand()) || tick < swings.getOrDefault(player.getUniqueId(), 0L)) {
            return;
        }
        swings.put(player.getUniqueId(), tick + 4);
        SoundCue.SABER_SWING.play(player.getLocation(), 0.8f);
    }

    public void forget(Player player) {
        ignitions.remove(player.getUniqueId());
        swings.remove(player.getUniqueId());
    }

    public void clear() {
        ignitions.clear();
        swings.clear();
    }
}
