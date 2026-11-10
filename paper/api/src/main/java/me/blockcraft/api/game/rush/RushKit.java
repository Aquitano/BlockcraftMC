package me.blockcraft.api.game.rush;

import me.blockcraft.api.game.CombatStyle;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.common.message.Messages;
import me.blockcraft.api.game.rush.RushConfig.Kit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class RushKit {

    private final Kit config;
    private final Messages messages;
    private final String prefix;

    public RushKit(Kit config, Messages messages) {
        this(config, messages, "item.");
    }

    public RushKit(Kit config, Messages messages, String prefix) {
        this.config = config;
        this.prefix = prefix;
        this.messages = messages;
    }

    /** Deals the kit in the player's current layout and returns it, so refills follow the same order until the next deal. */
    public RushLayout give(Player player, int slot) {
        var layout = RushLayout.of(player);
        var inventory = player.getInventory();
        inventory.setItem(layout.stick(), stick(player));
        inventory.setItem(layout.blocks(), blocks(slot));
        inventory.setItem(layout.pickaxe(), pickaxe());
        CombatStyle.LEGACY.apply(player);
        return layout;
    }

    public void refill(Player player, int slot, RushLayout layout) {
        player.getInventory().setItem(layout.blocks(), blocks(slot));
    }

    /** Whether the player holds the blocks, wherever the dealt layout put them. */
    public boolean holdsBlocks(Player player, RushLayout layout) {
        return player.getInventory().getHeldItemSlot() == layout.blocks();
    }

    /** The piece as the player would carry it, for a menu that shows the kit. */
    public ItemStack preview(Player player, RushLayout.Item item) {
        return switch (item) {
            case STICK -> stick(player);
            case BLOCKS -> blocks(0);
            case PICKAXE -> pickaxe();
        };
    }

    private ItemStack stick(Player player) {
        var stick = Items.of(Material.STICK).name(messages.get(prefix + "stick")).build();
        stick.addUnsafeEnchantment(Enchantment.KNOCKBACK, config.knockbackLevel());
        return BlockcraftApi.get().weapons().stick(player, stick);
    }

    private ItemStack pickaxe() {
        return Items.of(Material.WOODEN_PICKAXE).name(messages.get(prefix + "pickaxe"))
                .edit(meta -> meta.setUnbreakable(true)).build();
    }

    private ItemStack blocks(int slot) {
        return Items.of(slot == 0 ? Material.RED_TERRACOTTA : Material.BLUE_TERRACOTTA, config.blocks())
                .name(messages.get(prefix + "blocks")).build();
    }
}
