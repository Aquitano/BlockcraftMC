package me.blockcraft.signs.sign;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.GroupStatus;
import me.blockcraft.common.network.ServerState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;

import java.util.Locale;

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
        var shared = BlockcraftApi.get().messages();
        String nameKey = "games." + statusSign.group() + ".name";
        var name = shared.has(nameKey) ? shared.get(nameKey) : Component.text(statusSign.group());
        front.line(0, messages.get("sign.name", Placeholder.component("name", name)));
        front.line(1, shared.get("game-status." + status.availability().name().toLowerCase(Locale.ROOT)));
        front.line(2, messages.get("sign.players", Formatter.number("online", status.online()), Formatter.number("max", status.maxPlayers())));
        front.line(3, messages.get(status.joinable() ? "sign.click" : "sign.retry"));
        attachedBlock(sign).setType(status.state().map(state -> indicator(state, status.joinable())).orElse(Material.RED_TERRACOTTA));
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
