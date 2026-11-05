package me.blockcraft.sternenfront.secret;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.common.message.Messages;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Tessar's secrets on a running Sternenfront server (EASTER_EGGS.md, "Sternenfront"): the kyber crystals, the droid
 * Brummi, the sand beast, the cantina's music box, the Kessel-Lauf and 4 May. They only watch the round through the
 * game's public state and Bukkit's events and change nothing in it, apart from 4 May's coin factor.
 */
public final class Secrets {

    private Secrets() {
    }

    public static void start(JavaPlugin plugin, SternenfrontGame game, Messages messages) {
        var config = SecretsConfig.from(plugin.getConfig());
        if (game.map().secrets().isEmpty()) {
            return;
        }
        var spots = game.map().secrets().get();
        var discoveries = new Discoveries(messages, config.coins());
        var times = new KesselTimes(BlockcraftApi.get().database());
        times.createTable();
        var crystals = new KyberCrystals(plugin, game, spots.crystals(), discoveries);
        var brummi = new Brummi(plugin, messages, discoveries, spots.droidPath(), config.brummiHits(), config.brummiHideSeconds());
        var beast = new SandBeast(game, messages, discoveries, spots.dune(), config);
        var musicBox = new MusicBox(game, messages, discoveries, spots.musicBox());
        var kessel = new KesselCourse(plugin, game, messages, discoveries, times, spots.course(), config);
        var events = Bukkit.getPluginManager();
        events.registerEvents(discoveries, plugin);
        events.registerEvents(brummi, plugin);
        events.registerEvents(musicBox, plugin);
        events.registerEvents(kessel, plugin);
        events.registerEvents(new MayTheFourth(plugin, messages, game.map(), config.mayTheFourthFactor()), plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long tick = Bukkit.getCurrentTick();
            crystals.tick(tick);
            brummi.tick(tick);
            beast.tick(tick);
            musicBox.tick(tick);
            kessel.tick(tick);
        }, 1, 1);
    }
}
