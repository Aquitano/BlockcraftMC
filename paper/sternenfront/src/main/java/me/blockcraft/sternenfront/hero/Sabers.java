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
import me.blockcraft.sternenfront.game.SoundCue;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import me.blockcraft.sternenfront.game.SternenfrontItem;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The lightsabers: a hilt that ignites a moment after it is drawn, a blade in the hero's colour (white for whoever
 * found every kyber crystal), and the hum that follows it while it is held.
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

    private final SternenfrontGame game;
    private final HeroConfig.Saber config;
    private final Set<UUID> white = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Ignition> ignitions = new HashMap<>();

    /** The blade shows its first length at {@code from} and one more each tick until it is whole. */
    record Ignition(Hero hero, long from) {

        static Ignition drawn(Hero hero, long tick, int igniteTicks, int stages) {
            return new Ignition(hero, tick + Math.max(0, igniteTicks - (stages - 2)));
        }

        int stage(long tick) {
            return (int) (tick - from) + 1;
        }
    }

    public Sabers(SternenfrontGame game, HeroConfig.Saber config) {
        this.game = game;
        this.config = config;
    }

    /** Reads whether the player found every kyber crystal, off the main thread; the next blade they draw shows it. */
    public void refresh(Player player) {
        var id = player.getUniqueId();
        Tasks.async(game.plugin(), () -> BlockcraftApi.get().unlocks().owns(id, WHITE_UNLOCK),
                owns -> {
                    if (owns) {
                        white.add(id);
                    } else {
                        white.remove(id);
                    }
                });
    }

    private static Hero.Weapon.Saber saber(Hero hero) {
        if (!(hero.weapon() instanceof Hero.Weapon.Saber saber)) {
            throw new IllegalArgumentException(hero + " carries no lightsaber");
        }
        return saber;
    }

    /** The hero's saber, its blade lit in their colour or still in the hilt. */
    public ItemStack item(Player owner, Hero hero, boolean lit) {
        return itemAtStage(owner, hero, lit ? saber(hero).stages().size() - 1 : 0);
    }

    /**
     * The saber at {@code stage} of its ignition; only the last, the whole blade, cuts and blocks. The server refuses a
     * guard with anything less, and a hilt the client raised anyway would show the block pose until that refusal arrives.
     */
    private ItemStack itemAtStage(Player owner, Hero hero, int stage) {
        var stages = saber(hero).stages();
        var model = stages.get(stage);
        boolean lit = stage == stages.size() - 1;
        var item = model.item().name(game.messages().get("hero." + hero.key() + ".weapon"))
                .lore(game.messages().getLines("hero.saber-lore")).hideAttributes().edit(meta -> meta.setUnbreakable(true)).build();
        if (model.tinted()) {
            tint(item, model, colour(owner, hero));
        }
        item.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.itemAttributes()
                .addModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(DAMAGE, (lit ? config.damage() : 0) - PLAYER_BASE_DAMAGE,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND))
                .addModifier(Attribute.ATTACK_SPEED, new AttributeModifier(SPEED, SWORD_SPEED,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND))
                .build());
        item.editMeta(meta -> meta.getPersistentDataContainer().set(LIT, PersistentDataType.BOOLEAN, lit));
        if (lit) {
            makeBlockable(item);
        }
        return SternenfrontItem.SABER.tag(item);
    }

    public static void makeBlockable(ItemStack item) {
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

    /** Hands the hilt over; the blade grows over the last ticks before it is whole {@code igniteTicks} later. */
    public void draw(Player player, Hero hero, long tick) {
        player.getInventory().setItem(SABER_SLOT, item(player, hero, false));
        ignitions.put(player.getUniqueId(), Ignition.drawn(hero, tick, config.igniteTicks(), saber(hero).stages().size()));
    }

    /** Puts the hilt back into the hand without a blade, as while the saber flies. */
    public void unlit(Player player, Hero hero) {
        ignitions.remove(player.getUniqueId());
        player.getInventory().setItem(SABER_SLOT, item(player, hero, false));
        SoundCue.SABER_RETRACT.playOn(player, 1f, 1f, game::drawnAs);
    }

    public void relight(Player player, Hero hero) {
        player.getInventory().setItem(SABER_SLOT, item(player, hero, true));
        SoundCue.SABER_IGNITE.playOn(player, 1f, 1f, game::drawnAs);
    }

    /** Grows each igniting blade by a stage and hums for every saber hero who holds a lit blade. */
    public void tick(long tick, Map<Player, Hero> sabers) {
        ignitions.entrySet().removeIf(entry -> {
            var ignition = entry.getValue();
            if (tick < ignition.from()) {
                return false;
            }
            var player = Bukkit.getPlayer(entry.getKey());
            if (player == null || sabers.get(player) != ignition.hero() || !isSaber(player.getInventory().getItem(SABER_SLOT))) {
                return true;
            }
            int last = saber(ignition.hero()).stages().size() - 1;
            int stage = Math.min(ignition.stage(tick), last);
            if (stage == 1) {
                SoundCue.SABER_IGNITE.playOn(player, 1f, 1f, game::drawnAs);
                game.effects().ignite(player, colour(player, ignition.hero()));
            }
            player.getInventory().setItem(SABER_SLOT, itemAtStage(player, ignition.hero(), stage));
            return stage == last;
        });
        if (tick % config.humTicks() != 0) {
            return;
        }
        sabers.keySet().stream().filter(player -> isLit(player.getInventory().getItemInMainHand()))
                .forEach(player -> SoundCue.SABER_HUM.playOn(player, HUM_VOLUME, 1f, game::drawnAs));
    }

    public static boolean isSaber(ItemStack item) {
        return HotbarItem.of(item, SternenfrontItem.class).filter(SternenfrontItem.SABER::equals).isPresent();
    }

    public static boolean isLit(ItemStack item) {
        return isSaber(item) && Boolean.TRUE.equals(item.getPersistentDataContainer().get(LIT, PersistentDataType.BOOLEAN));
    }

    public void forget(Player player) {
        ignitions.remove(player.getUniqueId());
    }

    public void clear() {
        ignitions.clear();
    }
}
