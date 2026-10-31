package me.blockcraft.replay.recording;

import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.inventory.ItemStack;

import java.util.List;

final class VisibleEquipment {

    private static final List<DataComponentType.Valued<?>> COMPONENTS = List.of(
            DataComponentTypes.ITEM_MODEL, DataComponentTypes.CUSTOM_MODEL_DATA, DataComponentTypes.DYED_COLOR,
            DataComponentTypes.TRIM, DataComponentTypes.DAMAGE, DataComponentTypes.BANNER_PATTERNS,
            DataComponentTypes.BASE_COLOR);

    private VisibleEquipment() { }

    static byte[] serialize(ItemStack original) {
        if (original.getType().isAir()) { return new byte[0]; }
        var visible = ItemStack.of(original.getType(), original.getAmount());
        COMPONENTS.forEach(component -> copy(original, visible, component));
        var glint = original.getData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE);
        var enchantments = original.getData(DataComponentTypes.ENCHANTMENTS);
        if (glint != null) {
            visible.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, glint);
        } else if (enchantments != null && !enchantments.enchantments().isEmpty()) {
            visible.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        var profile = original.getData(DataComponentTypes.PROFILE);
        if (profile != null) {
            var appearance = ResolvableProfile.resolvableProfile().uuid(profile.uuid()).name(profile.name())
                    .skinPatch(profile.skinPatch());
            profile.properties().stream().filter(property -> property.getName().equals("textures"))
                    .forEach(appearance::addProperty);
            visible.setData(DataComponentTypes.PROFILE, appearance.build());
        }
        return visible.serializeAsBytes();
    }

    private static <T> void copy(ItemStack source, ItemStack target, DataComponentType.Valued<T> component) {
        var value = source.getData(component);
        if (value != null) { target.setData(component, value); }
    }
}
