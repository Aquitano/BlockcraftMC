package me.blockcraft.sternenfront.game;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import net.kyori.adventure.key.Key;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Objects;

/**
 * A long rifle's scope while it aims. The client draws {@code (movement speed / walking speed + 1) / 2} of its field
 * of view, scaled by its FOV effects, and reads the walking speed for nothing else. So the scope tells the client a
 * negative walking speed that leaves {@code zoom} of the view, and puts back the movement speed that Paper's
 * {@code setWalkSpeed} also sets. The helmet carries the reticle as its camera overlay, which only its wearer sees.
 * Nothing else gives a player a negative walk speed, so it marks a raised scope.
 */
final class RifleScope {

    private static final Key RETICLE = Key.key("blockcraft", "misc/long_rifle_scope");
    private static final float NORMAL_WALK = 0.2f;
    private static final float CHANGE = 0.001f;

    private RifleScope() {
    }

    static void raise(Player player, double zoom, boolean reticle) {
        var speed = movement(player);
        float walk = (float) Math.clamp(2 * speed.getValue() / (2 * zoom - 1), -1, -CHANGE);
        if (Math.abs(player.getWalkSpeed() - walk) > CHANGE) {
            tell(player, speed, walk);
        }
        wear(player, reticle);
    }

    static void lower(Player player) {
        if (player.getWalkSpeed() < 0) {
            tell(player, movement(player), NORMAL_WALK);
        }
        wear(player, false);
    }

    private static void tell(Player player, AttributeInstance speed, float walk) {
        double base = speed.getBaseValue();
        player.setWalkSpeed(walk);
        speed.setBaseValue(base);
    }

    private static void wear(Player player, boolean reticle) {
        var helmet = player.getInventory().getHelmet();
        if (helmet == null || helmet.isEmpty()) {
            return;
        }
        var equippable = helmet.getData(DataComponentTypes.EQUIPPABLE);
        if (reticle == (equippable != null && RETICLE.equals(equippable.cameraOverlay()))) {
            return;
        }
        if (reticle) {
            helmet.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(EquipmentSlot.HEAD).cameraOverlay(RETICLE).build());
        } else {
            helmet.resetData(DataComponentTypes.EQUIPPABLE);
        }
        player.getInventory().setHelmet(helmet);
    }

    private static AttributeInstance movement(Player player) {
        return Objects.requireNonNull(player.getAttribute(Attribute.MOVEMENT_SPEED));
    }
}
