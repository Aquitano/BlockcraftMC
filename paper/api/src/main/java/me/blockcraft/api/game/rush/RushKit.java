package me.blockcraft.api.game.rush;

import me.blockcraft.api.game.CombatStyle;
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

    public void give(Player player, int slot) {
        var stick = Items.of(Material.STICK).name(messages.get(prefix + "stick")).build();
        stick.addUnsafeEnchantment(Enchantment.KNOCKBACK, config.knockbackLevel());
        player.getInventory().setItem(0, stick);
        player.getInventory().setItem(1, blocks(slot));
        player.getInventory().setItem(2, Items.of(Material.WOODEN_PICKAXE).name(messages.get(prefix + "pickaxe"))
                .edit(meta -> meta.setUnbreakable(true)).build());
        CombatStyle.LEGACY.apply(player);
    }

    public void refill(Player player, int slot) {
        player.getInventory().setItem(1, blocks(slot));
    }

    private ItemStack blocks(int slot) {
        return Items.of(slot == 0 ? Material.RED_TERRACOTTA : Material.BLUE_TERRACOTTA, config.blocks())
                .name(messages.get(prefix + "blocks")).build();
    }
}
