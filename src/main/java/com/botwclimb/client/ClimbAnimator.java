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
    private static final float CYCLE_UP = 3.6f;   // radians of limb cycle per block climbed (slower, rounder than before)
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

    private static final float HOOK_LEG_X = -1.45f; // thigh swings forward and up, foot level with the wall
    private static final float HOOK_LEG_Y = -2.2f;  // knee bent so the foot can reach the surface
    private static final float HOOK_ARM_X = -2.9f;  // right arm reaching up for the grip
    private static final float HOOK_IN = 4f;        // ticks to lift the leg
    private static final float HOOK_HOLD = 11f;      // ticks the hook is held, then the climb cycle takes over
    private static final float HOOK_OUT = 7f;

    /** 0 -> 1 while the hook pose is lifted in, 1 while held, back to 0 as the climb cycle takes over. */
    private static float hookWeight(ClimbData d, float pt) {
        if (d.grabAge >= 999) return 0f;
        float t = d.grabAge + pt;
        return smooth(t / HOOK_IN) * (1f - smooth((t - HOOK_HOLD) / HOOK_OUT));
    }

    // ---- climb cycle value ----
    public static float basePhase(ClimbData d) {
        return (float) (d.distUp * CYCLE_UP + d.distSide * CYCLE_SIDE) + d.phaseOffset;
    }

    /** The -1..1 value (positive: left arm and right leg high) of the uneven climb cycle at a phase. */
    public static float climbS(float phase) {
        return Mth.sin(phase + 0.6f * Mth.sin(phase * 0.37f) + 0.25f * Mth.sin(phase * 1.9f + 1.1f));
    }

    /** Smooth 0..1..0 window over [a,b]. */
    private static float bump(float t, float a, float b) {
        if (t <= a || t >= b) return 0f;
        float x = Mth.sin((float) Math.PI * (t - a) / (b - a));
        return x * x;
    }

    /** Ticks over which an absorbed pose switch decays to exactly zero. */
    private static final float INERTIA_TICKS = 10f;

    /**
     * Inertialization (Bollo / Gears of War 4, popularised by Daniel Holden): at a pose switch the difference between
     * the last pose shown and the new target is stored as an offset, and that offset decays smoothly to zero over
     * INERTIA_TICKS. The limbs keep moving from where they were instead of jumping to the new pose. The offset is only
     * set on a switch, never every frame, so normal motion is not lagged.
     */
    private static void inertia(ClimbData d, float[] v) {
        if (d.poseEvent) {
            for (int i = 0; i < v.length; i++) d.poseOff[i] = d.poseOut[i] - v[i];
            d.poseAge = 0;
            d.poseEvent = false;
        } else if (d.poseAge < 999) {
            d.poseAge++;
        }
        float w = 1f - smooth(d.poseAge / INERTIA_TICKS);
        for (int i = 0; i < v.length; i++) {
            v[i] += d.poseOff[i] * w;
            d.poseOut[i] = v[i];
        }
    }

    private static float jumpTime(ClimbData d, float pt) {
        return Mth.lerp(pt, d.jumpAgeO, d.jumpAge) * (float) d.jumpSpeed; // jump time, so a slower jump plays the whole animation slower
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

    /** 0..1 how much the long dash under a deep overhang is in its leaping part (0 for every other move). */
    private static float leapWeight(ClimbData d, float pt) {
        if (d.state != ClimbState.LEDGE_TRANSITION || d.ledgeOutNeed < 1.5 || d.ledgeGrabbed) return 0f;
        float q = (float) Mth.clamp((d.ledgeOut + pt * d.ledgeOutSpeed) / d.ledgeOutNeed, 0, 1);
        return smooth(q / 0.45f) * (1f - smooth((q - 0.55f) / 0.45f)); // a slow lean in and a slow swing out, not a snap
    }

    /**
     * Whole-body tilt away from the wall for the long dash: the body lays out almost flat along the line of travel
     * (head leading, like the last moment of falling onto a bed), then swings back up to vertical so the hands
     * land on the edge with the legs coming down under them, the way a leap-to-ledge is staged in action games.
     */
    public static float leapDegrees(ClimbData d, float pt) {
        float lw = leapWeight(d, pt);
        float q = (float) Mth.clamp((d.ledgeOut + pt * d.ledgeOutSpeed) / Math.max(0.1, d.ledgeOutNeed), 0, 1);
        return lw * (74f + 6f * Mth.sin(q * 17f)); // the body strains and wavers as it lays out
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

    /** Limbs flail and the body tumbles while knocked off a wall; the pose eases out over the last few ticks. */
    /** Shoulder rotation that puts the hands on the ledge top when the shoulder pivot is `gap` blocks below it (arm 0.75 long). */
    private static float armToGap(float gap) {
        return -(float) (Math.PI - Math.acos(Mth.clamp(gap / 0.75f, -1f, 1f)));
    }

    /**
     * The climb-up pose, a function of the same progress value (mantleT) that moves the player. The hands stay on the
     * ledge: the arm angle is recomputed from where the shoulders are, so the body rises towards fixed hands and the arms
     * swing from pulling to pushing by themselves. One leg (alternating) comes over the top first, the other follows,
     * the chest leans over the edge, and everything eases back to neutral over the last quarter.
     */
    private static float[] mantlePose(ClimbData d, float pt) {
        float t = Mth.lerp(pt, d.mantleTO, d.mantleT);
        float gap = Mth.lerp(pt, d.ledgeGapO, d.ledgeGap);
        float out = smooth((t - 0.72f) / 0.28f);
        float keep = 1f - out;
        float draw = bump(t, 0f, 0.3f); // drawing in before the thrust: arms bend, knees come up, back hunches
        float arm = (armToGap(gap) + 0.7f * draw) * keep;
        float land = bump(t, 0.72f, 1f); // absorbing the landing: knees give, back hunches
        float lean = 0.10f + 0.25f * draw + 0.70f * smooth((t - 0.25f) / 0.3f) + 0.25f * land; // hunched to load, then thrown over the edge
        lean = Mth.lerp(smooth((t - 0.5f) / 0.35f), lean, 0.25f) * keep;
        float lead = -0.8f * draw - 0.9f * smooth((t - 0.3f) / 0.2f), trail = -0.9f * bump(t, 0.5f, 0.9f);
        boolean leftLeads = d.ledgeSide != 0 ? d.ledgeSide > 0 : d.jumpSide > 0; // diagonal: the leg on the side you came from leads
        float sd = d.ledgeSide;
        float lBase = -0.35f, rBase = -0.25f;
        float leadX = Mth.lerp(smooth((t - 0.62f) / 0.28f), (leftLeads ? lBase : rBase) + lead, -0.3f);
        float lLeg = (leftLeads ? leadX : lBase + trail - 0.8f * draw) * keep - 0.7f * land, rLeg = (leftLeads ? rBase + trail - 0.8f * draw : leadX) * keep - 0.7f * land;
        // effort: the arms heave in turns, the body sways and twitches; strongest while pulling, gone by the end
        float eff = Mth.sin((float) Math.PI * Mth.clamp(t / 0.8f, 0f, 1f)) * keep;
        float heave = Mth.sin(t * 17f), alt = Mth.sin(t * 17f + 1.9f);
        float twist = Mth.sin(t * 13f + 0.7f);
        float side = Mth.sin((float) Math.PI * t) * keep; // leaning and reaching towards the side being climbed
        return new float[]{arm - 0.30f * heave * eff - 0.26f * sd * side, 0.15f * keep + 0.14f * alt * eff, 0.18f * twist * eff,
                arm + 0.30f * heave * eff + 0.26f * sd * side, -0.15f * keep - 0.14f * alt * eff, -0.18f * twist * eff,
                lLeg + 0.12f * alt * eff, 0, rLeg - 0.12f * alt * eff, 0, lean + 0.07f * heave * eff, 0.10f * Mth.sin(t * 11f + 0.6f) * eff + 0.36f * sd * side,
                0.42f * sd * keep}; // torso turned into the diagonal (same as the hang, so no pop)
    }

    private static void applyRagdoll(PlayerModel m, ClimbData d, float pt) {
        float t = d.ragdollAge + pt;
        float fade = 1f - smooth((t - (ClimbConstants.RAGDOLL_TICKS - 5f)) / 5f);
        m.leftArm.xRot = Mth.lerp(fade, m.leftArm.xRot, -1.8f + 1.1f * Mth.sin(t * 1.9f));
        m.rightArm.xRot = Mth.lerp(fade, m.rightArm.xRot, -1.2f + 1.1f * Mth.sin(t * 2.3f + 1f));
        m.leftArm.zRot = Mth.lerp(fade, m.leftArm.zRot, 0.5f + 0.4f * Mth.sin(t * 1.4f));
        m.rightArm.zRot = Mth.lerp(fade, m.rightArm.zRot, -0.5f + 0.4f * Mth.sin(t * 1.7f + 2f));
        m.leftLeg.xRot = Mth.lerp(fade, m.leftLeg.xRot, -0.8f + 0.9f * Mth.sin(t * 1.6f + 0.5f));
        m.rightLeg.xRot = Mth.lerp(fade, m.rightLeg.xRot, -0.6f + 0.9f * Mth.sin(t * 2.0f + 3f));
        m.body.zRot = Mth.lerp(fade, m.body.zRot, 0.35f * Mth.sin(t * 0.8f));
        m.body.xRot = Mth.lerp(fade, m.body.xRot, 0.3f + 0.2f * Mth.sin(t * 1.1f));
        m.hat.copyFrom(m.head);
        m.leftPants.copyFrom(m.leftLeg);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightSleeve.copyFrom(m.rightArm);
        m.jacket.copyFrom(m.body);
    }

    public static void apply(PlayerModel m, ClimbData d, float pt, float netHeadYaw) {
        if (d.ragdollTicks > 0) {
            applyRagdoll(m, d, pt);
            return;
        }
        float ws = blend(d, pt);
        if (ws <= 0.001f) {
            // still on the ground while the climb key is held: lift the right leg and reach for the wall as it charges
            float charge = d.state == ClimbState.NONE && d.keyHold > 0
                    ? smooth((d.keyHold + pt) / ClimbConstants.CLIMB_HOLD_TICKS) : 0f;
            if (charge > 0.001f) {
                limb(m.rightLeg, charge, HOOK_LEG_X, 0.06f, HOOK_LEG_Y);
                limb(m.rightArm, charge, HOOK_ARM_X, -0.15f, 0f);
                m.body.xRot = Mth.lerp(charge, m.body.xRot, 0.12f);
                m.body.zRot = Mth.lerp(charge, m.body.zRot, 0.10f);
            }
            return;
        }

        float su = Mth.clamp((float) (Mth.lerp(pt, d.speedUpPrev, d.speedUp) / ClimbConstants.CLIMB_SPEED), -1f, 1f);
        float ss = Mth.clamp((float) (Mth.lerp(pt, d.speedSidePrev, d.speedSide) / ClimbConstants.SIDE_SPEED), -1f, 1f);
        float moveAmt = Math.min(1f, Math.max(Math.abs(su), Math.abs(ss)));
        float amp = Mth.lerp(moveAmt, 0.45f, 1f);
        float vDown = Math.max(0f, -su);
        float phase = (float) (Mth.lerp(pt, d.distUpO, d.distUp) * CYCLE_UP
                + Mth.lerp(pt, d.distSideO, d.distSide) * CYCLE_SIDE) + d.phaseOffset;
        // Uneven cadence: the grip rhythm speeds up and slows down with the surface, like a struggle, not a metronome.
        float wobblePhase = phase + 0.6f * Mth.sin(phase * 0.37f) + 0.25f * Mth.sin(phase * 1.9f + 1.1f);
        float s = Mth.sin(wobblePhase);
        float lift = 0.5f + 0.5f * s; // 1 when the left arm / right leg is high
        // Each limb strains on its own frequency, so the pull never looks perfectly mirrored.
        float strain = Math.min(1f, Math.abs(su) + Math.abs(ss) + 0.25f);
        float tremorL = 0.07f * strain * Mth.sin(phase * 2.9f + 0.4f);
        float tremorR = 0.07f * strain * Mth.sin(phase * 3.3f + 2.2f);
        float kneeKick = 0.20f * Mth.sin(phase * 2f) * strain; // hips and knees push off in short bursts

        // ---- normal climbing pose: diagonal limbs move together, driven by distance climbed ----
        float armBase = -2.85f + 0.25f * vDown;
        float cLArmX = armBase - 0.16f * s * amp + tremorL;
        float cRArmX = armBase + 0.16f * s * amp + tremorR;
        float sideLead = Mth.clamp(ss, 0f, 1f), sideLeadR = Mth.clamp(-ss, 0f, 1f);
        float cLArmY = -2.0f * s * amp + 1.5f * vDown - 1.2f * sideLead;
        float cRArmY = 2.0f * s * amp + 1.5f * vDown - 1.2f * sideLeadR;
        float cLArmZ = 0.12f + 0.45f * ss;   // the leading arm reaches far out, the trailing one crosses in
        float cRArmZ = -(0.12f - 0.45f * ss);
        float cRLegX = -0.28f - 0.50f * s * amp - kneeKick;
        float cLLegX = -0.28f + 0.50f * s * amp - kneeKick;
        float cRLegY = -3.0f * lift * amp;
        float cLLegY = -3.0f * (1f - lift) * amp;
        float cBodyX = 0.05f + 0.08f * vDown + 0.07f * Mth.cos(phase * 2f) * strain; // body rises and sinks twice a cycle
        float cBodyZ = 0.10f * ss + 0.05f * s * Math.abs(ss) + 0.05f * s * amp + 0.04f * Mth.sin(phase * 1.3f) * strain;

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
        float bodyTwist = 0f; // torso turn about its own axis
        float legSplay = 0f;  // legs spread apart sideways

        // ---- first latch: right leg kicks up and hooks its foot onto the wall, right arm reaches up, then climbs ----
        float g = hookWeight(d, pt) * (1f - j);
        if (g > 0f) {
            // effort: the hand and leg fight for the grip, the torso sways and shakes a little, all fading out
            float age = d.grabAge + pt;
            float shake = (1f - smooth(age / HOOK_HOLD)) * 0.04f; // a wobble, not an earthquake
            rLegX = Mth.lerp(g, rLegX, HOOK_LEG_X + shake * Mth.sin(age * 1.9f));
            rLegY = Mth.lerp(g, rLegY, HOOK_LEG_Y + shake * Mth.sin(age * 2.6f));
            rArmX = Mth.lerp(g, rArmX, HOOK_ARM_X + shake * Mth.sin(age * 2.3f + 1f));
            rArmY = Mth.lerp(g, rArmY, 0f);
            rArmZ = Mth.lerp(g, rArmZ, -0.15f + shake * Mth.sin(age * 1.4f));
            lArmX = Mth.lerp(g, lArmX, -1.6f + shake * Mth.sin(age * 2.0f));
            bodyX = Mth.lerp(g, bodyX, 0.12f + shake * 0.5f * Mth.sin(age * 1.1f));
            // a slow side-to-side sway of the body while it hangs on, fading out as it settles
            bodyZ = Mth.lerp(g, bodyZ, 0.10f + 0.07f * (1f - smooth(age / HOOK_HOLD)) * Mth.sin(age * 0.6f));
        }

        // ---- ledge grip: hands land on the top edge, then push down while a knee comes up and the body is pulled over.
        if (d.state == ClimbState.LEDGE_TRANSITION && d.mantleTick > 0) {
            float[] v = mantlePose(d, pt);
            lArmX = v[0]; lArmZ = v[1]; lArmY = v[2]; rArmX = v[3]; rArmZ = v[4]; rArmY = v[5];
            lLegX = v[6]; lLegY = v[7]; rLegX = v[8]; rLegY = v[9]; bodyX = v[10]; bodyZ = v[11]; bodyTwist = v[12];
        } else if (d.state == ClimbState.LEDGE_TRANSITION) {
            float gu = d.ledgeU;
            float grab = smooth(gu / 0.3f);                     // climb pose -> hands on the edge
            float push = smooth((gu - 0.4f) / 0.45f);            // hands push down, body is pulled over
            float armX = Mth.lerp(push, armToGap(Mth.lerp(pt, d.ledgeGapO, d.ledgeGap)), -0.4f);       // reaching up and a little forward: the hands land on the front edge, not inside the block
            lArmX = Mth.lerp(grab, lArmX, armX); rArmX = Mth.lerp(grab, rArmX, armX);
            lArmY = Mth.lerp(grab, lArmY, 0f);   rArmY = Mth.lerp(grab, rArmY, 0f);
            lArmZ = Mth.lerp(grab, lArmZ, 0.15f); rArmZ = Mth.lerp(grab, rArmZ, -0.15f);
            lLegX = Mth.lerp(grab, lLegX, -0.35f - 1.0f * push);   // the near knee drives up and over
            rLegX = Mth.lerp(grab, rLegX, -0.25f);
            lLegY = Mth.lerp(grab, lLegY, -1.5f * push); rLegY = Mth.lerp(grab, rLegY, 0f);
            bodyX = Mth.lerp(grab, bodyX, 0.10f + 0.28f * push);
            bodyTwist = 0.42f * d.ledgeSide * grab;
            float lw = leapWeight(d, pt); // the long dash under a deep overhang: the whole body is tilted in render (leapDegrees), the limbs stretch out along it
            float lq = (float) Mth.clamp((d.ledgeOut + pt * d.ledgeOutSpeed) / Math.max(0.1, d.ledgeOutNeed), 0, 1);
            float lstrA = Mth.sin(lq * 19f), lstrB = Mth.sin(lq * 19f + 2.1f); // effort: the arms reach in alternating bursts, the legs kick unevenly
            lArmX = Mth.lerp(lw, lArmX, -3.0f + 0.25f * lstrA); rArmX = Mth.lerp(lw, rArmX, -3.0f - 0.25f * lstrA);
            lArmZ = Mth.lerp(lw, lArmZ, 0.25f + 0.12f * lstrB); rArmZ = Mth.lerp(lw, rArmZ, -0.25f - 0.12f * lstrB);
            // the legs move on their own, not locked to the torso: a wide scissoring stride (one kicks forward while the other trails), knees working, spread apart
            float scissor = Mth.sin(lq * 3.6f + 0.3f); // one slow, smooth kick across the dash
            lLegX = Mth.lerp(lw, lLegX, -0.35f + 0.95f * scissor + 0.08f * lstrB);
            rLegX = Mth.lerp(lw, rLegX, -0.35f - 0.95f * scissor - 0.08f * lstrB);
            legSplay = 0.35f * lw;
        }

        // ---- absorb pose switches (state changes) so they flow ----
        float[] ch = {lArmX, lArmZ, lArmY, rArmX, rArmZ, rArmY, lLegX, lLegY, rLegX, rLegY, bodyX, bodyZ};
        inertia(d, ch);
        lArmX = ch[0]; lArmZ = ch[1]; lArmY = ch[2]; rArmX = ch[3]; rArmZ = ch[4]; rArmY = ch[5];
        lLegX = ch[6]; lLegY = ch[7]; rLegX = ch[8]; rLegY = ch[9]; bodyX = ch[10]; bodyZ = ch[11];

        // ---- blend onto the vanilla pose ----
        limb(m.leftArm, ws, lArmX, lArmZ, lArmY);
        limb(m.rightArm, ws, rArmX, rArmZ, rArmY);
        float legSpread = Mth.lerp(j, -0.25f * ss, 0f); // legs step wide in the travel direction
        limb(m.leftLeg, ws, lLegX, -0.06f + legSpread - legSplay, lLegY);
        limb(m.rightLeg, ws, rLegX, 0.06f + legSpread + legSplay, rLegY);
        m.body.xRot = Mth.lerp(ws, m.body.xRot, bodyX);
        m.body.zRot = Mth.lerp(ws, m.body.zRot, bodyZ);
        m.body.yRot = Mth.lerp(ws, m.body.yRot, bodyTwist);
        // limbs are separate parts in the vanilla model and would stay facing straight ahead: turn them with the torso (legs a bit more, they lead the diagonal)
        m.leftArm.yRot = Mth.lerp(ws, m.leftArm.yRot, bodyTwist);
        m.rightArm.yRot = Mth.lerp(ws, m.rightArm.yRot, bodyTwist);
        m.leftLeg.yRot = Mth.lerp(ws, m.leftLeg.yRot, 1.4f * bodyTwist);
        m.rightLeg.yRot = Mth.lerp(ws, m.rightLeg.yRot, 1.4f * bodyTwist);
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
