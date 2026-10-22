package me.blockcraft.mlgrush.game;

import me.blockcraft.api.game.CombatStyle;
import me.blockcraft.api.item.Items;
import me.blockcraft.common.message.Messages;
import me.blockcraft.mlgrush.config.RushConfig.Kit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class RushKit {

    private final Kit config;
    private final Messages messages;

    public RushKit(Kit config, Messages messages) {
        this.config = config;
        this.messages = messages;
    }

    public void give(Player player, int slot) {
        var stick = Items.of(Material.STICK).name(messages.get("item.stick")).build();
        stick.addUnsafeEnchantment(Enchantment.KNOCKBACK, config.knockbackLevel());
        player.getInventory().setItem(0, stick);
        player.getInventory().setItem(1, blocks(slot));
        player.getInventory().setItem(2, Items.of(Material.WOODEN_PICKAXE).name(messages.get("item.pickaxe"))
                .edit(meta -> meta.setUnbreakable(true)).build());
        CombatStyle.LEGACY.apply(player);
    }

    public void refill(Player player, int slot) {
        player.getInventory().setItem(1, blocks(slot));
    }

    private ItemStack blocks(int slot) {
        return Items.of(slot == 0 ? Material.RED_TERRACOTTA : Material.BLUE_TERRACOTTA, config.blocks())
                .name(messages.get("item.blocks")).build();
    }
}
