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
    private final ModelPart ribbonLeft, ribbonRight;

    public ParagliderModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
        this.ribbonLeft = root.getChild("ribbon_l");
        this.ribbonRight = root.getChild("ribbon_r");
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
        canopy.addOrReplaceChild("wing_l", CubeListBuilder.create()
                .texOffs(64, 0).addBox(0, -1, -8, 12, 1, 16)
                .texOffs(0, 34).addBox(0, -2, -10, 12, 2, 2), PartPose.offsetAndRotation(8, 0, 0, 0, 0, 0.28f));
        canopy.addOrReplaceChild("wing_r", CubeListBuilder.create()
                .texOffs(0, 17).addBox(-12, -1, -8, 12, 1, 16)
                .texOffs(0, 34).addBox(-12, -2, -10, 12, 2, 2), PartPose.offsetAndRotation(-8, 0, 0, 0, 0, -0.28f));
        // tassels along the trailing edge (the outer ones follow the drooping wings)
        float[][] tassels = {{0, 0}, {6, 0}, {-6, 0}, {11.8f, 1.1f}, {-11.8f, 1.1f}, {17.6f, 2.8f}, {-17.6f, 2.8f}};
        for (int i = 0; i < tassels.length; i++) {
            canopy.addOrReplaceChild("tassel_" + i, CubeListBuilder.create()
                    .texOffs(0, 40).addBox(-0.5f, 0, -0.5f, 1, 4, 1), PartPose.offset(tassels[i][0], tassels[i][1], 8));
        }
        // two long ribbons trailing behind and below the canopy
        root.addOrReplaceChild("ribbon_l", CubeListBuilder.create()
                .texOffs(64, 40).addBox(-1, 0, 0, 2, 1, 20), PartPose.offsetAndRotation(5, -22, 8, -0.45f, 0, 0));
        root.addOrReplaceChild("ribbon_r", CubeListBuilder.create()
                .texOffs(64, 40).addBox(-1, 0, 0, 2, 1, 20), PartPose.offsetAndRotation(-5, -22, 8, -0.45f, 0, 0));
        root.addOrReplaceChild("strut_l", CubeListBuilder.create()
                .texOffs(110, 40).addBox(-0.5f, 0, -0.5f, 1, 13, 1), PartPose.offset(5, -22, -1));
        root.addOrReplaceChild("strut_r", CubeListBuilder.create()
                .texOffs(110, 40).addBox(-0.5f, 0, -0.5f, 1, 13, 1), PartPose.offset(-5, -22, -1));
        return LayerDefinition.create(mesh, 128, 64);
    }

    public void setRibbons(boolean visible) {
        ribbonLeft.visible = visible;
        ribbonRight.visible = visible;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
