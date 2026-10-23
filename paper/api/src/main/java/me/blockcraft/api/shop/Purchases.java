package me.blockcraft.api.shop;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.dialog.ConfirmDialog;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.sound.Sounds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Quotes and buys a coin unlock, then returns only while the player is still on that purchase's screen. */
public final class Purchases {

    private record Quote(int balance, boolean owned) { }

    private final BlockcraftApi api;
    private final ItemStack subject;
    private final String item;
    private final int price;
    private final Predicate<Player> eligible;
    private final Consumer<Player> onOwned;
    private final Consumer<Player> returnTo;

    private Purchases(ItemStack subject, String item, int price, Predicate<Player> eligible,
                      Consumer<Player> onOwned, Consumer<Player> returnTo) {
        this.api = BlockcraftApi.get();
        this.subject = subject.clone();
        this.item = item;
        this.price = price;
        this.eligible = eligible;
        this.onOwned = onOwned;
        this.returnTo = returnTo;
    }

    public static void confirm(Player player, ItemStack subject, String item, int price,
                               Consumer<Player> onOwned, Consumer<Player> returnTo) {
        confirm(player, subject, item, price, ignored -> true, onOwned, returnTo);
    }

    /** {@code eligible} is a pure check, such as whether the game is still in its lobby. */
    public static void confirm(Player player, ItemStack subject, String item, int price, Predicate<Player> eligible,
                               Consumer<Player> onOwned, Consumer<Player> returnTo) {
        new Purchases(subject, item, price, eligible, onOwned, returnTo).open(player);
    }

    private void open(Player player) {
        if (!available(player)) {
            return;
        }
        var id = player.getUniqueId();
        api.playerTasks().latest(player, () -> new Quote(api.coins().get(id), api.unlocks().owns(id, item)), quote -> {
            if (!available(player)) {
                Menu.close(player);
                return;
            }
            if (quote.owned()) {
                var current = api.playerTasks().currentScreen(player);
                onOwned.accept(player);
                returnIfCurrent(player, current);
                return;
            }
            var messages = api.messages();
            var details = List.of(
                    messages.get("purchase.confirm-price", Formatter.number("coins", price)),
                    messages.get("purchase.confirm-balance", Formatter.number("coins", quote.balance())),
                    messages.get(quote.balance() >= price ? "purchase.confirm-remaining" : "purchase.confirm-missing",
                            Formatter.number("coins", Math.abs(quote.balance() - price))));
            Optional<Consumer<Player>> accept = quote.balance() >= price ? Optional.of(this::buy) : Optional.empty();
            MenuIcon.style(subject, api.resourcePack().applied(player));
            new ConfirmDialog(subject, details, accept, previous -> {
                if (eligible.test(previous)) {
                    returnTo.accept(previous);
                }
            }).open(player, () -> eligible.test(player));
        });
    }

    private void buy(Player player) {
        if (!available(player)) {
            return;
        }
        var messages = api.messages();
        api.notifications().feedback(player, messages.get("purchase.pending"));
        var current = api.playerTasks().currentScreen(player);
        var id = player.getUniqueId();
        var name = api.realName(player);
        api.playerTasks().write(player, () -> api.shop().buy(id, name, item, price), purchase -> {
            switch (purchase.outcome()) {
                case BOUGHT -> {
                    api.notifications().rewardSound(player, Sounds.PURCHASE);
                    api.notifications().feedback(player, messages.get("purchase.completed"));
                    var meta = subject.getItemMeta();
                    var subjectName = meta == null ? null : meta.displayName();
                    player.sendMessage(messages.get("purchase.bought",
                            Placeholder.component("item", subjectName != null ? subjectName : Component.translatable(subject.translationKey())),
                            Formatter.number("coins", price)));
                    onOwned.accept(player);
                }
                case ALREADY_OWNED -> {
                    api.notifications().feedback(player, messages.get("purchase.completed"));
                    onOwned.accept(player);
                }
                case NOT_ENOUGH_COINS -> {
                    Sounds.MENU_DENY.play(player);
                    api.notifications().feedback(player, messages.get("purchase.confirm-missing",
                            Formatter.number("coins", price - purchase.balance())));
                    player.sendMessage(messages.get("purchase.too-expensive", Formatter.number("coins", price)));
                }
            }
            player.sendMessage(messages.get("purchase.balance", Formatter.number("coins", purchase.balance())));
            returnIfCurrent(player, current);
        }, failed -> {
            api.notifications().feedback(failed, messages.get("operation.failed"));
            returnIfCurrent(failed, current);
        });
    }

    private boolean available(Player player) {
        if (eligible.test(player)) {
            return true;
        }
        Sounds.MENU_DENY.play(player);
        api.notifications().feedback(player, api.messages().get("purchase.unavailable"));
        return false;
    }

    private void returnIfCurrent(Player player, BooleanSupplier current) {
        if (current.getAsBoolean() && eligible.test(player)) {
            returnTo.accept(player);
        }
    }
}
