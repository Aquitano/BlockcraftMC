package me.blockcraft.story.game;

import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.tracker.EntityTracker;
import me.blockcraft.story.dialogue.DialogueHud;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * An NPC who is aware of the party between cutscenes: she turns her head and then her body towards the nearest member
 * close by, back to her post when nobody is, and gestures while a member's conversation with her is speaking.
 */
final class NpcPresence {

    private static final double NOTICE = 8;
    private static final float TURN_PER_TICK = 9;
    private static final String TALK = "talk";

    private final LivingEntity npc;
    private final Location post;
    private final Supplier<List<Player>> members;
    private final DialogueHud dialogue;
    private Optional<EntityTracker> body = Optional.empty();
    private boolean held;
    private boolean talking;

    NpcPresence(LivingEntity npc, Location post, Supplier<List<Player>> members, DialogueHud dialogue) {
        this.npc = npc;
        this.post = post;
        this.members = members;
        this.dialogue = dialogue;
    }

    void dress(String model) {
        body = Bodies.dress(npc, model);
    }

    /** A cutscene moves her while it plays, so she leaves her bearing to it. */
    void hold(boolean held) {
        this.held = held;
        if (held) talk(false);
    }

    void tick() {
        if (held || !npc.isValid()) return;
        var near = members.get().stream().filter(member -> member.getWorld().equals(npc.getWorld()))
                .filter(member -> member.getLocation().distanceSquared(npc.getLocation()) < NOTICE * NOTICE)
                .min(Comparator.comparingDouble(member -> member.getLocation().distanceSquared(npc.getLocation())));
        var target = near.map(Player::getEyeLocation).orElseGet(() -> npc.getEyeLocation().add(post.getDirection()));
        var towards = target.toVector().subtract(npc.getEyeLocation().toVector());
        var aim = new Location(npc.getWorld(), 0, 0, 0).setDirection(towards.lengthSquared() > 0 ? towards : new Vector(1, 0, 0));
        float yaw = approach(npc.getYaw(), aim.getYaw());
        float pitch = approach(npc.getPitch(), near.isPresent() ? aim.getPitch() : post.getPitch());
        npc.setRotation(yaw, pitch);
        talk(near.filter(dialogue::speaking).isPresent());
    }

    private void talk(boolean now) {
        if (now == talking) return;
        talking = now;
        body.ifPresent(tracker -> {
            if (now) {
                tracker.animate(TALK, AnimationModifier.DEFAULT);
            } else {
                tracker.stopAnimation(TALK);
            }
        });
    }

    private static float approach(float from, float to) {
        float turn = (float) Math.IEEEremainder(to - from, 360);
        return from + Math.clamp(turn, -TURN_PER_TICK, TURN_PER_TICK);
    }
}
