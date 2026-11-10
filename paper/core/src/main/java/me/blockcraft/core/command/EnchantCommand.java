package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.registry.RegistryKey;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.MenuType;

import java.util.Optional;

/**
 * Without arguments an enchanting table anywhere; with them vanilla's {@code /enchant <targets> <enchantment> [level]}
 * on the item in each target's main hand.
 */
public final class EnchantCommand {

    private EnchantCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages) {
        return Commands.literal("enchant")
                .requires(CorePermission.of("enchant"))
                .executes(context -> {
                    Players.self(context.getSource(), messages.get("enchant.usage")).ifPresent(player ->
                            MenuType.ENCHANTMENT.builder().location(player.getLocation()).checkReachable(false).build(player).open());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("targets", ArgumentTypes.entities())
                        .then(Commands.argument("enchantment", ArgumentTypes.resource(RegistryKey.ENCHANTMENT))
                                .executes(context -> enchant(context, 1, messages))
                                .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                        .executes(context -> enchant(context, IntegerArgumentType.getInteger(context, "level"), messages)))))
                .build();
    }

    private static int enchant(CommandContext<CommandSourceStack> context, int level, Messages messages) throws CommandSyntaxException {
        var sender = context.getSource().getSender();
        var enchantment = context.getArgument("enchantment", Enchantment.class);
        if (level > enchantment.getMaxLevel()) {
            sender.sendMessage(messages.get("enchant.level-too-high", Formatter.number("max", enchantment.getMaxLevel())));
            return Command.SINGLE_SUCCESS;
        }
        var targets = TargetGuard.permitted(sender, Players.entities(context, "targets"), messages);
        int enchanted = 0;
        for (Entity target : targets) {
            var refusal = refusal(target, enchantment, messages);
            if (refusal.isPresent()) {
                if (targets.size() == 1) {
                    sender.sendMessage(refusal.get());
                }
                continue;
            }
            var equipment = ((LivingEntity) target).getEquipment();
            var item = equipment.getItemInMainHand();
            item.addUnsafeEnchantment(enchantment, level);
            equipment.setItemInMainHand(item);
            enchanted++;
        }
        if (enchanted > 0) {
            sender.sendMessage(messages.get("enchant.done", Placeholder.component("enchantment", enchantment.displayName(level)),
                    Formatter.number("count", enchanted)));
        }
        return Command.SINGLE_SUCCESS;
    }

    /** Why the target's held item cannot take the enchantment, the same checks vanilla makes. */
    private static Optional<Component> refusal(Entity target, Enchantment enchantment, Messages messages) {
        var name = Placeholder.component("player", target.name());
        if (!(target instanceof LivingEntity living) || living.getEquipment() == null || living.getEquipment().getItemInMainHand().isEmpty()) {
            return Optional.of(messages.get("enchant.no-item", name));
        }
        var item = living.getEquipment().getItemInMainHand();
        if (!enchantment.canEnchantItem(item)) {
            return Optional.of(messages.get("enchant.unsupported", name));
        }
        if (item.getEnchantments().keySet().stream().anyMatch(present -> !present.equals(enchantment) && present.conflictsWith(enchantment))) {
            return Optional.of(messages.get("enchant.conflict", name));
        }
        return Optional.empty();
    }
}
