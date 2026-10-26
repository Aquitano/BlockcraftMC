package me.blockcraft.api.item;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class Items {

    private Items() {
    }

    public static Builder of(Material material) {
        return new Builder(ItemStack.of(material));
    }

    public static Builder of(Material material, int amount) {
        return new Builder(ItemStack.of(material, amount));
    }

    /** Edits the given stack in place. */
    public static Builder of(ItemStack stack) {
        return new Builder(stack);
    }

    public static Builder head(UUID owner, String name) {
        return head(Bukkit.createProfile(owner, name));
    }

    /** Skin resolved by name only, for players that may never have joined. */
    public static Builder head(String name) {
        return head(Bukkit.createProfile(name));
    }

    private static Builder head(PlayerProfile profile) {
        var builder = of(Material.PLAYER_HEAD);
        builder.stack.editMeta(SkullMeta.class, meta -> meta.setPlayerProfile(profile));
        return builder;
    }

    /** Strips the italic that Minecraft applies to custom names and lore by default. */
    public static Component plain(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static final class Builder {

        private final ItemStack stack;

        private Builder(ItemStack stack) {
            this.stack = stack;
        }

        public Builder name(Component name) {
            stack.editMeta(meta -> meta.displayName(plain(name)));
            return this;
        }

        public Builder lore(Component... lines) {
            return lore(Arrays.asList(lines));
        }

        public Builder lore(List<Component> lines) {
            stack.editMeta(meta -> meta.lore(lines.stream().map(Items::plain).toList()));
            return this;
        }

        public Builder glow() {
            stack.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            return this;
        }

        public Builder color(Color color) {
            stack.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(color));
            return this;
        }

        /**
         * Asks the pack for the model {@code key} names. A client without the pack, or a pack without that model,
         * draws the item as its material, so this is safe on anything other players see.
         */
        public Builder modelData(String key) {
            stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString(key).build());
            return this;
        }

        /**
         * Draws the item with a model from the resource pack, whatever the material. Only for an item no client
         * without the pack ever sees, such as one in a menu: anywhere else it is a missing model to them.
         */
        public Builder model(Key model) {
            stack.setData(DataComponentTypes.ITEM_MODEL, model);
            return this;
        }

        /** Draws the item as its material again. */
        public Builder noModel() {
            stack.resetData(DataComponentTypes.ITEM_MODEL);
            return this;
        }

        /** The sprites behind the item's tooltip, {@code tooltip/<style>_background} and {@code _frame} of the pack. */
        public Builder tooltipStyle(Key style) {
            stack.setData(DataComponentTypes.TOOLTIP_STYLE, style);
            return this;
        }

        public Builder hideAttributes() {
            stack.editMeta(meta -> meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS));
            return this;
        }

        public Builder edit(Consumer<ItemMeta> edit) {
            stack.editMeta(edit);
            return this;
        }

        public ItemStack build() {
            return stack;
        }
    }
}
