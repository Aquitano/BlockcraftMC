package me.blockcraft.sternenfront.appearance;

import me.blockcraft.sternenfront.game.CombatClass;
import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.hero.Hero;
import me.blockcraft.sternenfront.reinforcement.Reinforcement;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * A rigged fighter's model family and how its parts hang together, as rig-bodies.yml gives it. Offsets are in unscaled
 * model space; the rig scales them.
 */
enum RigBody {

    HEGEMONIE_SOLDIER(Faction.HEGEMONIE),
    HEGEMONIE_HEAVY(Faction.HEGEMONIE, false, "arm_left"),
    HEGEMONIE_SCOUT(Faction.HEGEMONIE),
    HEGEMONIE_MEDIC(Faction.HEGEMONIE),
    KOLONIEN_SOLDIER(Faction.KOLONIEN),
    KOLONIEN_HEAVY(Faction.KOLONIEN),
    KOLONIEN_SCOUT(Faction.KOLONIEN),
    KOLONIEN_MEDIC(Faction.KOLONIEN),
    REINFORCEMENT_ARC_TROOPER(Faction.KOLONIEN),
    REINFORCEMENT_COMMANDO_DROID(Faction.HEGEMONIE),
    HERO_JEDI(Faction.KOLONIEN),
    HERO_COMMANDER(Faction.KOLONIEN),
    HERO_SITH(Faction.HEGEMONIE),
    HERO_BOUNTY_HUNTER(Faction.HEGEMONIE);

    static final float WEAPON_SCALE = number(Layout.FILE, "weapon-scale");

    record Joints(float shoulderX, float shoulderY, float shoulderZ, float hipX, float hipY, float torsoY, float torsoLean,
                  float headY, float headZ, float weaponX, float weaponY, float weaponZ) {

        private static Joints from(ConfigurationSection joints) {
            return new Joints(number(joints, "shoulder.x"), number(joints, "shoulder.y"), number(joints, "shoulder.z"),
                    number(joints, "hip.x"), number(joints, "hip.y"), number(joints, "torso.y"), number(joints, "torso.lean"),
                    number(joints, "head.y"), number(joints, "head.z"),
                    number(joints, "weapon.x"), number(joints, "weapon.y"), number(joints, "weapon.z"));
        }
    }

    private final Faction faction;
    private final Joints joints;
    private final float scale;
    private final float armLift;
    private final float gripTurn;
    private final boolean showsWeapon;
    private final String leftArmPart;

    RigBody(Faction faction) {
        this(faction, true, "arm");
    }

    RigBody(Faction faction, boolean showsWeapon, String leftArmPart) {
        var layout = section(Layout.FILE, "bodies." + family());
        this.faction = faction;
        this.joints = Joints.from(section(Layout.FILE, "joints." + layout.getString("joints")));
        this.scale = layout.contains("scale") ? number(layout, "scale") : 1;
        this.armLift = number(layout, "arm-lift");
        this.gripTurn = number(layout, "grip-turn");
        this.showsWeapon = showsWeapon;
        this.leftArmPart = leftArmPart;
    }

    /** Holds the file apart because an enum builds its constants before its own static fields. */
    private static final class Layout {
        static final YamlConfiguration FILE = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(RigBody.class.getResourceAsStream("/rig-bodies.yml"), "rig-bodies.yml is missing"),
                StandardCharsets.UTF_8));
    }

    private static ConfigurationSection section(ConfigurationSection parent, String path) {
        return Objects.requireNonNull(parent.getConfigurationSection(path), () -> "rig-bodies.yml has no " + path);
    }

    private static float number(ConfigurationSection section, String path) {
        if (!(section.get(path) instanceof Number number)) {
            throw new IllegalStateException("rig-bodies.yml has no number at " + MemorySection.createPath(section, path));
        }
        return number.floatValue();
    }

    static RigBody of(Faction faction, CombatClass role) {
        return switch (faction) {
            case HEGEMONIE -> switch (role) {
                case SOLDIER -> HEGEMONIE_SOLDIER;
                case HEAVY -> HEGEMONIE_HEAVY;
                case SCOUT -> HEGEMONIE_SCOUT;
                case MEDIC -> HEGEMONIE_MEDIC;
            };
            case KOLONIEN -> switch (role) {
                case SOLDIER -> KOLONIEN_SOLDIER;
                case HEAVY -> KOLONIEN_HEAVY;
                case SCOUT -> KOLONIEN_SCOUT;
                case MEDIC -> KOLONIEN_MEDIC;
            };
        };
    }

    /** The champion keeps its side's soldier body, which shows the relic blade. */
    static RigBody of(Faction faction, CombatClass role, Optional<Hero> hero) {
        return hero.map(chosen -> switch (chosen) {
            case JEDI -> HERO_JEDI;
            case COMMANDER -> HERO_COMMANDER;
            case SITH -> HERO_SITH;
            case BOUNTY_HUNTER -> HERO_BOUNTY_HUNTER;
            case CHAMPION -> of(faction, CombatClass.SOLDIER);
        }).orElseGet(() -> of(faction, role));
    }

    static RigBody of(Reinforcement unit) {
        return switch (unit) {
            case ARC_TROOPER -> REINFORCEMENT_ARC_TROOPER;
            case COMMANDO_DROID -> REINFORCEMENT_COMMANDO_DROID;
        };
    }

    static Optional<RigBody> ofFamily(String family) {
        return Arrays.stream(values()).filter(body -> body.family().equals(family)).findFirst();
    }

    String family() {
        return name().toLowerCase(Locale.ROOT);
    }

    Faction faction() {
        return faction;
    }

    Joints joints() {
        return joints;
    }

    float scale() {
        return scale;
    }

    float armLift() {
        return armLift;
    }

    float gripTurn() {
        return gripTurn;
    }

    boolean showsWeapon() {
        return showsWeapon;
    }

    boolean dualWield() {
        return this == HERO_COMMANDER || this == HERO_BOUNTY_HUNTER;
    }

    String leftArmPart() {
        return leftArmPart;
    }
}
