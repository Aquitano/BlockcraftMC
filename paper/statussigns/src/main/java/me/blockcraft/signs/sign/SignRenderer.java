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

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public final class SignRenderer {

    private final Messages messages;

    public SignRenderer(Messages messages) {
        this.messages = messages;
    }

    public void render(StatusSign statusSign, GroupStatus status) {
        if (!statusSign.location().isChunkLoaded()) return;
        if (!(statusSign.location().getBlock().getState() instanceof Sign sign)) {
            return;
        }
        var front = sign.getSide(Side.FRONT);
        var shared = BlockcraftApi.get().messages();
        String nameKey = "games." + statusSign.group() + ".name";
        var name = shared.has(nameKey) ? shared.get(nameKey) : readable(statusSign.group());
        String joinKey = status.online() == 0 ? "sign.first" : "sign.click";
        var lines = java.util.List.of(
                messages.get("sign.name", Placeholder.component("name", name)),
                shared.get("game-status." + status.availability().name().toLowerCase(Locale.ROOT)),
                messages.get("sign.players", Formatter.number("online", status.online()), Formatter.number("max", status.maxPlayers())),
                messages.get(status.joinable() ? joinKey : "sign.retry"));
        boolean changed = false;
        for (int index = 0; index < lines.size(); index++) {
            if (!front.line(index).equals(lines.get(index))) {
                front.line(index, lines.get(index));
                changed = true;
            }
        }
        var block = attachedBlock(sign);
        var material = status.state().map(state -> indicator(state, status.joinable())).orElse(Material.RED_TERRACOTTA);
        if (block.getLocation().isChunkLoaded() && block.getType() != material) {
            block.setType(material);
        }
        if (changed) sign.update();
    }

    /** The name of a group the shared messages lack, from its id: {@code build-battle} becomes "Build Battle". */
    private static Component readable(String group) {
        return Component.text(Arrays.stream(group.split("[-_]+"))
                .filter(word -> !word.isEmpty())
                .map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1))
                .collect(Collectors.joining(" ")));
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
