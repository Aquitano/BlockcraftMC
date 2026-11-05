package me.blockcraft.api.presentation;

import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ScreenEffects {

    public enum Atmosphere {
        HOLOGRAM, STORM, FOCUS, PORTAL, HURT;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private enum Channel {
        FRAME("presentation/cinematic/"), ATMOSPHERE("presentation/atmosphere/");

        final String prefix;
        final Map<UUID, PresentationScope> owners = new HashMap<>();

        Channel(String prefix) { this.prefix = prefix; }
    }

    private ScreenEffects() { }

    public static void frame(Player player, double fade, boolean letterbox) {
        Channel.FRAME.owners.remove(player.getUniqueId());
        replace(player, Channel.FRAME.prefix, frameKey(fade, letterbox));
    }

    public static void frame(PresentationScope owner, Player player, double fade, boolean letterbox) {
        ownedReplace(owner, player, Channel.FRAME, frameKey(fade, letterbox));
    }

    public static void atmosphere(PresentationScope owner, Player player, Atmosphere atmosphere) {
        ownedReplace(owner, player, Channel.ATMOSPHERE, Key.key("blockcraft", "presentation/atmosphere/" + atmosphere.key()));
    }

    public static void atmosphere(Player player, Atmosphere atmosphere) {
        Channel.ATMOSPHERE.owners.remove(player.getUniqueId());
        replace(player, "presentation/atmosphere/", Key.key("blockcraft", "presentation/atmosphere/" + atmosphere.key()));
    }

    public static void clear(Player player) {
        for (var channel : Channel.values()) channel.owners.remove(player.getUniqueId());
        replace(player, "presentation/", null);
    }

    public static void accent(Player player, Atmosphere atmosphere, double intensity) {
        replace(player, "presentation/accent/", accentKey(atmosphere, intensity));
    }

    public static void clearAccent(Player player) {
        replace(player, "presentation/accent/", null);
    }

    public static void clearAtmosphere(Player player) {
        Channel.ATMOSPHERE.owners.remove(player.getUniqueId());
        replace(player, "presentation/atmosphere/", null);
    }

    private static void ownedReplace(PresentationScope owner, Player player, Channel channel, Key wanted) {
        if (owner.closed()) return;
        var id = player.getUniqueId();
        if (channel.owners.put(id, owner) != owner) {
            owner.own(() -> {
                if (channel.owners.remove(id, owner)) replace(player, channel.prefix, null);
            });
        }
        replace(player, channel.prefix, wanted);
    }

    static Key accentKey(Atmosphere atmosphere, double intensity) {
        int step = (int) Math.round((Double.isFinite(intensity) ? Math.clamp(intensity, 0, 1) : 0) * 8);
        return step == 0 ? null : Key.key("blockcraft", "presentation/accent/" + atmosphere.key() + "/" + step);
    }

    static Key frameKey(double fade, boolean letterbox) {
        int step = (int) Math.round((Double.isFinite(fade) ? Math.clamp(fade, 0, 1) : 0) * 32);
        return step == 0 && !letterbox ? null
                : Key.key("blockcraft", "presentation/cinematic/" + (letterbox ? "letterbox/" : "fade/") + step);
    }

    private static void replace(Player player, String prefix, Key wanted) {
        var current = player.postEffects().values();
        var next = new ArrayList<>(current);
        next.removeIf(key -> key.namespace().equals("blockcraft") && key.value().startsWith(prefix));
        if (wanted != null) next.add(wanted);
        if (!next.equals(current)) player.postEffects().set(next);
    }
}
