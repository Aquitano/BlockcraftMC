package me.blockcraft.common.nick;

/** A worn nick: the name and skin others see, and the LuckPerms group whose rank is shown instead of the real one. */
public record Nick(String name, String skinOwner, String rankGroup) {
}
