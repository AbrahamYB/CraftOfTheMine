package com.botwclimb.client.paraglider;

import com.botwclimb.common.paraglider.ParagliderConstants;
import com.botwclimb.common.paraglider.ParagliderData;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Hanging-under-the-glider pose and the render-only bank / sway numbers. Radians for rotations, pixels for pivots. */
public final class ParagliderAnimator {
    private ParagliderAnimator() {}

    private static final float RAD = (float) Math.PI / 180f;

    private static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    /** 0 closed .. 1 open, eased; drives pose blend and canopy unfolding. */
    public static float open(ParagliderData g, float pt) {
        return smooth(Mth.lerp(pt, g.deployO, g.deploy));
    }

    /** Roll of the whole rig in degrees, positive = leaning to the player's right: turn bank plus a slow passive sway. */
    public static float rollDegrees(ParagliderData g, float pt, float age) {
        return Mth.lerp(pt, g.bankO, g.bank) + ParagliderConstants.PASSIVE_SWAY_DEGREES * Mth.sin(age * 0.11f);
    }

    public static float pitchDegrees(ParagliderData g, float pt) {
        return Mth.lerp(pt, g.pitchO, g.pitch);
    }

    public static void apply(PlayerModel m, ParagliderData g, float pt, float age, float netHeadYaw) {
        float w = open(g, pt);
        if (w <= 0.001f) return;

        float lagF = Mth.lerp(pt, g.lagFO, g.lagF);   // + = accelerating forward: legs swing back
        float lagR = Mth.lerp(pt, g.lagRO, g.lagR);   // + = accelerating right: legs swing left
        float bank = Mth.lerp(pt, g.bankO, g.bank) * RAD;
        float sway = Mth.sin(age * 0.11f);

        // both hands grip the struts above the head, shoulders raised
        arm(m.leftArm, w, -3.0f + 0.04f * sway, 0.10f, -1.5f);
        arm(m.rightArm, w, -3.0f - 0.04f * sway, -0.10f, -1.5f);
        // legs hang and trail: bent forward a little, lagging behind accelerations, outside of a turn
        float legBase = 0.28f + 0.35f * lagF;
        float legSide = -(0.9f * bank + 0.35f * lagR);
        arm(m.leftLeg, w, legBase + 0.18f + 0.05f * sway, legSide - 0.05f, 0);
        arm(m.rightLeg, w, legBase - 0.05f - 0.05f * sway, legSide + 0.05f, 0);
        m.body.xRot = Mth.lerp(w, m.body.xRot, 0.04f);
        m.body.zRot = Mth.lerp(w, m.body.zRot, -0.3f * bank);
        m.body.yRot = Mth.lerp(w, m.body.yRot, 0f);
        m.head.yRot = Mth.lerp(w, m.head.yRot, Mth.clamp(netHeadYaw, -70f, 70f) * RAD);
        m.head.zRot = Mth.lerp(w, m.head.zRot, 0f);

        m.hat.copyFrom(m.head);
        m.leftPants.copyFrom(m.leftLeg);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightSleeve.copyFrom(m.rightArm);
        m.jacket.copyFrom(m.body);
    }

    private static void arm(ModelPart part, float w, float x, float z, float yOffset) {
        part.xRot = Mth.lerp(w, part.xRot, x);
        part.yRot = Mth.lerp(w, part.yRot, 0f);
        part.zRot = Mth.lerp(w, part.zRot, z);
        part.y += yOffset * w;
    }
}
