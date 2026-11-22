package me.blockcraft.zombies.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.shop.UnlockRewards;
import me.blockcraft.zombies.arena.ArenaMap;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The outpost's story, one note per room, read in chat under its title. The first read of each note is a secret: an
 * unlock that pays coins.
 */
public final class Notes {

    private static final Title.Times TITLE_TIMES = Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(500));
    private final Messages messages;
    private final Supplier<ArenaMap> arena;
    private final int coins;
    private final UnlockRewards rewards;
    private final Map<UUID, Set<String>> read = new HashMap<>();

    public Notes(Messages messages, Supplier<ArenaMap> arena, int coins) {
        this.messages = messages;
        this.arena = arena;
        this.coins = coins;
        this.rewards = new UnlockRewards(BlockcraftApi.get().database());
    }

    public void read(Player player, ArenaMap.Note note) {
        var prefix = "note." + note.id() + ".";
        player.sendMessage(messages.get("note.title", Placeholder.component("note", messages.get(prefix + "name"))));
        messages.getLines(prefix + "body").forEach(player::sendMessage);
        var uuid = player.getUniqueId();
        if (!read.computeIfAbsent(uuid, ignored -> new HashSet<>()).add(note.id())) {
            return;
        }
        var api = BlockcraftApi.get();
        var name = api.realName(player);
        api.playerTasks().write(player, () -> {
            if (!rewards.grant(uuid, name, note.item(), coins)) {
                return OptionalLong.empty();
            }
            var owned = api.unlocks().owned(uuid, ArenaMap.Note.ITEM_PREFIX);
            return OptionalLong.of(arena.get().notes().stream().filter(each -> owned.contains(each.item())).count());
        }, found -> found.ifPresent(count -> celebrate(player, note, count)), failed -> forget(failed, note));
    }

    public void forget(Player player) {
        read.remove(player.getUniqueId());
    }

    private void forget(Player player, ArenaMap.Note note) {
        read.getOrDefault(player.getUniqueId(), new HashSet<>()).remove(note.id());
    }

    private void celebrate(Player player, ArenaMap.Note note, long found) {
        var tags = TagResolver.resolver(Formatter.number("coins", coins), Formatter.number("found", found),
                Formatter.number("total", arena.get().notes().size()),
                Placeholder.component("note", messages.get("note." + note.id() + ".name")));
        BlockcraftApi.get().notifications().reward(player,
                Title.title(messages.get("note.found-title", tags), messages.get("note.found-subtitle", tags), TITLE_TIMES), Sounds.SECRET);
        player.sendMessage(messages.get("note.found", tags));
    }
}
