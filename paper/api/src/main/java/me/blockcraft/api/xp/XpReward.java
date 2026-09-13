package me.blockcraft.api.xp;

/** What games hand out XP for; the amounts are {@code xp.rewards.<key>} in BlockcraftAPI's config.yml. */
public enum XpReward {
    KILL("kill"),
    WIN("win");

    private final String key;

    XpReward(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
