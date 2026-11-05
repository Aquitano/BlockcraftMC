package me.blockcraft.sternenfront.secret;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.common.message.Messages;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

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
        var events = Bukkit.getPluginManager();
        events.registerEvents(new MayTheFourth(plugin, messages, game.map(), config.mayTheFourthFactor()), plugin);
        if (game.map().secrets().isEmpty()) {
            return;
        }
        var spots = game.map().secrets().get();
        var discoveries = new Discoveries(messages, config.coins());
        var crystals = new KyberCrystals(plugin, game, spots.crystals(), discoveries);
        var brummi = spots.droidPath().isEmpty() ? Optional.<Brummi>empty()
                : Optional.of(new Brummi(plugin, messages, discoveries, spots.droidPath(), config.brummiHits(), config.brummiHideSeconds()));
        var beast = spots.dune().map(dune -> new SandBeast(game, messages, discoveries, dune, config));
        var musicBox = spots.musicBox().map(at -> new MusicBox(game, messages, discoveries, at.getBlock()));
        var kessel = spots.course().map(course -> {
            var times = new KesselTimes(BlockcraftApi.get().database());
            times.createTable();
            return new KesselCourse(plugin, game, messages, discoveries, times, course, config);
        });
        events.registerEvents(discoveries, plugin);
        brummi.ifPresent(listener -> events.registerEvents(listener, plugin));
        musicBox.ifPresent(listener -> events.registerEvents(listener, plugin));
        kessel.ifPresent(listener -> events.registerEvents(listener, plugin));
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long tick = Bukkit.getCurrentTick();
            crystals.tick(tick);
            brummi.ifPresent(secret -> secret.tick(tick));
            beast.ifPresent(secret -> secret.tick(tick));
            musicBox.ifPresent(secret -> secret.tick(tick));
            kessel.ifPresent(secret -> secret.tick(tick));
        }, 1, 1);
    }
}
