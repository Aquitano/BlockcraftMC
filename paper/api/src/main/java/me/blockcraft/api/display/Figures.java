package me.blockcraft.api.display;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.util.function.Consumer;

/** Player-shaped figures: they stand still, players walk through them, they cannot be hurt and are never saved into the map. */
public final class Figures {

    private static final Vector3f ABOVE_HEAD = new Vector3f(0, 0.3f, 0);

    private Figures() {
    }

    public static Mannequin spawn(Location location, ResolvableProfile skin, Component name) {
        return spawn(location, skin, name, unnamed -> { });
    }

    /**
     * A figure with its name on a card riding on its head; a vanilla name tag would show through walls. The card is
     * hidden by default when the figure is. Remove it with {@link #remove}, which takes the card along.
     */
    public static Mannequin spawn(Location location, ResolvableProfile skin, Component name, Consumer<Mannequin> extra) {
        var figure = spawn(location, skin, extra);
        var card = Cards.line(location, name);
        card.setPersistent(false);
        card.setVisibleByDefault(figure.isVisibleByDefault());
        var pose = card.getTransformation();
        card.setTransformation(new Transformation(ABOVE_HEAD, pose.getLeftRotation(), pose.getScale(), pose.getRightRotation()));
        figure.addPassenger(card);
        return figure;
    }

    public static Mannequin spawn(Location location, ResolvableProfile skin, Consumer<Mannequin> extra) {
        return location.getWorld().spawn(location, Mannequin.class, figure -> {
            figure.setProfile(skin);
            figure.setDescription(null);
            figure.setImmovable(true);
            figure.setCollidable(false);
            figure.setGravity(false);
            figure.setInvulnerable(true);
            figure.setSilent(true);
            figure.setPersistent(false);
            figure.setRemoveWhenFarAway(false);
            extra.accept(figure);
        });
    }

    public static void remove(Entity figure) {
        figure.getPassengers().forEach(Entity::remove);
        figure.remove();
    }
}
