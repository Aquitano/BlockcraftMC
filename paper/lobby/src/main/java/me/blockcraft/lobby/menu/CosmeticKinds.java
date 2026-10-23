package me.blockcraft.lobby.menu;

import me.blockcraft.api.cosmetic.ChatTagChoice;
import me.blockcraft.api.cosmetic.KillEffect;
import me.blockcraft.api.cosmetic.WinEffect;
import me.blockcraft.api.item.Items;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.lobby.cosmetic.Head;
import me.blockcraft.lobby.cosmetic.PetType;
import me.blockcraft.lobby.cosmetic.Shirt;
import me.blockcraft.lobby.cosmetic.Trail;
import me.blockcraft.lobby.gadget.Gadget;
import me.blockcraft.lobby.menu.CosmeticKind.Live;
import me.blockcraft.lobby.menu.CosmeticKind.Preview;
import me.blockcraft.lobby.menu.CosmeticKind.Price;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

final class CosmeticKinds {

    private static final String FITTING_LORE = "cosmetics.fitting-lore";
    private static final String PREVIEW_LORE = "cosmetics.preview-lore";

    private CosmeticKinds() {
    }

    static CosmeticKind<Shirt> shirts(Menus menus) {
        var messages = menus.messages();
        return new CosmeticKind<>(PlayerSetting.SHIRT, messages.get("shirts.title"), 1, menus::openShop,
                List.of(Shirt.values()), Shirt::key, CosmeticKinds::shirt,
                shirt -> messages.get("shirts.name." + shirt.key()), shirt -> List.of(), false,
                Optional.of(new Price<>(menus.config().shirtPrice(), Shirt.ITEM_PREFIX, Shirt::item)),
                Optional.of(new Preview<>(FITTING_LORE, (player, shirt, back) ->
                        menus.previews().equipment(player, shirt(shirt), EquipmentSlot.CHEST, back))),
                Optional.empty(), shirt -> messages.get("shirts.equipped"), messages.get("cosmetics.removed"), Optional.empty());
    }

    static CosmeticKind<Head> heads(Menus menus) {
        var messages = menus.messages();
        return new CosmeticKind<>(PlayerSetting.HEAD, messages.get("heads.title"), 3, menus::openShop,
                List.of(Head.values()), Head::storageKey, head -> Items.head(head.owner()).build(),
                head -> messages.get("heads.name." + head.key()), head -> List.of(), false,
                Optional.of(new Price<>(menus.config().headPrice(), Head.ITEM_PREFIX, Head::item)),
                Optional.of(new Preview<>(FITTING_LORE, (player, head, back) ->
                        menus.previews().equipment(player, Items.head(head.owner()).build(), EquipmentSlot.HEAD, back))),
                Optional.empty(), head -> messages.get("heads.equipped"), messages.get("cosmetics.removed"), Optional.empty());
    }

    static CosmeticKind<Trail> trails(Menus menus) {
        var messages = menus.messages();
        return new CosmeticKind<>(PlayerSetting.TRAIL, messages.get("trails.title"), 1, menus::openExtras,
                List.of(Trail.values()), Trail::key, trail -> ItemStack.of(trail.icon()),
                trail -> messages.get("trails.name." + trail.key()),
                trail -> List.of(messages.get("trails.lore." + trail.key())), true, Optional.empty(),
                Optional.of(new Preview<>(PREVIEW_LORE, menus.previews()::trail)),
                Optional.empty(), trail -> messages.get("trails.selected." + trail.key()), messages.get("cosmetics.removed"),
                Optional.of(new CosmeticKind.Unlock<>(Trail.ITEM_PREFIX, trail -> trail.premium() ? Optional.empty() : Optional.of(trail.item()))));
    }

    static CosmeticKind<PetType> pets(Menus menus) {
        var messages = menus.messages();
        var pets = menus.pets();
        return new CosmeticKind<>(PlayerSetting.PET, messages.get("pets.title"), 1, menus::openPremium,
                List.of(PetType.values()), PetType::key, pet -> ItemStack.of(pet.icon()),
                pet -> messages.get("pets.name." + pet.key()),
                pet -> List.of(messages.get("pets.lore." + pet.key())), true, Optional.empty(),
                Optional.of(new Preview<>(PREVIEW_LORE, menus.previews()::pet)),
                Optional.of(new Live<>((player, pet) -> pets.of(player).filter(pet::equals).isPresent(), (player, pet) -> {
                    pets.release(player.getUniqueId());
                    pets.spawn(player, pet);
                })),
                pet -> messages.get("pets.chosen." + pet.key()), messages.get("cosmetics.removed"), Optional.empty());
    }

    static CosmeticKind<Gadget> gadgets(Menus menus) {
        var messages = menus.messages();
        return new CosmeticKind<>(PlayerSetting.GADGET, messages.get("gadgets.title"), 1, menus::openExtras,
                List.of(Gadget.values()), Gadget::key, gadget -> ItemStack.of(gadget.material()),
                gadget -> messages.get("gadgets.name." + gadget.key()),
                gadget -> List.of(messages.get("gadgets.lore." + gadget.key())), false, Optional.empty(),
                Optional.empty(), Optional.empty(), gadget -> messages.get("cosmetics.selected"), messages.get("cosmetics.removed"),
                Optional.of(new CosmeticKind.Unlock<>("gadget.", gadget -> gadget == Gadget.BIRTHDAY_CAKE ? Optional.of("gadget.birthday-cake") : Optional.empty())));
    }

    /** {@code choices} are the tags the viewer may wear, which their rank and their earned unlocks decide. */
    static CosmeticKind<ChatTagChoice> chatTags(Menus menus, List<ChatTagChoice> choices) {
        var messages = menus.messages();
        var tags = menus.apiMessages();
        return new CosmeticKind<>(PlayerSetting.CHAT_TAG, messages.get("tags.title"), 3, menus::openExtras,
                choices, ChatTagChoice::value,
                choice -> ItemStack.of(choice.tag().earned() ? Material.GOLDEN_HELMET : Material.NAME_TAG),
                choice -> choice.text(tags),
                choice -> choice.tag().earned() ? List.of(messages.get("tags.earned-lore")) : List.of(), false, Optional.empty(),
                Optional.empty(), Optional.empty(),
                choice -> messages.get("tags.chosen", Placeholder.component("tag", choice.text(tags))),
                messages.get("tags.removed"), Optional.empty());
    }

    static CosmeticKind<KillEffect> killEffects(Menus menus, Consumer<Player> back) {
        return effects(menus, back, PlayerSetting.KILL_EFFECT, "effects.kill", List.of(KillEffect.values()), KillEffect::key,
                KillEffect::icon, new Price<>(menus.config().killEffectPrice(), "effect.kill.", KillEffect::item),
                KillEffect::earned, EffectPreviews::kill);
    }

    static CosmeticKind<WinEffect> winEffects(Menus menus, Consumer<Player> back) {
        return effects(menus, back, PlayerSetting.WIN_EFFECT, "effects.win", List.of(WinEffect.values()), WinEffect::key,
                WinEffect::icon, new Price<>(menus.config().winEffectPrice(), "effect.win.", WinEffect::item),
                WinEffect::earned, EffectPreviews::win);
    }

    private static <E> CosmeticKind<E> effects(Menus menus, Consumer<Player> back, PlayerSetting setting, String prefix, List<E> effects,
                                               Function<E, String> key,
                                               Function<E, Material> icon, Price<E> price,
                                               Predicate<E> earned, BiConsumer<Player, E> preview) {
        var messages = menus.messages();
        return new CosmeticKind<>(setting, messages.get(prefix + ".title"), 1, back,
                effects, key, effect -> ItemStack.of(icon.apply(effect)),
                effect -> messages.get(prefix + ".name." + key.apply(effect)), effect -> List.of(), false,
                Optional.of(price),
                Optional.of(new Preview<>("effects.preview-lore", (player, effect, returnTo) ->
                        menus.previews().effect(player, viewer -> preview.accept(viewer, effect), returnTo))),
                Optional.empty(),
                effect -> messages.get("effects.chosen", Placeholder.component("effect", messages.get(prefix + ".name." + key.apply(effect)))),
                messages.get("effects.reset-done"),
                Optional.of(new CosmeticKind.Unlock<>(price.itemPrefix(),
                        effect -> earned.test(effect) ? Optional.of(price.item().apply(effect)) : Optional.empty())));
    }

    private static ItemStack shirt(Shirt shirt) {
        return Items.of(Material.LEATHER_CHESTPLATE).color(shirt.color()).build();
    }
}
