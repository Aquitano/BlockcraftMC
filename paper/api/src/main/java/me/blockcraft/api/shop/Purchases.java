package me.blockcraft.api.shop;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.chat.ChatPrompt;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.sound.Sounds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Quotes a coin unlock, asks in chat whether to buy it, then returns to the menu it was bought from. */
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
            if (quote.balance() < price) {
                Sounds.MENU_DENY.play(player);
                api.notifications().feedback(player, messages.get("purchase.confirm-missing",
                        Formatter.number("coins", price - quote.balance())));
                return;
            }
            // Chat cannot be clicked behind an open menu; either answer leads back to it.
            Menu.close(player, closed -> closed.sendMessage(prompt(closed, quote.balance())));
        });
    }

    private Component prompt(Player player, int balance) {
        var messages = api.messages();
        var details = Component.join(JoinConfiguration.newlines(),
                messages.get("purchase.confirm-price", Formatter.number("coins", price)),
                messages.get("purchase.confirm-balance", Formatter.number("coins", balance)),
                messages.get("purchase.confirm-remaining", Formatter.number("coins", balance - price)));
        var prompt = new ChatPrompt(player);
        return messages.get("purchase.prompt",
                Placeholder.component("item", subjectName()),
                Formatter.number("coins", price),
                Placeholder.component("buy", prompt.button(messages.get("purchase.buy"), details, this::buy)),
                Placeholder.component("cancel", prompt.button(messages.get("purchase.cancel"),
                        messages.get("purchase.cancel-hover"), cancelled -> {
                            if (eligible.test(cancelled)) {
                                returnTo.accept(cancelled);
                            }
                        })));
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
                    player.sendMessage(messages.get("purchase.bought",
                            Placeholder.component("item", subjectName()), Formatter.number("coins", price)));
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

    private Component subjectName() {
        var meta = subject.getItemMeta();
        var name = meta == null ? null : meta.displayName();
        return name != null ? name : Component.translatable(subject.translationKey());
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
