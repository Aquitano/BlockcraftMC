package me.blockcraft.sternenfront.appearance;

import me.blockcraft.sternenfront.game.CombatClass;
import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.hero.Hero;
import me.blockcraft.sternenfront.reinforcement.Reinforcement;

import java.util.Locale;
import java.util.Optional;

/** A rigged fighter's model family and how its parts hang together. Offsets are in unscaled model space; the rig scales them. */
enum RigBody {

    HEGEMONIE_SOLDIER(Faction.HEGEMONIE, new Joints(.25f, 1.47f, 0, .085f, .96f, .98f, 0, 1.64f, 0, -.22f, 1.3f, .53f), 1, -1.1f, .45f, true, "arm"),
    HEGEMONIE_HEAVY(Faction.HEGEMONIE, new Joints(.47f, 1.46f, .14f, .22f, .78f, .8f, .26f, 1.32f, .3f, 0, 0, 0), 1.25f, -1.45f, 0, false, "arm"),
    HEGEMONIE_SCOUT(Faction.HEGEMONIE, new Joints(.25f, 1.47f, 0, .085f, .96f, .98f, 0, 1.64f, 0, -.22f, 1.3f, .53f), 1, -1.1f, .45f, true, "arm"),
    HEGEMONIE_MEDIC(Faction.HEGEMONIE, new Joints(.2f, 1.44f, 0, .09f, .96f, .98f, 0, 1.58f, 0, -.17f, 1.3f, .55f), 1, -1.15f, 0, true, "arm"),
    KOLONIEN_SOLDIER(Faction.KOLONIEN, new Joints(.3f, 1.4f, 0, .105f, .95f, .97f, 0, 1.5f, 0, -.26f, 1.27f, .53f), 1, -1.1f, .55f, true, "arm"),
    KOLONIEN_HEAVY(Faction.KOLONIEN, new Joints(.3f, 1.4f, 0, .105f, .95f, .97f, 0, 1.5f, 0, -.26f, 1.27f, .53f), 1, -1.1f, .55f, true, "arm"),
    KOLONIEN_SCOUT(Faction.KOLONIEN, new Joints(.24f, 1.38f, 0, .085f, .95f, .97f, 0, 1.51f, 0, -.2f, 1.25f, .55f), 1, -1.15f, .5f, true, "arm"),
    KOLONIEN_MEDIC(Faction.KOLONIEN, new Joints(.28f, 1.4f, 0, .1f, .95f, .97f, 0, 1.5f, 0, -.24f, 1.27f, .53f), 1, -1.15f, 0, true, "arm"),
    REINFORCEMENT_ARC_TROOPER(Faction.KOLONIEN, new Joints(.3f, 1.4f, 0, .105f, .95f, .97f, 0, 1.5f, 0, -.26f, 1.27f, .53f), 1, -1.1f, .55f, true, "arm"),
    REINFORCEMENT_COMMANDO_DROID(Faction.HEGEMONIE, new Joints(.25f, 1.44f, 0, .09f, .96f, .98f, 0, 1.55f, 0, -.22f, 1.3f, .53f), 1, -1.1f, .45f, true, "arm"),
    HERO_JEDI(Faction.KOLONIEN, new Joints(.28f, 1.4f, 0, .1f, .95f, .97f, 0, 1.5f, 0, -.25f, 1.12f, .35f), -.5f),
    HERO_COMMANDER(Faction.KOLONIEN, new Joints(.3f, 1.4f, 0, .105f, .95f, .97f, 0, 1.5f, 0, -.26f, 1.27f, .53f), -1.1f),
    HERO_SITH(Faction.HEGEMONIE, new Joints(.28f, 1.4f, 0, .1f, .95f, .97f, 0, 1.5f, 0, -.25f, 1.12f, .35f), -.5f),
    HERO_BOUNTY_HUNTER(Faction.HEGEMONIE, new Joints(.3f, 1.4f, 0, .105f, .95f, .97f, 0, 1.5f, 0, -.26f, 1.27f, .53f), -1.1f);

    /** Where the parts attach above the feet; a positive {@code torsoLean} hunches the torso forward from the waist. */
    record Joints(float shoulderX, float shoulderY, float shoulderZ, float hipX, float hipY, float torsoY, float torsoLean,
                  float headY, float headZ, float weaponX, float weaponY, float weaponZ) { }

    private final Faction faction;
    private final Joints joints;
    private final float scale;
    private final float armLift;
    private final float gripTurn;
    private final boolean showsWeapon;
    private final String leftArm;

    RigBody(Faction faction, Joints joints, float scale, float armLift, float gripTurn, boolean showsWeapon, String leftArm) {
        this.faction = faction;
        this.joints = joints;
        this.scale = scale;
        this.armLift = armLift;
        this.gripTurn = gripTurn;
        this.showsWeapon = showsWeapon;
        this.leftArm = leftArm;
    }

    RigBody(Faction faction, Joints joints, float armLift) {
        this(faction, joints, 1, armLift, 0, true, "arm");
    }

    static RigBody of(Faction faction, CombatClass role) {
        return valueOf(faction.name() + "_" + role.name());
    }

    static RigBody of(Faction faction, CombatClass role, Optional<Hero> hero) {
        return hero.filter(chosen -> chosen != Hero.CHAMPION).map(chosen -> valueOf("HERO_" + chosen.name()))
                .orElseGet(() -> of(faction, hero.isPresent() ? CombatClass.SOLDIER : role));
    }

    static RigBody of(Reinforcement unit) {
        return switch (unit) {
            case ARC_TROOPER -> REINFORCEMENT_ARC_TROOPER;
            case COMMANDO_DROID -> REINFORCEMENT_COMMANDO_DROID;
        };
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

    /** How far the aiming arms rise from hanging straight down, in radians. */
    float armLift() {
        return armLift;
    }

    /** How far the left arm turns inward to reach the weapon's fore-end, in radians. */
    float gripTurn() {
        return gripTurn;
    }

    boolean showsWeapon() {
        return showsWeapon;
    }

    boolean dualWield() {
        return this == HERO_COMMANDER || this == HERO_BOUNTY_HUNTER;
    }

    String leftArm() {
        return leftArm;
    }
}
