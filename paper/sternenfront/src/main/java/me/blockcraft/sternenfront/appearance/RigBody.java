package me.blockcraft.sternenfront.appearance;

import me.blockcraft.sternenfront.game.CombatClass;
import me.blockcraft.sternenfront.game.Faction;

import java.util.Arrays;

/** A rigged fighter's model family and how its parts hang together. Offsets are in unscaled model space; the rig scales them. */
enum RigBody {

    HEGEMONIE_SOLDIER(Faction.HEGEMONIE, CombatClass.SOLDIER, new Joints(.25f, 1.47f, 0, .085f, .96f, .98f, 0, 1.64f, 0, -.22f, 1.3f, .53f), 1, -1.1f, .45f, true, "arm"),
    HEGEMONIE_HEAVY(Faction.HEGEMONIE, CombatClass.HEAVY, new Joints(.47f, 1.46f, .14f, .22f, .78f, .8f, .26f, 1.32f, .3f, 0, 0, 0), 1.25f, -1.45f, 0, false, "arm"),
    HEGEMONIE_SCOUT(Faction.HEGEMONIE, CombatClass.SCOUT, new Joints(.17f, 1.22f, .18f, .08f, .88f, .9f, .5f, 1.28f, .3f, -.13f, 1.08f, .68f), 1, -1.2f, .6f, true, "arm"),
    HEGEMONIE_MEDIC(Faction.HEGEMONIE, CombatClass.MEDIC, new Joints(.2f, 1.44f, 0, .09f, .96f, .98f, 0, 1.58f, 0, -.17f, 1.3f, .55f), 1, -1.15f, 0, true, "tool_arm"),
    KOLONIEN_SOLDIER(Faction.KOLONIEN, CombatClass.SOLDIER, new Joints(.3f, 1.4f, 0, .105f, .95f, .97f, 0, 1.5f, 0, -.26f, 1.27f, .53f), 1, -1.1f, .55f, true, "arm"),
    KOLONIEN_HEAVY(Faction.KOLONIEN, CombatClass.HEAVY, new Joints(.43f, 1.38f, 0, .15f, .92f, .94f, 0, 1.46f, .02f, -.39f, 1.27f, .6f), 1.08f, -1.1f, .5f, true, "arm"),
    KOLONIEN_SCOUT(Faction.KOLONIEN, CombatClass.SCOUT, new Joints(.24f, 1.38f, 0, .085f, .95f, .97f, 0, 1.51f, 0, -.2f, 1.25f, .55f), 1, -1.15f, .5f, true, "arm"),
    KOLONIEN_MEDIC(Faction.KOLONIEN, CombatClass.MEDIC, new Joints(.28f, 1.4f, 0, .1f, .95f, .97f, 0, 1.5f, 0, -.24f, 1.27f, .53f), 1, -1.15f, 0, true, "arm");

    /** Where the parts attach above the feet; a positive {@code torsoLean} hunches the torso forward from the waist. */
    record Joints(float shoulderX, float shoulderY, float shoulderZ, float hipX, float hipY, float torsoY, float torsoLean,
                  float headY, float headZ, float weaponX, float weaponY, float weaponZ) { }

    private final Faction faction;
    private final CombatClass role;
    private final Joints joints;
    private final float scale;
    private final float armLift;
    private final float gripTurn;
    private final boolean showsWeapon;
    private final String leftArm;

    RigBody(Faction faction, CombatClass role, Joints joints, float scale, float armLift, float gripTurn, boolean showsWeapon, String leftArm) {
        this.faction = faction;
        this.role = role;
        this.joints = joints;
        this.scale = scale;
        this.armLift = armLift;
        this.gripTurn = gripTurn;
        this.showsWeapon = showsWeapon;
        this.leftArm = leftArm;
    }

    static RigBody of(Faction faction, CombatClass role) {
        return Arrays.stream(values()).filter(body -> body.faction == faction && body.role == role).findFirst().orElseThrow();
    }

    String family() {
        return faction.key() + "_" + role.key();
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

    String leftArm() {
        return leftArm;
    }
}
