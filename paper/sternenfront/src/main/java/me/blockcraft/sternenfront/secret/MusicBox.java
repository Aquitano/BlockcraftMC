package me.blockcraft.sternenfront.secret;

import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

final class MusicBox {

    private static final double HEARD = 24;
    private static final int UNSTARTED = -1;

    private final Predicate<Player> deployed;
    private final Messages messages;
    private final BiConsumer<Player, Secret> discover;
    private final BiConsumer<Player, Component> feedback;
    private final Location block;
    private final Location speaker;
    private final Secret secret;
    private long startedAt = UNSTARTED;
    private int next;

    MusicBox(Predicate<Player> deployed, Messages messages, BiConsumer<Player, Secret> discover,
             BiConsumer<Player, Component> feedback, Location block, Secret secret) {
        this.deployed = deployed;
        this.messages = messages;
        this.discover = discover;
        this.feedback = feedback;
        this.block = block;
        this.speaker = block.clone().add(0.5, 0.8, 0.5);
        this.secret = secret;
    }

    boolean use(Player player, Block clicked, long tick) {
        if (!block.equals(clicked.getLocation())) {
            return false;
        }
        if (!deployed.test(player) || player.isInsideVehicle()) {
            return true;
        }
        var key = secret == Secret.RECEIVER ? "receiver" : "music-box";
        if (startedAt != UNSTARTED) {
            feedback.accept(player, messages.get("secret." + key + "-busy"));
            return true;
        }
        startedAt = tick;
        next = 0;
        if (secret == Secret.RECEIVER) {
            player.sendMessage(messages.get("secret." + key));
        } else {
            feedback.accept(player, messages.get("secret." + key));
        }
        discover.accept(player, secret);
        return true;
    }

    void tick(long tick) {
        if (startedAt == UNSTARTED) {
            return;
        }
        long played = tick - startedAt;
        var notes = CantinaTune.NOTES;
        boolean melody = false;
        while (next < notes.size() && notes.get(next).tick() <= played) {
            var note = notes.get(next++);
            melody |= note.instrument() == CantinaTune.Instrument.BIT;
            for (var listener : speaker.getWorld().getPlayers()) {
                if (listener.getLocation().distanceSquared(speaker) <= HEARD * HEARD) {
                    listener.playSound(speaker, note.instrument().sound(), SoundCategory.MUSIC, note.volume(), note.pitch());
                }
            }
        }
        if (melody) {
            speaker.getWorld().spawnParticle(Particle.NOTE, speaker.clone().add(0, 0.6, 0), 1, 0.3, 0.2, 0.3, 1);
        }
        if (next >= notes.size()) {
            stop();
        }
    }

    void stop() {
        startedAt = UNSTARTED;
        next = 0;
    }
}
