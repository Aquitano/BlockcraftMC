package me.blockcraft.sternenfront.appearance;

import me.blockcraft.api.animation.ActorActions;
import me.blockcraft.api.animation.AnimationClip;
import me.blockcraft.sternenfront.game.CombatClass;
import me.blockcraft.sternenfront.game.Faction;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;

public final class CinematicActor implements AutoCloseable {
    private final List<ItemDisplay> displays = new ArrayList<>();
    private final ActorActions actions;

    public CinematicActor(Plugin plugin, Player viewer, Location at, Faction faction, AnimationClip clip) {
        try {
            var body = RigBody.of(faction, CombatClass.SOLDIER);
            var joints = body.joints();
            var bones = new LinkedHashMap<String, Consumer<AnimationClip.Pose>>();
            bones.put("torso", part(plugin, viewer, at, body, "torso", 0, joints.torsoY()));
            bones.put("head", part(plugin, viewer, at, body, "head", 0, joints.headY()));
            bones.put("left_arm", part(plugin, viewer, at, body, "arm", joints.shoulderX(), joints.shoulderY()));
            bones.put("right_arm", part(plugin, viewer, at, body, "arm", -joints.shoulderX(), joints.shoulderY()));
            bones.put("left_leg", part(plugin, viewer, at, body, "leg", -joints.hipX(), joints.hipY()));
            bones.put("right_leg", part(plugin, viewer, at, body, "leg", joints.hipX(), joints.hipY()));
            actions = new ActorActions(bones);
            if (!actions.play(clip, 0)) {
                close();
                throw new IllegalArgumentException("Animation references bones absent from the infantry rig");
            }
        } catch (RuntimeException failure) {
            close();
            throw failure;
        }
    }

    private Consumer<AnimationClip.Pose> part(Plugin plugin, Player viewer, Location at, RigBody body, String name, float x, float y) {
        var origin = at.clone();
        float yaw = (float) Math.toRadians(-origin.getYaw());
        origin.setYaw(0);
        origin.setPitch(0);
        var display = Displays.item(origin, Displays.model("sternenfront/body/" + body.family() + "/" + name), entity -> {
            entity.setVisibleByDefault(false);
            entity.setInterpolationDuration(1);
            entity.setDisplayWidth(3);
            entity.setDisplayHeight(3);
            entity.setBrightness(Displays.LIT);
        });
        viewer.showEntity(plugin, display);
        displays.add(display);
        return pose -> {
            var position = pose.position();
            var rotation = pose.rotation();
            var scale = pose.scale();
            var facing = new Quaternionf().rotationY(yaw);
            var offset = facing.transform(new Vector3f(x + (float) position.x() / 16, y + (float) position.y() / 16,
                    (float) position.z() / 16));
            facing.rotateXYZ((float) Math.toRadians(rotation.x()), (float) Math.toRadians(rotation.y()), (float) Math.toRadians(rotation.z()));
            display.setInterpolationDelay(0);
            display.setTransformation(new Transformation(offset, facing,
                    new Vector3f((float) scale.x(), (float) scale.y(), (float) scale.z()), new Quaternionf().rotationY((float) Math.PI)));
        };
    }

    public void tick(int tick) {
        actions.tick(tick / 20.0);
    }

    @Override
    public void close() {
        displays.forEach(ItemDisplay::remove);
    }
}
