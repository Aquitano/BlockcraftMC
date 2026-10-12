package me.blockcraft.lobby.menu;

import me.blockcraft.common.settings.PlayerSetting;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

/** What sets one cosmetic menu apart from the others; {@link CosmeticMenu} runs the shared select, buy and preview flow. */
record CosmeticKind<C>(
        PlayerSetting setting,
        Component title,
        Layout layout,
        Consumer<Player> back,
        List<C> choices,
        Function<C, String> key,
        Function<C, ItemStack> picture,
        Function<C, Component> name,
        Function<C, List<Component>> description,
        boolean premium,
        Optional<Price<C>> price,
        Optional<Preview<C>> preview,
        Optional<Live<C>> live,
        Function<C, Component> selected,
        Component removed,
        Optional<Unlock<C>> unlock
) {

    record Layout(int rows, int backSlot, int removeSlot) {
        static final Layout ROW = new Layout(1, 7, 8);
    }

    record Price<C>(int coins, String itemPrefix, Function<C, String> item) {
    }

    @FunctionalInterface
    interface PreviewAction<C> {
        void show(Player player, C choice, Consumer<Player> back);
    }

    record Preview<C>(String loreKey, PreviewAction<C> action) {
    }

    /** A choice that exists in the world besides the setting; it only counts as worn while it is there. */
    record Live<C>(BiPredicate<Player, C> present, BiConsumer<Player, C> restore) {
    }

    /** A choice with an unlock item belongs to whoever unlocked it, premium or not, and cannot be bought. */
    record Unlock<C>(String itemPrefix, Function<C, Optional<String>> item) {
    }

    /** The namespace of the unlocks the menu must know the viewer owns. */
    Optional<String> ownedPrefix() {
        return price.map(Price::itemPrefix).or(() -> unlock.map(Unlock::itemPrefix));
    }
}
