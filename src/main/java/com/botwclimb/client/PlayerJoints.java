package com.botwclimb.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Elbows and knees for the player model. Vanilla draws each arm and leg as one 12 pixel block; here each is two 6 pixel blocks, the lower one a
 * child part called "joint" that hangs from the middle of the upper one. Both blocks use the skin's own pixels (the top half and the bottom half
 * of the old block), so a player with the joints at rest looks exactly as before. Only the Tears of the Kingdom ride bends them (see TotkRide).
 * Armor has its own model and stays stiff.
 * <p>
 * shortcut: only for Minecraft before 1.21.9, whose player model is built the way written below; from 1.21.9 the model changed and the limbs stay
 * single blocks (TotkRide then leaves the elbows and knees out).
 */
public final class PlayerJoints {
    public static final String JOINT = "joint";

    private PlayerJoints() {}

    /** Called when the player model's mesh has just been built: swaps the arms, sleeves, legs and pants for the two-block versions. */
    public static void split(PartDefinition root, CubeDeformation deform, boolean slim) {
        //? if <1.21.9 {
        CubeDeformation outer = deform.extend(0.25f);
        float ax = slim ? 3f : 4f, ay = slim ? 2.5f : 2f;
        // right arm (-5, ay), left arm (5, ay): the box is 4 (slim 3) wide, 12 tall from 2 above the pivot to 10 below it
        limb(root, "right_arm", 40, 16, slim ? -2f : -3f, -2f, ax, 12f, PartPose.offset(-5f, ay, 0f), deform);
        limb(root, "right_sleeve", 40, 32, slim ? -2f : -3f, -2f, ax, 12f, PartPose.offset(-5f, ay, 0f), outer);
        limb(root, "left_arm", 32, 48, -1f, -2f, ax, 12f, PartPose.offset(5f, ay, 0f), deform);
        limb(root, "left_sleeve", 48, 48, -1f, -2f, ax, 12f, PartPose.offset(5f, ay, 0f), outer);
        limb(root, "right_leg", 0, 16, -2f, 0f, 4f, 12f, PartPose.offset(-1.9f, 12f, 0f), deform);
        limb(root, "right_pants", 0, 32, -2f, 0f, 4f, 12f, PartPose.offset(-1.9f, 12f, 0f), outer);
        limb(root, "left_leg", 16, 48, -2f, 0f, 4f, 12f, PartPose.offset(1.9f, 12f, 0f), deform);
        limb(root, "left_pants", 0, 48, -2f, 0f, 4f, 12f, PartPose.offset(1.9f, 12f, 0f), outer);
        //?}
    }

    /**
     * The same elbows and knees for the armor drawn over a player (built by HumanoidArmorModel), so a bent knee bends the leggings and
     * boots with it instead of the foot poking out of them. Only before 1.21.2: from then the armor is posed from a render state, which
     * carries no joints.
     */
    public static void splitArmor(PartDefinition root, CubeDeformation deform) {
        //? if <1.21.2 {
        CubeDeformation legs = deform.extend(-0.1f);
        limb(root, "right_arm", 40, 16, -3f, -2f, 4f, 12f, PartPose.offset(-5f, 2f, 0f), deform, false);
        limb(root, "left_arm", 40, 16, -1f, -2f, 4f, 12f, PartPose.offset(5f, 2f, 0f), deform, true);
        limb(root, "right_leg", 0, 16, -2f, 0f, 4f, 12f, PartPose.offset(-1.9f, 12f, 0f), legs, false);
        limb(root, "left_leg", 0, 16, -2f, 0f, 4f, 12f, PartPose.offset(1.9f, 12f, 0f), legs, true);
        //?}
    }

    /** Puts the elbows and knees of one model (the player's) on another (their armor). */
    public static void copyJoints(net.minecraft.client.model.HumanoidModel<?> from, net.minecraft.client.model.HumanoidModel<?> to) {
        //? if <1.21.9 {
        copy(to.leftArm, from.leftArm);
        copy(to.rightArm, from.rightArm);
        copy(to.leftLeg, from.leftLeg);
        copy(to.rightLeg, from.rightLeg);
        //?}
    }

    //? if <1.21.9 {
    private static void limb(PartDefinition root, String name, int u, int v, float x, float y, float w, float h, PartPose pose, CubeDeformation deform) {
        limb(root, name, u, v, x, y, w, h, pose, deform, false);
    }

    /** One limb as an upper half and a lower half "joint" hanging 6 pixels below the top of the box; {@code mirror} flips the texture (left armor). */
    private static void limb(PartDefinition root, String name, int u, int v, float x, float y, float w, float h, PartPose pose, CubeDeformation deform, boolean mirror) {
        float half = h / 2f;
        // the upper half carries a 2 pixel cap past the joint, a hair inside the lower half and showing the same skin pixels: hidden while the
        // limb is straight, it fills the wedge that opens on the outside of the joint when the lower half bends
        PartDefinition upper = root.addOrReplaceChild(name, CubeListBuilder.create().mirror(mirror).texOffs(u, v).addBox(x, y, -2f, w, half, 4f, deform)
                .texOffs(u, v + (int) half).addBox(x, y + half, -2f, w, 2f, 4f, deform.extend(-0.03f)), pose);
        upper.addOrReplaceChild(JOINT, CubeListBuilder.create().mirror(mirror).texOffs(u, v + (int) half).addBox(x, 0f, -2f, w, half, 4f, deform), PartPose.offset(0f, y + half, 0f));
    }
    //?}

    /** The lower half of a limb, or null where the model has none (Minecraft 1.21.9 and later). */
    public static ModelPart joint(ModelPart limb) {
        return limb != null && limb.hasChild(JOINT) ? limb.getChild(JOINT) : null;
    }

    /** Puts the lower halves of the outer layers (sleeves, pants) in the pose of the arms and legs. */
    public static void sync(PlayerModel m) {
        //? if <1.21.9 {
        copy(m.leftSleeve, m.leftArm);
        copy(m.rightSleeve, m.rightArm);
        copy(m.leftPants, m.leftLeg);
        copy(m.rightPants, m.rightLeg);
        //?}
    }

    //? if <1.21.9 {
    private static void copy(ModelPart outer, ModelPart limb) {
        ModelPart a = joint(outer), b = joint(limb);
        if (a != null && b != null) a.copyFrom(b);
    }
    //?}

    /** Straightens all four elbows and knees (the poses the ride sets would otherwise stay on every player drawn afterwards). */
    public static void straighten(PlayerModel m) {
        //? if <1.21.9 {
        for (ModelPart limb : new ModelPart[] {m.leftArm, m.rightArm, m.leftLeg, m.rightLeg, m.leftSleeve, m.rightSleeve, m.leftPants, m.rightPants}) {
            ModelPart j = joint(limb);
            if (j != null) { j.xRot = 0f; j.yRot = 0f; j.zRot = 0f; }
        }
        //?}
    }
}
