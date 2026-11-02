package com.botwclimb.client.paraglider;

import com.botwclimb.BotwClimb;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;

/**
 * One canopy for every fabric: a wide, slightly drooping wing with a thick leading edge, tassels, two long ribbons and
 * two struts down to the hands. Coordinates are in player-model pixels (y down, shoulders at y = 0, hands at about
 * y = -10). Fabrics only differ in texture.
 */
public final class ParagliderModel extends Model {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "paraglider"), "main");

    private final ModelPart root;
    private final ModelPart ribbonLeft, ribbonRight, wingL, wingR;
    private final ModelPart canopy, shadeCenter, shadeL, shadeR;
    private final ModelPart[] tassels = new ModelPart[7];
    private final ModelPart[] segL = new ModelPart[SEGS], segR = new ModelPart[SEGS]; // ribbon segments, root to tip
    private static final int SEGS = 10; // 2 px each, so the ribbon bends as one smooth curve
    /** Share of the ribbon's bend taken by each segment: the root barely bends, the end bends most. */
    private static final float[] BEND = new float[SEGS];
    static {
        float sum = 0;
        for (int i = 0; i < SEGS; i++) sum += BEND[i] = 0.05f + (float) Math.pow(i / (SEGS - 1f), 1.6);
        for (int i = 0; i < SEGS; i++) BEND[i] /= sum;
    }

    public ParagliderModel(ModelPart root) {
        //? if >=1.21.2 {
        /*super(root, RenderType::entityCutoutNoCull);
        *///?} else {
        super(RenderType::entityCutoutNoCull);
        //?}
        this.root = root;
        this.ribbonLeft = root.getChild("ribbon_l");
        this.ribbonRight = root.getChild("ribbon_r");
        for (int i = 0; i < SEGS; i++) {
            segL[i] = (i == 0 ? ribbonLeft : segL[i - 1]).getChild("seg_l" + (i + 1));
            segR[i] = (i == 0 ? ribbonRight : segR[i - 1]).getChild("seg_r" + (i + 1));
        }
        ModelPart canopy = root.getChild("canopy");
        this.wingL = canopy.getChild("wing_l");
        this.wingR = canopy.getChild("wing_r");
        this.canopy = canopy;
        this.shadeCenter = canopy.getChild("shade");
        this.shadeL = wingL.getChild("shade_l");
        this.shadeR = wingR.getChild("shade_r");
        setShade(false);
        for (int i = 0; i < tassels.length; i++) tassels[i] = canopy.getChild("tassel_" + i);
    }

    /**
     * Fixed UV layout shared by every fabric (see tools/make_fabrics.py): centre net (0,0), left wing net (64,0),
     * right wing net (0,17), edge strip (0,34), tassel (0,40), ribbon net (64,40), strut net (110,40).
     */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition canopy = root.addOrReplaceChild("canopy", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-8, -1, -8, 16, 1, 16)
                        .texOffs(0, 34).addBox(-8, -2, -10, 16, 2, 2),
                PartPose.offset(0, -22, 0));
        PartDefinition wingL = canopy.addOrReplaceChild("wing_l", CubeListBuilder.create()
                .texOffs(64, 0).addBox(0, -1, -8, 12, 1, 16)
                .texOffs(0, 34).addBox(0, -2, -10, 12, 2, 2), PartPose.offsetAndRotation(8, 0, 0, 0, 0, 0.28f));
        wingL.addOrReplaceChild("shade_l", CubeListBuilder.create()
                .texOffs(64, 0).addBox(0, 0.01f, -8, 12, 0.01f, 16), PartPose.ZERO);
        PartDefinition wingR = canopy.addOrReplaceChild("wing_r", CubeListBuilder.create()
                .texOffs(0, 17).addBox(-12, -1, -8, 12, 1, 16)
                .texOffs(0, 34).addBox(-12, -2, -10, 12, 2, 2), PartPose.offsetAndRotation(-8, 0, 0, 0, 0, -0.28f));
        wingR.addOrReplaceChild("shade_r", CubeListBuilder.create()
                .texOffs(0, 17).addBox(-12, 0.01f, -8, 12, 0.01f, 16), PartPose.ZERO);
        // the whole underside of the centre panel, just under the canopy
        canopy.addOrReplaceChild("shade", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-8, 0.01f, -8, 16, 0.01f, 16), PartPose.ZERO);
        // tassels along the trailing edge (the outer ones follow the drooping wings)
        float[][] tassels = {{0, 0}, {6, 0}, {-6, 0}, {11.8f, 1.1f}, {-11.8f, 1.1f}, {17.6f, 2.8f}, {-17.6f, 2.8f}};
        for (int i = 0; i < tassels.length; i++) {
            canopy.addOrReplaceChild("tassel_" + i, CubeListBuilder.create()
                    .texOffs(0, 40).addBox(-0.5f, 0, -0.5f, 1, 4, 1), PartPose.offset(tassels[i][0], tassels[i][1], 8));
        }
        // two long ribbons trailing behind and below the canopy, each ten linked 2-long segments so they bend smoothly
        for (String side : new String[]{"l", "r"}) {
            float x = side.equals("l") ? 5 : -5;
            PartDefinition seg = root.addOrReplaceChild("ribbon_" + side, CubeListBuilder.create(),
                    PartPose.offsetAndRotation(x, -22, 8, -0.45f, 0, 0));
            for (int i = 1; i <= SEGS; i++) {
                seg = seg.addOrReplaceChild("seg_" + side + i, CubeListBuilder.create()
                                .texOffs(64 + 8 * ((i - 1) % 5), 40 + 3 * ((i - 1) / 5)).addBox(-1, 0, 0, 2, 1, 2),
                        i == 1 ? PartPose.ZERO : PartPose.offset(0, 0, 2));
            }
        }
        root.addOrReplaceChild("strut_l", CubeListBuilder.create()
                .texOffs(110, 40).addBox(-0.5f, 0, -0.5f, 1, 13, 1), PartPose.offset(5, -22, -1));
        root.addOrReplaceChild("strut_r", CubeListBuilder.create()
                .texOffs(110, 40).addBox(-0.5f, 0, -0.5f, 1, 13, 1), PartPose.offset(-5, -22, -1));
        return LayerDefinition.create(mesh, 128, 64);
    }

    /**
     * Pose the loose parts for the air rushing past: windF / windR in -1..1 (+ = air from the front / from the right).
     * Wing tips lift and yaw a little, tassels and ribbons trail backwards, and everything flutters slowly.
     */
    public void flutter(float windF, float windR, float age, float gust, float rocket) {
        float wobble = (0.02f + 0.04f * gust) * Mth.sin(age * 0.3f);
        wingL.zRot = 0.28f + wobble - 0.04f * windF;
        wingR.zRot = -0.28f - wobble + 0.04f * windF;
        wingL.yRot = 0f;
        wingR.yRot = 0f;
        for (int i = 0; i < tassels.length; i++) {
            tassels[i].xRot = 0.10f + 0.70f * windF + (0.10f + 0.25f * gust) * Mth.sin(age * 0.45f + i * 1.7f);
            tassels[i].zRot = 0.45f * windR + (0.08f + 0.20f * gust) * Mth.sin(age * 0.5f + i);
        }
        // ribbons: the root only droops (slow) or lies level (cruise); the waving happens along the segments, a wave
        // that runs down the ribbon, strongest at the tip. A rocket boost makes it whip up and down much harder.
        float level = Math.max(0f, Math.min(1f, windF));
        float droop = -0.45f * (1f - level);
        ribbonLeft.xRot = droop;
        ribbonRight.xRot = droop;
        ribbonLeft.yRot = 0.04f * windR;   // the ribbons run along Z, so sideways sway is a yaw (Y), not a roll (Z)
        ribbonRight.yRot = 0.04f * windR;
        // the wind pushes the whole ribbon upward as speed climbs (rocket = 0..1 follows speed), curving it toward
        // the end; one slow travelling wave rides on top, the same function for every segment so it stays connected
        float lift = 1.2f * rocket * rocket + 0.12f * level;
        float wave = 0.12f + 0.04f * rocket, side = 0.5f + 0.15f * rocket; // total angle at the tip (radians)
        float t = age * (0.21f + 0.05f * rocket); // calm at speed: the rocket adds only a little tempo
        for (int i = 0; i < SEGS; i++) {
            float p = t - i * 0.25f;
            segL[i].xRot = BEND[i] * (lift + wave * Mth.sin(p));
            segR[i].xRot = BEND[i] * (lift + wave * Mth.sin(p + 1.9f));
            segL[i].yRot = BEND[i] * side * Mth.sin(p * 0.8f + 0.7f);
            segR[i].yRot = -BEND[i] * side * Mth.sin(p * 0.8f + 0.7f);
        }
    }

    public void setRibbons(boolean visible) {
        ribbonLeft.visible = visible;
        ribbonRight.visible = visible;
    }

    private void setShade(boolean on) {
        shadeCenter.visible = on;
        shadeL.visible = on;
        shadeR.visible = on;
    }

    /**
     * The underside, drawn after the canopy in a dark tint. Each shade plane is drawn inside its part's own
     * transform, so it moves and bends with the wing or panel it belongs to.
     */
    public void renderShade(PoseStack poseStack, VertexConsumer buffer, int packedLight) {
        final int tint = 0xFF333333;
        setShade(true);
        poseStack.pushPose();
        canopy.translateAndRotate(poseStack);
        shadeCenter.render(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, tint);
        poseStack.pushPose();
        wingL.translateAndRotate(poseStack);
        shadeL.render(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, tint);
        poseStack.popPose();
        poseStack.pushPose();
        wingR.translateAndRotate(poseStack);
        shadeR.render(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, tint);
        poseStack.popPose();
        poseStack.popPose();
        setShade(false);
    }

    //? if <1.21.2 {
    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
    //?}
}
