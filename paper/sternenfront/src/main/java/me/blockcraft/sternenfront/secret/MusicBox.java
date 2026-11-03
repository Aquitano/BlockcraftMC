package me.blockcraft.sternenfront.secret;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.common.message.Messages;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * The cantina's music box. The round answers no click on a block, so the box listens for the click itself: a deployed
 * player who right-clicks it starts the cantina tune for everyone near, and the first time finds the secret.
 */
final class MusicBox implements Listener {

    private static final double HEARD = 24;
    private static final int UNSTARTED = -1;

    private final SternenfrontGame game;
    private final Messages messages;
    private final Discoveries discoveries;
    private final Block box;
    private final Location speaker;
    private long startedAt = UNSTARTED;
    private int next;

    MusicBox(SternenfrontGame game, Messages messages, Discoveries discoveries, Block box) {
        this.game = game;
        this.messages = messages;
        this.discoveries = discoveries;
        this.box = box;
        this.speaker = box.getLocation().add(0.5, 0.8, 0.5);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || !box.equals(event.getClickedBlock()) || !game.onField(event.getPlayer())) {
            return;
        }
        var player = event.getPlayer();
        if (startedAt != UNSTARTED) {
            BlockcraftApi.get().notifications().feedback(player, messages.get("secret.music-box-busy"));
            return;
        }
        startedAt = player.getServer().getCurrentTick();
        next = 0;
        BlockcraftApi.get().notifications().feedback(player, messages.get("secret.music-box"));
        discoveries.find(player, Secret.CANTINA);
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
            var sound = note.instrument().sound();
            melody |= note.instrument() == CantinaTune.Instrument.BIT;
            for (var listener : speaker.getWorld().getPlayers()) {
                if (listener.getLocation().distanceSquared(speaker) <= HEARD * HEARD) {
                    listener.playSound(speaker, sound, SoundCategory.MUSIC, note.volume(), note.pitch());
                }
            }
        }
        if (melody) {
            speaker.getWorld().spawnParticle(Particle.NOTE, speaker.clone().add(0, 0.6, 0), 1, 0.3, 0.2, 0.3, 1);
        }
        if (next >= notes.size()) {
            startedAt = UNSTARTED;
        }
    }
}
