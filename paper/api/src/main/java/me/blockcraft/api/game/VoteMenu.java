package me.blockcraft.api.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.ListMenu;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A lobby vote, the same in every game: the options with how many votes each has, the viewer's own vote lit, and
 * the choice to leave it to chance. What a click does is the game's ({@code choose}, with the option's key, empty for
 * chance); {@link #confirm} tells the voter only, so that the lobby's chat stays quiet.
 */
public final class VoteMenu extends ListMenu<VoteMenu.Option> {

    /** {@code key} empty is the vote for chance. */
    public record Option(Optional<String> key, Component name, ItemStack icon, List<Component> details, long votes) {
    }

    private final Messages messages;
    private final Optional<Optional<String>> own;
    private final BiConsumer<Player, Optional<String>> choose;

    /**
     * {@code own} is the viewer's vote, if they cast one; {@code chance} offers leaving it to chance, with the votes
     * it has; {@code back} leads to the menu before, else the menu closes from the corner.
     */
    public VoteMenu(Component title, List<Option> options, Optional<Long> chance, Optional<Optional<String>> own,
                    Optional<Consumer<Player>> back, BiConsumer<Player, Optional<String>> choose) {
        super(rowsFor(options.size() + chance.map(votes -> 1).orElse(0)), title);
        this.messages = BlockcraftApi.get().messages();
        this.own = own;
        this.choose = choose;
        back.ifPresentOrElse(this::backButton, this::closeButton);
        var shown = new ArrayList<Option>();
        chance.ifPresent(votes -> shown.add(new Option(Optional.empty(), messages.get("vote.random"),
                Items.of(Material.ENDER_PEARL).build(), List.of(messages.get("vote.random-lore")), votes)));
        shown.addAll(options);
        show(shown);
    }

    /** The voter hears where their vote went, and whether it replaced an earlier one; nobody else does. */
    public static void confirm(Player voter, Messages gameMessages, Component option, boolean changed) {
        voter.sendMessage(BlockcraftApi.get().messages().prefixedLike(gameMessages)
                .get(changed ? "vote.changed" : "vote.cast", Placeholder.component("option", option)));
    }

    /** The vote is over before the voter clicked. */
    public static void closed(Player voter, Messages gameMessages) {
        voter.sendMessage(BlockcraftApi.get().messages().prefixedLike(gameMessages).get("vote.closed"));
    }

    @Override
    protected ItemStack icon(Option option) {
        boolean mine = own.equals(Optional.of(option.key()));
        var lore = new ArrayList<>(option.details());
        lore.add(messages.get("vote.votes", Formatter.number("votes", option.votes())));
        if (mine) {
            lore.add(messages.get("vote.own"));
        }
        lore.add(messages.get("vote.choose"));
        var icon = Items.of(option.icon().clone()).name(option.name()).lore(lore).hideAttributes();
        if (mine) {
            icon.glow();
        }
        return icon.build();
    }

    @Override
    protected void onSelect(Player player, Option option, InventoryClickEvent event) {
        choose.accept(player, option.key());
    }
}
