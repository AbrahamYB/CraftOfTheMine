package com.botwclimb.client;

import com.botwclimb.common.climb.ClimbConstants;
import com.botwclimb.common.climb.ClimbData;
import com.botwclimb.common.climb.ClimbState;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Procedural climbing poses, applied on top of the vanilla pose at the end of {@code PlayerModel.setupAnim}.
 * Everything is a continuous function of accumulated climb distance, jump age and blend weights, interpolated with
 * the partial tick, so limbs never snap. Rotations are radians, pivot offsets are model pixels.
 *
 * Notes: a negative xRot swings a limb forward (towards the wall). For a raised arm (xRot near -PI) a NEGATIVE zRot
 * on the right arm and a POSITIVE one on the left arm splays it outwards.
 */
public final class ClimbAnimator {
    private ClimbAnimator() {}

    private static final float RAD = (float) Math.PI / 180f;
    private static final float CYCLE_UP = 5.2f;   // radians of limb cycle per block climbed
    private static final float CYCLE_SIDE = 6.3f; // radians per block shuffled sideways

    /** One climb-jump key pose. */
    private record Key(float armX, float armSplay, float armY, float legX, float legY, float bodyX, float stretch) {}

    private static final Key LOAD = new Key(-2.30f, 0.30f, 2.0f, -1.00f, -3.0f, 0.28f, 0.96f);
    private static final Key LAUNCH = new Key(-3.10f, 0.10f, -3.0f, 0.25f, 0.0f, -0.06f, 1.07f);
    private static final Key AIR = new Key(-3.05f, 0.14f, -2.0f, -0.55f, -2.0f, 0.0f, 1.0f);
    private static final float T_LOAD = ClimbConstants.JUMP_WINDUP_TICKS;
    private static final float T_LAUNCH = T_LOAD + 2f;
    private static final float T_AIR = T_LAUNCH + 3f;
    private static final float RECOVER_TICKS = 6f;

    private static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    private static Key mix(Key a, Key b, float t) {
        return new Key(Mth.lerp(t, a.armX, b.armX), Mth.lerp(t, a.armSplay, b.armSplay), Mth.lerp(t, a.armY, b.armY),
                Mth.lerp(t, a.legX, b.legX), Mth.lerp(t, a.legY, b.legY), Mth.lerp(t, a.bodyX, b.bodyX),
                Mth.lerp(t, a.stretch, b.stretch));
    }

    private static float jumpTime(ClimbData d, float pt) {
        return Mth.lerp(pt, d.jumpAgeO, d.jumpAge);
    }

    /** 0 = no jump pose, 1 = full jump pose. Fades in on the windup and out after the re-grab. */
    private static float jumpWeight(ClimbData d, float pt) {
        if (d.jumpAge >= 999) return 0f;
        float in = smooth(jumpTime(d, pt) / T_LOAD);
        if (d.state == ClimbState.CLIMB_JUMP) return in;
        if (d.jumpEndAge >= 999) return 0f;
        return in * (1f - smooth((d.jumpEndAge + pt) / RECOVER_TICKS));
    }

    private static Key jumpKey(float t) {
        if (t <= T_LOAD) return LOAD;
        if (t <= T_LAUNCH) return mix(LOAD, LAUNCH, smooth((t - T_LOAD) / (T_LAUNCH - T_LOAD)));
        return mix(LAUNCH, AIR, smooth((t - T_LAUNCH) / (T_AIR - T_LAUNCH)));
    }

    /** Vertical stretch used by the renderer for the "extend upwards" part of the launch. */
    public static float stretch(ClimbData d, float pt) {
        float j = jumpWeight(d, pt);
        if (j <= 0f) return 1f;
        return Mth.lerp(j, 1f, jumpKey(jumpTime(d, pt)).stretch);
    }

    /** How far the body tilts away from the wall (degrees), driven by the real outward excursion. */
    public static float leanDegrees(ClimbData d, float pt) {
        return 24f * Mth.clamp(Mth.lerp(pt, d.leanO, d.lean) / 1.2f, 0f, 1f);
    }

    /**
     * Sideways roll of the whole body in degrees, positive = leaning to the player's left. The body leans into the
     * direction of travel, rocks with each hand-over-hand step, and swings to one side during a climb jump.
     */
    public static float rollDegrees(ClimbData d, float pt) {
        float ss = Mth.clamp((float) (Mth.lerp(pt, d.speedSidePrev, d.speedSide) / ClimbConstants.SIDE_SPEED), -1f, 1f);
        float phase = (float) (Mth.lerp(pt, d.distSideO, d.distSide) * CYCLE_SIDE);
        float roll = 9f * ss + 3f * Mth.sin(phase) * Math.abs(ss);
        float t = jumpTime(d, pt);
        float j = jumpWeight(d, pt);
        if (j > 0f) roll += d.jumpSide * 6f * Mth.sin((float) Math.PI * Mth.clamp((t - T_LOAD) / 9f, 0f, 1f)) * j;
        return roll;
    }

    public static float blend(ClimbData d, float pt) {
        return smooth(Mth.lerp(pt, d.blendO, d.blend));
    }

    public static void apply(PlayerModel<?> m, ClimbData d, float pt, float netHeadYaw) {
        float ws = blend(d, pt);
        if (ws <= 0.001f) return;

        float su = Mth.clamp((float) (Mth.lerp(pt, d.speedUpPrev, d.speedUp) / ClimbConstants.CLIMB_SPEED), -1f, 1f);
        float ss = Mth.clamp((float) (Mth.lerp(pt, d.speedSidePrev, d.speedSide) / ClimbConstants.SIDE_SPEED), -1f, 1f);
        float moveAmt = Math.min(1f, Math.max(Math.abs(su), Math.abs(ss)));
        float amp = Mth.lerp(moveAmt, 0.45f, 1f);
        float vDown = Math.max(0f, -su);
        float phase = (float) (Mth.lerp(pt, d.distUpO, d.distUp) * CYCLE_UP
                + Mth.lerp(pt, d.distSideO, d.distSide) * CYCLE_SIDE);
        float s = Mth.sin(phase);
        float lift = 0.5f + 0.5f * s; // 1 when the left arm / right leg is high

        // ---- normal climbing pose: diagonal limbs move together, driven by distance climbed ----
        float armBase = -2.85f + 0.25f * vDown;
        float cLArmX = armBase - 0.16f * s * amp;
        float cRArmX = armBase + 0.16f * s * amp;
        float sideLead = Mth.clamp(ss, 0f, 1f), sideLeadR = Mth.clamp(-ss, 0f, 1f);
        float cLArmY = -2.0f * s * amp + 1.5f * vDown - 1.2f * sideLead;
        float cRArmY = 2.0f * s * amp + 1.5f * vDown - 1.2f * sideLeadR;
        float cLArmZ = 0.12f + 0.45f * ss;   // the leading arm reaches far out, the trailing one crosses in
        float cRArmZ = -(0.12f - 0.45f * ss);
        float cRLegX = -0.28f - 0.50f * s * amp;
        float cLLegX = -0.28f + 0.50f * s * amp;
        float cRLegY = -3.0f * lift * amp;
        float cLLegY = -3.0f * (1f - lift) * amp;
        float cBodyX = 0.05f + 0.08f * vDown;
        float cBodyZ = 0.10f * ss + 0.05f * s * Math.abs(ss) + 0.05f * s * amp;

        // ---- climb jump overlay (the right arm keeps reaching a little longer: it makes the re-grab) ----
        float t = jumpTime(d, pt);
        float j = jumpWeight(d, pt);
        float tuck = Mth.clamp(Mth.lerp(pt, d.leanO, d.lean), 0f, 1f);
        Key k = jumpKey(t);
        float legX = k.legX - 0.45f * tuck * smooth((t - T_LAUNCH) / 3f);
        float jL = j * j;

        float lArmX = Mth.lerp(jL, cLArmX, k.armX + 0.08f * d.jumpSide);
        float rArmX = Mth.lerp(j, cRArmX, k.armX - 0.08f * d.jumpSide);
        float lArmY = Mth.lerp(jL, cLArmY, k.armY);
        float rArmY = Mth.lerp(j, cRArmY, k.armY);
        float lArmZ = Mth.lerp(jL, cLArmZ, k.armSplay);
        float rArmZ = Mth.lerp(j, cRArmZ, -k.armSplay);
        float lLegX = Mth.lerp(j, cLLegX, legX);
        float rLegX = Mth.lerp(j, cRLegX, legX);
        float lLegY = Mth.lerp(j, cLLegY, k.legY);
        float rLegY = Mth.lerp(j, cRLegY, k.legY);
        float bodyX = Mth.lerp(j, cBodyX, k.bodyX);
        float bodyZ = Mth.lerp(j, cBodyZ, 0f);

        // ---- blend onto the vanilla pose ----
        limb(m.leftArm, ws, lArmX, lArmZ, lArmY);
        limb(m.rightArm, ws, rArmX, rArmZ, rArmY);
        float legSpread = Mth.lerp(j, -0.25f * ss, 0f); // legs step wide in the travel direction
        limb(m.leftLeg, ws, lLegX, -0.06f + legSpread, lLegY);
        limb(m.rightLeg, ws, rLegX, 0.06f + legSpread, rLegY);
        m.body.xRot = Mth.lerp(ws, m.body.xRot, bodyX);
        m.body.zRot = Mth.lerp(ws, m.body.zRot, bodyZ);
        m.body.yRot = Mth.lerp(ws, m.body.yRot, 0f);
        m.head.yRot = Mth.lerp(ws, m.head.yRot, Mth.clamp(netHeadYaw, -70f, 70f) * RAD);
        m.head.zRot = Mth.lerp(ws, m.head.zRot, 0f);

        m.hat.copyFrom(m.head);
        m.leftPants.copyFrom(m.leftLeg);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightSleeve.copyFrom(m.rightArm);
        m.jacket.copyFrom(m.body);
    }

    private static void limb(ModelPart part, float w, float x, float z, float yOffset) {
        part.xRot = Mth.lerp(w, part.xRot, x);
        part.yRot = Mth.lerp(w, part.yRot, 0f);
        part.zRot = Mth.lerp(w, part.zRot, z);
        part.y += yOffset * w;
    }
}
