package me.blockcraft.signs.sign;

import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.ServerState;
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

    public void render(StatusSign statusSign, GroupStatus status) {
        if (!(statusSign.location().getBlock().getState() instanceof Sign sign)) {
            return;
        }
        var front = sign.getSide(Side.FRONT);
        status.state().ifPresentOrElse(state -> {
            front.line(0, messages.get("sign.name", Placeholder.unparsed("name", statusSign.group())));
            front.line(1, messages.get("sign.state." + state.name().toLowerCase()));
            front.line(2, messages.get("sign.players",
                    Formatter.number("online", status.online()), Formatter.number("max", status.maxPlayers())));
            front.line(3, messages.get("sign.click"));
            attachedBlock(sign).setType(indicator(state, status.joinable()));
        }, () -> {
            front.line(0, messages.get("sign.offline.border"));
            front.line(1, messages.get("sign.offline.status"));
            front.line(2, messages.get("sign.offline.players"));
            front.line(3, messages.get("sign.offline.border"));
            attachedBlock(sign).setType(Material.RED_TERRACOTTA);
        });
        sign.update();
    }

    private static Material indicator(ServerState state, boolean joinable) {
        if (joinable) {
            return Material.LIME_TERRACOTTA;
        }
        return switch (state) {
            case LOBBY, INGAME -> Material.RED_TERRACOTTA;
            case RESTART -> Material.YELLOW_TERRACOTTA;
        };
    }

    private static Block attachedBlock(Sign sign) {
        BlockFace attachedFace = sign.getBlockData() instanceof WallSign wallSign
                ? wallSign.getFacing().getOppositeFace()
                : BlockFace.DOWN;
        return sign.getBlock().getRelative(attachedFace);
    }
}
