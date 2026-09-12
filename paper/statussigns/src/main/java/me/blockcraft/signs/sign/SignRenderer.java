package me.blockcraft.signs.sign;

import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;


public final class SignRenderer {

    private final Messages messages;

    public SignRenderer(Messages messages) {
        this.messages = messages;
    }

    public void render(StatusSign statusSign, ServerStatus status) {
        if (!(statusSign.location().getBlock().getState() instanceof Sign sign)) {
            return;
        }
        var front = sign.getSide(Side.FRONT);
        switch (status) {
            case ServerStatus.Online online -> {
                front.line(0, messages.get("sign.name", Placeholder.unparsed("name", statusSign.name())));
                front.line(1, messages.get(statusKey(online.motd()), Placeholder.unparsed("motd", online.motd())));
                front.line(2, messages.get("sign.players",
                        Formatter.number("online", online.online()), Formatter.number("max", online.max())));
                front.line(3, messages.get("sign.click"));
                attachedBlock(sign).setType(indicator(online.motd()));
            }
            case ServerStatus.Offline _ -> {
                front.line(0, messages.get("sign.offline.border"));
                front.line(1, messages.get("sign.offline.status"));
                front.line(2, messages.get("sign.offline.players"));
                front.line(3, messages.get("sign.offline.border"));
                attachedBlock(sign).setType(Material.RED_TERRACOTTA);
            }
        }
        sign.update();
    }

    private static String statusKey(String motd) {
        return switch (motd.toUpperCase()) {
            case "LOBBY" -> "sign.status.lobby";
            case "INGAME" -> "sign.status.ingame";
            case "RESTART" -> "sign.status.restart";
            default -> "sign.status.other";
        };
    }

    private static Material indicator(String motd) {
        return switch (motd.toUpperCase()) {
            case "INGAME" -> Material.RED_TERRACOTTA;
            case "RESTART" -> Material.YELLOW_TERRACOTTA;
            default -> Material.LIME_TERRACOTTA;
        };
    }

    private static Block attachedBlock(Sign sign) {
        BlockFace attachedFace = sign.getBlockData() instanceof WallSign wallSign
                ? wallSign.getFacing().getOppositeFace()
                : BlockFace.DOWN;
        return sign.getBlock().getRelative(attachedFace);
    }
}
