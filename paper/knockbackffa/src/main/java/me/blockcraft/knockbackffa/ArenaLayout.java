package me.blockcraft.knockbackffa;

enum ArenaLayout {
    ISLANDS, RING, CROSSROADS;

    boolean floor(int x, int z) {
        int radius = x * x + z * z;
        return switch (this) {
            case ISLANDS -> radius <= 100 || Math.pow(Math.abs(x) - 18, 2) + z * z <= 36
                    || x * x + Math.pow(Math.abs(z) - 18, 2) <= 36
                    || Math.abs(x) <= 1 && Math.abs(z) <= 18 || Math.abs(z) <= 1 && Math.abs(x) <= 18;
            case RING -> radius >= 169 && radius <= 529
                    || Math.abs(x) <= 2 && Math.abs(z) <= 18 || Math.abs(z) <= 2 && Math.abs(x) <= 18;
            case CROSSROADS -> Math.abs(x) <= 3 && Math.abs(z) <= 23
                    || Math.abs(z) <= 3 && Math.abs(x) <= 23
                    || Math.abs(Math.abs(x) - 9) <= 3 && Math.abs(Math.abs(z) - 9) <= 3;
        };
    }

    ArenaLayout next() {
        return values()[(ordinal() + 1) % values().length];
    }

    String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
